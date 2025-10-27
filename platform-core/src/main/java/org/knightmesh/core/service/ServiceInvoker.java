package org.knightmesh.core.service;

import org.knightmesh.core.model.ServiceRequest;
import org.knightmesh.core.model.ServiceResponse;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

/**
 * Abstraction for invoking services by name. Implementations may route locally or remotely.
 * Provides synchronous and asynchronous variants and a simple decorator hook for tracing/metrics.
 */
public interface ServiceInvoker {

    /**
     * Invoke the given request synchronously.
     */
    ServiceResponse invoke(ServiceRequest request);

    /**
     * Invoke the given request asynchronously using a default executor (common pool) unless overridden.
     */
    default CompletableFuture<ServiceResponse> invokeAsync(ServiceRequest request) {
        return CompletableFuture.supplyAsync(() -> invoke(request));
    }

    /**
     * Decorate this invoker with pre/post hooks (e.g., tracing).
     * @param decorator a callback receiving (request,response) after invocation; may be used for tracing/metrics
     * @return a new invoker instance applying the decorator
     */
    default ServiceInvoker withDecorator(BiConsumer<ServiceRequest, ServiceResponse> decorator) {
        Objects.requireNonNull(decorator, "decorator");
        ServiceInvoker self = this;
        return new ServiceInvoker() {
            @Override
            public ServiceResponse invoke(ServiceRequest request) {
                ServiceResponse response = self.invoke(request);
                try {
                    decorator.accept(request, response);
                } catch (RuntimeException ignored) {
                    // best-effort tracing/metrics hook
                }
                return response;
            }

            @Override
            public CompletableFuture<ServiceResponse> invokeAsync(ServiceRequest request) {
                return self.invokeAsync(request)
                        .whenComplete((resp, ex) -> {
                            if (ex == null) {
                                try { decorator.accept(request, resp); } catch (RuntimeException ignored) {}
                            }
                        });
            }
        };
    }
}
