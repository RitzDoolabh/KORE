package org.knightmesh.runtime.monitoring;

import org.knightmesh.runtime.registry.LocalServiceRegistry;
import org.knightmesh.runtime.registry.LocalServiceRegistry.CapacityView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Exposes capacity views for each module domain.
 */
@RestController
public class CapacityController {

    private final LocalServiceRegistry registry;

    public CapacityController(LocalServiceRegistry registry) {
        this.registry = registry;
    }

    // SPM capacity
    @GetMapping("/spm/capacity")
    public Map<String, CapacityView> spmCapacity() {
        return registry.capacitySnapshot();
    }

    // IRP capacity
    @GetMapping("/irp/capacity")
    public Map<String, CapacityView> irpCapacity() {
        return registry.capacitySnapshot();
    }

    // QPM capacity
    @GetMapping("/qpm/capacity")
    public Map<String, CapacityView> qpmCapacity() {
        return registry.capacitySnapshot();
    }
}
