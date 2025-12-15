#!/bin/bash
set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}=== Keycloak Local Build Script ===${NC}"

# Check prerequisites
echo -e "${YELLOW}Checking prerequisites...${NC}"

# Set JAVA_HOME if not already set
if [ -z "$JAVA_HOME" ]; then
    JAVA_PATH=$(readlink -f $(which java) 2>/dev/null || echo "")
    if [ -n "$JAVA_PATH" ]; then
        JAVA_HOME=$(dirname $(dirname "$JAVA_PATH"))
        export JAVA_HOME
        echo -e "${YELLOW}JAVA_HOME not set, using: $JAVA_HOME${NC}"
    fi
fi

if ! command -v java &> /dev/null; then
    echo -e "${RED}Error: Java is not installed. Please install JDK 17 or 21.${NC}"
    echo -e "${YELLOW}Install with: sudo apt-get install -y openjdk-17-jdk${NC}"
    exit 1
fi

JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | sed '/^1\./s///' | cut -d'.' -f1)
if [ "$JAVA_VERSION" -lt 17 ]; then
    echo -e "${RED}Error: Java 17 or higher is required. Found: $JAVA_VERSION${NC}"
    echo -e "${YELLOW}Install with: sudo apt-get install -y openjdk-17-jdk${NC}"
    exit 1
fi

if ! command -v docker &> /dev/null; then
    echo -e "${RED}Error: Docker is not installed.${NC}"
    exit 1
fi

# Set Maven memory options for 4GB RAM machine
export MAVEN_OPTS="${MAVEN_OPTS:--Xmx1024m -XX:MaxMetaspaceSize=512m}"

# Parse arguments
BUILD_ONLY=false
SKIP_BUILD=false
SKIP_DOCKER=false
START_COMPOSE=false

while [[ $# -gt 0 ]]; do
    case $1 in
        --build-only)
            BUILD_ONLY=true
            shift
            ;;
        --skip-build)
            SKIP_BUILD=true
            shift
            ;;
        --skip-docker)
            SKIP_DOCKER=true
            shift
            ;;
        --start)
            START_COMPOSE=true
            shift
            ;;
        *)
            echo -e "${YELLOW}Unknown option: $1${NC}"
            echo "Usage: $0 [--build-only] [--skip-build] [--skip-docker] [--start]"
            exit 1
            ;;
    esac
done

# Step 1: Build Keycloak locally
if [ "$SKIP_BUILD" = false ]; then
    echo -e "${GREEN}Step 1: Building Keycloak locally...${NC}"
    echo -e "${YELLOW}This may take several minutes...${NC}"
    
    if [ ! -f "./mvnw" ]; then
        echo -e "${RED}Error: mvnw not found. Are you in the Keycloak root directory?${NC}"
        exit 1
    fi
    
    ./mvnw -pl quarkus/deployment,quarkus/dist -am -DskipTests -DskipProtoLock=true clean install
    
    if [ $? -ne 0 ]; then
        echo -e "${RED}Build failed!${NC}"
        exit 1
    fi
    
    # Check if distribution was created
    DIST_FILE=$(find quarkus/dist/target -name "keycloak-*.tar.gz" -type f | head -n 1)
    if [ -z "$DIST_FILE" ]; then
        echo -e "${RED}Error: Distribution file not found in quarkus/dist/target/${NC}"
        exit 1
    fi
    
    echo -e "${GREEN}✓ Build successful! Distribution: $DIST_FILE${NC}"
else
    echo -e "${YELLOW}Skipping build (--skip-build)${NC}"
    DIST_FILE=$(find quarkus/dist/target -name "keycloak-*.tar.gz" -type f | head -n 1)
    if [ -z "$DIST_FILE" ]; then
        echo -e "${RED}Error: No distribution found. Build first or remove --skip-build${NC}"
        exit 1
    fi
fi

if [ "$BUILD_ONLY" = true ]; then
    echo -e "${GREEN}Build complete! (--build-only specified)${NC}"
    exit 0
fi

# Step 2: Build Docker image
if [ "$SKIP_DOCKER" = false ]; then
    echo -e "${GREEN}Step 2: Building Docker image...${NC}"
    
    docker build -f Dockerfile.runtime -t keycloak:local .
    
    if [ $? -ne 0 ]; then
        echo -e "${RED}Docker build failed!${NC}"
        exit 1
    fi
    
    echo -e "${GREEN}✓ Docker image built successfully!${NC}"
else
    echo -e "${YELLOW}Skipping Docker build (--skip-docker)${NC}"
fi

# Step 3: Start docker-compose (optional)
if [ "$START_COMPOSE" = true ]; then
    echo -e "${GREEN}Step 3: Starting docker-compose...${NC}"
    docker compose up -d
    echo -e "${GREEN}✓ Services started!${NC}"
    echo -e "${YELLOW}Keycloak will be available at http://localhost:8080${NC}"
    echo -e "${YELLOW}View logs: docker compose logs -f keycloak${NC}"
fi

echo -e "${GREEN}=== Done! ===${NC}"

