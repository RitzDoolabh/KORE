package org.knightmesh.plugins.queue;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PersistentQueueMessageRepository extends JpaRepository<PersistentQueueMessage, UUID> {
    Optional<PersistentQueueMessage> findTopByQueueNameAndStatusOrderByCreatedAtAsc(String queueName, String status);

    long countByQueueNameAndStatus(String queueName, String status);
}
