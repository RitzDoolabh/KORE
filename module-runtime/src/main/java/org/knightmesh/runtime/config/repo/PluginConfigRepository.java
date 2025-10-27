package org.knightmesh.runtime.config.repo;

import org.knightmesh.core.config.PluginConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PluginConfigRepository extends JpaRepository<PluginConfig, UUID> {
    Optional<PluginConfig> findByName(String name);
}
