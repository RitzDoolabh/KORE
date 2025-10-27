package org.knightmesh.mgm.api;

import org.knightmesh.core.config.ModuleConfig;
import org.knightmesh.runtime.config.ConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/mgm")
public class MgmController {
    private static final Logger log = LoggerFactory.getLogger(MgmController.class);

    private final ConfigRepository configRepository;

    public MgmController(ConfigRepository configRepository) {
        this.configRepository = configRepository;
    }

    @GetMapping("/modules")
    public ResponseEntity<List<ModuleView>> listModules() {
        List<ModuleConfig> desired = configRepository.listEnabledModules();
        // Observed state placeholder (controller disabled by default)
        List<ModuleView> out = desired.stream()
                .map(ModuleView::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(out);
    }

    @PostMapping("/modules/{name}/start")
    public ResponseEntity<Map<String, Object>> start(@PathVariable String name) {
        log.info("[MGM] Simulated start of module '{}' (controller disabled or not implemented)", name);
        return ResponseEntity.ok(Map.of("module", name, "status", "STARTED(simulated)"));
    }

    @PostMapping("/modules/{name}/stop")
    public ResponseEntity<Map<String, Object>> stop(@PathVariable String name) {
        log.info("[MGM] Simulated stop of module '{}' (controller disabled or not implemented)", name);
        return ResponseEntity.ok(Map.of("module", name, "status", "STOPPED(simulated)"));
    }

    @PostMapping("/reconcile")
    public ResponseEntity<Map<String, Object>> reconcile() {
        // Placeholder reconcile that simply refreshes ConfigRepository cache
        configRepository.reload();
        log.info("[MGM] Simulated reconcile executed (reloaded desired state)");
        return ResponseEntity.ok(Map.of("result", "OK", "details", "Reloaded desired state"));
    }
}
