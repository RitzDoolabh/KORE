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


---

## Source map: where to find things (by module)

- platform-core
  - Contracts and models
    - `platform-core/src/main/java/org/knightmesh/core/service/CKService.java`
    - `platform-core/src/main/java/org/knightmesh/core/model/{ServiceRequest,ServiceResponse,ServiceMetrics}.java`
    - `platform-core/src/main/java/org/knightmesh/core/annotations/CKServiceRegistration.java`
  - Config entities (JPA)
    - `platform-core/src/main/java/org/knightmesh/core/config/{ModuleConfig,ServiceConfig,GatewayRoute,...}.java`
- module-runtime
  - Registry and descriptors
    - `module-runtime/src/main/java/org/knightmesh/runtime/registry/{LocalServiceRegistry,LocalServiceDescriptor,ServiceStatus}.java`
    - Auto registration: `.../LocalServiceAutoRegistrar.java`
  - Router and discovery
    - `module-runtime/src/main/java/org/knightmesh/runtime/router/ServiceRouter.java`
    - `module-runtime/src/main/java/org/knightmesh/runtime/router/{RemoteServiceLocator,KubernetesRemoteServiceLocator,ServiceInstance}.java`
    - HTTP invoker + client: `RemoteHttpInvoker.java`, `RemoteHttpConfig.java`
  - Monitoring
    - `module-runtime/src/main/java/org/knightmesh/runtime/monitoring/{ObservabilityConfig,CapacityController}.java`
  - Config repository + Spring Data repos
    - `module-runtime/src/main/java/org/knightmesh/runtime/config/ConfigRepository.java`
    - `module-runtime/src/main/java/org/knightmesh/runtime/config/repo/*.java`
  - DB migrations (Flyway)
    - `module-runtime/src/main/resources/db/migration/V1__init_config_tables.sql`
    - `V2__align_config_schema.sql`, `V3__gateway_routes.sql`
- modules
  - IRP app and controller
    - `modules/irp/src/main/java/org/knightmesh/irp/{IrpApplication,IrpController}.java`
  - SPM app and example services
    - `modules/spm/src/main/java/org/knightmesh/spm/SpmApplication.java`
    - `modules/spm/src/main/java/org/knightmesh/spm/services/{RegisterUserService,UserAuthService}.java`
  - QPM app and worker
    - `modules/qpm/src/main/java/org/knightmesh/qpm/{QpmApplication,QpmWorker}.java`
- plugins
  - Queue abstraction and impls
    - `plugins/src/main/java/org/knightmesh/plugins/queue/{QueuePlugin,InMemoryQueuePlugin,PersistentQueuePlugin, PersistentQueueMessage, PersistentQueueMessageRepository}.java`
- mgm
  - `mgm/src/main/java/org/knightmesh/mgm/{MgmApplication}.java`
  - `mgm/src/main/java/org/knightmesh/mgm/api/{MgmController,ModuleView}.java`
- gateway
  - `gateway/src/main/java/org/knightmesh/gateway/{GatewayApplication,SecurityConfig,RouteConfig,RequiredRolesGatewayFilterFactory}.java`

---

## Verified request flow (step-by-step)

1) Gateway authorization (WebFlux)
   - `SecurityConfig` maps Keycloak roles to `ROLE_*` and enforces:
     - `/mgm/**` → ADMIN, `/irp/**` → USER, `/spm/**` → SERVICE.
   - `RouteConfig` either loads DB routes (`GatewayRoute`) or uses static fallbacks from properties.
   - The `Authorization: Bearer <jwt>` header is preserved and forwarded to downstream services (see `GatewaySecurityIntegrationTest`).

2) IRP ingest
   - `IrpController.post("/irp/{serviceName}")` builds a `ServiceRequest` (metadata includes `timestamp` and `source=IRP`).
   - IRP reads `ModuleConfig` via `ConfigRepository.getModuleConfig("irp")` to choose `DIRECT` vs `QUEUE`.
     - `DIRECT`: `ServiceRouter.route(request)` and return body as JSON with 200/400 based on `ServiceResponse.Status`.
     - `QUEUE`: `QueuePlugin.enqueue(queueName, request)` and `202 ACCEPTED` with `{correlationId}`.

3) ServiceRouter local-first
   - Looks up `LocalServiceDescriptor` in `LocalServiceRegistry` by `serviceName`.
   - If `hasCapacity()`: `incrementActive()`, call `descriptor.getInstance().execute(req)` in `try/finally` with `decrementActive()`.
   - Records metrics via `MeterRegistry` if present.

4) Remote fallback
   - Uses `RemoteServiceLocator.findInstances(serviceName)` to get `List<ServiceInstance>`.
   - Chooses instance round-robin and calls `RemoteHttpInvoker.post(instance, request)` which posts to `/internal/service/{serviceName}` with Resilience4j retry + circuit breaker.
   - On errors after retries (or circuit open), returns `ServiceResponse.failure("SERVICE_UNAVAILABLE", ..)`.

5) SPM services
   - `@CKServiceRegistration` beans (e.g., `RegisterUserService`, `UserAuthService`) are auto-registered on startup by `LocalServiceAutoRegistrar`.
   - `RegisterUserService` constructs another `ServiceRequest` and uses `ServiceInvoker` (implemented by `RouterServiceInvoker`) to call `USER_AUTH` locally.

6) QPM asynchronous processing
   - `QpmWorker` scans enabled modules with `RouteMode=QUEUE` and drains their queues, routing each `ServiceRequest` via `ServiceRouter`.

Tests proving each step:
- Gateway: `gateway/.../GatewaySecurityIntegrationTest` → JWT required, header forwarded.
- IRP DIRECT: `modules/irp/.../IrpDirectIntegrationTest` → calls SPM locally.
- IRP remote fallback: `modules/irp/.../IrpRemoteFallbackIntegrationTest` → capacity saturated, remote WireMock called.
- Router local: `module-runtime/.../ServiceRouterLocalTest`.
- Retry/Circuit: `ServiceRouterRetryTest`, `ServiceRouterCircuitBreakerTest`.

---

## Configuration properties (by concern)

- Gateway OIDC:
  - `spring.security.oauth2.resourceserver.jwt.issuer-uri` (defaults to `${OIDC_ISSUER_URI}` env)
- Static backend URIs for gateway (fallback when DB has no routes):
  - `gateway.mgm.uri`, `gateway.irp.uri`, `gateway.spm.uri`
- Observability common tags (applied by `ObservabilityConfig`):
  - `observability.module`, `observability.module_type`, `observability.instance_id`
- Tracing exporter endpoint:
  - `otel.exporter.otlp.endpoint` (e.g., `http://localhost:4317`)
- QPM poll frequency:
  - `qpm.poll.delay.ms` (default 250)
- Resilience4j (example keys – set at module level):
  - Retry: `resilience4j.retry.instances.remoteRouter.max-attempts`, `...wait-duration`, `...enable-exponential-backoff`, `...exponential-backoff-multiplier`
  - Circuit breaker: `resilience4j.circuitbreaker.instances.remoteRouter.*` (e.g., `sliding-window-size`, `wait-duration-in-open-state`)

---

## Database schema and migrations

- Migrations live in `module-runtime/src/main/resources/db/migration` and are applied where Flyway is enabled.
- Core tables:
  - `module_config` – desired modules, route mode, queue name, etc.
  - `service_config` – per-service settings, including `max_threads`.
  - `gateway_route` – DB-configured gateway routes and roles.
  - `persistent_queue_message` – used by `PersistentQueuePlugin`.
- To add a migration, create `V<next>__description.sql` in that folder; Flyway orders by version.

---

## Testcontainers patterns (examples)

- Postgres-backed IRP test: `IrpSpmPostgresIntegrationTest`
  - Uses `@DynamicPropertySource` to point Spring Data to the container and enables Flyway.
  - Seeds `ModuleConfig` and performs an HTTP POST to IRP and expects SUCCESS.
- WireMock-backed remote fallback: `IrpRemoteFallbackIntegrationTest`
  - Starts WireMock on a dynamic port and provides a `RemoteServiceLocator` bean pointing at it.

---

## Tips for extending

- New CKService: implement the interface, annotate with `@CKServiceRegistration`, and ensure package scanning. Provide `ServiceMetrics` with a sane `maxThreads`.
- Calling other services: inject `ServiceInvoker` (preferred) instead of `ServiceRouter` to avoid tight coupling and ease future async integration.
- New queue plugin: implement `QueuePlugin` and register as a Spring bean; configure IRP `RouteMode=QUEUE` in `ModuleConfig` and ensure QPM is running.
- New gateway route: insert a `GatewayRoute` row (via migration or JPA) and `ConfigRepository.reload()`; verify required roles are enforced by `RequiredRolesGatewayFilterFactory`.

