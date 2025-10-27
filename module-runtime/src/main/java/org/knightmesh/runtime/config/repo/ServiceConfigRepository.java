package org.knightmesh.runtime.config.repo;

import org.knightmesh.core.config.ServiceConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ServiceConfigRepository extends JpaRepository<ServiceConfig, UUID> {
    Optional<ServiceConfig> findByServiceName(String serviceName);
    List<ServiceConfig> findByModuleNameAndEnabledTrue(String moduleName);
}
