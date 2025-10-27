package org.knightmesh.runtime.router;

import org.knightmesh.runtime.registry.LocalServiceDescriptor;

/**
 * Helper that wraps LocalServiceDescriptor counters and, in the future, records latency via Micrometer.
 */
public class ThreadPoolMonitor {

    /**
     * Try to reserve a slot by incrementing active threads if capacity exists.
     * @return true if reservation succeeded
     */
    public boolean reserveIfAvailable(LocalServiceDescriptor descriptor) {
        return descriptor != null && descriptor.incrementActive();
    }

    /**
     * Release a previously reserved slot and optionally record latency.
     * @param startNanos start time in nanos for latency measurement (optional)
     */
    public void release(LocalServiceDescriptor descriptor, long startNanos) {
        if (descriptor != null) {
            descriptor.decrementActive();
            // Future: record latency with Micrometer Timer using (System.nanoTime() - startNanos)
        }
    }
}
