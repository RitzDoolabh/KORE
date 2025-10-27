package org.knightmesh.core.config;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "gateway_route")
public class GatewayRoute {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @Column(name = "path_pattern", nullable = false, length = 300)
    private String pathPattern; // e.g., /irp/**

    @Column(name = "uri", nullable = false, length = 500)
    private String uri; // e.g., http://irp:8080

    @Column(name = "required_roles", length = 300)
    private String requiredRoles; // CSV of roles, e.g., ADMIN or USER,SERVICE

    @Column(name = "strip_prefix")
    private Integer stripPrefix = 0; // default: do not strip prefix (modules expect /irp/** etc)

    @Lob
    @Column(name = "filters_json")
    private String filtersJson; // future extension for custom filters

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getPathPattern() { return pathPattern; }
    public void setPathPattern(String pathPattern) { this.pathPattern = pathPattern; }

    public String getUri() { return uri; }
    public void setUri(String uri) { this.uri = uri; }

    public String getRequiredRoles() { return requiredRoles; }
    public void setRequiredRoles(String requiredRoles) { this.requiredRoles = requiredRoles; }

    public Integer getStripPrefix() { return stripPrefix; }
    public void setStripPrefix(Integer stripPrefix) { this.stripPrefix = stripPrefix; }

    public String getFiltersJson() { return filtersJson; }
    public void setFiltersJson(String filtersJson) { this.filtersJson = filtersJson; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
