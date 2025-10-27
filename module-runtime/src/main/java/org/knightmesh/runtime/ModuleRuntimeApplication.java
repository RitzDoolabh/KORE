package org.knightmesh.runtime;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;

@SpringBootApplication(scanBasePackages = {"org.knightmesh.runtime", "org.knightmesh.spm"})
@EntityScan(basePackages = {"org.knightmesh.core.config"})
public class ModuleRuntimeApplication {
    public static void main(String[] args) {
        SpringApplication.run(ModuleRuntimeApplication.class, args);
    }
}
