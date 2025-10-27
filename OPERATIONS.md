# Operations Guide

This document explains how to operate Knightmesh in development and production‑like environments: health and metrics, dashboards, scaling, control plane (MGM) APIs, reconcile workflows, emergency procedures, and Helm‑based deployment.

## Table of contents
- Health checks and endpoints
- Metrics and dashboards (Prometheus/Grafana)
- Tracing (OpenTelemetry/Jaeger)
- Capacity views
- Scaling guidance
- MGM (control plane): reconcile, start/stop, desired state
- Production deployment with Helm
- Runbooks (emergency restart, diagnostics)

---

## Health checks and endpoints
All runnable modules expose Spring Boot Actuator endpoints:
- `/actuator/health` – overall health
- `/actuator/health/liveness` – liveness probe (K8s)
- `/actuator/health/readiness` – readiness probe (K8s)
- `/actuator/prometheus` – metrics endpoint for Prometheus scraping

Recommended K8s probes (already templated in Helm chart):
- liveness: `GET /actuator/health/liveness`
- readiness: `GET /actuator/health/readiness`

Security
- In dev, these endpoints are open (Gateway may be more restrictive). In prod, consider IP allowlists, auth proxies, or dedicated metrics network.

---

## Metrics and dashboards (Prometheus/Grafana)
Micrometer is used across modules with Prometheus registry. Common tags attached to all metrics:
- `module` (e.g., spm, irp, mgm, qpm, gateway)
- `module_type` (SPM, IRP, MGM, QPM, GATEWAY)
- `instance_id` (e.g., pod/hostname)

Key metrics:
- Router counters/timers
  - `router_requests_total{service_name,route,outcome}`
  - `router_latency{service_name,route,outcome}`
- Thread/Capacity gauges
  - `spm_active_threads{service_name}`
  - `spm_max_threads{service_name}`
  - `spm_thread_utilization{service_name}`
- JVM and process metrics are also exposed by Spring Boot automatically.

Local monitoring stack
- `deployment/monitoring/docker-compose.yml` provides Prometheus, Grafana, and Jaeger.
- Prometheus scrape config: `deployment/monitoring/prometheus.yml` (targets for local ports; adjust as needed).
- Grafana provisioning:
  - Datasource: `deployment/monitoring/grafana/provisioning/datasources/datasource.yml`
  - Dashboards: `deployment/monitoring/grafana/dashboards/knightmesh-observability.json`

To start the stack:
```
cd deployment/monitoring
docker compose up -d
```
Open Grafana at http://localhost:3000 (admin/admin) and Prometheus at http://localhost:9090.

---

## Tracing (OpenTelemetry/Jaeger)
- Micrometer Tracing bridge to OpenTelemetry is enabled. Set `otel.exporter.otlp.endpoint` (defaults to `http://localhost:4317`).
- In local monitoring mode, Jaeger all‑in‑one exposes OTLP gRPC 4317 and UI at 16686.
- HTTP server/client spans are auto‑instrumented by Spring. You can add custom observations in services if desired.

View traces:
- Jaeger UI: http://localhost:16686
- Search for service names matching your modules (e.g., `spm`, `gateway`).

---

## Capacity views
Runtime exposes capacity snapshots per module via `CapacityController` (enabled by scanning in SPM/IRP/QPM):
- `/spm/capacity`
- `/irp/capacity`
- `/qpm/capacity`

Each returns a map of service → `{name, activeThreads, maxThreads, status}`.
Use these endpoints in dashboards or for diagnostics when investigating throttling or backpressure.

---

## Scaling guidance
Cluster autoscaling considerations:
- Horizontal scaling: increase replicas of SPM/IRP/QPM via Helm values or HPA.
- Vertical scaling: adjust CPU/Memory resource requests/limits in Helm values.
- Thread capacity: `ServiceMetrics.maxThreads` per service controls local concurrency. Use it alongside pod CPU to balance throughput.
- Queue‑backed workloads: prefer `RouteMode=QUEUE` on IRP to decouple ingest from processing; scale QPM replicas.

Prometheus indicators for scaling decisions:
- Rising `spm_thread_utilization` approaching 1.0 → add replicas or increase `maxThreads` (after CPU assessment)
- High `router_requests_total{route="remote", outcome="failure"}` → remote instance or network saturation; check discovery and circuit breakers
- Latency growth in `router_latency`

---

## MGM (control plane): reconcile, start/stop, desired state
MGM reads desired state from the database (`ModuleConfig`, `ServiceConfig`). In simulation mode (default), it logs planned changes.

Properties (env or application):
- `mgm.controller.enabled` – when true, use Kubernetes client to ensure Deployments/Services
- `mgm.k8s.namespace` – namespace to deploy into (default `default`)
- `mgm.seed` – seed demo configs on startup (dev only)

APIs (via Gateway require ADMIN role; or direct to MGM port in dev):
- `GET /mgm/modules` – desired (and observed if available) state
- `POST /mgm/modules/{name}/start` – ensure deployment/service
- `POST /mgm/modules/{name}/stop` – delete deployment
- `POST /mgm/reconcile` – run reconcile loop now

Caveats:
- Real K8s reconcile requires enabling and wiring the Kubernetes client (Fabric8 or official). Simulation mode keeps observed state in memory and logs actions.

---

## Production deployment with Helm
A generic Helm chart is provided at `deployment/helm/knightmesh`.

Key values (`values.yaml`):
- `image.repository`, `image.tag`, `image.pullPolicy`
- `module.name`, `module.type`
- `replicaCount`
- `resources.requests/limits`
- `env` (e.g., `SPRING_PROFILES_ACTIVE`, DB URLs, issuer URIs)
- `envFrom.configMaps/secrets` for config injection
- `ingress.enabled` and host/path rules

Example (SPM):
```
helm upgrade --install spm ./deployment/helm/knightmesh \
  --set image.repository=knightmesh/spm \
  --set image.tag=1.0.0 \
  --set module.name=spm \
  --set module.type=SPM \
  --set replicaCount=3
```

Ingress example:
```
helm upgrade --install gateway ./deployment/helm/knightmesh \
  --set image.repository=knightmesh/gateway \
  --set image.tag=1.0.0 \
  --set module.name=gateway \
  --set module.type=GATEWAY \
  --set ingress.enabled=true \
  --set ingress.hosts[0].host=knightmesh.local \
  --set ingress.hosts[0].paths[0].path=/ \
  --set ingress.hosts[0].paths[0].pathType=Prefix
```

Security in production:
- Configure Gateway with `OIDC_ISSUER_URI` for your Keycloak/IdP
- Lock down Actuator endpoints; restrict `/actuator/prometheus` to Prometheus IPs
- Enable TLS for Ingress and downstream services where appropriate

---

## Runbooks (emergency restart, diagnostics)

Restart a module (Helm):
```
helm rollout restart deployment/<release-name>-knightmesh
kubectl rollout status deployment/<release-name>-knightmesh
```

Scale up/down quickly:
```
kubectl scale deployment/<release-name>-knightmesh --replicas=5
```

Force reconcile via MGM:
```
curl -X POST http://<gateway>/mgm/reconcile -H "Authorization: Bearer <ADMIN_TOKEN>"
```

Diagnose capacity issues:
- Check `/spm/capacity` on the target pod
- Inspect `router_requests_total` for spikes in `route=remote` or `outcome=failure`
- Look at `router_latency` distributions

Circuit breaker events:
- If you see `SERVICE_UNAVAILABLE` with a “Circuit open” message, inspect remote targets, network policies, and retry budgets; wait for half‑open period or manually drain traffic.

Logs and traces:
- Use Jaeger to follow a request across Gateway → IRP → ServiceRouter → SPM (and remote calls if any)
- Correlate with `correlationId` present in `ServiceRequest` metadata

Backup/Restore (DB):
- PostgreSQL holds config state. Implement regular backups for `module_config`, `service_config`, `gateway_route`, and persistent queue tables if used.

---

If you operate Knightmesh in multiple environments, maintain separate Helm values files (e.g., `values-dev.yaml`, `values-staging.yaml`, `values-prod.yaml`) and apply via `-f` flags. Ensure environment‑specific Keycloak issuer URIs, DB URLs, and per‑service thread capacities are configured accordingly.


---

## Verified architecture and flows (operations perspective)

This section maps runtime behaviors directly to code, helping you diagnose issues with confidence.

- Request authorization and routing (Gateway)
  - Code: `gateway/src/main/java/org/knightmesh/gateway/SecurityConfig.java`
    - Enforces roles: `/mgm/**` → `ROLE_ADMIN`, `/irp/**` → `ROLE_USER`, `/spm/**` → `ROLE_SERVICE`.
  - Code: `gateway/src/main/java/org/knightmesh/gateway/RouteConfig.java`
    - Builds routes from DB (`GatewayRoute`) when available, else uses static fallbacks (`gateway.*.uri`).
    - JWT `Authorization` header is preserved and forwarded (covered by `GatewaySecurityIntegrationTest`).

- Ingress (IRP) behavior
  - Code: `modules/irp/src/main/java/org/knightmesh/irp/IrpController.java`
    - Endpoint: `POST /irp/{serviceName}`; builds `ServiceRequest` with `metadata.timestamp` and `source=IRP`.
    - Reads `ModuleConfig` via `ConfigRepository.getModuleConfig("irp")` to decide `DIRECT` vs `QUEUE`.

- Local service execution and capacity
  - Code: `module-runtime/.../LocalServiceRegistry.java` and `LocalServiceDescriptor.java`
    - Registration logs at INFO and registers Micrometer gauges per service.
    - `capacitySnapshot()` powers `/spm|/irp|/qpm/capacity` endpoints via `CapacityController`.

- Routing and resilience
  - Code: `module-runtime/.../ServiceRouter.java`
    - Local‑first path: `incrementActive()` → `execute()` → `decrementActive()` in `finally`.
    - Remote path: `RemoteServiceLocator.findInstances()` → `RemoteHttpInvoker.post()` → `/internal/service/{serviceName}`.
    - Error codes returned by router (as `ServiceResponse.failure(errorCode, message, data)`):
      - `INVALID_REQUEST` – null request or service name
      - `NO_INSTANCES` – discovery returned empty list
      - `EMPTY_RESPONSE` – HTTP returned no body
      - `SERVICE_UNAVAILABLE` – failures after retries or circuit open
      - `NO_REMOTE_PATH` – neither `RemoteServiceLocator` nor kube fallback present
      - `EXCEPTION` – local execution threw a `RuntimeException`

- Discovery (Kubernetes or DB fallback)
  - Code: `module-runtime/.../KubernetesRemoteServiceLocator.java`
    - Uses Spring Cloud `DiscoveryClient` when env `KUBERNETES_SERVICE_HOST` is present (or property flag).
    - Otherwise, reads `ModuleConfig.extraJson` with shape `{ "instances": [{"host":"...","port":1234, "metadata":{...}}] }`.

- Queue processing (QPM)
  - Code: `modules/qpm/src/main/java/org/knightmesh/qpm/QpmWorker.java`
    - Polls queues for modules configured with `RouteMode=QUEUE` and `queueName`, drains up to batch size (50), and routes each message.

---

## PromQL quick reference (maps to code metrics)

- Requests per service (5m rate)
  - `sum by (service_name) (rate(router_requests_total[5m]))`
- Local vs remote ratio (5m)
  - `sum by (route) (rate(router_requests_total[5m]))`
- Thread utilization per service
  - `avg by (service_name) (spm_thread_utilization)`
- Active vs Max threads
  - `sum by (service_name) (spm_active_threads)` vs `sum by (service_name) (spm_max_threads)`

Each metric automatically has common tags attached: `module`, `module_type`, `instance_id` (from `ObservabilityConfig`).

---

## Resilience4j configuration knobs

These properties control remote HTTP retry and circuit breaker used by `RemoteHttpInvoker` / `ServiceRouter`.

- Retry (`remoteRouter` instance)
  - `resilience4j.retry.instances.remoteRouter.max-attempts`
  - `resilience4j.retry.instances.remoteRouter.wait-duration`
  - `resilience4j.retry.instances.remoteRouter.enable-exponential-backoff`
  - `resilience4j.retry.instances.remoteRouter.exponential-backoff-multiplier`
- Circuit breaker (`remoteRouter` instance)
  - `resilience4j.circuitbreaker.instances.remoteRouter.sliding-window-size`
  - `resilience4j.circuitbreaker.instances.remoteRouter.minimum-number-of-calls`
  - `resilience4j.circuitbreaker.instances.remoteRouter.failure-rate-threshold`
  - `resilience4j.circuitbreaker.instances.remoteRouter.wait-duration-in-open-state`

Defaults for tests are in `module-runtime/src/test/resources/application-test.properties`; adjust in production via module properties or environment variables.

---

## Troubleshooting playbook

- 401/403 at Gateway
  - Ensure JWT is present and roles match path policy (ADMIN for `/mgm/**`, USER for `/irp/**`).
  - Verify `spring.security.oauth2.resourceserver.jwt.issuer-uri` points to your Keycloak realm.
- IRP returns 400 with `FAILURE`
  - Look for `errorCode` in body. Common cases:
    - `NO_INSTANCES` – discovery returned empty list; check Kubernetes discovery or DB fallback `extraJson`.
    - `SERVICE_UNAVAILABLE` – retry budget exhausted or circuit open; see Resilience4j section and check remote service health.
    - `EXCEPTION` – local service threw; inspect service logs.
- Requests are slow or backpressure observed
  - Inspect `spm_thread_utilization` and `router_latency`; consider raising `ServiceMetrics.maxThreads` or scaling replicas.
- Capacity endpoints show no services
  - Ensure `@CKServiceRegistration` annotations exist and that `LocalServiceAutoRegistrar` ran (check startup logs for registrations).
- Remote routing never triggers
  - If you expect remote but execution is local, you may have the service registered locally. Deregister or lower local `maxThreads` to test remote fallback.

---

## Operational sequences

1) Scale SPM capacity safely
   - Increase `ServiceMetrics.maxThreads` in code (and redeploy), or scale pod replicas via Helm.
   - Monitor `spm_thread_utilization` and CPU usage before/after.

2) Force MGM reconcile (simulated or real controller)
   - `POST /mgm/reconcile` via Gateway (ADMIN role). In simulation, this refreshes the in‑memory desired state.

3) Switch IRP from DIRECT to QUEUE
   - Update `module_config.route_mode` for name `irp` to `QUEUE` and set `queue_name`.
   - Ensure `QpmWorker` is running; monitor queue sizes (`QueuePlugin.size()` exposed via logs if instrumented) and router metrics.

4) Update Gateway routes from DB
   - Insert/modify `gateway_route` rows; call any endpoint that triggers `ConfigRepository.reload()` (or restart Gateway if not hot‑reloading).
   - Verify path policies using `RequiredRolesGatewayFilterFactory` (roles CSV must include required roles).

---

## Security notes

- Actuator exposure
  - In dev, `/actuator/prometheus` is open. In prod, restrict via network policies or require auth on a sidecar/proxy.
- Token forwarding
  - Gateway forwards the `Authorization` header; downstream services can perform additional checks if desired.
- Service role separation
  - Use the `SERVICE` realm role for internal routes like `/spm/**` when exposing them via Gateway for internal clients.

