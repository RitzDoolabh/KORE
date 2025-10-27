package org.knightmesh.spm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"org.knightmesh"})
public class SpmApplication {
    public static void main(String[] args) {
        SpringApplication.run(SpmApplication.class, args);
    }
}
