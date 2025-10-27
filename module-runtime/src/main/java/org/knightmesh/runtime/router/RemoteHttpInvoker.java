package org.knightmesh.runtime.router;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.knightmesh.core.model.ServiceRequest;
import org.knightmesh.core.model.ServiceResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Objects;

/**
 * Performs HTTP invocations to a specific ServiceInstance with resilience (Retry + CircuitBreaker).
 */
@Component
public class RemoteHttpInvoker {
    private static final Logger log = LoggerFactory.getLogger(RemoteHttpInvoker.class);

    private final RestTemplate restTemplate;

    public RemoteHttpInvoker(RestTemplate restTemplate) {
        this.restTemplate = Objects.requireNonNull(restTemplate, "restTemplate");
    }

    @Retry(name = "remoteRouter")
    @CircuitBreaker(name = "remoteRouter")
    public ServiceResponse post(ServiceInstance instance, ServiceRequest request) {
        String url = instance.baseUrl() + "/internal/service/" + request.getServiceName();
        log.debug("Remote POST {} corrId={}", url, request.getCorrelationId());
        ServiceResponse resp = restTemplate.postForObject(url, request, ServiceResponse.class);
        if (resp == null) {
            throw new IllegalStateException("Remote call returned no body");
        }
        return resp;
    }
}
