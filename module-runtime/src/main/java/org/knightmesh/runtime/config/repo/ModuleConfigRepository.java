package org.knightmesh.runtime.config.repo;

import org.knightmesh.core.config.ModuleConfig;
import org.knightmesh.core.config.ModuleType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ModuleConfigRepository extends JpaRepository<ModuleConfig, UUID> {
    Optional<ModuleConfig> findByTypeAndInstance(ModuleType type, String instance);
    List<ModuleConfig> findByEnabledTrue();
    Optional<ModuleConfig> findByName(String name);
}
