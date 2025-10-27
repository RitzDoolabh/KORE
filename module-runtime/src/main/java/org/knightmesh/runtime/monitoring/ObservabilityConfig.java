package org.knightmesh.runtime.monitoring;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ObservabilityConfig {

    @Bean
    public MeterRegistryCustomizer<MeterRegistry> commonTagsCustomizer(
            @Value("${observability.module:unknown}") String module,
            @Value("${observability.module_type:UNKNOWN}") String moduleType,
            @Value("${observability.instance_id:local}") String instanceId) {
        return registry -> registry.config().commonTags(
                "module", module,
                "module_type", moduleType,
                "instance_id", instanceId
        );
    }
}
