package org.knightmesh.core.model;

/**
 * Basic metrics describing a CK service.
 */
public class ServiceMetrics {
    private final int maxThreads;
    private final double avgLatencyMs;
    private final long successCount;
    private final long failureCount;

    public ServiceMetrics(int maxThreads, double avgLatencyMs, long successCount, long failureCount) {
        this.maxThreads = maxThreads;
        this.avgLatencyMs = avgLatencyMs;
        this.successCount = successCount;
        this.failureCount = failureCount;
    }

    public int getMaxThreads() {
        return maxThreads;
    }

    public double getAvgLatencyMs() {
        return avgLatencyMs;
    }

    public long getSuccessCount() {
        return successCount;
    }

    public long getFailureCount() {
        return failureCount;
    }

    @Override
    public String toString() {
        return "ServiceMetrics{" +
                "maxThreads=" + maxThreads +
                ", avgLatencyMs=" + avgLatencyMs +
                ", successCount=" + successCount +
                ", failureCount=" + failureCount +
                '}';
    }
}
