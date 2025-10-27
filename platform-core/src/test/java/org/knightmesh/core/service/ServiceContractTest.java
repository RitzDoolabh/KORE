package org.knightmesh.core.service;

import org.junit.jupiter.api.Test;
import org.knightmesh.core.annotations.CKServiceRegistration;
import org.knightmesh.core.model.ServiceMetrics;
import org.knightmesh.core.model.ServiceRequest;
import org.knightmesh.core.model.ServiceResponse;

import static org.junit.jupiter.api.Assertions.*;

class ServiceContractTest {

    @CKServiceRegistration(name = "DUMMY_SERVICE")
    static class DummyService implements CKService {
        private final ServiceMetrics metrics = new ServiceMetrics(8, 5.0, 2, 0);

        @Override
        public String getServiceName() {
            return "DUMMY_SERVICE";
        }

        @Override
        public ServiceResponse execute(ServiceRequest request) {
            if (!"DUMMY_SERVICE".equals(request.getServiceName())) {
                return ServiceResponse.failure("WRONG_SERVICE", "Wrong service name", null);
            }
            return ServiceResponse.success(request.getPayload());
        }

        @Override
        public ServiceMetrics getMetrics() {
            return metrics;
        }
    }

    @Test
    void annotation_present_and_methods_behave() {
        DummyService svc = new DummyService();
        var ann = svc.getClass().getAnnotation(CKServiceRegistration.class);
        assertNotNull(ann);
        assertEquals("DUMMY_SERVICE", ann.name());
        assertEquals("DUMMY_SERVICE", svc.getServiceName());

        ServiceRequest req = new ServiceRequest("DUMMY_SERVICE", java.util.Map.of("echo", 1), java.util.Map.of(), "c1");
        ServiceResponse resp = svc.execute(req);
        assertEquals(ServiceResponse.Status.SUCCESS, resp.getStatus());
        assertEquals(1, resp.getData().get("echo"));
        assertNull(resp.getErrorCode());
        assertNull(resp.getErrorMessage());

        assertEquals(8, svc.getMetrics().getMaxThreads());
    }
}
