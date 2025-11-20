FROM registry.access.redhat.com/ubi9 AS ubi-micro-build

ARG KEYCLOAK_VERSION=999.0.0-SNAPSHOT
ARG KEYCLOAK_DIST=https://github.com/keycloak/keycloak/releases/download/$KEYCLOAK_VERSION/keycloak-$KEYCLOAK_VERSION.tar.gz

RUN dnf install -y tar gzip maven java-17-openjdk-devel

# Copy project files for build
COPY . /tmp/keycloak-source
WORKDIR /tmp/keycloak-source

# Build Keycloak from source
RUN ./mvnw -pl quarkus/deployment,quarkus/dist -am -DskipTests clean install

# Extract distribution
RUN tar -xvf quarkus/dist/target/keycloak-*.tar.gz -C /opt && \
    mv /opt/keycloak-* /opt/keycloak && \
    mkdir -p /opt/keycloak/data && \
    chmod -R g+rwX /opt/keycloak

FROM registry.access.redhat.com/ubi9-micro
ENV LANG=en_US.UTF-8
ENV KC_RUN_IN_CONTAINER=true

COPY --from=ubi-micro-build /opt/keycloak /opt/keycloak

RUN echo "keycloak:x:0:root" >> /etc/group && \
    echo "keycloak:x:1000:0:keycloak user:/opt/keycloak:/sbin/nologin" >> /etc/passwd

USER 1000

EXPOSE 8080
EXPOSE 8443
EXPOSE 9000

ENTRYPOINT [ "/opt/keycloak/bin/kc.sh" ]
