package org.knightmesh.irp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"org.knightmesh"})
public class IrpApplication {
    public static void main(String[] args) {
        SpringApplication.run(IrpApplication.class, args);
    }
}
