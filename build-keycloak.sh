#!/bin/bash

# Script to build Keycloak from source and create Docker image
# Usage: ./build-keycloak.sh

set -e

echo "Building Keycloak from source..."

# Build Keycloak distribution
./mvnw -pl quarkus/deployment,quarkus/dist -am -DskipTests clean install

# Find the distribution tar.gz file (relative to project root)
DIST_FILE=$(find quarkus/dist/target -name "keycloak-*.tar.gz" | head -1)

if [ -z "$DIST_FILE" ]; then
    echo "Error: Keycloak distribution file not found in quarkus/dist/target/"
    echo "Make sure you've run: ./mvnw -pl quarkus/deployment,quarkus/dist -am -DskipTests clean install"
    exit 1
fi

echo "Found distribution: $DIST_FILE"

# Extract version from filename
VERSION=$(basename "$DIST_FILE" | sed 's/keycloak-\(.*\)\.tar\.gz/\1/')
echo "Building Docker image with version: $VERSION"

# Build Docker image from project root
# Copy the distribution file to container directory temporarily for build context
cp "$DIST_FILE" quarkus/container/
DIST_FILENAME=$(basename "$DIST_FILE")

# Build from container directory with the file in context
cd quarkus/container

docker build \
    --build-arg KEYCLOAK_DIST="$DIST_FILENAME" \
    --build-arg KEYCLOAK_VERSION="$VERSION" \
    -f Dockerfile \
    -t keycloak:local \
    .

# Clean up copied file
rm -f "$DIST_FILENAME"
cd ../..

echo "Docker image 'keycloak:local' built successfully!"
echo "You can now run: docker-compose up -d"

