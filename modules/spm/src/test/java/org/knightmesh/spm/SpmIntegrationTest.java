package org.knightmesh.spm;

import org.junit.jupiter.api.Test;
import org.knightmesh.core.model.ServiceRequest;
import org.knightmesh.core.model.ServiceResponse;
import org.knightmesh.core.service.CKService;
import org.knightmesh.core.service.ServiceInvoker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = {SpmApplication.class, SpmIntegrationTest.LocalInvokerConfig.class})
class SpmIntegrationTest {

    @TestConfiguration
    static class LocalInvokerConfig {
        @Bean
        ServiceInvoker localServiceInvoker(ApplicationContext applicationContext) {
            return request -> {
                Map<String, CKService> byType = applicationContext.getBeansOfType(CKService.class);
                for (CKService svc : byType.values()) {
                    if (svc.getServiceName().equals(request.getServiceName())) {
                        return svc.execute(request);
                    }
                }
                return ServiceResponse.failure("NOT_FOUND", "No local service for name: " + request.getServiceName(), null);
            };
        }
    }

    @Autowired
    private ServiceInvoker invoker;

    @Test
    void register_user_routes_to_user_auth_and_succeeds() {
        ServiceRequest req = new ServiceRequest(
                "REGISTER_USER",
                Map.of("username", "alice", "email", "alice@example.org", "password", "pw"),
                Map.of("source", "spm-it"),
                "corr-spm-1"
        );
        ServiceResponse resp = invoker.invoke(req);
        assertEquals(ServiceResponse.Status.SUCCESS, resp.getStatus());
        assertEquals("alice", resp.getData().get("user"));
        assertEquals("alice@example.org", resp.getData().get("email"));
        assertNotNull(resp.getData().get("userId"));
        assertNotNull(resp.getData().get("token"));
    }
}
