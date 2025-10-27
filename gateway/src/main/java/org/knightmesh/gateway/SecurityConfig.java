package org.knightmesh.gateway;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import reactor.core.publisher.Flux;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeExchange(auth -> auth
                .pathMatchers("/actuator/health", "/actuator/info").permitAll()
                .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .pathMatchers("/mgm/**").hasRole("ADMIN")
                .pathMatchers("/irp/**").hasRole("USER")
                .pathMatchers("/spm/**").hasAnyRole("SERVICE")
                .anyExchange().authenticated()
            )
            .oauth2ResourceServer(oauth -> oauth
                .jwt(jwt -> jwt.jwtAuthenticationConverter(reactiveJwtConverter()))
            );
        return http.build();
    }

    private ReactiveJwtAuthenticationConverter reactiveJwtConverter() {
        ReactiveJwtAuthenticationConverter converter = new ReactiveJwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> Flux.fromIterable(mapKeycloakRoles(jwt)));
        return converter;
    }

    private Collection<? extends GrantedAuthority> mapKeycloakRoles(Jwt jwt) {
        Set<String> roles = new HashSet<>();
        Object realmAccess = jwt.getClaim("realm_access");
        if (realmAccess instanceof java.util.Map<?,?> m) {
            Object rs = m.get("roles");
            if (rs instanceof List<?> list) {
                list.forEach(r -> roles.add(String.valueOf(r)));
            }
        }
        Object resourceAccess = jwt.getClaim("resource_access");
        if (resourceAccess instanceof java.util.Map<?,?> res) {
            for (Object clientObj : res.values()) {
                if (clientObj instanceof java.util.Map<?,?> cm) {
                    Object rs = cm.get("roles");
                    if (rs instanceof List<?> list) {
                        list.forEach(r -> roles.add(String.valueOf(r)));
                    }
                }
            }
        }
        return roles.stream().map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList();
    }
}
