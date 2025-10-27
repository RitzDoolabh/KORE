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
