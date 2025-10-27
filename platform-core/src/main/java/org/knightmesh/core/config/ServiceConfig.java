package org.knightmesh.core.config;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "service_config")
public class ServiceConfig {
    public ServiceConfig() {}
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    // Logical service name (e.g., REGISTER_USER)
    @Column(name = "service_name", length = 120, unique = true)
    private String serviceName;

    // Owning module name (e.g., spm)
    @Column(name = "module_name", length = 120)
    private String moduleName;

    @Column(name = "max_threads", nullable = false)
    private int maxThreads = 4;

    // Optional backward-compat fields
    @Lob
    @Column(name = "dependencies", columnDefinition = "text")
    private String dependenciesJson; // JSON array of dependency service names

    @Lob
    @Column(name = "config_json", columnDefinition = "text")
    private String configJson; // Additional JSON config per service

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getServiceName() { return serviceName; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }

    public String getModuleName() { return moduleName; }
    public void setModuleName(String moduleName) { this.moduleName = moduleName; }

    public int getMaxThreads() { return maxThreads; }
    public void setMaxThreads(int maxThreads) { this.maxThreads = maxThreads; }

    public String getDependenciesJson() { return dependenciesJson; }
    public void setDependenciesJson(String dependenciesJson) { this.dependenciesJson = dependenciesJson; }

    public String getConfigJson() { return configJson; }
    public void setConfigJson(String configJson) { this.configJson = configJson; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
