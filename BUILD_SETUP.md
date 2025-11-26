# Build Setup Summary

## Installed Tools

All essential build tools have been installed:

- **Java JDK 17** - OpenJDK 17.0.17 (required for building Keycloak)
- **Node.js 24.11.1** - For frontend build
- **pnpm 10.14.0** - Package manager for frontend dependencies
- **Docker 29.0.4** - Already installed

## JAVA_HOME

The build script automatically detects and sets JAVA_HOME if not already set. Current JAVA_HOME:
```
/usr/lib/jvm/java-17-openjdk-amd64
```

To set it permanently, add to your `~/.bashrc`:
```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
```

## Quick Start

### Build Keycloak Locally
```bash
./build.sh
```

This will:
1. Build Keycloak using Maven (locally, not in Docker)
2. Create a Docker image from the pre-built distribution
3. Optionally start docker-compose

### Build Options

- `./build.sh` - Build + create Docker image
- `./build.sh --build-only` - Only build, skip Docker
- `./build.sh --skip-build` - Skip build, just create Docker image (if already built)
- `./build.sh --start` - Build + create image + start docker-compose
- `./build.sh --skip-docker --start` - Just start docker-compose

## Benefits

1. **Faster iteration** - Build locally, rebuild Docker image in seconds
2. **Better debugging** - See Maven errors directly in terminal
3. **No repeated Docker builds** - Only rebuild image when needed
4. **Memory efficient** - Maven runs on host, not in container

## Memory Settings

The build script sets conservative Maven memory limits for 4GB RAM machines:
```bash
MAVEN_OPTS="-Xmx1024m -XX:MaxMetaspaceSize=512m"
```

If you have more RAM, you can override:
```bash
export MAVEN_OPTS="-Xmx2048m -XX:MaxMetaspaceSize=1024m"
./build.sh
```

## Troubleshooting

### Java not found
```bash
sudo apt-get install -y openjdk-17-jdk
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
```

### Node.js not found
```bash
curl -fsSL https://deb.nodesource.com/setup_24.x | sudo bash -
sudo apt-get install -y nodejs
```

### pnpm not found
```bash
sudo npm install -g pnpm@10.14.0
```


