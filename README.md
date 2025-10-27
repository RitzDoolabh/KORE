# Knightmesh Platform

Knightmesh is a modular, service‑mesh‑like platform built on Spring Boot 3 and Java 21. It provides a runtime for pluggable services (SPM), an ingress request processor (IRP), a queue processor (QPM), a control plane (MGM), and a secured API Gateway. It includes observability (Actuator, Prometheus/Micrometer, OpenTelemetry), Kubernetes discovery and deployment artifacts (Helm), and local development stacks (Docker Compose, Minikube).

- Project root: `knightmesh-platform` (this repo)
- Language/Runtime: Java 21, Spring Boot 3.3.x
- Security: Keycloak (OIDC / JWT)
- Discovery: Spring Cloud Kubernetes (with DB fallback)
- Resilience: Resilience4j (retries, circuit breaker)
- Observability: Micrometer, Prometheus, Grafana, Jaeger (OTLP)


## Table of contents
- Overview
- Architecture diagram
- Modules
- Quick start (Docker Compose)
- Running example calls (IRP and MGM)
- Building images
- Minikube quick start
- Running tests
- Next steps


## Overview
At a high level:
- platform-core: API contracts and domain models (`CKService`, `ServiceRequest`, `ServiceResponse`, `ServiceMetrics`, `@CKServiceRegistration`, config entities)
- module-runtime: Local service registry, router (local‑first, remote fallback), discovery, resilience, metrics, capacity endpoints
- modules:
  - SPM: Service Processing Module. Hosts actual business `CKService` implementations (e.g., `REGISTER_USER`, `USER_AUTH`).
  - IRP: Ingress Request Processor. Public API that transforms incoming JSON into `ServiceRequest` and routes DIRECT or QUEUE.
  - QPM: Queue Processor Module. Dequeues and invokes via router.
  - ORM: Output Runtime Module (skeleton, for future sinks).
- plugins: Queue abstraction (`QueuePlugin`) with in‑memory and JPA‑backed persistent implementations.
- mgm: Mesh Grid Manager. Control plane that reads desired state from DB and reconciles (K8s if enabled). Exposes control APIs.
- gateway: Spring Cloud Gateway, Keycloak JWT validation, path routing to modules, role‑based access control.
- deployment: Helm chart skeleton and local Docker Compose for dev.


## Architecture (ASCII)
```
                    +------------------+
                    |   Keycloak OIDC  |
                    +---------+--------+
                              |
                   JWT        v
+-----------------------------+------------------------------+
|                         API Gateway                        |
|  (Spring Cloud Gateway + OAuth2 Resource Server)          |
|  Routes: /irp/**, /mgm/**, /spm/**                        |
+-----------+-------------------------+----------------------+
            |                         |
            v                         v
        +---+----+                 +--+---+
        |  IRP   |  DIRECT/QUEUE  |  MGM |  (control plane)
        +---+----+-----------------+--+---+
            |                          |
            | ServiceRouter            | Reconcile / Observe
            v                          v
        +---+-----------------------------------+
        |           module-runtime               |
        | LocalServiceRegistry + ServiceRouter   |
        |  - Local-first execution               |
        |  - Remote via Kubernetes/DB discovery  |
        |  - Resilience4j retry/circuit breaker  |
        +---+-----------------------------------+
            |
     local  |                     remote (HTTP /internal/service/{name})
            v                                       ^
        +---+---+                                    |
        |  SPM  |  implements CKService(s)           |
        +---+---+------------------------------------+
            ^
            |
       +----+----+
       |  QPM    |  (dequeues via plugins, calls router)
       +---------+

  Storage: PostgreSQL (configs, persistent queues)
  Security roles: ADMIN (MGM), USER (IRP), SERVICE (internal)
  Observability: /actuator/prometheus, Jaeger traces (OTLP)
```


## Modules
- platform-core: shared contracts and entities
- module-runtime: registry, router, discovery (`RemoteServiceLocator` + K8s), resilience (`RemoteHttpInvoker`)
- modules/irp: REST `/irp/{serviceName}`; DIRECT vs QUEUE based on `ConfigRepository`
- modules/spm: sample services `REGISTER_USER`, `USER_AUTH` auto‑registered by `LocalServiceAutoRegistrar`
- modules/qpm: `QpmWorker` scheduled dequeuer (with `QueuePlugin` implementations)
- plugins: `QueuePlugin` API + `InMemoryQueuePlugin`, `PersistentQueuePlugin`
- mgm: REST `/mgm/*` (desired state), seeding, simulation mode by default
- gateway: JWT validation, role mapping, static/DB routes, forwards Authorization
- deployment: Helm chart `deployment/helm/knightmesh`, compose stacks under `deployment/local` and `deployment/monitoring`


## Quick start (Docker Compose)
Prereqs: Docker Desktop (or Docker Engine), Java 21 + Gradle (for local builds).

1) Copy env and adjust values if needed
```
cp deployment/local/.env.sample deployment/local/.env
```

2) Build application images (recommended for faster compose restarts)
```
./scripts/build-images.sh --tag dev
```

3) Start local stack (Postgres + Keycloak + Gateway + MGM + SPM)
```
docker compose --env-file deployment/local/.env \
  -f deployment/local/docker-compose.dev.yml up --build
```
Optionally add monitoring (Prometheus, Grafana, Jaeger):
```
docker compose --env-file deployment/local/.env \
  -f deployment/local/docker-compose.dev.yml --profile monitoring up --build
```

4) Verify
- Keycloak admin UI: http://localhost:8080 (users: admin/password, devuser/password)
- Gateway: http://localhost:8088
- MGM: http://localhost:8085/mgm/modules
- SPM actuator: http://localhost:8081/actuator/health


## Example calls (IRP and MGM)
These examples use the Gateway so JWT is required.

1) Obtain a JWT from Keycloak (password grant; public client `knightmesh-gateway`)
```
export TOKEN=$(curl -s -X POST \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -d 'grant_type=password' \
  -d 'client_id=knightmesh-gateway' \
  -d 'username=devuser' -d 'password=password' \
  http://localhost:8080/realms/knightmesh/protocol/openid-connect/token | jq -r .access_token)
```

2) IRP DIRECT call via Gateway (requires role USER)
```
curl -s -X POST http://localhost:8088/irp/REGISTER_USER \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"username":"alice","email":"alice@example.org","password":"pw"}' | jq .
```
Expected: `{ "status": "SUCCESS", "data": {"user":"alice", ...} }`

3) MGM API via Gateway (requires role ADMIN; use admin/password)
```
export ADMIN_TOKEN=$(curl -s -X POST \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -d 'grant_type=password' \
  -d 'client_id=knightmesh-gateway' \
  -d 'username=admin' -d 'password=admin' \
  http://localhost:8080/realms/knightmesh/protocol/openid-connect/token | jq -r .access_token)

curl -s http://localhost:8088/mgm/modules \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq .
```

Direct MGM (bypassing Gateway, useful locally):
```
curl -s http://localhost:8085/mgm/modules | jq .
```


## Building images
Use the helper script to build all runnable module images. It can target your local Docker or Minikube’s Docker daemon.

- Local Docker:
```
./scripts/build-images.sh --tag dev
```
- Minikube (build straight into cluster daemon):
```
./scripts/minikube-setup.sh --use-docker-env
./scripts/build-images.sh --minikube --tag local
```

Images produced:
- `knightmesh/spm:<tag>`
- `knightmesh/irp:<tag>`
- `knightmesh/qpm:<tag>`
- `knightmesh/mgm:<tag>`
- `knightmesh/gateway:<tag>`


## Minikube quick start
1) Start Minikube and enable ingress
```
./scripts/minikube-setup.sh --cpus 4 --memory 8192 --use-docker-env
```
2) Build images into Minikube daemon
```
./scripts/build-images.sh --minikube --tag local
```
3) Deploy a module with Helm (example: SPM)
```
helm upgrade --install spm ./deployment/helm/knightmesh \
  --set image.repository=knightmesh/spm \
  --set image.tag=local \
  --set module.name=spm \
  --set module.type=SPM \
  --set replicaCount=1
```
4) Verify pod
```
kubectl get pods
```


## Running tests
This project includes unit tests and integration tests using Testcontainers (PostgreSQL, WireMock). Ensure Docker is available.
```
./gradlew clean build
```
Tips:
- To run a single module’s tests: `./gradlew :module-runtime:test`
- To skip tests temporarily: `./gradlew clean build -x test`


## Learn more
- Development guide: [DEVELOPMENT.md](DEVELOPMENT.md)
- Operations guide: [OPERATIONS.md](OPERATIONS.md)


---

## Deep architecture and request flows (verified against source)

This appendix explains, in detail, how the platform is wired internally. It cites concrete classes, methods, and endpoints present in this repo so you can confidently trace an end‑to‑end request.

### Core contracts (platform-core)
- Service contract: `org.knightmesh.core.service.CKService`
  - `String getServiceName()` – logical name, e.g. `"REGISTER_USER"`.
  - `ServiceResponse execute(ServiceRequest request)` – synchronous execution.
  - `ServiceMetrics getMetrics()` – provides `maxThreads` used for capacity.
- Models (package `org.knightmesh.core.model`):
  - `ServiceRequest(String serviceName, Map<String,Object> payload, Map<String,String> metadata, String correlationId)`.
  - `ServiceResponse` with `enum Status { SUCCESS, FAILURE }` and `success(..)/failure(..)` factories.
  - `ServiceMetrics(int maxThreads, double avgLatencyMs, long successCount, long failureCount)`.
- Registration annotation: `org.knightmesh.core.annotations.CKServiceRegistration(name=...)` – marks services for auto‑registration.
- Config entities (package `org.knightmesh.core.config`): `ModuleConfig`, `ServiceConfig`, `GatewayRoute`, etc.

### Runtime (module-runtime)
- Local descriptor: `org.knightmesh.runtime.registry.LocalServiceDescriptor`
  - Fields include `serviceName`, `CKService instance`, `AtomicInteger activeThreads`, `int maxThreads`, `ServiceStatus status`, `Instant lastHeartbeat`.
  - Methods: `hasCapacity()`, `incrementActive()`, `decrementActive()`, getters.
- Local registry: `org.knightmesh.runtime.registry.LocalServiceRegistry`
  - Thread‑safe `ConcurrentHashMap<String, LocalServiceDescriptor>` store.
  - `register(LocalServiceDescriptor)` updates `lastHeartbeat`, logs, and registers Micrometer gauges:
    - `spm_active_threads{service_name}`
    - `spm_max_threads{service_name}`
    - `spm_thread_utilization{service_name}`
  - `lookup`, `listAll`, `deregister`, `capacitySnapshot()`.
- Auto‑registration: `org.knightmesh.runtime.registry.LocalServiceAutoRegistrar`
  - Listens on `ContextRefreshedEvent`, finds beans of type `CKService` and/or annotated with `@CKServiceRegistration` and calls `LocalServiceRegistry.register(..)` using `getMetrics().getMaxThreads()` (defaults applied if invalid).
- Router: `org.knightmesh.runtime.router.ServiceRouter`
  - Local‑first and remote‑fallback routing:
    1) Lookup `LocalServiceDescriptor` by `request.serviceName`.
    2) If present and `hasCapacity()`:
       - CAS reserve via `incrementActive()`, then `try/finally` execute `descriptor.getInstance().execute(request)` and `decrementActive()`.
       - Record metrics: `router_requests_total{service_name,route="local",outcome}` and `router_latency{..}`.
    3) Else, remote flow `routeRemote(..)`:
       - Discover instances using `RemoteServiceLocator` (preferred) or fall back to `KubernetesServiceLocator` stub if wired.
       - Select instance round‑robin; POST `ServiceRequest` to `/internal/service/{serviceName}` using `RemoteHttpInvoker`.
       - Resilience with Resilience4j: `@Retry(name="remoteRouter")` and `@CircuitBreaker(name="remoteRouter")` around HTTP calls; failures map to `ServiceResponse.failure("SERVICE_UNAVAILABLE", ..)`; open circuit maps to `ServiceResponse.failure("SERVICE_UNAVAILABLE", "Circuit open for remote service: "+svc, null)`.

- HTTP invoker: `org.knightmesh.runtime.router.RemoteHttpInvoker`
  - Method `post(ServiceInstance, ServiceRequest)` posts to `/internal/service/{serviceName}` on the chosen instance using a `RestTemplate` bean defined in `RemoteHttpConfig`.
  - Annotated with Resilience4j retry and circuit breaker; null responses are treated as errors.

- Discovery: `org.knightmesh.runtime.router.KubernetesRemoteServiceLocator`
  - If env `KUBERNETES_SERVICE_HOST` is present (or property), uses Spring Cloud `DiscoveryClient` to find instances by name.
  - Else, DB fallback: parses `ModuleConfig.extraJson` for `instances: [{host,port,metadata}]` and returns a list of `ServiceInstance`.

### Ingress (IRP)
- App: `org.knightmesh.irp.IrpApplication` (scans `org.knightmesh` packages).
- Controller: `org.knightmesh.irp.IrpController`
  - Endpoint: `POST /irp/{serviceName}` accepts JSON `payload` (mapped to `Map<String,Object>`).
  - Builds `ServiceRequest` with `correlationId=UUID.randomUUID()` and metadata `{timestamp, source=IRP}`.
  - Reads route mode via `ConfigRepository.getModuleConfig("irp")` → `ModuleConfig.routeMode`.
    - `DIRECT` (default): call `ServiceRouter.route(request)` and return `ServiceResponse` (HTTP 200 on SUCCESS else 400).
    - `QUEUE`: send to `QueuePlugin.enqueue(queueNameOrDefault, request)` and return HTTP 202 with `{status: "ACCEPTED", correlationId}`.

### Service Processing Module (SPM)
- App: `org.knightmesh.spm.SpmApplication` (scans `org.knightmesh`).
- Sample services: `org.knightmesh.spm.services.RegisterUserService` and `UserAuthService`, both annotated with `@CKServiceRegistration` so the `LocalServiceAutoRegistrar` registers them with the local registry.
  - `RegisterUserService`: validates payload (`username`, `email`), simulates persistence (logs), then invokes `USER_AUTH` via `ServiceInvoker` and aggregates the response.
  - `UserAuthService`: simulates authentication, returns `ServiceResponse.success` with a token.

### Queue Processor Module (QPM)
- App: `org.knightmesh.qpm.QpmApplication` with `@EnableScheduling`.
- Worker: `org.knightmesh.qpm.QpmWorker`
  - On schedule (`qpm.poll.delay.ms`, default 250ms), lists enabled modules; for those with `RouteMode=QUEUE` and a `queueName`, drains messages using configured `QueuePlugin.dequeue(queueName)` up to a batch size (50) and routes each via `ServiceRouter`.

### Gateway
- App: `org.knightmesh.gateway.GatewayApplication` (WebFlux).
- Security: `org.knightmesh.gateway.SecurityConfig`
  - Configured as a resource server that validates JWTs from Keycloak (`OIDC_ISSUER_URI`).
  - Maps Keycloak roles from `realm_access.roles` and `resource_access.*.roles` to `ROLE_*` authorities.
  - Authorization rules:
    - `/mgm/**` → `ROLE_ADMIN`
    - `/irp/**` → `ROLE_USER`
    - `/spm/**` → `ROLE_SERVICE`
  - `/actuator/health` and OPTIONS requests are permitted.
- Routes: `org.knightmesh.gateway.RouteConfig`
  - If DB has `GatewayRoute` rows via `ConfigRepository.listGatewayRoutes()`, builds routes dynamically and applies `RequiredRolesGatewayFilterFactory` when `requiredRoles` is set.
  - Else, static fallbacks map `/mgm/**`, `/irp/**`, `/spm/**` to URIs from properties and preserve the `Authorization` header so downstream modules receive the JWT.

### Mesh Grid Manager (MGM)
- App: `org.knightmesh.mgm.MgmApplication`.
- Controller: `org.knightmesh.mgm.api.MgmController`
  - `GET /mgm/modules` → lists desired modules from the DB via `ConfigRepository.listEnabledModules()`.
  - `POST /mgm/modules/{name}/start|stop` and `POST /mgm/reconcile` → simulated responses unless a real K8s controller is enabled.

### Observability
- Common Micrometer tags are applied via `org.knightmesh.runtime.monitoring.ObservabilityConfig`:
  - `module`, `module_type`, `instance_id` from `observability.*` properties.
- Capacity endpoints are exposed by `org.knightmesh.runtime.monitoring.CapacityController`:
  - `/spm/capacity`, `/irp/capacity`, `/qpm/capacity` (returns `LocalServiceRegistry.CapacityView` per service).
- Router metrics: `router_requests_total` and `router_latency` with tags `service_name`, `route`, `outcome`.
- Thread gauges per service: `spm_active_threads`, `spm_max_threads`, `spm_thread_utilization`.

### Data model overview (Flyway migrations V1..V3)
- `module_config` (desired modules): name, type, instance, domain, enabled, route_mode, queue_name, services, extra_json, created/updated.
- `service_config` (per service in a module): service_name (unique), module_name, max_threads, enabled, config_json.
- `gateway_route` (gateway path/routing rules): path_pattern, uri, required_roles, strip_prefix, filters_json, enabled.
- Persistent queue table when using `PersistentQueuePlugin`: `persistent_queue_message` (id, queue_name, payload_json, status, created_at).

### End‑to‑end request sequences
1) Direct local execution (Gateway → IRP → SPM local):
   - Gateway verifies JWT (`ROLE_USER`) and forwards `/irp/REGISTER_USER` to IRP.
   - IRP builds `ServiceRequest(correlationId, metadata)` and sees `RouteMode=DIRECT`.
   - ServiceRouter finds local `LocalServiceDescriptor(REGISTER_USER)` with capacity, reserves a slot, executes `RegisterUserService.execute` and releases.
   - `RegisterUserService` invokes `USER_AUTH` via `ServiceInvoker` which routes locally to `UserAuthService`.
   - IRP returns `ServiceResponse(Status=SUCCESS)` to Gateway; Gateway returns 200 JSON.

2) Remote fallback (local full → remote instance):
   - Local descriptor exists but its `activeThreads == maxThreads`.
   - Router calls `RemoteServiceLocator.findInstances("REGISTER_USER")` to get remote `ServiceInstance` list.
   - Router selects round‑robin, `RemoteHttpInvoker.post(..)` to `/internal/service/REGISTER_USER` with retry + circuit breaker.
   - On success, returns remote `ServiceResponse` to IRP/Gateway; on repeated failure, returns `FAILURE` with `errorCode=SERVICE_UNAVAILABLE`.

3) Asynchronous path (QUEUE):
   - IRP sees `RouteMode=QUEUE` with `queueName` from `ModuleConfig` and enqueues via `QueuePlugin.enqueue`.
   - Returns 202 `{status: "ACCEPTED", correlationId}` immediately.
   - QPM’s `QpmWorker` dequeues and submits each `ServiceRequest` to `ServiceRouter`; responses are logged/observed via metrics.

For concrete examples, see tests:
- Local path: `module-runtime/src/test/java/.../ServiceRouterLocalTest.java`
- Remote path & retry/circuit: `ServiceRouterRemoteTest`, `ServiceRouterRetryTest`, `ServiceRouterCircuitBreakerTest`
- IRP direct: `modules/irp/.../IrpDirectIntegrationTest.java`
- IRP remote fallback: `modules/irp/.../IrpRemoteFallbackIntegrationTest.java`
- Gateway auth/forward: `gateway/.../GatewaySecurityIntegrationTest.java`