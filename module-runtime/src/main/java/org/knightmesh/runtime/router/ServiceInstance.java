package org.knightmesh.runtime.router;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Simple representation of a remote service endpoint.
 */
public class ServiceInstance {
    private final String host;
    private final int port;
    private final Map<String, String> metadata;

    public ServiceInstance(String host, int port, Map<String, String> metadata) {
        this.host = Objects.requireNonNull(host, "host");
        this.port = port;
        this.metadata = metadata == null ? Collections.emptyMap() : Collections.unmodifiableMap(metadata);
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public String baseUrl() {
        String scheme = metadata.getOrDefault("scheme", "http");
        return scheme + "://" + host + ":" + port;
    }

    @Override
    public String toString() {
        return "ServiceInstance{" +
                "host='" + host + '\'' +
                ", port=" + port +
                ", metadataKeys=" + metadata.keySet() +
                '}';
    }
}
