package org.knightmesh.qpm;

import org.junit.jupiter.api.Test;
import org.knightmesh.core.config.ModuleConfig;
import org.knightmesh.core.config.RouteMode;
import org.knightmesh.core.model.ServiceRequest;
import org.knightmesh.core.model.ServiceResponse;
import org.knightmesh.plugins.queue.QueuePlugin;
import org.knightmesh.runtime.config.ConfigRepository;
import org.knightmesh.runtime.router.ServiceRouter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest(classes = QpmApplication.class)
@ActiveProfiles("test")
class QpmWorkerInMemoryTest {

    @Autowired
    private QueuePlugin queuePlugin; // InMemoryQueuePlugin should be @Primary

    @MockBean
    private ConfigRepository configRepository;

    @MockBean
    private ServiceRouter router;

    @Test
    void worker_dequeues_and_invokes_router() throws Exception {
        ModuleConfig irp = new ModuleConfig();
        irp.setName("irp");
        irp.setRouteMode(RouteMode.QUEUE);
        irp.setQueueName("irp-default");
        when(configRepository.listEnabledModules()).thenReturn(List.of(irp));

        CountDownLatch latch = new CountDownLatch(3);
        when(router.route(any(ServiceRequest.class))).thenAnswer(inv -> {
            latch.countDown();
            return ServiceResponse.success(Map.of("ok", true));
        });

        // Enqueue 3 messages
        for (int i = 0; i < 3; i++) {
            ServiceRequest req = new ServiceRequest(
                    "USER_AUTH",
                    Map.of("user", "u" + i),
                    Map.of(),
                    "c" + i
            );
            queuePlugin.enqueue("irp-default", req);
        }

        // Wait up to 5 seconds for worker to process
        boolean completed = latch.await(5, TimeUnit.SECONDS);
        assertThat(completed).isTrue();
        verify(router, atLeast(3)).route(any(ServiceRequest.class));
    }
}
