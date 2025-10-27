# Development Guide

This guide explains how to build, run, extend, and test the Knightmesh Platform. It focuses on adding new runtime services, extending plugins, and working with configuration in PostgreSQL.

## Table of contents
- Project structure
- Local development
- Adding a new service (CKService)
- Invoking services (ServiceInvoker/ServiceRouter)
- Adding/Extending plugins (QueuePlugin)
- Seeding and reading configuration (PostgreSQL + Flyway)
- Discovery and remote calls (Kubernetes + Resilience4j)
- Observability (metrics, tracing)
- Running tests
- Building images

---

## Project structure
- platform-core
  - `org.knightmesh.core.service.CKService`: service contract
  - `org.knightmesh.core.model.*`: `ServiceRequest`, `ServiceResponse`, `ServiceMetrics`
  - `org.knightmesh.core.annotations.CKServiceRegistration`: annotation for auto‑registration
  - `org.knightmesh.core.config.*`: JPA entities for Module/Service/Plugin/Flow/GatewayRoute configs
- module-runtime
  - Local in‑memory registry + descriptors
  - Router (`ServiceRouter`) with local‑first execution, remote fallback
  - Discovery (`RemoteServiceLocator` + `KubernetesRemoteServiceLocator`), HTTP invoker (`RemoteHttpInvoker`)
  - Auto‑registration (`LocalServiceAutoRegistrar`)
  - Observability config and capacity endpoints
  - ConfigRepository + Spring Data repos for configs
- modules
  - `modules/spm`: sample services (`REGISTER_USER`, `USER_AUTH`)
  - `modules/irp`: ingress controller `/irp/{serviceName}` (DIRECT/QUEUE)
  - `modules/qpm`: scheduled dequeuer `QpmWorker`
  - `modules/orm`: skeleton runtime for future sinks
- plugins
  - QueuePlugin API + `InMemoryQueuePlugin`, `PersistentQueuePlugin` (+ JPA entity/repo)
- mgm
  - Control plane REST `/mgm/*`, seed runner, (simulated) reconciler scaffolding
- gateway
  - Spring Cloud Gateway, JWT validation (Keycloak), static/DB routes
- deployment
  - Helm chart (`deployment/helm/knightmesh`) and Docker Compose stacks

---

## Local development
- Quick local stack: see README "Quick start (Docker Compose)".
- To run a single module locally via Gradle:
  - `./gradlew :modules:spm:bootRun`
  - `./gradlew :modules:irp:bootRun`
  - `./gradlew :mgm:bootRun`
- For observability stack (Prometheus/Grafana/Jaeger), use `deployment/monitoring/docker-compose.yml`.

Environment variables of interest:
- `OIDC_ISSUER_URI` (gateway) – Keycloak issuer URL
- `OTEL_EXPORTER_OTLP_ENDPOINT` – Jaeger/OTEL collector endpoint (default `http://localhost:4317`)
- `observability.*` – common tags (module, module_type, instance_id)

---

## Adding a new service (CKService)
1) Create a class in an SPM module (or any module runtime that hosts services):
```java
package org.knightmesh.spm.services;

import org.knightmesh.core.annotations.CKServiceRegistration;
import org.knightmesh.core.model.ServiceMetrics;
import org.knightmesh.core.model.ServiceRequest;
import org.knightmesh.core.model.ServiceResponse;
import org.knightmesh.core.service.CKService;

@CKServiceRegistration(name = "MY_SERVICE")
public class MyService implements CKService {
    private final ServiceMetrics metrics = new ServiceMetrics(
        10,   // maxThreads
        2.5,  // avgLatencyMs (hint only)
        0,    // successCount (runtime may track)
        0     // failureCount
    );

    @Override
    public String getServiceName() { return "MY_SERVICE"; }

    @Override
    public ServiceResponse execute(ServiceRequest request) {
        // business logic here; read request.getPayload()/getMetadata()
        return ServiceResponse.success(Map.of("echo", request.getPayload()));
    }

    @Override
    public ServiceMetrics getMetrics() { return metrics; }
}
```
2) Ensure the service’s package is scanned by your Spring Boot app (e.g., SPM uses `@SpringBootApplication(scanBasePackages = {"org.knightmesh"})`).
3) Auto‑registration: `LocalServiceAutoRegistrar` discovers beans of type `CKService` and/or annotated with `@CKServiceRegistration` and registers them into `LocalServiceRegistry` using `getMetrics().getMaxThreads()`.
4) Thread capacity: Adjust `maxThreads` via `ServiceMetrics`. Router does CAS reservation and `try/finally` release to enforce limits.
5) Testing your service:
   - Unit test: call `execute(...)` directly with a `ServiceRequest`.
   - Integration test: start your runtime context and route via `ServiceRouter` to ensure local execution path.

---

## Invoking services (ServiceInvoker/ServiceRouter)
- Prefer the `ServiceInvoker` abstraction (API in platform-core) to decouple business code from routing specifics.
- Default implementation `RouterServiceInvoker` (module-runtime) delegates to `ServiceRouter`.
- Example (used in `RegisterUserService`):
```java
@Service
public class RegisterUserService implements CKService {
  private final ServiceInvoker invoker;
  // constructor injection
  public RegisterUserService(ServiceInvoker invoker) { this.invoker = invoker; }
  @Override
  public ServiceResponse execute(ServiceRequest req) {
    // ... validate, persist, then call another service
    ServiceResponse auth = invoker.invoke(new ServiceRequest("USER_AUTH", Map.of("user", "alice"), Map.of(), req.getCorrelationId()));
    // ... merge/return
    return auth;
  }
}
```

`ServiceRouter` logic:
- Local‑first: if service is locally registered and has capacity → execute in‑process
- Otherwise remote: discover instances (`RemoteServiceLocator`), POST to `/internal/service/{serviceName}` via `RemoteHttpInvoker`
- Resilience: `@Retry` (3 attempts, backoff) + `@CircuitBreaker` (opens after repeated failures)

---

## Adding/Extending plugins (QueuePlugin)
`QueuePlugin` interface:
```java
public interface QueuePlugin {
   void enqueue(String queueName, ServiceRequest request);
   ServiceRequest dequeue(String queueName);
   int size(String queueName);
}
```
Available implementations:
- `InMemoryQueuePlugin`: simple per‑queue `ConcurrentLinkedQueue`
- `PersistentQueuePlugin`: JPA entity `PersistentQueueMessage` and repository, FIFO with optimistic locking

Build your own plugin by implementing the interface and registering it as a Spring bean (e.g., `@Component`). IRP will `enqueue` when `RouteMode=QUEUE`, and QPM’s `QpmWorker` will `dequeue` and route.

Testing a plugin:
- Unit test with in‑memory behavior
- Integration test with Testcontainers + Postgres if persistence is involved

---

## Seeding and reading configuration (PostgreSQL + Flyway)
Configuration entities live in `platform-core` under `org.knightmesh.core.config` (e.g., `ModuleConfig`, `ServiceConfig`, `GatewayRoute`). Migrations live in `module-runtime/src/main/resources/db/migration` (V1…V3).

Ways to seed config:
1) Using MGM’s seed runner (local dev): set `mgm.seed=true` (default in `mgm/application.properties`), MGM will insert demo `ModuleConfig` and `ServiceConfig` on first start (H2 by default; adjust to Postgres via env in docker-compose).
2) SQL migrations: add a Flyway migration under `module-runtime/src/main/resources/db/migration` (V4__your_change.sql) to insert or alter configs.
3) Programmatic in tests: use Spring Data repositories in integration tests with Testcontainers to insert rows before assertions.

Accessing config at runtime:
- `ConfigRepository` aggregates Spring Data repositories and exposes:
  - `listEnabledModules()`, `getModuleConfig(name)`
  - `findServicesForModule(moduleName)`
  - `listGatewayRoutes()`
- IRP uses `getModuleConfig("irp")` to decide `RouteMode` and `queueName`.

---

## Discovery and remote calls (Kubernetes + Resilience4j)
- `KubernetesRemoteServiceLocator` implements `RemoteServiceLocator`.
  - If env `KUBERNETES_SERVICE_HOST` is present (or a property flag enabled), it uses `DiscoveryClient` to find instances.
  - Otherwise it falls back to DB `ModuleConfig.extraJson` (expects `instances` array with `{host,port,metadata}`) for local dev.
- `RemoteHttpInvoker` performs the HTTP call with `@Retry` and `@CircuitBreaker`.
  - Retries: 3 attempts with exponential backoff (configured in test properties; can be overridden in app properties)
  - Circuit breaker: opens after repeated failures and waits before half‑open

---

## Observability (metrics, tracing)
- Actuator endpoints exposed: `/actuator/health`, `/actuator/prometheus`
- Common tags are automatically added to all meters: `module`, `module_type`, `instance_id`
- Router metrics:
  - `router_requests_total{service_name,route, outcome}`
  - `router_latency{service_name,route,outcome}`
- Thread/capacity gauges per service registered by `LocalServiceRegistry`:
  - `spm_active_threads`, `spm_max_threads`, `spm_thread_utilization`
- Tracing: Micrometer tracing + OTLP exporter. Set `otel.exporter.otlp.endpoint` and view in Jaeger.

---

## Running tests
This repository includes unit tests and integration tests with Testcontainers. Ensure Docker is available.
- All tests: `./gradlew clean build`
- By module: `./gradlew :module-runtime:test`, `./gradlew :modules:irp:test`, etc.
- Troubleshooting: If Docker isn’t available, Testcontainers tests will skip/fail; run unit tests only or install Docker.

---

## Building images
Use the helper scripts:
- Local Docker: `./scripts/build-images.sh --tag dev`
- Minikube: `./scripts/minikube-setup.sh --use-docker-env && ./scripts/build-images.sh --minikube --tag local`

Images:
- `knightmesh/spm`, `knightmesh/irp`, `knightmesh/qpm`, `knightmesh/mgm`, `knightmesh/gateway`

---

Happy hacking! If you add a new module or plugin, consider adding a short README in that subproject describing its endpoints, configuration, and any DB migrations. 
