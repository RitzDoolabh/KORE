package org.knightmesh.irp;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.knightmesh.core.config.ModuleConfig;
import org.knightmesh.core.config.ModuleType;
import org.knightmesh.core.config.RouteMode;
import org.knightmesh.runtime.config.repo.ModuleConfigRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Full integration test that starts a PostgreSQL Testcontainer, applies Flyway migrations,
 * boots the IRP Spring context (scanning SPM and runtime), seeds ModuleConfig with DIRECT routing,
 * and verifies that POSTing to IRP routes into local SPM services and returns SUCCESS.
 */
@SpringBootTest(classes = IrpApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@org.springframework.boot.autoconfigure.domain.EntityScan(basePackages = {"org.knightmesh.core.config", "org.knightmesh.plugins.queue"})
@org.springframework.data.jpa.repository.config.EnableJpaRepositories(basePackages = {"org.knightmesh.runtime.config.repo"})
@Testcontainers
class IrpSpmPostgresIntegrationTest {

    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("km")
            .withUsername("km")
            .withPassword("km");

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        postgres.start();
        registry.add("spring.datasource.url", () -> postgres.getJdbcUrl());
        registry.add("spring.datasource.username", () -> postgres.getUsername());
        registry.add("spring.datasource.password", () -> postgres.getPassword());
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ModuleConfigRepository moduleConfigRepository;

    @BeforeAll
    static void beforeAll() {
        // container started via @DynamicPropertySource
    }

    @AfterAll
    static void afterAll() {
        if (postgres != null) postgres.stop();
    }

    @Test
    void irp_to_spm_flow_succeeds_with_postgres_backing_config() throws Exception {
        // Seed the IRP ModuleConfig with DIRECT route mode
        ModuleConfig mc = new ModuleConfig();
        mc.setName("irp");
        mc.setType(ModuleType.IRP);
        mc.setEnabled(true);
        mc.setRouteMode(RouteMode.DIRECT);
        mc.setDomain("dev");
        mc.setInstance("irp-1");
        mc.setCreatedAt(OffsetDateTime.now());
        mc.setUpdatedAt(OffsetDateTime.now());
        moduleConfigRepository.save(mc);

        String body = "{\"username\":\"alice\",\"email\":\"alice@example.org\"}";

        mockMvc.perform(post("/irp/REGISTER_USER")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("SUCCESS")))
                .andExpect(jsonPath("$.data.user", is("alice")))
                .andExpect(jsonPath("$.data.email", is("alice@example.org")))
                .andExpect(jsonPath("$.data.token", notNullValue()));
    }
}
