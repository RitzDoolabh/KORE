package org.knightmesh.gateway;

import org.knightmesh.core.config.GatewayRoute;
import org.knightmesh.runtime.config.ConfigRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;
import java.util.List;

@Configuration
public class RouteConfig {

    @Bean
    public RouteLocator routeLocator(RouteLocatorBuilder builder,
                                     org.springframework.beans.factory.ObjectProvider<org.knightmesh.runtime.config.ConfigRepository> configRepositoryProvider,
                                     RequiredRolesGatewayFilterFactory requiredRoles,
                                     @Value("${gateway.mgm.uri:http://mgm:8080}") String mgmUri,
                                     @Value("${gateway.irp.uri:http://irp:8080}") String irpUri,
                                     @Value("${gateway.spm.uri:http://spm:8080}") String spmUri) {
        var configRepository = configRepositoryProvider != null ? configRepositoryProvider.getIfAvailable() : null;
        List<GatewayRoute> routes = configRepository != null ? configRepository.listGatewayRoutes() : List.of();
        RouteLocatorBuilder.Builder routesBuilder = builder.routes();

        if (routes != null && !routes.isEmpty()) {
            for (GatewayRoute gr : routes) {
                if (!gr.isEnabled()) continue;
                routesBuilder.route(gr.getPathPattern(), r -> r
                        .path(gr.getPathPattern())
                        .filters(f -> {
                            Integer sp = gr.getStripPrefix();
                            if (sp != null && sp > 0) {
                                f.stripPrefix(sp);
                            }
                            if (gr.getRequiredRoles() != null && !gr.getRequiredRoles().isBlank()) {
                                RequiredRolesGatewayFilterFactory.Config cfg = new RequiredRolesGatewayFilterFactory.Config();
                                cfg.setRolesCsv(gr.getRequiredRoles());
                                f.filter(requiredRoles.apply(cfg));
                            }
                            return f;
                        })
                        .uri(URI.create(gr.getUri()))
                );
            }
        } else {
            // Fallback static routes if DB not populated; URIs from properties
            routesBuilder
                .route("mgm", r -> r.path("/mgm/**")
                        .filters(f -> f.stripPrefix(0))
                        .uri(mgmUri))
                .route("irp", r -> r.path("/irp/**")
                        .filters(f -> f.stripPrefix(0))
                        .uri(irpUri))
                .route("spm", r -> r.path("/spm/**")
                        .filters(f -> f.stripPrefix(0))
                        .uri(spmUri));
        }
        return routesBuilder.build();
    }
}
