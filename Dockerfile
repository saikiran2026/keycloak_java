# Builder: Maven + JDK 21 pre-installed in base image
FROM maven:3.9-eclipse-temurin-21 AS builder

# Install Node.js 24 and pnpm (for frontend build)
# Maven and JDK are already in the base image
RUN apt-get update && \
    curl -fsSL https://deb.nodesource.com/setup_24.x | bash - && \
    apt-get install -y nodejs && \
    npm install -g pnpm@10.14.0 && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /build
COPY . .

# Build server distribution
# The frontend-maven-plugin will install node/pnpm to /tmp/js-node (configured in js/pom.xml)
# /tmp supports symlinks in Docker, avoiding the overlay filesystem issue
RUN ./mvnw -pl quarkus/deployment,quarkus/dist -am -DskipTests -DskipProtoLock=true clean install

# Runtime stage
FROM eclipse-temurin:21-jre-jammy
COPY --from=builder /build/quarkus/dist/target/keycloak-*.tar.gz /tmp/
RUN tar -xzf /tmp/keycloak-*.tar.gz -C /opt && \
    mv /opt/keycloak-* /opt/keycloak && \
    rm /tmp/*.tar.gz && \
    useradd -r keycloak && \
    chown -R keycloak:keycloak /opt/keycloak

USER keycloak
WORKDIR /opt/keycloak
EXPOSE 8080 8443 9000
ENTRYPOINT ["/opt/keycloak/bin/kc.sh"]
CMD ["start-dev"]
