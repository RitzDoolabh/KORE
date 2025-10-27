package org.knightmesh.spm.services;

import org.knightmesh.core.annotations.CKServiceRegistration;
import org.knightmesh.core.model.ServiceMetrics;
import org.knightmesh.core.model.ServiceRequest;
import org.knightmesh.core.model.ServiceResponse;
import org.knightmesh.core.service.CKService;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Demo authentication service that returns a mock auth token.
 */
@Component
@CKServiceRegistration(name = "USER_AUTH")
public class UserAuthService implements CKService {

    private final ServiceMetrics metrics = new ServiceMetrics(16, 2.5, 0, 0);

    @Override
    public String getServiceName() {
        return "USER_AUTH";
    }

    @Override
    public ServiceResponse execute(ServiceRequest request) {
        String user = (String) request.getPayload().getOrDefault("user", "unknown");
        // Simulate small work
        String token = "token-" + user + "-123";
        return ServiceResponse.success(Map.of("user", user, "token", token));
    }

    @Override
    public ServiceMetrics getMetrics() {
        return metrics;
    }
}
