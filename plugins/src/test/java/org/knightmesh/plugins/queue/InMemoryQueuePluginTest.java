package org.knightmesh.plugins.queue;

import org.junit.jupiter.api.Test;
import org.knightmesh.core.model.ServiceRequest;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryQueuePluginTest {

    @Test
    void fifo_enqueue_dequeue_and_size() {
        InMemoryQueuePlugin q = new InMemoryQueuePlugin();
        String queue = "q1";
        ServiceRequest a = new ServiceRequest("SVC", Map.of("i", 1), Map.of(), "c1");
        ServiceRequest b = new ServiceRequest("SVC", Map.of("i", 2), Map.of(), "c2");
        q.enqueue(queue, a);
        q.enqueue(queue, b);
        assertThat(q.size(queue)).isEqualTo(2);
        ServiceRequest r1 = q.dequeue(queue);
        ServiceRequest r2 = q.dequeue(queue);
        ServiceRequest r3 = q.dequeue(queue);
        assertThat(r1.getCorrelationId()).isEqualTo("c1");
        assertThat(r2.getCorrelationId()).isEqualTo("c2");
        assertThat(r3).isNull();
        assertThat(q.size(queue)).isEqualTo(0);
    }
}
