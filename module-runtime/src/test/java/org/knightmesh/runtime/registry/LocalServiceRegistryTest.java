package org.knightmesh.runtime.registry;

import org.junit.jupiter.api.Test;
import org.knightmesh.core.annotations.CKServiceRegistration;
import org.knightmesh.core.model.ServiceMetrics;
import org.knightmesh.core.model.ServiceRequest;
import org.knightmesh.core.model.ServiceResponse;
import org.knightmesh.core.service.CKService;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LocalServiceRegistryTest {

    @CKServiceRegistration(name = "FAKE_SERVICE")
    static class FakeService implements CKService {
        private final ServiceMetrics metrics = new ServiceMetrics(5, 1.0, 0, 0);

        @Override
        public String getServiceName() {
            return "FAKE_SERVICE";
        }

        @Override
        public ServiceResponse execute(ServiceRequest request) {
            return ServiceResponse.success(request.getPayload());
        }

        @Override
        public ServiceMetrics getMetrics() {
            return metrics;
        }
    }

    @Test
    void register_lookup_and_capacity_tracking_work() {
        LocalServiceRegistry registry = new LocalServiceRegistry();
        FakeService svc = new FakeService();

        // Register using descriptor API
        LocalServiceDescriptor d = new LocalServiceDescriptor("FAKE_SERVICE", svc, 5);
        LocalServiceDescriptor registered = registry.register(d);
        assertNotNull(registered);
        assertEquals("FAKE_SERVICE", registered.getServiceName());
        assertEquals(5, registered.getMaxThreads());
        assertNotNull(registered.getLastHeartbeat());
        assertTrue(registered.getLastHeartbeat().isBefore(Instant.now().plusSeconds(1)));

        // Lookup via Optional API
        assertTrue(registry.lookup("FAKE_SERVICE").isPresent());
        LocalServiceDescriptor lookedUp = registry.lookup("FAKE_SERVICE").orElseThrow();
        assertTrue(lookedUp.hasCapacity());

        // Simulate activity increments up to capacity
        int reserves = 0;
        while (lookedUp.incrementActive()) {
            reserves++;
        }
        assertEquals(5, reserves, "Should reserve exactly up to maxThreads");
        assertFalse(lookedUp.hasCapacity(), "No capacity after reaching maxThreads");

        // Release one and verify capacity becomes available again
        lookedUp.decrementActive();
        assertTrue(lookedUp.hasCapacity());

        // Deregister and ensure it's removed
        registry.deregister("FAKE_SERVICE");
        assertTrue(registry.lookup("FAKE_SERVICE").isEmpty());
    }
}
