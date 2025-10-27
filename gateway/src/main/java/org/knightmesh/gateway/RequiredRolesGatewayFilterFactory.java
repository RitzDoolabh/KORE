package org.knightmesh.gateway;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@Component
public class RequiredRolesGatewayFilterFactory extends AbstractGatewayFilterFactory<RequiredRolesGatewayFilterFactory.Config> {

    public RequiredRolesGatewayFilterFactory() {
        super(Config.class);
    }

    public static class Config {
        private String rolesCsv;
        public String getRolesCsv() { return rolesCsv; }
        public void setRolesCsv(String rolesCsv) { this.rolesCsv = rolesCsv; }
    }

    @Override
    public GatewayFilter apply(Config config) {
        final Set<String> required = new HashSet<>();
        if (config.rolesCsv != null && !config.rolesCsv.isBlank()) {
            Arrays.stream(config.rolesCsv.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .forEach(r -> required.add("ROLE_" + r));
        }
        return (exchange, chain) -> {
            if (required.isEmpty()) {
                return chain.filter(exchange);
            }
            return ReactiveSecurityContextHolder.getContext()
                    .flatMap(ctx -> authorize(exchange, chain, ctx.getAuthentication(), required))
                    .switchIfEmpty(unauthorized(exchange));
        };
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }

    private Mono<Void> forbid(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
        return exchange.getResponse().setComplete();
    }

    private Mono<Void> authorize(ServerWebExchange exchange,
                                 org.springframework.cloud.gateway.filter.GatewayFilterChain chain,
                                 Authentication auth,
                                 Set<String> required) {
        if (auth == null || !auth.isAuthenticated()) {
            return unauthorized(exchange);
        }
        Set<String> have = new HashSet<>();
        for (GrantedAuthority ga : auth.getAuthorities()) {
            have.add(ga.getAuthority());
        }
        boolean ok = required.stream().allMatch(have::contains) || required.stream().anyMatch(have::contains);
        // By default, accept if any required role is present; adjust to all-match if needed per route metadata later
        if (!ok) {
            return forbid(exchange);
        }
        return chain.filter(exchange);
    }
}
