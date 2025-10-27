package org.knightmesh.runtime.router;

import org.knightmesh.core.model.ServiceRequest;
import org.knightmesh.core.model.ServiceResponse;
import org.knightmesh.core.service.ServiceInvoker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * ServiceInvoker implementation backed by ServiceRouter.
 */
@Component
public class RouterServiceInvoker implements ServiceInvoker {
    private static final Logger log = LoggerFactory.getLogger(RouterServiceInvoker.class);

    private final ServiceRouter router;

    public RouterServiceInvoker(ServiceRouter router) {
        this.router = router;
    }

    @Override
    public ServiceResponse invoke(ServiceRequest request) {
        if (request == null) {
            return ServiceResponse.failure("INVALID_REQUEST", "Request is null", null);
        }
        log.debug("Invoking via router for service: {}", request.getServiceName());
        return router.route(request);
    }
}
