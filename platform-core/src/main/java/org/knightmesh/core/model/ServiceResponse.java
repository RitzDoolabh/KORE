package org.knightmesh.core.model;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Represents the outcome of executing a CK service.
 */
public class ServiceResponse {
    public enum Status { SUCCESS, FAILURE }

    private final Status status;
    private final Map<String, Object> data;
    private final String errorCode;
    private final String errorMessage;

    public ServiceResponse(Status status, Map<String, Object> data, String errorCode, String errorMessage) {
        this.status = Objects.requireNonNull(status, "status");
        this.data = data == null ? Collections.emptyMap() : Collections.unmodifiableMap(data);
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    public static ServiceResponse success(Map<String, Object> data) {
        return new ServiceResponse(Status.SUCCESS, data, null, null);
    }

    public static ServiceResponse failure(String errorCode, String errorMessage, Map<String, Object> data) {
        return new ServiceResponse(Status.FAILURE, data, errorCode, errorMessage);
    }

    public Status getStatus() {
        return status;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    @Override
    public String toString() {
        return "ServiceResponse{" +
                "status=" + status +
                ", dataKeys=" + data.keySet() +
                ", errorCode='" + errorCode + '\'' +
                ", errorMessage='" + errorMessage + '\'' +
                '}';
    }
}
