package org.knightmesh.runtime.router;

import org.junit.jupiter.api.Test;
import org.knightmesh.core.model.ServiceRequest;
import org.knightmesh.core.model.ServiceResponse;
import org.knightmesh.runtime.ModuleRuntimeApplication;
import org.knightmesh.runtime.registry.LocalServiceRegistry;
import org.mockito.ArgumentMatchers;
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

@SpringBootTest(classes = {ModuleRuntimeApplication.class, ServiceRouterRetryTest.TestConfig.class})
@ActiveProfiles("test")
// Use default retry properties from application-test.properties: 3 attempts with backoff
@TestPropertySource(properties = {
        // Keep CB permissive here so we only validate retry behavior
        "resilience4j.circuitbreaker.instances.remoteRouter.failure-rate-threshold=100",
        "resilience4j.circuitbreaker.instances.remoteRouter.minimum-number-of-calls=5"
})
class ServiceRouterRetryTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        ServiceRouter serviceRouter(LocalServiceRegistry registry, RemoteServiceLocator locator, RemoteHttpInvoker invoker) {
            return new ServiceRouter(registry, locator, invoker);
        }

        @Bean
        RemoteServiceLocator remoteServiceLocator() {
            return serviceName -> List.of(new ServiceInstance("localhost", 8089, Map.of("scheme", "http")));
        }
    }

    @Autowired
    private ServiceRouter router;

    @MockBean
    private RestTemplate restTemplate; // used by RemoteHttpInvoker

    @Test
    void router_retries_remote_call_and_succeeds_on_third_attempt() {
        // First two attempts throw, third succeeds
        when(restTemplate.postForObject(any(String.class), any(ServiceRequest.class), eq(ServiceResponse.class)))
                .thenThrow(new RuntimeException("remote down #1"))
                .thenThrow(new RuntimeException("remote down #2"))
                .thenReturn(ServiceResponse.success(Map.of("ok", true)));

        ServiceRequest req = new ServiceRequest("SVC_REMOTE", Map.of("x", 1), Map.of(), "c-retry-1");
        ServiceResponse resp = router.route(req);

        assertThat(resp.getStatus()).isEqualTo(ServiceResponse.Status.SUCCESS);
        assertThat(resp.getData().get("ok")).isEqualTo(true);
        verify(restTemplate, times(3)).postForObject(any(String.class), any(ServiceRequest.class), eq(ServiceResponse.class));
    }
}
