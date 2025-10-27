package org.knightmesh.runtime.router;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.knightmesh.core.config.ModuleConfig;
import org.knightmesh.core.config.ServiceConfig;
import org.knightmesh.runtime.config.ConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * RemoteServiceLocator that prefers Kubernetes Discovery when available and
 * falls back to database-provided module config for local/dev environments.
 *
 * Fallback JSON structure expected in ModuleConfig.extraJson:
 * {
 *   "instances": [ { "host": "127.0.0.1", "port": 8081, "metadata": {"zone":"dev"} } ]
 * }
 */
public class KubernetesRemoteServiceLocator implements RemoteServiceLocator {
    private static final Logger log = LoggerFactory.getLogger(KubernetesRemoteServiceLocator.class);

    private final org.springframework.cloud.client.discovery.DiscoveryClient discoveryClient; // optional
    private final ConfigRepository configRepository; // fallback
    private final Environment environment;
    private final ObjectMapper mapper = new ObjectMapper();

    public KubernetesRemoteServiceLocator(org.springframework.cloud.client.discovery.DiscoveryClient discoveryClient,
                                          ConfigRepository configRepository,
                                          Environment environment) {
        this.discoveryClient = discoveryClient; // may be null if not on classpath
        this.configRepository = Objects.requireNonNull(configRepository, "configRepository");
        this.environment = Objects.requireNonNull(environment, "environment");
    }

    @Override
    public List<ServiceInstance> findInstances(String serviceName) {
        if (serviceName == null || serviceName.isBlank()) return List.of();
        boolean k8sEnabledProp = environment.getProperty("knightmesh.kubernetes.enabled", Boolean.class, false);
        boolean k8sEnv = System.getenv("KUBERNETES_SERVICE_HOST") != null;
        boolean useK8s = (k8sEnabledProp || k8sEnv) && discoveryClient != null;

        if (useK8s) {
            try {
                List<org.springframework.cloud.client.ServiceInstance> cloud = discoveryClient.getInstances(serviceName);
                if (CollectionUtils.isEmpty(cloud)) return List.of();
                return cloud.stream().map(si -> new ServiceInstance(
                        si.getHost(),
                        si.getPort(),
                        safeMetadata(si.getMetadata(), si.isSecure())
                )).collect(Collectors.toList());
            } catch (Exception ex) {
                log.warn("K8s discovery failed for service={}, falling back to DB: {}", serviceName, ex.getMessage());
                // then try DB
            }
        }
        return findFromDb(serviceName);
    }

    private Map<String, String> safeMetadata(Map<String, String> metadata, boolean secure) {
        if (metadata == null) metadata = Collections.emptyMap();
        if (secure && !metadata.containsKey("scheme")) {
            // include scheme hint for router URL building
            metadata = new java.util.HashMap<>(metadata);
            metadata.put("scheme", "https");
            return Collections.unmodifiableMap(metadata);
        }
        return metadata;
    }

    private List<ServiceInstance> findFromDb(String serviceName) {
        try {
            Optional<ServiceConfig> svcOpt = configRepository.getService(serviceName);
            if (svcOpt.isEmpty()) {
                log.debug("No ServiceConfig for {}, returning empty list", serviceName);
                return List.of();
            }
            String moduleName = svcOpt.get().getModuleName();
            if (moduleName == null || moduleName.isBlank()) {
                log.debug("ServiceConfig for {} has no moduleName", serviceName);
                return List.of();
            }
            List<ModuleConfig> enabled = configRepository.listEnabledModules();
            ModuleConfig mod = enabled.stream()
                    .filter(m -> moduleName.equalsIgnoreCase(m.getName()))
                    .findFirst()
                    .orElse(null);
            if (mod == null) return List.of();
            String extraJson = mod.getExtraJson();
            if (extraJson == null || extraJson.isBlank()) return List.of();

            JsonNode root = mapper.readTree(extraJson);
            JsonNode instances = root.get("instances");
            if (instances == null || !instances.isArray()) return List.of();

            List<ServiceInstance> list = new ArrayList<>();
            for (JsonNode n : instances) {
                String host = optText(n, "host");
                int port = n.has("port") ? n.get("port").asInt() : -1;
                if (host == null || port <= 0) continue;
                Map<String, String> md = Collections.emptyMap();
                JsonNode meta = n.get("metadata");
                if (meta != null && meta.isObject()) {
                    java.util.HashMap<String, String> m = new java.util.HashMap<>();
                    meta.fields().forEachRemaining(e -> m.put(e.getKey(), e.getValue().asText()));
                    md = m;
                }
                list.add(new ServiceInstance(host, port, md));
            }
            return list;
        } catch (Exception e) {
            log.warn("DB fallback discovery failed for service {}: {}", serviceName, e.getMessage());
            return List.of();
        }
    }

    private String optText(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v == null ? null : v.asText();
    }
}
