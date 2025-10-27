package org.knightmesh.runtime.monitoring;

import org.knightmesh.runtime.registry.LocalServiceDescriptor;
import org.knightmesh.runtime.registry.LocalServiceRegistry;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Utility to observe and adjust per-service thread usage.
 */
@Component
public class ThreadPoolMonitor {

    private final LocalServiceRegistry registry;

    public ThreadPoolMonitor(LocalServiceRegistry registry) {
        this.registry = registry;
    }

    public void increment(String serviceName) {
        LocalServiceDescriptor d = registry.get(serviceName);
        if (d != null) {
            d.getActiveThreads().incrementAndGet();
        }
    }

    public void decrement(String serviceName) {
        LocalServiceDescriptor d = registry.get(serviceName);
        if (d != null) {
            d.getActiveThreads().decrementAndGet();
        }
    }

    public void setMaxThreads(String serviceName, int maxThreads) {
        LocalServiceDescriptor d = registry.get(serviceName);
        if (d != null) {
            d.setMaxThreads(maxThreads);
        }
    }

    public Map<String, LocalServiceRegistry.CapacityView> snapshot() {
        return registry.capacitySnapshot();
    }
}
