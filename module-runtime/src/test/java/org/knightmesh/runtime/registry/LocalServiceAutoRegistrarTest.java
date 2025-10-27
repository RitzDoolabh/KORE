package org.knightmesh.runtime.registry;

import org.junit.jupiter.api.Test;
import org.knightmesh.core.model.ServiceRequest;
import org.knightmesh.core.model.ServiceResponse;
import org.knightmesh.runtime.ModuleRuntimeApplication;
import org.knightmesh.runtime.router.ServiceRouter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that {@link LocalServiceAutoRegistrar} discovers CKService beans and
 * registers them into the {@link LocalServiceRegistry} on context refresh.
 */
@SpringBootTest(classes = ModuleRuntimeApplication.class)
@ActiveProfiles("test")
class LocalServiceAutoRegistrarTest {

    @Autowired
    private LocalServiceRegistry registry;

    @Autowired
    private ServiceRouter router; // also ensure routing bean loads

    @Test
    void registry_is_populated_on_startup() {
        var snapshot = registry.capacitySnapshot();
        // From modules:spm we expect REGISTER_USER and USER_AUTH demo services
        assertTrue(snapshot.containsKey("REGISTER_USER"), "REGISTER_USER should be auto-registered");
        assertTrue(snapshot.containsKey("USER_AUTH"), "USER_AUTH should be auto-registered");
    }

    @Test
    void routing_uses_locally_registered_service() {
        ServiceRequest req = new ServiceRequest(
                "USER_AUTH",
                Map.of("user", "bob", "password", "pw"),
                Map.of(),
                "corr-auto-1"
        );
        ServiceResponse resp = router.route(req);
        assertEquals(ServiceResponse.Status.SUCCESS, resp.getStatus());
        assertEquals("bob", resp.getData().get("user"));
        assertNotNull(resp.getData().get("token"));
    }
}
