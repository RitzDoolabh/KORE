package org.knightmesh.runtime.router;

import org.knightmesh.core.model.ServiceRequest;
import org.knightmesh.core.model.ServiceResponse;
import org.springframework.stereotype.Component;

/**
 * Default stub implementation that simulates remote routing.
 */
@Component
public class NoopKubernetesServiceLocator implements KubernetesServiceLocator {
    @Override
    public ServiceResponse route(ServiceRequest request) {
        return ServiceResponse.failure("DELEGATED", "Routed to remote (stub)", null);
    }
}
