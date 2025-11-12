# Docker Setup with Jaeger, Elasticsearch, and Swagger UI

This guide explains how to run Keycloak with Docker Compose, including distributed tracing with Jaeger (using Elasticsearch as storage) and OpenAPI/Swagger UI.

## Prerequisites

- Docker and Docker Compose installed
- JDK 17 or 21 (for building Keycloak from source)
- Maven (or use the provided `mvnw` wrapper)

## Build Process

### Step 1: Build Keycloak from Source

Before running docker-compose, you need to build Keycloak distribution from source:

```bash
./mvnw -pl quarkus/deployment,quarkus/dist -am -DskipTests clean install
```

**Note:** The UI (admin-ui and account-ui) is automatically built and included during this process. The build will:
- Build the JavaScript/TypeScript UIs using pnpm (in the `js/` directory)
- Package them as Maven artifacts (`keycloak-admin-ui` and `keycloak-account-ui`)
- Include them in the Quarkus distribution
- The UI will be available at `/admin` (admin console) and `/realms/{realm}/account` (account management)

This creates the distribution tar.gz in `quarkus/dist/target/keycloak-<VERSION>.tar.gz` which includes the UI.

### Step 2: Build Docker Image

Build the Docker image using the local distribution. First, find the version number:

```bash
ls quarkus/dist/target/keycloak-*.tar.gz
```

Then build the image from the project root:

```bash
docker build --build-arg KEYCLOAK_DIST=quarkus/dist/target/keycloak-999.0.0-SNAPSHOT.tar.gz -f quarkus/container/Dockerfile -t keycloak:local .
```

Or use the helper script (if created):

```bash
./build-keycloak.sh
```

### Step 3: Configure Environment Variables (Optional)

Copy the example environment file and modify as needed:

```bash
cp .env.example .env
```

Edit `.env` to set your preferred database credentials and admin password.

### Step 4: Start the Stack

Start all services:

```bash
docker-compose up -d
```

To view logs:

```bash
docker-compose logs -f
```

To stop the stack:

```bash
docker-compose down
```

To stop and remove volumes (this will delete all data):

```bash
docker-compose down -v
```

## Access URLs

Once the stack is running, you can access:

- **Keycloak**: http://localhost:8080
- **Keycloak Admin Console**: http://localhost:8080/admin
  - Default username: `admin` (or value from `KC_ADMIN`)
  - Default password: `admin` (or value from `KC_ADMIN_PASSWORD`)
- **Account Management**: http://localhost:8080/realms/{realm}/account
- **Swagger UI**: http://localhost:8080/openapi/ui
- **OpenAPI Specification**: http://localhost:8080/openapi
- **Jaeger UI**: http://localhost:16686
- **Elasticsearch**: http://localhost:9200

## Services

### PostgreSQL
- Database for Keycloak
- Port: 5432
- Data persisted in `postgres_data` volume

### Elasticsearch
- Storage backend for Jaeger spans
- Ports: 9200 (HTTP), 9300 (transport)
- Data persisted in `elasticsearch_data` volume
- Security disabled for development (xpack.security.enabled=false)

### Jaeger
- Distributed tracing system
- Uses Elasticsearch for span storage
- Ports:
  - 16686: Jaeger UI
  - 4317: OTLP gRPC receiver (used by Keycloak)
  - 4318: OTLP HTTP receiver
  - 14268: Jaeger collector HTTP

### Keycloak
- Identity and access management server
- Built from source
- Ports: 8080 (HTTP), 8443 (HTTPS)
- Features enabled:
  - OpenAPI endpoint (`--openapi-enabled=true`)
  - Swagger UI (`--openapi-ui-enabled=true`)
  - Distributed tracing (`--tracing-enabled=true`)

## Viewing Traces in Jaeger

1. Access Jaeger UI at http://localhost:16686
2. Select "keycloak" from the Service dropdown
3. Click "Find Traces"
4. You should see traces from Keycloak requests

Traces include:
- HTTP requests to Keycloak
- Database operations
- LDAP requests (if configured)
- Outgoing HTTP requests

## Troubleshooting

### Keycloak fails to start

- Check that PostgreSQL is healthy: `docker-compose ps`
- Check Keycloak logs: `docker-compose logs keycloak`
- Ensure the Docker image was built: `docker images | grep keycloak:local`

### Jaeger shows no traces

- Verify tracing is enabled in Keycloak logs
- Check that Jaeger is healthy: `docker-compose ps`
- Ensure Keycloak can reach Jaeger: `docker-compose exec keycloak ping jaeger`

### Elasticsearch connection issues

- Check Elasticsearch health: `curl http://localhost:9200/_cluster/health`
- Verify Elasticsearch is running: `docker-compose ps elasticsearch`
- Check Jaeger logs: `docker-compose logs jaeger`

### OpenAPI/Swagger UI not accessible

- Verify the options were passed correctly: `docker-compose logs keycloak | grep openapi`
- Check that the build included OpenAPI features
- Access the OpenAPI spec directly: http://localhost:8080/openapi

## Building from Source Notes

- The UI (admin-ui and account-ui) is automatically included when building from source - no separate installation needed
- OpenAPI/Swagger UI options (`--openapi-enabled` and `--openapi-ui-enabled`) are build-time options and must be passed as command-line arguments, not environment variables
- Tracing is already supported via OpenTelemetry - we just need to enable it and point it to Jaeger
- Elasticsearch will be used exclusively by Jaeger for span storage

## Development Mode

The setup uses `start-dev` mode which is suitable for development. For production:

1. Change `start-dev` to `start` in docker-compose.yml
2. Build an optimized server first: `./mvnw -pl quarkus/deployment,quarkus/dist -am -DskipTests clean install`
3. Then build the Docker image with the optimized distribution

