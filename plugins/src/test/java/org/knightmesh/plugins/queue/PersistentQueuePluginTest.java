package org.knightmesh.plugins.queue;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.knightmesh.core.model.ServiceRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({PersistentQueuePlugin.class, PersistentQueuePluginTest.TestConfig.class})
class PersistentQueuePluginTest {

    static class TestConfig {
        @Bean
        ObjectMapper objectMapper() { return new ObjectMapper(); }
    }

    @Autowired
    private PersistentQueuePlugin plugin;

    @Test
    void enqueue_dequeue_fifo_and_size() {
        String queue = "pq";
        ServiceRequest a = new ServiceRequest("SVC", Map.of("i", 1), Map.of(), "c1");
        ServiceRequest b = new ServiceRequest("SVC", Map.of("i", 2), Map.of(), "c2");
        plugin.enqueue(queue, a);
        plugin.enqueue(queue, b);
        assertThat(plugin.size(queue)).isEqualTo(2);
        ServiceRequest r1 = plugin.dequeue(queue);
        ServiceRequest r2 = plugin.dequeue(queue);
        ServiceRequest r3 = plugin.dequeue(queue);
        assertThat(r1.getCorrelationId()).isEqualTo("c1");
        assertThat(r2.getCorrelationId()).isEqualTo("c2");
        assertThat(r3).isNull();
        assertThat(plugin.size(queue)).isEqualTo(0);
    }
}
