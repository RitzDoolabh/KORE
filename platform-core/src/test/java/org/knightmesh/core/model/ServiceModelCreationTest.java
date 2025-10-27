package org.knightmesh.core.model;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ServiceModelCreationTest {

    @Test
    void serviceRequest_canBeCreatedAndFieldsAccessible() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("user", "alice");
        Map<String, String> metadata = new HashMap<>();
        metadata.put("source", "unit-test");

        ServiceRequest req = new ServiceRequest(
                "REGISTER_USER",
                payload,
                metadata,
                "corr-1234");

        assertEquals("REGISTER_USER", req.getServiceName());
        assertEquals("alice", req.getPayload().get("user"));
        assertEquals("unit-test", req.getMetadata().get("source"));
        assertEquals("corr-1234", req.getCorrelationId());
    }

    @Test
    void serviceResponse_canBeCreated() {
        ServiceResponse ok = ServiceResponse.success(Map.of("id", 42));
        assertEquals(ServiceResponse.Status.SUCCESS, ok.getStatus());
        assertEquals(42, ok.getData().get("id"));
        assertNull(ok.getErrorCode());
        assertNull(ok.getErrorMessage());

        ServiceResponse err = ServiceResponse.failure("E_GENERIC", "Something went wrong", null);
        assertEquals(ServiceResponse.Status.FAILURE, err.getStatus());
        assertNotNull(err.getData());
        assertTrue(err.getData().isEmpty());
        assertEquals("E_GENERIC", err.getErrorCode());
        assertEquals("Something went wrong", err.getErrorMessage());
    }

    @Test
    void serviceMetrics_canBeCreated() {
        ServiceMetrics metrics = new ServiceMetrics(16, 12.5, 100, 2);
        assertEquals(16, metrics.getMaxThreads());
        assertEquals(12.5, metrics.getAvgLatencyMs());
        assertEquals(100, metrics.getSuccessCount());
        assertEquals(2, metrics.getFailureCount());
        assertTrue(metrics.toString().contains("maxThreads=16"));
    }
}
