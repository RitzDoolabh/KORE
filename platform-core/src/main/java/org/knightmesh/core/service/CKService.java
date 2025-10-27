package org.knightmesh.core.service;

import org.knightmesh.core.model.ServiceMetrics;
import org.knightmesh.core.model.ServiceRequest;
import org.knightmesh.core.model.ServiceResponse;

/**
 * Contract for a CK runtime service (e.g., REGISTER_USER, USER_AUTH, etc.).
 */
public interface CKService {
    String getServiceName();
    ServiceResponse execute(ServiceRequest request);
    ServiceMetrics getMetrics();
}
