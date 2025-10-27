package org.knightmesh.core.model;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Represents an invocation request for a CK service.
 */
public class ServiceRequest {
    private final String serviceName;
    private final Map<String, Object> payload;
    private final Map<String, String> metadata;
    private final String correlationId;

    public ServiceRequest(String serviceName,
                          Map<String, Object> payload,
                          Map<String, String> metadata,
                          String correlationId) {
        this.serviceName = Objects.requireNonNull(serviceName, "serviceName");
        this.payload = payload == null ? Collections.emptyMap() : Collections.unmodifiableMap(payload);
        this.metadata = metadata == null ? Collections.emptyMap() : Collections.unmodifiableMap(metadata);
        this.correlationId = correlationId;
    }

    public String getServiceName() {
        return serviceName;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    @Override
    public String toString() {
        return "ServiceRequest{" +
                "serviceName='" + serviceName + '\'' +
                ", correlationId='" + correlationId + '\'' +
                ", payloadKeys=" + payload.keySet() +
                ", metadataKeys=" + metadata.keySet() +
                '}';
    }
}
