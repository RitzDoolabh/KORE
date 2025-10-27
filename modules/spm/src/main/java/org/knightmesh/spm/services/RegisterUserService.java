package org.knightmesh.spm.services;

import org.knightmesh.core.annotations.CKServiceRegistration;
import org.knightmesh.core.model.ServiceMetrics;
import org.knightmesh.core.model.ServiceRequest;
import org.knightmesh.core.model.ServiceResponse;
import org.knightmesh.core.service.CKService;
import org.knightmesh.core.service.ServiceInvoker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@CKServiceRegistration(name = "REGISTER_USER")
public class RegisterUserService implements CKService {

    private static final Logger log = LoggerFactory.getLogger(RegisterUserService.class);

    private final ServiceInvoker invoker;
    private final ServiceMetrics metrics = new ServiceMetrics(8, 3.0, 0, 0);

    public RegisterUserService(ServiceInvoker invoker) {
        this.invoker = invoker;
    }

    @Override
    public String getServiceName() {
        return "REGISTER_USER";
    }

    @Override
    public ServiceResponse execute(ServiceRequest request) {
        // Validate input
        String username = (String) request.getPayload().getOrDefault("username", request.getPayload().get("user"));
        String email = (String) request.getPayload().get("email");
        if (username == null || username.isBlank()) {
            return ServiceResponse.failure("VALIDATION_ERROR", "Missing required field: username", null);
        }
        if (email == null || email.isBlank()) {
            return ServiceResponse.failure("VALIDATION_ERROR", "Missing required field: email", null);
        }
        log.info("[REGISTER_USER] creating user username={}, email={}", username, email);

        // mock: "persist" user and assign ID
        int userId = Math.abs((username + email).hashCode());

        // Call USER_AUTH via ServiceInvoker
        ServiceRequest authReq = new ServiceRequest(
                "USER_AUTH",
                Map.of("user", username, "password", request.getPayload().getOrDefault("password", "")),
                request.getMetadata(),
                request.getCorrelationId()
        );
        ServiceResponse authResp = invoker.invoke(authReq);
        if (authResp.getStatus() != ServiceResponse.Status.SUCCESS) {
            return ServiceResponse.failure("AUTH_FAILED", "Authentication failed during registration", null);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("userId", userId);
        data.put("user", username);
        data.put("email", email);
        // include token from auth service
        Object token = authResp.getData().get("token");
        data.put("token", token);
        return ServiceResponse.success(data);
    }

    @Override
    public ServiceMetrics getMetrics() {
        return metrics;
    }
}
