package org.knightmesh.runtime.registry;

/**
 * Represents the lifecycle/availability of a local service instance.
 */
public enum ServiceStatus {
    UP,
    DOWN,
    DRAINING
}
