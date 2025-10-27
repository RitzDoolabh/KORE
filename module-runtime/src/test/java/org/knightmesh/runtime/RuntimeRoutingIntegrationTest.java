package org.knightmesh.runtime;

import org.junit.jupiter.api.Test;
import org.knightmesh.core.model.ServiceRequest;
import org.knightmesh.core.model.ServiceResponse;
import org.knightmesh.runtime.registry.LocalServiceRegistry;
import org.knightmesh.runtime.router.ServiceRouter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = ModuleRuntimeApplication.class)
@ActiveProfiles("test")
class RuntimeRoutingIntegrationTest {

    @Autowired
    private LocalServiceRegistry registry;

    @Autowired
    private ServiceRouter router;

    @Test
    void services_areAutoRegistered_and_routing_executes_locally() {
        // Both demo services should be registered by LocalServiceAutoRegistrar
        var snapshot = registry.capacitySnapshot();
        assertTrue(snapshot.containsKey("REGISTER_USER"), "REGISTER_USER should be registered");
        assertTrue(snapshot.containsKey("USER_AUTH"), "USER_AUTH should be registered");

        // Route a REGISTER_USER request which internally uses USER_AUTH
        ServiceRequest req = new ServiceRequest(
                "REGISTER_USER",
                Map.of("user", "alice", "email", "alice@example.org", "password", "secret"),
                Map.of("source", "it"),
                "corr-it-1"
        );
        ServiceResponse resp = router.route(req);
        assertEquals(ServiceResponse.Status.SUCCESS, resp.getStatus());
        assertNotNull(resp.getData());
        Map<?,?> data = resp.getData();
        assertEquals("alice", data.get("user"));
        assertEquals("alice@example.org", data.get("email"));
        assertNotNull(data.get("userId"));
        assertNotNull(data.get("token"), "Expected token from USER_AUTH");
    }
}
