package org.knightmesh.runtime.registry;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import org.knightmesh.core.service.CKService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Thread-safe in-memory registry of local services.
 */
@Component
public class LocalServiceRegistry {

    private static final Logger log = LoggerFactory.getLogger(LocalServiceRegistry.class);

    private final Map<String, LocalServiceDescriptor> services = new ConcurrentHashMap<>();

    @Nullable
    private final MeterRegistry meterRegistry;
    private final Map<String, List<Meter.Id>> registeredMeters = new ConcurrentHashMap<>();

    public LocalServiceRegistry() {
        this.meterRegistry = null;
    }

    @Autowired
    public LocalServiceRegistry(@Nullable MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * Register or replace a local service using explicit parameters.
     * Backward-compatible helper that delegates to {@link #register(LocalServiceDescriptor)}.
     */
    public LocalServiceDescriptor register(String serviceName, CKService instance, int maxThreads) {
        Objects.requireNonNull(serviceName, "serviceName");
        Objects.requireNonNull(instance, "instance");
        if (maxThreads <= 0) throw new IllegalArgumentException("maxThreads must be > 0");
        LocalServiceDescriptor d = new LocalServiceDescriptor(serviceName, instance, maxThreads, ServiceStatus.UP);
        return register(d);
    }

    /**
     * Register or replace a local service descriptor.
     * Updates lastHeartbeat, logs the registration, and registers gauges if Micrometer is available.
     */
    public LocalServiceDescriptor register(LocalServiceDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor");
        descriptor.setLastHeartbeat(Instant.now());
        services.put(descriptor.getServiceName(), descriptor);
        log.info("Registered local service: {} (maxThreads={})", descriptor.getServiceName(), descriptor.getMaxThreads());
        registerGauges(descriptor);
        return descriptor;
    }

    /** Remove a service by name. */
    public void deregister(String serviceName) {
        if (serviceName != null) {
            services.remove(serviceName);
            removeGauges(serviceName);
            log.info("Deregistered local service: {}", serviceName);
        }
    }

    // Backward-compatible aliases
    public void unregister(String serviceName) { deregister(serviceName); }

    public LocalServiceDescriptor get(String serviceName) {
        return serviceName == null ? null : services.get(serviceName);
    }

    /** Lookup a service descriptor by name. */
    public Optional<LocalServiceDescriptor> lookup(String serviceName) {
        return Optional.ofNullable(get(serviceName));
    }

    public boolean hasCapacity(String serviceName) {
        LocalServiceDescriptor d = get(serviceName);
        return d != null && d.hasCapacity();
    }

    public Collection<LocalServiceDescriptor> listAll() {
        return Collections.unmodifiableCollection(services.values());
    }

    public Map<String, CapacityView> capacitySnapshot() {
        return services.values().stream()
                .collect(Collectors.toMap(LocalServiceDescriptor::getName,
                        d -> new CapacityView(d.getName(), d.getActiveThreads().get(), d.getMaxThreads(), d.getStatus())));
    }

    /** Lightweight DTO returned by the capacity endpoints. */
    public record CapacityView(String name, int activeThreads, int maxThreads, ServiceStatus status) {}

    private void registerGauges(LocalServiceDescriptor d) {
        if (meterRegistry == null || d == null) return;
        String svc = d.getServiceName();
        List<Meter.Id> ids = new ArrayList<>(3);
        ids.add(Gauge.builder("spm_active_threads", d, x -> x.getActiveThreads().get())
                .tag("service_name", svc)
                .description("Active threads currently executing for this service")
                .register(meterRegistry).getId());
        ids.add(Gauge.builder("spm_max_threads", d, LocalServiceDescriptor::getMaxThreads)
                .tag("service_name", svc)
                .description("Configured maximum concurrent threads for this service")
                .register(meterRegistry).getId());
        ids.add(Gauge.builder("spm_thread_utilization", d, x -> {
                    int max = Math.max(1, x.getMaxThreads());
                    return ((double) x.getActiveThreads().get()) / (double) max;
                })
                .tag("service_name", svc)
                .description("Thread utilization (active/max) for this service")
                .register(meterRegistry).getId());
        registeredMeters.put(svc, ids);
    }

    private void removeGauges(String serviceName) {
        if (meterRegistry == null) return;
        List<Meter.Id> ids = registeredMeters.remove(serviceName);
        if (ids != null) {
            for (Meter.Id id : ids) {
                meterRegistry.remove(id);
            }
        }
    }
}
