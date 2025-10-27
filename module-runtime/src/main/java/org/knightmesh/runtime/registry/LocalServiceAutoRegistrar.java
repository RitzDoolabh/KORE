package org.knightmesh.runtime.registry;

import org.knightmesh.core.annotations.CKServiceRegistration;
import org.knightmesh.core.service.CKService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Auto-discovers CKService beans on context refresh and registers them into the LocalServiceRegistry.
 * It supports discovery by type (CKService) and by @CKServiceRegistration annotation.
 */
@Component
public class LocalServiceAutoRegistrar {

    private static final Logger log = LoggerFactory.getLogger(LocalServiceAutoRegistrar.class);

    private final ApplicationContext applicationContext;
    private final LocalServiceRegistry registry;
    private final boolean enabled;
    private final int defaultMaxThreads;

    public LocalServiceAutoRegistrar(ApplicationContext applicationContext,
                                     LocalServiceRegistry registry,
                                     @Value("${runtime.autoRegistration.enabled:true}") boolean enabled,
                                     @Value("${runtime.autoRegistration.defaultMaxThreads:10}") int defaultMaxThreads) {
        this.applicationContext = applicationContext;
        this.registry = registry;
        this.enabled = enabled;
        this.defaultMaxThreads = defaultMaxThreads;
    }

    @EventListener(ContextRefreshedEvent.class)
    public void registerAnnotatedServices() {
        if (!enabled) {
            log.info("Auto-registration disabled via property runtime.autoRegistration.enabled=false");
            return;
        }
        // Collect unique service bean instances discovered by type and by annotation
        Set<CKService> services = new HashSet<>();

        Map<String, CKService> byType = applicationContext.getBeansOfType(CKService.class);
        services.addAll(byType.values());

        Map<String, Object> byAnno = applicationContext.getBeansWithAnnotation(CKServiceRegistration.class);
        for (Object bean : byAnno.values()) {
            if (bean instanceof CKService svc) {
                services.add(svc);
            }
        }

        // Also support legacy annotation org.knightmesh.core.annotations.CKService
        Map<String, Object> legacyAnno = applicationContext.getBeansWithAnnotation(org.knightmesh.core.annotations.CKService.class);
        for (Object bean : legacyAnno.values()) {
            if (bean instanceof CKService svc) {
                services.add(svc);
            }
        }

        // Register all discovered services
        for (CKService svc : services) {
            String name = svc.getServiceName();
            int maxThreads = defaultMaxThreads;
            try {
                if (svc.getMetrics() != null && svc.getMetrics().getMaxThreads() > 0) {
                    maxThreads = svc.getMetrics().getMaxThreads();
                }
            } catch (Exception ignored) {
            }
            registry.register(name, svc, maxThreads);
            log.info("Auto-registered CKService bean '{}' with maxThreads={}", name, maxThreads);
        }
        log.info("Auto-registration complete: {} services registered.", services.size());
    }
}
