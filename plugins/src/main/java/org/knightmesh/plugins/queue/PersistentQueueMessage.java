package org.knightmesh.plugins.queue;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "persistent_queue_message")
public class PersistentQueueMessage {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @Column(name = "queue_name", nullable = false, length = 200)
    private String queueName;

    @Lob
    @Column(name = "payload_json", columnDefinition = "text")
    private String payloadJson;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "PENDING"; // PENDING, PROCESSING, DONE

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Version
    private long version;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getQueueName() { return queueName; }
    public void setQueueName(String queueName) { this.queueName = queueName; }

    public String getPayloadJson() { return payloadJson; }
    public void setPayloadJson(String payloadJson) { this.payloadJson = payloadJson; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }
}
