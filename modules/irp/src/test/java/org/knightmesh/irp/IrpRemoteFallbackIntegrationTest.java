package org.knightmesh.irp;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.knightmesh.core.config.ModuleConfig;
import org.knightmesh.core.config.ModuleType;
import org.knightmesh.core.config.RouteMode;
import org.knightmesh.core.model.ServiceRequest;
import org.knightmesh.core.model.ServiceResponse;
import org.knightmesh.runtime.registry.LocalServiceDescriptor;
import org.knightmesh.runtime.registry.LocalServiceRegistry;
import org.knightmesh.runtime.router.RemoteServiceLocator;
import org.knightmesh.runtime.router.ServiceInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies that when local capacity is full, the router falls back to remote discovery and HTTP.
 */
@SpringBootTest(classes = {IrpApplication.class, IrpRemoteFallbackIntegrationTest.TestConfig.class},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                // Ensure DIRECT mode so IRP routes synchronously
                "spring.jpa.hibernate.ddl-auto=none",
                "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration"
        })
@AutoConfigureMockMvc
@ActiveProfiles("test")
class IrpRemoteFallbackIntegrationTest {

    static WireMockServer wireMock;

    @BeforeAll
    static void setupWireMock() {
        wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();
        WireMock.configureFor("localhost", wireMock.port());
        wireMock.stubFor(WireMock.post(WireMock.urlPathEqualTo("/internal/service/REGISTER_USER"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":\"SUCCESS\",\"data\":{\"ok\":true}}")));
        System.setProperty("remote.port", String.valueOf(wireMock.port()));
    }

    @AfterAll
    static void tearDown() {
        if (wireMock != null) wireMock.stop();
        System.clearProperty("remote.port");
    }

    @Configuration
    static class TestConfig {
        @Bean
        RemoteServiceLocator testRemoteLocator() {
            int port = Integer.parseInt(System.getProperty("remote.port"));
            return serviceName -> List.of(new ServiceInstance("localhost", port, Map.of()));
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LocalServiceRegistry registry;

    @Test
    void when_local_capacity_full_router_calls_remote_instance() throws Exception {
        // Register a local descriptor with maxThreads=1 and saturate it
        LocalServiceDescriptor d = new LocalServiceDescriptor("REGISTER_USER", new org.knightmesh.core.service.CKService() {
            @Override
            public String getServiceName() { return "REGISTER_USER"; }
            @Override
            public ServiceResponse execute(ServiceRequest request) { return ServiceResponse.success(Map.of("local", true)); }
            @Override
            public org.knightmesh.core.model.ServiceMetrics getMetrics() { return new org.knightmesh.core.model.ServiceMetrics(1, 1.0, 0, 0); }
        }, 1);
        registry.register(d);
        // Saturate
        d.incrementActive();

        // Now perform IRP call; should go remote (WireMock)
        String body = "{\"username\":\"bob\",\"email\":\"bob@example.org\"}";
        mockMvc.perform(post("/irp/REGISTER_USER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("SUCCESS")))
                .andExpect(jsonPath("$.data.ok", is(true)));

        // Verify remote endpoint was called exactly once
        wireMock.verify(1, postRequestedFor(urlPathEqualTo("/internal/service/REGISTER_USER")));
    }
}
