#!/bin/bash

# Script to disable HTTPS requirement for Keycloak realms
# This allows HTTP access in development environments
# Usage: ./fix-https-requirement.sh

set -e

echo "Configuring Keycloak to allow HTTP access..."
echo ""

# Check if Keycloak container is running
if ! docker compose ps keycloak | grep -q "Up"; then
    echo "Error: Keycloak container is not running."
    echo "Please start it first with: docker compose up -d keycloak"
    exit 1
fi

# Wait for Keycloak to be ready
echo "Waiting for Keycloak to be ready..."
timeout=60
elapsed=0
while ! curl -s http://localhost:8080/health/ready > /dev/null 2>&1; do
    if [ $elapsed -ge $timeout ]; then
        echo "Error: Keycloak did not become ready within $timeout seconds"
        exit 1
    fi
    sleep 2
    elapsed=$((elapsed + 2))
    echo -n "."
done
echo ""
echo "Keycloak is ready!"

# Configure admin credentials
echo "Configuring admin credentials..."
docker compose exec -T keycloak /opt/keycloak/bin/kcadm.sh config credentials \
    --server http://localhost:8080 \
    --realm master \
    --user admin \
    --password admin 2>&1 | grep -v "Logging into" || true

# Update master realm to allow HTTP
echo "Updating master realm SSL requirement to NONE..."
docker compose exec -T keycloak /opt/keycloak/bin/kcadm.sh update realms/master \
    -s sslRequired=NONE

# Get all realms and update them
echo "Updating all realms to allow HTTP..."
REALMS=$(docker compose exec -T keycloak /opt/keycloak/bin/kcadm.sh get realms \
    --format csv,noheader \
    --fields id 2>/dev/null | grep -v "^$" || echo "")

if [ -n "$REALMS" ]; then
    while IFS= read -r realm; do
        if [ -n "$realm" ]; then
            echo "  Updating realm: $realm"
            docker compose exec -T keycloak /opt/keycloak/bin/kcadm.sh update "realms/$realm" \
                -s sslRequired=NONE 2>/dev/null || true
        fi
    done <<< "$REALMS"
fi

echo ""
echo "✓ HTTPS requirement disabled for all realms!"
echo ""
echo "You can now access Keycloak over HTTP at: http://localhost:8080/admin"
echo "Login with: admin / admin"

