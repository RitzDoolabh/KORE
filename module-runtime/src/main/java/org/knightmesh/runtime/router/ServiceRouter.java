package org.knightmesh.runtime.router;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.knightmesh.core.model.ServiceRequest;
import org.knightmesh.core.model.ServiceResponse;
import org.knightmesh.runtime.registry.LocalServiceDescriptor;
import org.knightmesh.runtime.registry.LocalServiceRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Routes service requests either to a local instance (if available and has capacity)
 * or delegates to a remote instance discovered by {@link RemoteServiceLocator}. If no
 * remote locator is provided, falls back to the {@link KubernetesServiceLocator} stub.
 */
@Component
public class ServiceRouter {

    private final LocalServiceRegistry registry;
    private final KubernetesServiceLocator kubeLocator; // optional fallback
    private final RemoteServiceLocator remoteLocator;   // optional preferred remote path
    private final RestTemplate http;                    // optional HTTP client for remote calls (legacy)
    private final RemoteHttpInvoker remoteHttpInvoker;  // preferred invoker with resilience
    private final AtomicInteger rr = new AtomicInteger(0);
    private final ThreadPoolHelper tpHelper = new ThreadPoolHelper();

    @Nullable
    private final MeterRegistry meterRegistry;

    // Backward-compatible constructor used by existing integration test
    @Autowired
    public ServiceRouter(LocalServiceRegistry registry, KubernetesServiceLocator kubeLocator) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.kubeLocator = kubeLocator;
        this.remoteLocator = null;
        this.http = null;
        this.remoteHttpInvoker = null;
        this.meterRegistry = null;
    }

    // New constructor supporting RemoteServiceLocator + RestTemplate
    public ServiceRouter(LocalServiceRegistry registry, RemoteServiceLocator remoteLocator, RestTemplate http) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.remoteLocator = remoteLocator;
        this.http = http;
        this.kubeLocator = null;
        this.remoteHttpInvoker = null;
        this.meterRegistry = null;
    }

    // Preferred constructor using resilient RemoteHttpInvoker
    public ServiceRouter(LocalServiceRegistry registry, RemoteServiceLocator remoteLocator, RemoteHttpInvoker remoteHttpInvoker) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.remoteLocator = remoteLocator;
        this.remoteHttpInvoker = remoteHttpInvoker;
        this.kubeLocator = null;
        this.http = null;
        this.meterRegistry = null;
    }

    @Autowired(required = false)
    public ServiceRouter(LocalServiceRegistry registry, RemoteServiceLocator remoteLocator, RemoteHttpInvoker remoteHttpInvoker, MeterRegistry meterRegistry) {
        this.registry = registry;
        this.remoteLocator = remoteLocator;
        this.remoteHttpInvoker = remoteHttpInvoker;
        this.kubeLocator = null;
        this.http = null;
        this.meterRegistry = meterRegistry;
    }

    public ServiceResponse route(ServiceRequest request) {
        if (request == null || request.getServiceName() == null) {
            return ServiceResponse.failure("INVALID_REQUEST", "Invalid request or service name", null);
        }
        String name = request.getServiceName();
        LocalServiceDescriptor d = registry.get(name);
        if (d != null && d.hasCapacity()) {
            long start = System.nanoTime();
            if (!tpHelper.reserveIfAvailable(d)) {
                // Race: capacity was consumed; delegate to remote
                return routeRemote(request);
            }
            try {
                ServiceResponse resp = d.getInstance().execute(request);
                recordMetrics(name, "local", System.nanoTime() - start, resp);
                return resp;
            } catch (RuntimeException ex) {
                recordFailure(name, "local");
                return ServiceResponse.failure("EXCEPTION", ex.getMessage(), null);
            } finally {
                tpHelper.release(d, start);
            }
        }
        // local unavailable or full: remote path
        ServiceResponse resp = routeRemote(request);
        // routeRemote will record failure/success inside; if not, record generically
        return resp;
    }

    private ServiceResponse routeRemote(ServiceRequest request) {
        String svc = request.getServiceName();
        long start = System.nanoTime();
        // Prefer RemoteServiceLocator if available
        if (remoteLocator != null) {
            try {
                List<ServiceInstance> list = remoteLocator.findInstances(svc);
                if (list == null || list.isEmpty()) {
                    recordFailure(svc, "remote");
                    return ServiceResponse.failure("NO_INSTANCES", "No remote instances for service: " + svc, null);
                }
                int idx = Math.abs(rr.getAndIncrement());
                ServiceInstance chosen = list.get(idx % list.size());
                if (remoteHttpInvoker != null) {
                    try {
                        ServiceResponse resp = remoteHttpInvoker.post(chosen, request);
                        recordMetrics(svc, "remote", System.nanoTime() - start, resp);
                        return resp;
                    } catch (CallNotPermittedException cbOpen) {
                        recordFailure(svc, "remote");
                        return ServiceResponse.failure("SERVICE_UNAVAILABLE", "Circuit open for remote service: " + svc, null);
                    } catch (Exception ex) {
                        recordFailure(svc, "remote");
                        return ServiceResponse.failure("SERVICE_UNAVAILABLE", summarize(ex), null);
                    }
                } else if (http != null) {
                    String url = chosen.baseUrl() + "/internal/service/" + svc;
                    try {
                        ServiceResponse resp = http.postForObject(url, request, ServiceResponse.class);
                        if (resp == null) {
                            recordFailure(svc, "remote");
                            return ServiceResponse.failure("EMPTY_RESPONSE", "Remote call returned no body", null);
                        }
                        recordMetrics(svc, "remote", System.nanoTime() - start, resp);
                        return resp;
                    } catch (Exception ex) {
                        recordFailure(svc, "remote");
                        return ServiceResponse.failure("SERVICE_UNAVAILABLE", summarize(ex), null);
                    }
                }
            } catch (Exception ex) {
                recordFailure(svc, "remote");
                return ServiceResponse.failure("SERVICE_UNAVAILABLE", summarize(ex), null);
            }
        }
        // Fallback: KubernetesServiceLocator stub if present
        if (kubeLocator != null) {
            ServiceResponse resp = kubeLocator.route(request);
            recordMetrics(svc, "remote", System.nanoTime() - start, resp);
            return resp;
        }
        recordFailure(svc, "remote");
        return ServiceResponse.failure("NO_REMOTE_PATH", "No remote locator or kube locator configured", null);
    }

    private void recordMetrics(String serviceName, String route, long nanos, ServiceResponse resp) {
        if (meterRegistry == null) return;
        String outcome = resp != null && resp.getStatus() == ServiceResponse.Status.SUCCESS ? "success" : "failure";
        Counter c = Counter.builder("router_requests_total")
                .tag("service_name", serviceName)
                .tag("route", route)
                .tag("outcome", outcome)
                .register(meterRegistry);
        c.increment();
        Timer t = Timer.builder("router_latency")
                .tag("service_name", serviceName)
                .tag("route", route)
                .tag("outcome", outcome)
                .register(meterRegistry);
        t.record(nanos, TimeUnit.NANOSECONDS);
    }

    private void recordFailure(String serviceName, String route) {
        if (meterRegistry == null) return;
        Counter c = Counter.builder("router_requests_total")
                .tag("service_name", serviceName)
                .tag("route", route)
                .tag("outcome", "failure")
                .register(meterRegistry);
        c.increment();
    }

    /** Lightweight helper that wraps descriptor counters. */
    static class ThreadPoolHelper {
        boolean reserveIfAvailable(LocalServiceDescriptor d) {
            return d != null && d.incrementActive();
        }
        void release(LocalServiceDescriptor d, long startNanos) {
            if (d != null) {
                d.decrementActive();
            }
        }
    }

    private String summarize(Exception ex) {
        String msg = ex.getMessage();
        if (msg == null || msg.isBlank()) {
            msg = ex.getClass().getSimpleName();
        }
        return msg;
    }
}
