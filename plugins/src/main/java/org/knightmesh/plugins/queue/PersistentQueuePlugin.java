package org.knightmesh.plugins.queue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.knightmesh.core.model.ServiceRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * JPA-backed persistent queue implementation. Stores messages in a table and dequeues in FIFO order.
 */
@Component
public class PersistentQueuePlugin implements QueuePlugin {
    private static final Logger log = LoggerFactory.getLogger(PersistentQueuePlugin.class);

    private final PersistentQueueMessageRepository repo;
    private final ObjectMapper objectMapper;

    public PersistentQueuePlugin(PersistentQueueMessageRepository repo, ObjectMapper objectMapper) {
        this.repo = repo;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public void enqueue(String queueName, ServiceRequest request) {
        Objects.requireNonNull(queueName, "queueName");
        Objects.requireNonNull(request, "request");
        PersistentQueueMessage m = new PersistentQueueMessage();
        m.setQueueName(queueName);
        m.setStatus("PENDING");
        m.setPayloadJson(serialize(request));
        repo.save(m);
        log.debug("[PersistentQueue] enqueue queue={} corrId={}", queueName, request.getCorrelationId());
    }

    @Override
    @Transactional
    public ServiceRequest dequeue(String queueName) {
        // FIFO: oldest PENDING
        return repo.findTopByQueueNameAndStatusOrderByCreatedAtAsc(queueName, "PENDING")
                .map(m -> {
                    ServiceRequest req = deserialize(m.getPayloadJson());
                    // remove the message to ensure exactly-once for this simple implementation
                    repo.delete(m);
                    log.debug("[PersistentQueue] dequeue queue={} corrId={}", queueName, req.getCorrelationId());
                    return req;
                })
                .orElse(null);
    }

    @Override
    public int size(String queueName) {
        return (int) repo.countByQueueNameAndStatus(queueName, "PENDING");
    }

    private String serialize(ServiceRequest req) {
        Map<String, Object> wrapper = new HashMap<>();
        wrapper.put("serviceName", req.getServiceName());
        wrapper.put("payload", req.getPayload());
        wrapper.put("metadata", req.getMetadata());
        wrapper.put("correlationId", req.getCorrelationId());
        try {
            return objectMapper.writeValueAsString(wrapper);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize ServiceRequest", e);
        }
    }

    @SuppressWarnings("unchecked")
    private ServiceRequest deserialize(String json) {
        try {
            Map<String, Object> wrapper = objectMapper.readValue(json, Map.class);
            String serviceName = (String) wrapper.get("serviceName");
            Map<String, Object> payload = (Map<String, Object>) wrapper.getOrDefault("payload", Map.of());
            Map<String, String> metadata = (Map<String, String>) wrapper.getOrDefault("metadata", Map.of());
            String correlationId = (String) wrapper.get("correlationId");
            return new ServiceRequest(serviceName, payload, metadata, correlationId);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize ServiceRequest", e);
        }
    }
}
