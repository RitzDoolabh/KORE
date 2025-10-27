package org.knightmesh.runtime.router;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.knightmesh.core.model.ServiceRequest;
import org.knightmesh.core.model.ServiceResponse;
import org.knightmesh.runtime.ModuleRuntimeApplication;
import org.knightmesh.runtime.registry.LocalServiceRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@SpringBootTest(classes = {ModuleRuntimeApplication.class, ServiceRouterCircuitBreakerTest.TestConfig.class})
@ActiveProfiles("test")
@TestPropertySource(properties = {
        // ensure CB trips quickly after 5 failed calls (all fail due to mock)
        "resilience4j.circuitbreaker.instances.remoteRouter.sliding-window-size=5",
        "resilience4j.circuitbreaker.instances.remoteRouter.minimum-number-of-calls=5",
        "resilience4j.circuitbreaker.instances.remoteRouter.failure-rate-threshold=100",
        "resilience4j.circuitbreaker.instances.remoteRouter.wait-duration-in-open-state=1m"
})
class ServiceRouterCircuitBreakerTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        ServiceRouter serviceRouter(LocalServiceRegistry registry, RemoteServiceLocator locator, RemoteHttpInvoker invoker) {
            return new ServiceRouter(registry, locator, invoker);
        }

        @Bean
        RemoteServiceLocator remoteServiceLocator() {
            return serviceName -> List.of(new ServiceInstance("localhost", 8090, Map.of("scheme", "http")));
        }
    }

    @Autowired
    private ServiceRouter router;

    @MockBean
    private RestTemplate restTemplate;

    @BeforeEach
    void setupMockFailures() {
        when(restTemplate.postForObject(any(String.class), any(ServiceRequest.class), eq(ServiceResponse.class)))
                .thenThrow(new RuntimeException("remote down"));
    }

    @Test
    void router_opens_circuit_after_failures_and_short_circuits_subsequent_call() {
        ServiceRequest req = new ServiceRequest("SVC_REMOTE", Map.of("x", 1), Map.of(), "c-cb-1");
        // 5 failing calls to accumulate failures in CB
        for (int i = 0; i < 5; i++) {
            ServiceResponse r = router.route(req);
            assertThat(r.getStatus()).isEqualTo(ServiceResponse.Status.FAILURE);
            assertThat(r.getErrorCode()).isEqualTo("SERVICE_UNAVAILABLE");
        }

        // After CB is open, next call should be short-circuited (no HTTP invocations)
        reset(restTemplate);
        ServiceResponse shortCircuited = router.route(req);
        assertThat(shortCircuited.getStatus()).isEqualTo(ServiceResponse.Status.FAILURE);
        assertThat(shortCircuited.getErrorCode()).isEqualTo("SERVICE_UNAVAILABLE");
        verify(restTemplate, times(0)).postForObject(any(String.class), any(ServiceRequest.class), eq(ServiceResponse.class));
    }
}
