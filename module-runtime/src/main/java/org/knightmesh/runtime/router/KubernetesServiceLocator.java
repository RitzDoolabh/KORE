package org.knightmesh.runtime.router;

import org.knightmesh.core.model.ServiceRequest;
import org.knightmesh.core.model.ServiceResponse;

/**
 * Abstraction for delegating a service execution to the cluster (e.g., via Kubernetes Service/HTTP/GRPC).
 */
public interface KubernetesServiceLocator {

    /**
     * Route the given request to a remote node/pod that can handle it.
     */
    ServiceResponse route(ServiceRequest request);
}
