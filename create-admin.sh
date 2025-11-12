#!/bin/bash

# Script to create/reset Keycloak admin user
# Usage: ./create-admin.sh [username] [password]
# Default: username=admin, password=admin

set -e

USERNAME=${1:-admin}
PASSWORD=${2:-admin}

echo "Creating Keycloak admin user..."
echo "Username: $USERNAME"
echo "Password: $PASSWORD"
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

# Create admin user using kcadm.sh
echo "Creating admin user..."
docker compose exec -T keycloak /opt/keycloak/bin/kcadm.sh config credentials \
    --server http://localhost:8080 \
    --realm master \
    --user admin \
    --password admin 2>/dev/null || {
    
    echo "Note: Admin user might already exist. Resetting admin user..."
    
    # Try to delete existing user if it exists, then create new one
    USER_ID=$(docker compose exec -T keycloak /opt/keycloak/bin/kcadm.sh get users \
        --server http://localhost:8080 \
        --realm master \
        --username admin \
        --format csv,noheader \
        --fields id 2>/dev/null | cut -d',' -f1 | head -1 || echo "")
    
    if [ -n "$USER_ID" ]; then
        echo "Deleting existing admin user..."
        docker compose exec -T keycloak /opt/keycloak/bin/kcadm.sh delete \
            "users/$USER_ID" \
            --server http://localhost:8080 \
            --realm master 2>/dev/null || true
    fi
}

# Create new admin user
echo "Creating new admin user: $USERNAME"
docker compose exec -T keycloak /opt/keycloak/bin/kcadm.sh create users \
    --server http://localhost:8080 \
    --realm master \
    -s username="$USERNAME" \
    -s enabled=true \
    -s emailVerified=true

# Get the user ID
USER_ID=$(docker compose exec -T keycloak /opt/keycloak/bin/kcadm.sh get users \
    --server http://localhost:8080 \
    --realm master \
    --username "$USERNAME" \
    --format csv,noheader \
    --fields id | cut -d',' -f1 | head -1)

if [ -z "$USER_ID" ]; then
    echo "Error: Failed to create user"
    exit 1
fi

# Set password
echo "Setting password..."
docker compose exec -T keycloak /opt/keycloak/bin/kcadm.sh set-password \
    --server http://localhost:8080 \
    --realm master \
    --username "$USERNAME" \
    --new-password "$PASSWORD" \
    --temporary=false

# Grant admin role
echo "Granting admin role..."
docker compose exec -T keycloak /opt/keycloak/bin/kcadm.sh add-roles \
    --server http://localhost:8080 \
    --realm master \
    --uusername "$USERNAME" \
    --rolename admin

echo ""
echo "✓ Admin user created successfully!"
echo ""
echo "Login credentials:"
echo "  Username: $USERNAME"
echo "  Password: $PASSWORD"
echo ""
echo "Access the admin console at: http://localhost:8080/admin"

