package org.knightmesh.runtime.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.knightmesh.core.config.*;
import org.knightmesh.runtime.ModuleRuntimeApplication;
import org.knightmesh.runtime.config.repo.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = ModuleRuntimeApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ConfigRepositoryIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void dataSourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driverClassName", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.jpa.show-sql", () -> "false");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.clean-disabled", () -> "true");
    }

    @Autowired
    private ConfigRepository configRepository;

    @Autowired private ServiceConfigRepository serviceRepo;
    @Autowired private ModuleConfigRepository moduleRepo;
    @Autowired private PluginConfigRepository pluginRepo;
    @Autowired private FlowConfigRepository flowRepo;
    @Autowired private GlobalSettingsRepository globalsRepo;

    @Test
    void loadsConfigurationsFromPostgresKnightKore() {
        // Given: some configuration records persisted via Spring Data JPA
        ServiceConfig svc = new ServiceConfig();
        svc.setServiceName("USER_AUTH");
        svc.setModuleName("spm");
        svc.setMaxThreads(7);
        svc.setEnabled(true);
        svc.setDependenciesJson("[\"JWT_ISSUER\"]");
        serviceRepo.save(svc);

        ModuleConfig mod = new ModuleConfig();
        mod.setName("spm");
        mod.setType(ModuleType.SPM);
        mod.setInstance("spm-1");
        mod.setDomain("sales");
        mod.setEnabled(true);
        mod.setRouteMode(RouteMode.LOCAL_FIRST);
        moduleRepo.save(mod);

        PluginConfig plug = new PluginConfig();
        plug.setName("audit");
        plug.setEnabled(true);
        plug.setSettingsJson("{\"sink\":\"stdout\"}");
        pluginRepo.save(plug);

        FlowConfig flow = new FlowConfig();
        flow.setName("user_onboarding");
        flow.setDefinitionJson("{\"steps\":[\"REGISTER_USER\",\"USER_AUTH\"]}");
        flow.setEnabled(true);
        flowRepo.save(flow);

        GlobalSettings gs = new GlobalSettings();
        gs.setSettingsJson("{\"featureFlags\":{\"beta\":true}}");
        globalsRepo.save(gs);

        // When: ConfigRepository reloads
        configRepository.reload();

        // Then: configurations are available
        Optional<ServiceConfig> svcCfg = configRepository.getService("USER_AUTH");
        assertTrue(svcCfg.isPresent());
        assertEquals(7, svcCfg.get().getMaxThreads());

        // New API verifications
        List<ServiceConfig> servicesForSpm = configRepository.findServicesForModule("spm");
        assertEquals(1, servicesForSpm.size());
        assertEquals("USER_AUTH", servicesForSpm.get(0).getServiceName());

        assertTrue(configRepository.findModule(ModuleType.SPM, "spm-1").isPresent());
        assertTrue(configRepository.listEnabledModules().stream().anyMatch(m -> "spm".equals(m.getName())));
        assertTrue(configRepository.getModuleConfig("spm").isPresent());

        assertTrue(configRepository.getPlugin("audit").isPresent());
        assertTrue(configRepository.getFlow("user_onboarding").isPresent());
        assertTrue(configRepository.getGlobalSettings().isPresent());
    }
}
