package org.knightmesh.qpm;

import org.knightmesh.core.config.ModuleConfig;
import org.knightmesh.core.config.RouteMode;
import org.knightmesh.core.model.ServiceRequest;
import org.knightmesh.core.model.ServiceResponse;
import org.knightmesh.plugins.queue.QueuePlugin;
import org.knightmesh.runtime.config.ConfigRepository;
import org.knightmesh.runtime.router.ServiceRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

@Component
public class QpmWorker {
    private static final Logger log = LoggerFactory.getLogger(QpmWorker.class);

    private final ConfigRepository configRepository;
    private final QueuePlugin queue;
    private final ServiceRouter router;

    // Tuneables
    private final int batchSize = 50;

    public QpmWorker(ConfigRepository configRepository, QueuePlugin queue, ServiceRouter router) {
        this.configRepository = Objects.requireNonNull(configRepository);
        this.queue = Objects.requireNonNull(queue);
        this.router = Objects.requireNonNull(router);
    }

    @Scheduled(fixedDelayString = "${qpm.poll.delay.ms:250}")
    public void pollQueuesAndProcess() {
        List<ModuleConfig> enabled = configRepository.listEnabledModules();
        if (enabled == null || enabled.isEmpty()) {
            return;
        }
        enabled.stream()
                .filter(m -> m.getRouteMode() == RouteMode.QUEUE)
                .filter(m -> m.getQueueName() != null && !m.getQueueName().isBlank())
                .map(ModuleConfig::getQueueName)
                .distinct()
                .forEach(this::drainQueue);
    }

    private void drainQueue(String queueName) {
        int processed = 0;
        ServiceRequest req;
        while (processed < batchSize && (req = queue.dequeue(queueName)) != null) {
            try {
                ServiceResponse resp = router.route(req);
                log.debug("[QPM] processed queue={} corrId={} status={}", queueName, req.getCorrelationId(), resp.getStatus());
            } catch (Exception ex) {
                log.warn("[QPM] error processing queue={} corrId={}: {}", queueName, req.getCorrelationId(), ex.toString());
            }
            processed++;
        }
        if (processed > 0) {
            log.info("[QPM] processed {} message(s) from queue {}", processed, queueName);
        }
    }
}
