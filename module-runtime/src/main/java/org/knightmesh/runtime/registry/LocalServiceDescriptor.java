package org.knightmesh.runtime.registry;

import org.knightmesh.core.service.CKService;

import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Descriptor for a locally hosted service instance.
 */
public class LocalServiceDescriptor {

    private String name; // serviceName
    private CKService instance;
    private final AtomicInteger activeThreads = new AtomicInteger(0);
    private int maxThreads;
    private ServiceStatus status = ServiceStatus.UP;
    private Instant lastHeartbeat;

    public LocalServiceDescriptor() {
    }

    public LocalServiceDescriptor(String name, CKService instance, int maxThreads) {
        this(name, instance, maxThreads, ServiceStatus.UP);
    }

    public LocalServiceDescriptor(String name, CKService instance, int maxThreads, ServiceStatus status) {
        this.name = Objects.requireNonNull(name, "name");
        this.instance = Objects.requireNonNull(instance, "instance");
        this.maxThreads = maxThreads;
        this.status = Objects.requireNonNullElse(status, ServiceStatus.UP);
    }

    public String getName() {
        return name;
    }

    /**
     * Alias matching the spec terminology.
     */
    public String getServiceName() { return name; }

    public void setName(String name) {
        this.name = name;
    }

    public CKService getInstance() {
        return instance;
    }

    public void setInstance(CKService instance) {
        this.instance = instance;
    }

    public AtomicInteger getActiveThreads() {
        return activeThreads;
    }

    public int getMaxThreads() {
        return maxThreads;
    }

    public void setMaxThreads(int maxThreads) {
        this.maxThreads = maxThreads;
    }

    public ServiceStatus getStatus() {
        return status;
    }

    public void setStatus(ServiceStatus status) {
        this.status = status;
    }

    public Instant getLastHeartbeat() {
        return lastHeartbeat;
    }

    public void setLastHeartbeat(Instant lastHeartbeat) {
        this.lastHeartbeat = lastHeartbeat;
    }

    public boolean hasCapacity() {
        return status == ServiceStatus.UP && activeThreads.get() < maxThreads;
    }

    /**
     * Attempt to reserve a thread slot if available, returning true on success.
     */
    public boolean incrementActive() {
        while (true) {
            int current = activeThreads.get();
            if (current >= maxThreads) {
                return false;
            }
            if (activeThreads.compareAndSet(current, current + 1)) {
                return true;
            }
        }
    }

    /**
     * Release a previously reserved thread slot.
     */
    public void decrementActive() {
        activeThreads.updateAndGet(v -> Math.max(0, v - 1));
    }

    @Override
    public String toString() {
        return "LocalServiceDescriptor{" +
                "name='" + name + '\'' +
                ", activeThreads=" + activeThreads.get() +
                ", maxThreads=" + maxThreads +
                ", status=" + status +
                ", lastHeartbeat=" + lastHeartbeat +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LocalServiceDescriptor that = (LocalServiceDescriptor) o;
        return Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }
}
