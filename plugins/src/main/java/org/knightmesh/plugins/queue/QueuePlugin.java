package org.knightmesh.plugins.queue;

import org.knightmesh.core.model.ServiceRequest;

/**
 * Queue plugin abstraction for enqueuing inbound ServiceRequests for async processing.
 */
public interface QueuePlugin {
    /**
     * Enqueue the given request on the named queue. Implementations may be no-ops in tests.
     */
    void enqueue(String queueName, ServiceRequest request);

    /**
     * Dequeue the next available request from the named queue, or return null if none.
     */
    ServiceRequest dequeue(String queueName);

    /**
     * Return the approximate size of the queue.
     */
    int size(String queueName);
}
