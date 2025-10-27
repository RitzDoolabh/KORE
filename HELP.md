# Getting Started

### Reference Documentation
For further reference, please consider the following sections:

* [Official Gradle documentation](https://docs.gradle.org)
* [Spring Boot Gradle Plugin Reference Guide](https://docs.spring.io/spring-boot/3.5.7/gradle-plugin)
* [Create an OCI image](https://docs.spring.io/spring-boot/3.5.7/gradle-plugin/packaging-oci-image.html)

### Additional Links
These additional references should also help you:

* [Gradle Build Scans – insights for your project's build](https://scans.gradle.com#gradle)


# Getting Started

### Reference Documentation
For further reference, please consider the following sections:

* [Official Gradle documentation](https://docs.gradle.org)
* [Spring Boot Gradle Plugin Reference Guide](https://docs.spring.io/spring-boot/3.5.7/gradle-plugin)
* [Create an OCI image](https://docs.spring.io/spring-boot/3.5.7/gradle-plugin/packaging-oci-image.html)

### Additional Links
These additional references should also help you:

* [Gradle Build Scans – insights for your project's build](https://scans.gradle.com#gradle)

---

## Local Dev with Docker Compose (Postgres + Keycloak + Gateway/MGM/SPM)

A quick local ensemble is provided under `deployment/local/docker-compose.dev.yml`. It brings up:
- Postgres 16 with a persistent volume
- Keycloak (imports the provided `realm.json` with roles and users)
- One instance each of: Gateway, MGM, and SPM
- Optional monitoring stack (Prometheus, Grafana, Jaeger) via the `monitoring` profile

### 1) Prepare environment variables

Copy the sample env file and adjust values as needed:

```
cp deployment/local/.env.sample deployment/local/.env
```

Defaults:
- Postgres: `DB=knightmesh`, `USER=km`, `PASSWORD=kmPass!`
- Keycloak admin: `admin/admin`
- IMAGE_TAG: `dev`

### 2) Build application jars and (optionally) images

Option A — build images using the helper script (recommended):
```
./scripts/build-images.sh --tag dev
```
This creates images like `knightmesh/mgm:dev`, `knightmesh/spm:dev`, `knightmesh/gateway:dev`.

Option B — rely on `docker compose` to build Dockerfiles:
- Ensure boot JARs exist locally (required if Dockerfiles copy from `build/libs`):
```
./gradlew :mgm:bootJar :modules:spm:bootJar :gateway:bootJar
```
- Then compose will build images from local sources when using `--build`.

### 3) Start the stack

From the repository root:
```
docker compose --env-file deployment/local/.env \
  -f deployment/local/docker-compose.dev.yml up --build
```
To also start monitoring components:
```
docker compose --env-file deployment/local/.env \
  -f deployment/local/docker-compose.dev.yml --profile monitoring up --build
```

### 4) Verify checkpoint

- Postgres should be healthy (check logs): `km-postgres`
- Keycloak admin UI reachable: http://localhost:8080
  - Realm: `knightmesh`
  - Users: `admin/password` (ADMIN), `devuser/password` (USER)

### 5) Useful URLs

- Gateway: http://localhost:8088
- MGM REST: http://localhost:8085/mgm/modules
- SPM Actuator: http://localhost:8081/actuator/health
- Prometheus (if enabled): http://localhost:9090
- Grafana (if enabled): http://localhost:3000 (admin/admin)
- Jaeger UI (if enabled): http://localhost:16686

### 6) Data persistence and cleanup

- Postgres data is persisted in the named volume `postgres-data`.
- To reset the database:
```
docker compose -f deployment/local/docker-compose.dev.yml down -v
```
This removes the volume and all data.


---

## How the pieces fit together (quick reference)

- Contracts and models live in `platform-core` (`CKService`, `ServiceRequest`, `ServiceResponse`).
- Runtime wiring lives in `module-runtime` (LocalServiceRegistry, ServiceRouter, discovery, resilience, metrics).
- Business services live in SPM (`modules/spm/.../services`). They are auto‑registered by `LocalServiceAutoRegistrar`.
- IRP receives external requests (`POST /irp/{serviceName}`) and either routes DIRECT to ServiceRouter or enqueues (QUEUE mode) using `QueuePlugin`.
- QPM dequeues and routes to ServiceRouter.
- Gateway enforces JWT roles and proxies to IRP/MGM/SPM; it forwards the `Authorization` header to downstream modules.
- MGM exposes desired state APIs and (in simulation mode) logs reconcile actions.

### End‑to‑end flow (DIRECT)
1) Client → Gateway `/irp/REGISTER_USER` with `Authorization: Bearer <jwt>` (role USER)
2) Gateway validates JWT and forwards to IRP
3) IRP builds `ServiceRequest` and calls `ServiceRouter.route(..)`
4) Router executes `REGISTER_USER` locally (capacity permitting) and it invokes `USER_AUTH` via `ServiceInvoker`
5) IRP returns `ServiceResponse` to client via Gateway

### End‑to‑end flow (QUEUE)
1) Client → Gateway `/irp/REGISTER_USER` (role USER)
2) IRP enqueues request using `QueuePlugin` and returns 202 `{correlationId}`
3) QPM drains the queue and calls `ServiceRouter`

See README “Deep architecture and request flows” for code‑level details.

---

## Useful endpoints and commands

- Health/metrics
  - `GET /actuator/health` (all modules)
  - `GET /actuator/prometheus` (all modules)
- Capacity
  - `GET /spm/capacity`, `/irp/capacity`, `/qpm/capacity`
- MGM APIs
  - `GET /mgm/modules`
  - `POST /mgm/reconcile`
- IRP
  - `POST /irp/{serviceName}` – JSON body is the payload map

Curl examples (via Gateway):
```
# Acquire JWT for devuser (USER)
export TOKEN=$(curl -s -X POST \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -d 'grant_type=password' \
  -d 'client_id=knightmesh-gateway' \
  -d 'username=devuser' -d 'password=password' \
  http://localhost:8080/realms/knightmesh/protocol/openid-connect/token | jq -r .access_token)

curl -s -X POST http://localhost:8088/irp/REGISTER_USER \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"username":"alice","email":"alice@example.org"}' | jq .
```

---

## Quick troubleshooting

- 401/403 at Gateway
  - Missing/invalid token or insufficient role; check `OIDC_ISSUER_URI` and Keycloak realm users/roles
- IRP returns `FAILURE`
  - Inspect `errorCode` in JSON: `NO_INSTANCES`, `SERVICE_UNAVAILABLE`, `EXCEPTION` etc.; see OPERATIONS.md for remedies
- Slow or saturated
  - Check Prometheus metrics: `spm_thread_utilization`, `router_latency`, `router_requests_total` (route=remote)

---

## Links to detailed docs

- Deep architecture and flows: see README (appendix)
- Developer guide (adding services/plugins, tests): DEVELOPMENT.md
- Operations guide (metrics, tracing, scaling, reconcile): OPERATIONS.md
