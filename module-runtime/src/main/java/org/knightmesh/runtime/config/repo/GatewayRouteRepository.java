package org.knightmesh.runtime.config.repo;

import org.knightmesh.core.config.GatewayRoute;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface GatewayRouteRepository extends JpaRepository<GatewayRoute, UUID> {
    List<GatewayRoute> findByEnabledTrue();
}
