package org.knightmesh.runtime.config;

import org.knightmesh.core.config.*;
import org.knightmesh.runtime.config.repo.*;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class ConfigRepository {

    private final ModuleConfigRepository moduleRepo;
    private final ServiceConfigRepository serviceRepo;
    private final PluginConfigRepository pluginRepo;
    private final FlowConfigRepository flowRepo;
    private final GlobalSettingsRepository globalsRepo;
    private GatewayRouteRepository gatewayRouteRepo;

    private final AtomicReference<Map<String, ServiceConfig>> servicesByName = new AtomicReference<>(Map.of());
    private final AtomicReference<Map<String, PluginConfig>> pluginsByName = new AtomicReference<>(Map.of());
    private final AtomicReference<Map<String, FlowConfig>> flowsByName = new AtomicReference<>(Map.of());
    private final AtomicReference<List<ModuleConfig>> modules = new AtomicReference<>(List.of());
    private final AtomicReference<GlobalSettings> globalSettings = new AtomicReference<>(null);
    private final AtomicReference<List<GatewayRoute>> gatewayRoutes = new AtomicReference<>(List.of());

    public ConfigRepository(ModuleConfigRepository moduleRepo,
                            ServiceConfigRepository serviceRepo,
                            PluginConfigRepository pluginRepo,
                            FlowConfigRepository flowRepo,
                            GlobalSettingsRepository globalsRepo) {
        this.moduleRepo = moduleRepo;
        this.serviceRepo = serviceRepo;
        this.pluginRepo = pluginRepo;
        this.flowRepo = flowRepo;
        this.globalsRepo = globalsRepo;
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setGatewayRouteRepository(GatewayRouteRepository gatewayRouteRepo) {
        this.gatewayRouteRepo = gatewayRouteRepo;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void loadAtStartup() {
        reload();
    }

    public synchronized void reload() {
        List<ServiceConfig> services = serviceRepo.findAll();
        Map<String, ServiceConfig> sMap = new HashMap<>();
        for (ServiceConfig s : services) {
            if (s.getServiceName() != null) {
                sMap.put(s.getServiceName(), s);
            }
        }
        servicesByName.set(Collections.unmodifiableMap(sMap));

        List<PluginConfig> plugins = pluginRepo.findAll();
        Map<String, PluginConfig> pMap = new HashMap<>();
        for (PluginConfig p : plugins) {
            pMap.put(p.getName(), p);
        }
        pluginsByName.set(Collections.unmodifiableMap(pMap));

        List<FlowConfig> flows = flowRepo.findAll();
        Map<String, FlowConfig> fMap = new HashMap<>();
        for (FlowConfig f : flows) {
            fMap.put(f.getName(), f);
        }
        flowsByName.set(Collections.unmodifiableMap(fMap));

        modules.set(Collections.unmodifiableList(moduleRepo.findAll()));

        List<GlobalSettings> gAll = globalsRepo.findAll();
        globalSettings.set(gAll.isEmpty() ? null : gAll.get(0));

        try {
            gatewayRoutes.set(Collections.unmodifiableList(gatewayRouteRepo.findByEnabledTrue()));
        } catch (Exception ignored) {
            // In modules/tests that don't have the table, ignore
            gatewayRoutes.set(List.of());
        }
    }

    // ----- New API -----
    public List<ServiceConfig> findServicesForModule(String moduleName) {
        return serviceRepo.findByModuleNameAndEnabledTrue(moduleName);
    }

    public List<ModuleConfig> listEnabledModules() {
        return moduleRepo.findByEnabledTrue();
    }

    public Optional<ModuleConfig> getModuleConfig(String name) {
        return moduleRepo.findByName(name);
    }

    // Existing helpers
    public Optional<ServiceConfig> getService(String name) {
        return Optional.ofNullable(servicesByName.get().get(name));
    }

    public Optional<PluginConfig> getPlugin(String name) {
        return Optional.ofNullable(pluginsByName.get().get(name));
    }

    public Optional<FlowConfig> getFlow(String name) {
        return Optional.ofNullable(flowsByName.get().get(name));
    }

    public List<ModuleConfig> getModules() {
        return modules.get();
    }

    public Optional<ModuleConfig> findModule(ModuleType type, String instance) {
        return modules.get().stream()
                .filter(m -> m.getType() == type && Objects.equals(m.getInstance(), instance))
                .findFirst();
    }

    public Optional<GlobalSettings> getGlobalSettings() {
        return Optional.ofNullable(globalSettings.get());
    }

    public List<GatewayRoute> listGatewayRoutes() {
        return gatewayRoutes.get();
    }
}
