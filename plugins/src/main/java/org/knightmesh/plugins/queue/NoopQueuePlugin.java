package org.knightmesh.plugins.queue;

import org.knightmesh.core.model.ServiceRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Simple QueuePlugin implementation that logs enqueue/dequeue requests.
 * Useful as a default bean so applications can start without a real queue.
 */
@Component
public class NoopQueuePlugin implements QueuePlugin {
    private static final Logger log = LoggerFactory.getLogger(NoopQueuePlugin.class);

    @Override
    public void enqueue(String queueName, ServiceRequest request) {
        log.info("[NoopQueue] Enqueued to {} correlationId={} service={}",
                queueName, request.getCorrelationId(), request.getServiceName());
    }

    @Override
    public ServiceRequest dequeue(String queueName) {
        log.info("[NoopQueue] Dequeue requested from {} (always empty)", queueName);
        return null;
    }

    @Override
    public int size(String queueName) {
        return 0;
    }
}
