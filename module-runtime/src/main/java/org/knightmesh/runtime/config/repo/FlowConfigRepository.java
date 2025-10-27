package org.knightmesh.runtime.config.repo;

import org.knightmesh.core.config.FlowConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface FlowConfigRepository extends JpaRepository<FlowConfig, UUID> {
    Optional<FlowConfig> findByName(String name);
}
