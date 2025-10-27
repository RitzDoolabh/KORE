package org.knightmesh.plugins.queue;

import org.knightmesh.core.model.ServiceRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Simple in-memory queue plugin backed by a ConcurrentLinkedQueue per queue name.
 * Not suitable for production but useful for tests and demos.
 */
@org.springframework.context.annotation.Primary
@Component
public class InMemoryQueuePlugin implements QueuePlugin {
    private static final Logger log = LoggerFactory.getLogger(InMemoryQueuePlugin.class);

    private final Map<String, Queue<ServiceRequest>> queues = new ConcurrentHashMap<>();

    @Override
    public void enqueue(String queueName, ServiceRequest request) {
        Objects.requireNonNull(queueName, "queueName");
        Objects.requireNonNull(request, "request");
        queues.computeIfAbsent(queueName, q -> new ConcurrentLinkedQueue<>()).add(request);
        log.debug("[InMemoryQueue] enqueue queue={} corrId={} service={}", queueName, request.getCorrelationId(), request.getServiceName());
    }

    @Override
    public ServiceRequest dequeue(String queueName) {
        Queue<ServiceRequest> q = queues.get(queueName);
        ServiceRequest r = (q == null ? null : q.poll());
        if (r != null) {
            log.debug("[InMemoryQueue] dequeue queue={} corrId={} service={}", queueName, r.getCorrelationId(), r.getServiceName());
        }
        return r;
    }

    @Override
    public int size(String queueName) {
        Queue<ServiceRequest> q = queues.get(queueName);
        return q == null ? 0 : q.size();
    }
}
