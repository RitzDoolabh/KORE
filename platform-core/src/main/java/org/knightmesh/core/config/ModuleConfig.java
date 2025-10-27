package org.knightmesh.core.config;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "module_config")
public class ModuleConfig {

    public ModuleConfig() {}

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    // Human-friendly name of the module (e.g., "spm")
    @Column(name = "name", length = 120, unique = true)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 32)
    private ModuleType type;

    // Instance identifier (e.g., spm-1)
    @Column(name = "instance", nullable = false, length = 100)
    private String instance;

    @Column(name = "domain", nullable = false, length = 100)
    private String domain;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "route_mode", nullable = false, length = 32)
    private RouteMode routeMode = RouteMode.LOCAL_FIRST;

    // Optional queue name for inbound messages
    @Column(name = "queue_name", length = 200)
    private String queueName;

    // Comma-separated list of services hosted by this module (denormalized for now)
    @Lob
    @Column(name = "services")
    private String services;

    // Extra JSON configuration
    @Lob
    @Column(name = "extra_json")
    private String extraJson;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public ModuleType getType() { return type; }
    public void setType(ModuleType type) { this.type = type; }

    public String getInstance() { return instance; }
    public void setInstance(String instance) { this.instance = instance; }

    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public RouteMode getRouteMode() { return routeMode; }
    public void setRouteMode(RouteMode routeMode) { this.routeMode = routeMode; }

    public String getQueueName() { return queueName; }
    public void setQueueName(String queueName) { this.queueName = queueName; }

    public String getServices() { return services; }
    public void setServices(String services) { this.services = services; }

    public String getExtraJson() { return extraJson; }
    public void setExtraJson(String extraJson) { this.extraJson = extraJson; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
