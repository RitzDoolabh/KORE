package org.knightmesh.runtime.router;

import org.junit.jupiter.api.Test;
import org.knightmesh.core.model.ServiceMetrics;
import org.knightmesh.core.model.ServiceRequest;
import org.knightmesh.core.model.ServiceResponse;
import org.knightmesh.core.service.CKService;
import org.knightmesh.runtime.registry.LocalServiceDescriptor;
import org.knightmesh.runtime.registry.LocalServiceRegistry;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ServiceRouterRemoteTest {

    static class NoopService implements CKService {
        private final ServiceMetrics metrics = new ServiceMetrics(0, 0.0, 0, 0);
        @Override public String getServiceName() { return "REMOTE_ECHO"; }
        @Override public ServiceResponse execute(ServiceRequest request) { return ServiceResponse.success(Map.of("local", true)); }
        @Override public ServiceMetrics getMetrics() { return metrics; }
    }

    static class FixedRemoteLocator implements RemoteServiceLocator {
        private final List<ServiceInstance> list;
        FixedRemoteLocator(List<ServiceInstance> list) { this.list = list; }
        @Override public List<ServiceInstance> findInstances(String serviceName) { return list; }
    }

    @Test
    void route_delegates_to_remote_when_local_full() {
        // Registry with a service that has no capacity (maxThreads = 0)
        LocalServiceRegistry registry = new LocalServiceRegistry();
        registry.register(new LocalServiceDescriptor("REMOTE_ECHO", new NoopService(), 0));

        // Remote locator returns a single instance
        ServiceInstance inst = new ServiceInstance("127.0.0.1", 9999, Map.of());
        RemoteServiceLocator locator = new FixedRemoteLocator(List.of(inst));

        // RestTemplate mocked
        RestTemplate restTemplate = mock(RestTemplate.class);
        String url = inst.baseUrl() + "/internal/service/REMOTE_ECHO";
        when(restTemplate.postForObject(eq(url), any(ServiceRequest.class), eq(ServiceResponse.class)))
                .thenReturn(ServiceResponse.success(Map.of("y", 2)));

        ServiceRouter router = new ServiceRouter(registry, locator, restTemplate);
        ServiceRequest req = new ServiceRequest("REMOTE_ECHO", Map.of("x", 1), Map.of(), "c2");
        ServiceResponse resp = router.route(req);

        verify(restTemplate, times(1)).postForObject(eq(url), any(ServiceRequest.class), eq(ServiceResponse.class));
        assertEquals(ServiceResponse.Status.SUCCESS, resp.getStatus());
        assertEquals(2, resp.getData().get("y"));
    }
}
