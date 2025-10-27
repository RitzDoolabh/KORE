package org.knightmesh.mgm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;

@SpringBootApplication(scanBasePackages = {"org.knightmesh"})
@EntityScan(basePackages = {"org.knightmesh.core.config"})
public class MgmApplication {
    public static void main(String[] args) {
        SpringApplication.run(MgmApplication.class, args);
    }
}
