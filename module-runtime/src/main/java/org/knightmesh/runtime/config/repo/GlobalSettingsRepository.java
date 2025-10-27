package org.knightmesh.runtime.config.repo;

import org.knightmesh.core.config.GlobalSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface GlobalSettingsRepository extends JpaRepository<GlobalSettings, UUID> {
}
