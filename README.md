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
