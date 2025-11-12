#!/bin/bash

# Script to reset Keycloak database and start fresh with bootstrap admin
# This will delete all existing data and create a new admin user automatically
# Usage: ./reset-and-start.sh

set -e

echo "⚠️  WARNING: This will delete all Keycloak data!"
echo "This script will:"
echo "  1. Stop all services"
echo "  2. Remove the PostgreSQL volume (all data will be lost)"
echo "  3. Start services fresh"
echo "  4. Admin user will be created automatically via bootstrap options"
echo ""
read -p "Are you sure you want to continue? (yes/no): " confirm

if [ "$confirm" != "yes" ]; then
    echo "Aborted."
    exit 0
fi

echo ""
echo "Stopping services..."
docker compose down

echo ""
echo "Removing PostgreSQL volume..."
docker volume rm keycloak_postgres_data 2>/dev/null || echo "Volume already removed or doesn't exist"

echo ""
echo "Starting services with fresh database..."
docker compose up -d

echo ""
echo "Waiting for Keycloak to start and create admin user..."
echo "This may take 30-60 seconds..."
sleep 10

# Wait for admin user to be created
timeout=120
elapsed=0
while ! docker compose logs keycloak 2>/dev/null | grep -q "Created temporary admin user"; do
    if [ $elapsed -ge $timeout ]; then
        echo "Warning: Timeout waiting for admin user creation. Check logs with: docker compose logs keycloak"
        break
    fi
    sleep 2
    elapsed=$((elapsed + 2))
    echo -n "."
done
echo ""

# Wait for Keycloak to be ready
echo "Waiting for Keycloak to be ready..."
timeout=60
elapsed=0
while ! curl -s http://localhost:8080/health/ready > /dev/null 2>&1; do
    if [ $elapsed -ge $timeout ]; then
        echo "Warning: Keycloak did not become ready within $timeout seconds"
        break
    fi
    sleep 2
    elapsed=$((elapsed + 2))
    echo -n "."
done
echo ""

echo ""
echo "✓ Keycloak is ready!"
echo ""
echo "Admin credentials (created via bootstrap):"
echo "  Username: admin"
echo "  Password: admin"
echo ""
echo "Access the admin console at: http://localhost:8080/admin"
echo ""
echo "To view logs: docker compose logs -f keycloak"

