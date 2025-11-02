# Fraud and Risk Scanner

A stub implementation of the Fraud and Risk Scanner application for Mastercard's BizOps Bank team.

## Overview

This is a minimal stub application that provides basic functionality and health checks. It serves as a foundation for the actual Fraud and Risk Scanner implementation.

## Project Structure

```
fraud-risk-scanner/
├── habitat/                    # Habitat configuration
│   ├── plan.sh               # Habitat plan file
│   ├── default.toml          # Default configuration
│   └── hooks/                # Habitat hooks
│       ├── init              # Initialization hook
│       ├── run               # Run hook
│       └── health_check      # Health check hook
├── alert-bridge/             # Alert Bridge JAR (G1122-5697)
│   ├── pom.xml              # Maven configuration
│   └── src/main/java/       # Java source code
├── scanner/                  # Scanner JAR (G1122-5698)
│   ├── pom.xml              # Maven configuration
│   └── src/main/java/       # Java source code
├── feeder/                   # Feeder JAR (G1122-5699)
│   ├── pom.xml              # Maven configuration
│   └── src/main/java/       # Java source code
├── Jenkinsfile               # Jenkins CI/CD pipeline
└── README.md                # This file
```

## Features

- **Three Separate Stub JARs**: Alert Bridge, Scanner, and Feeder components
- **Minimal Implementation**: Each component does nothing but provides basic functionality
- **Java 21**: All components built with Java 21
- **Habitat Integration**: Full Habitat package configuration
- **CI/CD Pipeline**: Jenkins pipeline for automated builds and deployments
- **SonarQube Integration**: Code quality analysis
- **Artifactory Integration**: Artifact storage and management

## Components

### Alert Bridge (G1122-5697)
- Simple Java application with main() method
- Executable JAR with Maven Shade plugin
- Basic logging with SLF4J/Logback
- Stub implementation that does nothing

### Scanner (G1122-5698)
- Simple Java application with main() method
- Executable JAR with Maven Shade plugin
- Basic logging with SLF4J/Logback
- Stub implementation that does nothing

### Feeder (G1122-5699)
- Simple Java application with main() method
- Executable JAR with Maven Shade plugin
- Basic logging with SLF4J/Logback
- Stub implementation that does nothing

## Building the Application

### Prerequisites

- Java 21
- Maven 3.6+
- Habitat CLI  # clarify with 

### Local Build

```bash
# Build Alert Bridge
cd alert-bridge
mvn clean package

# Build Scanner
cd ../scanner
mvn clean package

# Build Feeder
cd ../feeder
mvn clean package
```

### Running Individual Components

```bash
# Run Alert Bridge
java -jar alert-bridge/target/fraud-risk-scanner-alert-bridge-*.jar

# Run Scanner
java -jar scanner/target/fraud-risk-scanner-scanner-*.jar

# Run Feeder
java -jar feeder/target/fraud-risk-scanner-feeder-*.jar
```

### Habitat Build

```bash
# Build Habitat package
hab studio build

# Install and run
hab pkg install results/*.hart
hab svc load results/*.hart
```

## Configuration

Each component has its own Maven configuration:

- `alert-bridge/pom.xml` - Alert Bridge Maven configuration
- `scanner/pom.xml` - Scanner Maven configuration  
- `feeder/pom.xml` - Feeder Maven configuration

All components use Java 21 and minimal dependencies (logging only).

## CI/CD Pipeline

The Jenkins pipeline includes:

1. **Checkout** - Source code checkout
2. **Build** - Maven compilation for all components
3. **Test** - Unit test execution for all components
4. **Package** - JAR file creation for all components
5. **SonarQube Analysis** - Code quality analysis
6. **Publish to Artifactory** - All three JARs published to Artifactory
7. **Build Habitat Package** - Habitat package creation
8. **Publish Habitat Package** - Habitat package upload

## Success Criteria

- ✅ **G1122-5697**: Alert Bridge stub jar created and published to Artifactory
- ✅ **G1122-5698**: Scanner stub jar created and published to Artifactory  
- ✅ **G1122-5699**: Feeder stub jar created and published to Artifactory
- ✅ Jenkins job builds all three stub jars and pushes to Artifactory
- ✅ Can log into SonarQube and see results
- ✅ Habitat package builds successfully
- ✅ All components start and run with Java 21

## Team Information

- **Team**: BizOps Bank
- **Project**: B2B Projects
- **Maintainer**: BizOps Bank Team
- **Jira Tickets**: G1122-4669, G1122-5697, G1122-5698, G1122-5699

## Next Steps

This stub implementation provides the foundation for:

1. Adding actual fraud detection logic
2. Implementing risk assessment algorithms
3. Adding database integration
4. Implementing security features
5. Adding monitoring and alerting
