package org.knightmesh.mgm.seed;

import org.knightmesh.core.config.ModuleConfig;
import org.knightmesh.core.config.ModuleType;
import org.knightmesh.core.config.RouteMode;
import org.knightmesh.core.config.ServiceConfig;
import org.knightmesh.runtime.config.repo.ModuleConfigRepository;
import org.knightmesh.runtime.config.repo.ServiceConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Seeds minimal configuration records for local development when mgm.seed=true.
 */
@Component
public class SeedDataRunner implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(SeedDataRunner.class);

    private final boolean enabled;
    private final ModuleConfigRepository moduleRepo;
    private final ServiceConfigRepository serviceRepo;

    public SeedDataRunner(@Value("${mgm.seed:true}") boolean enabled,
                          ModuleConfigRepository moduleRepo,
                          ServiceConfigRepository serviceRepo) {
        this.enabled = enabled;
        this.moduleRepo = moduleRepo;
        this.serviceRepo = serviceRepo;
    }

    @Override
    public void run(String... args) {
        if (!enabled) {
            return;
        }
        log.info("[MGM Seed] Seeding default ModuleConfig and ServiceConfig rows (if absent)...");

        // Seed SPM module
        ensureModule("spm", ModuleType.SPM, "spm-1", "sales", RouteMode.LOCAL_FIRST,
                "REGISTER_USER,USER_AUTH", null);
        // Seed IRP module (DIRECT by default)
        ensureModule("irp", ModuleType.IRP, "irp-1", "sales", RouteMode.DIRECT,
                null, "irp-default");
        // Seed QPM module (QUEUE processor)
        ensureModule("qpm", ModuleType.QPM, "qpm-1", "sales", RouteMode.QUEUE,
                null, "irp-default");

        // Seed services hosted by SPM
        ensureService("REGISTER_USER", "spm", 8);
        ensureService("USER_AUTH", "spm", 16);

        log.info("[MGM Seed] Seeding complete.");
    }

    private void ensureModule(String name, ModuleType type, String instance, String domain, RouteMode routeMode,
                              String services, String queueName) {
        Optional<ModuleConfig> existing = moduleRepo.findByName(name);
        if (existing.isPresent()) {
            return;
        }
        ModuleConfig m = new ModuleConfig();
        m.setName(name);
        m.setType(type);
        m.setInstance(instance);
        m.setDomain(domain);
        m.setEnabled(true);
        m.setRouteMode(routeMode);
        m.setServices(services);
        m.setQueueName(queueName);
        moduleRepo.save(m);
        log.info("[MGM Seed] Inserted module {} ({})", name, type);
    }

    private void ensureService(String serviceName, String moduleName, int maxThreads) {
        if (serviceRepo.findByServiceName(serviceName).isPresent()) {
            return;
        }
        ServiceConfig s = new ServiceConfig();
        s.setServiceName(serviceName);
        s.setModuleName(moduleName);
        s.setMaxThreads(maxThreads);
        s.setEnabled(true);
        serviceRepo.save(s);
        log.info("[MGM Seed] Inserted service {} (module={}, maxThreads={})", serviceName, moduleName, maxThreads);
    }
}
