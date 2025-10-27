package org.knightmesh.runtime.router;

import java.util.List;

/**
 * Discovers remote service instances capable of handling a given service name.
 */
public interface RemoteServiceLocator {
    List<ServiceInstance> findInstances(String serviceName);
}
