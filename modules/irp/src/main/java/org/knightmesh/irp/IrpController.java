package org.knightmesh.irp;

import org.knightmesh.core.config.ModuleConfig;
import org.knightmesh.core.config.RouteMode;
import org.knightmesh.core.model.ServiceRequest;
import org.knightmesh.core.model.ServiceResponse;
import org.knightmesh.runtime.config.ConfigRepository;
import org.knightmesh.runtime.router.ServiceRouter;
import org.knightmesh.plugins.queue.QueuePlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/irp")
public class IrpController {

    private static final Logger log = LoggerFactory.getLogger(IrpController.class);

    private final ConfigRepository configRepository;
    private final ServiceRouter serviceRouter;
    private final QueuePlugin queuePlugin;

    public IrpController(ConfigRepository configRepository, ServiceRouter serviceRouter, QueuePlugin queuePlugin) {
        this.configRepository = configRepository;
        this.serviceRouter = serviceRouter;
        this.queuePlugin = queuePlugin;
    }

    @PostMapping("/{serviceName}")
    public ResponseEntity<?> post(@PathVariable String serviceName, @RequestBody Map<String, Object> payload) {
        String correlationId = UUID.randomUUID().toString();
        Map<String, String> metadata = new HashMap<>();
        metadata.put("timestamp", Instant.now().toString());
        metadata.put("source", "IRP");

        ServiceRequest request = new ServiceRequest(serviceName, payload == null ? Map.of() : payload, metadata, correlationId);

        // Determine routing mode for IRP module; default DIRECT
        RouteMode mode = RouteMode.DIRECT;
        String queueName = null;
        Optional<ModuleConfig> irpModule = configRepository.getModuleConfig("irp");
        if (irpModule.isPresent()) {
            ModuleConfig mc = irpModule.get();
            // Allow overriding via DB fields
            if (mc.getRouteMode() != null) {
                mode = mc.getRouteMode();
            }
            queueName = mc.getQueueName();
        }

        if (mode == RouteMode.QUEUE) {
            String q = (queueName != null && !queueName.isBlank()) ? queueName : "irp-default";
            log.info("IRP enqueuing request service={}, correlationId={}, queue={}", serviceName, correlationId, q);
            queuePlugin.enqueue(q, request);
            Map<String, Object> ack = Map.of(
                    "status", "ACCEPTED",
                    "correlationId", correlationId
            );
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(ack);
        } else { // DIRECT or other
            log.info("IRP routing DIRECT service={}, correlationId={}", serviceName, correlationId);
            ServiceResponse response = serviceRouter.route(request);
            HttpStatus status = response.getStatus() == ServiceResponse.Status.SUCCESS ? HttpStatus.OK : HttpStatus.BAD_REQUEST;
            return ResponseEntity.status(status).body(response);
        }
    }
}
