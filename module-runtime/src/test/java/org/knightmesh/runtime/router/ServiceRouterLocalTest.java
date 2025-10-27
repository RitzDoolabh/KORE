package org.knightmesh.runtime.router;

import org.junit.jupiter.api.Test;
import org.knightmesh.core.model.ServiceMetrics;
import org.knightmesh.core.model.ServiceRequest;
import org.knightmesh.core.model.ServiceResponse;
import org.knightmesh.core.service.CKService;
import org.knightmesh.runtime.registry.LocalServiceDescriptor;
import org.knightmesh.runtime.registry.LocalServiceRegistry;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ServiceRouterLocalTest {

    static class FakeLocalService implements CKService {
        private final ServiceMetrics metrics = new ServiceMetrics(3, 1.0, 0, 0);
        @Override public String getServiceName() { return "ECHO"; }
        @Override public ServiceResponse execute(ServiceRequest request) { return ServiceResponse.success(request.getPayload()); }
        @Override public ServiceMetrics getMetrics() { return metrics; }
    }

    static class FailingKubeLocator implements KubernetesServiceLocator {
        @Override public ServiceResponse route(ServiceRequest request) {
            fail("Should not delegate to kubeLocator for local available path");
            return null;
        }
    }

    @Test
    void route_executes_locally_when_capacity_available() {
        LocalServiceRegistry registry = new LocalServiceRegistry();
        FakeLocalService svc = new FakeLocalService();
        registry.register(new LocalServiceDescriptor("ECHO", svc, 3));

        ServiceRouter router = new ServiceRouter(registry, new FailingKubeLocator());

        ServiceRequest req = new ServiceRequest("ECHO", Map.of("x", 1), Map.of(), "c1");
        ServiceResponse resp = router.route(req);
        assertEquals(ServiceResponse.Status.SUCCESS, resp.getStatus());
        assertEquals(1, resp.getData().get("x"));
    }
}
