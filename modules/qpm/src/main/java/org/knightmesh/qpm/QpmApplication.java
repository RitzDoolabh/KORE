package org.knightmesh.qpm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {"org.knightmesh"})
@EnableScheduling
@EntityScan(basePackages = {"org.knightmesh.core.config", "org.knightmesh.plugins.queue"})
public class QpmApplication {
    public static void main(String[] args) {
        SpringApplication.run(QpmApplication.class, args);
    }
}
