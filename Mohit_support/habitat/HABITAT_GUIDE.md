# Habitat Guide - Fraud and Risk Scanner

## Table of Contents

1. [Introduction to Habitat](#introduction-to-habitat)
2. [Habitat Components Overview](#habitat-components-overview)
3. [Detailed Component Explanation](#detailed-component-explanation)
   - [plan.sh](#plansh---the-build-plan)
   - [default.toml](#defaulttoml---runtime-configuration-template)
   - [.kitchen.yml](#kitchenyml---test-kitchen-configuration)
   - [hooks/](#hooks---lifecycle-scripts)
4. [Tutorial: Getting Started with Habitat](#tutorial-getting-started-with-habitat)
5. [Build and Deploy Workflow](#build-and-deploy-workflow)
6. [Best Practices](#best-practices)
7. [Troubleshooting](#troubleshooting)

---

## Introduction to Habitat

**Habitat** is an open-source automation framework that packages, deploys, and manages applications. It provides:

- **Application Packaging**: Packages applications with their dependencies
- **Configuration Management**: Handles runtime configuration
- **Service Management**: Manages application lifecycle (start, stop, restart)
- **Service Discovery**: Helps applications find each other
- **Health Monitoring**: Built-in health checking capabilities

### Key Concepts

- **Package (`.hart` file)**: The built artifact containing your application
- **Service**: A running instance of a package
- **Plan**: Defines how to build and configure a package
- **Scaffolding**: Pre-built templates for common application types
- **Hooks**: Lifecycle scripts that run at specific service stages
- **Configuration**: Runtime values that can be changed without rebuilding

---

## Habitat Components Overview

The `habitat/` folder contains all necessary files to build and deploy the scanner application:

```
habitat/
├── plan.sh              # Build plan definition
├── default.toml         # Default runtime configuration
├── .kitchen.yml         # Test Kitchen configuration for testing
├── hooks/               # Lifecycle scripts
│   ├── init            # Initialization hook
│   ├── run             # Runtime hook (starts application)
│   └── health_check    # Health check hook
└── HABITAT_GUIDE.md    # This file
```

---

## Detailed Component Explanation

### `plan.sh` - The Build Plan

**Location:** `habitat/plan.sh`  
**Purpose:** Bash script that defines how Habitat builds the package.

#### Package Metadata

```bash
pkg_name='scanner'                    # Unique package name
pkg_origin='bizopsbank'               # Organization/owner (like namespace)
pkg_maintainer="BizOps Bank <bizopsbank@mastercard.com>"
pkg_license=("Mastercard")            # License type
pkg_description="scanner"              # Package description
```

- **`pkg_name`**: Unique identifier for your package (used as `bizopsbank/scanner`)
- **`pkg_origin`**: Organizational namespace (prevents name conflicts)
- **`pkg_maintainer`**: Contact information for the package
- **`pkg_license`**: License information

#### Scaffolding

```bash
pkg_scaffolding='mastercard/scaffolding_java_standalone'
scaffold_java_major_minor='1.21'
```

**What is Scaffolding?**  
Scaffolding provides pre-configured build and runtime logic for common application types. Instead of writing everything from scratch, scaffolding handles:

- Downloading JAR from Artifactory
- Setting up Java runtime
- Configuring application startup
- Creating run hooks
- Managing dependencies

**Why Java 1.21?**  
This corresponds to Java 21 (LTS version). The scaffolding uses this to select the appropriate JDK.

#### Dependencies

```bash
pkg_build_deps=(mastercard/maven mastercard/zulujdk11)
```

- **`pkg_build_deps`**: Packages needed only during the build phase
  - `mastercard/maven`: Maven build tool
  - `mastercard/zulujdk11`: Zulu JDK 11 for compilation
- **`pkg_deps`**: Runtime dependencies (commented out because scaffolding provides them)

#### Application Configuration

```bash
standalone_app_artifactory_url="https://artifacts.mastercard.int/artifactory/snapshots/com/mastercard/fraud-risk-scanner-scanner/0.1.0-SNAPSHOT/fraud-risk-scanner-scanner-0.1.0-SNAPSHOT.jar"
standalone_app_name="scanner"
```

- **`standalone_app_artifactory_url`**: Full URL where the built JAR is stored
  - The scaffolding will download this JAR during build
  - Supports both SNAPSHOT (development) and release versions
- **`standalone_app_name`**: Name identifier for the application

#### Build Functions

```bash
do_prepare() {
  pkg_svc_user='hab'
  pkg_svc_group='hab'
}
```

**Lifecycle Functions:**
- **`do_prepare()`**: Runs before build, sets up environment
- **`do_build()`**: Performs the actual build (commented - handled by scaffolding)
- **`do_install()`**: Copies files to package directory (commented - handled by scaffolding)

**Service User/Group:**
- Runs as `hab` user for security (non-root)

#### Version Management

```bash
pkg_version='0.1.0-SNAPSHOT'
```

- **Static Version**: Hard-coded in plan (current approach)
- **Dynamic Version** (commented): Can extract version from Maven POM if needed

---

### `default.toml` - Runtime Configuration Template

**Location:** `habitat/default.toml`  
**Purpose:** TOML template providing default runtime configuration values.

#### Structure Explained

**Application Identity**
```toml
[standalone.application]
name = "scanner"
esf_program_id = ""
esf_application_id = ""
```
- Defines application name
- ESF (Enterprise Security Framework) IDs for Mastercard integration (empty for now)

**Server Configuration**
```toml
[standalone.application.serverConfiguration]
protocol = ""
```
- HTTP protocol settings
- Empty = uses default from scaffolding

**JVM Settings**
```toml
[standalone.application.serverConfiguration.jvm]
minHeap = "512m"
maxHeap = "512m"
```
- **`minHeap`**: Initial heap size (Java `-Xms`)
- **`maxHeap`**: Maximum heap size (Java `-Xmx`)
- Scaffolding uses these to set JVM options

**Logging**
```toml
[standalone.application.serverConfiguration.serverLogging]
CustomLogMessage = "Scanner in the Penguin"
```
- Custom message for application identification in logs

**Commented Sections**  
The file includes commented examples for:
- File deployments
- System properties
- Environment variables
- Security settings
- Port configurations

#### How Configuration Works

1. **Template Variables**: Values use Handlebars syntax like `{{cfg.key}}`
2. **Runtime Override**: Values can be changed via:
   - Environment-specific config files
   - Habitat Supervisor CLI
   - Configuration updates without rebuild
3. **Scaffolding Consumption**: The scaffolding reads these values and applies them

---

### `.kitchen.yml` - Test Kitchen Configuration

**Location:** `habitat/.kitchen.yml`  
**Purpose:** Defines testing environments using Test Kitchen (integration testing).

#### Components

**Provisioner**
```yaml
provisioner:
  name: habitat
  package_name: scanner
```
- Uses Habitat to install the package
- Specifies which package to test

**Verifier**
```yaml
verifier:
  name: inspec
```
- **InSpec**: Compliance and security testing framework
- Validates the package works correctly

**Platforms**
```yaml
platforms:
  - name: linux-7
    driver:
      name: docker
      driver_config:
        image: artifacts.mastercard.int/.../oracle:7.6_chef15_latest
        platform: rhel
        privileged: true
```
- **Platform**: Target OS (RHEL 7)
- **Driver**: Docker containers for testing
- **Image**: Specific Oracle Linux image for Mastercard environments
- **Privileged**: Container needs elevated permissions for Habitat

**Test Suites**
```yaml
suites:
  - name: default
    includes:
      - linux-7
    provisioner:
      provision_script_name: 'default.sh'
```
- Defines test scenarios
- Links to provision script

#### Usage

```bash
# Install Test Kitchen (if not already installed)
# Run tests
kitchen test

# Converge (build and install)
kitchen converge

# Verify only
kitchen verify

# Destroy test instances
kitchen destroy
```

---

### `hooks/` - Lifecycle Scripts

**Location:** `habitat/hooks/`  
**Purpose:** Bash scripts executed at different service lifecycle stages.

#### Hook Execution Order

1. **`init`** - Runs once when service starts
2. **`run`** - Executed to start the application (becomes the main process)
3. **`health_check`** - Runs periodically to verify health

#### `hooks/init` - Initialization Hook

**When:** Runs once when the service starts, before `run` hook.

**Purpose:**
- Set up directories
- Create configuration files
- Initialize data structures
- One-time setup tasks

**Example from current file:**
```bash
#!/bin/bash
mkdir -p /hab/svc/fraud-risk-scanner/logs
cat > /hab/svc/fraud-risk-scanner/config/application.properties << EOF
server.port={{cfg.server_port}}
EOF
```

**Note:** With scaffolding, many initialization tasks are handled automatically.

#### `hooks/run` - Runtime Hook

**When:** Executed to start the application process. This becomes PID 1 in the container.

**Purpose:**
- Start the application
- Set environment variables
- Configure JVM options
- Launch the main process

**Example from current file:**
```bash
#!/bin/bash
export JAVA_OPTS="-Xms512m -Xmx1024m"
exec java $JAVA_OPTS -jar /hab/svc/fraud-risk-scanner/app.jar
```

**Important:** Must use `exec` to replace the shell process with the application.

**Note:** With scaffolding, the run hook is typically provided by the scaffolding package.

#### `hooks/health_check` - Health Check Hook

**When:** Runs periodically (default: every 30 seconds) to check service health.

**Purpose:**
- Verify application is responding
- Check dependencies (database, external services)
- Validate application state
- Return exit code: 0 = healthy, 1 = unhealthy

**Example from current file:**
```bash
#!/bin/bash
HEALTH_URL="http://localhost:{{cfg.management_server_port}}/actuator/health"
if curl -f -s "$HEALTH_URL" > /dev/null 2>&1; then
    exit 0
else
    exit 1
fi
```

**Handlebars Templates:** `{{cfg.management_server_port}}` gets replaced with actual value from `default.toml`.

**Note:** With scaffolding, basic health checks are usually provided.

---

## Tutorial: Getting Started with Habitat

### Prerequisites

1. **Habitat CLI** installed
   ```bash
   # Check installation
   hab --version
   
   # If not installed, download from https://www.habitat.sh/docs/install-habitat/
   ```

2. **Habitat Builder Access** (Mastercard internal)
   - Access to `bizopsbank` origin
   - Artifactory credentials configured

3. **Docker** (for Test Kitchen)
   ```bash
   docker --version
   ```

### Step 1: Understanding Your Setup

Your Habitat package is configured for the **scanner** application:

- **Package Name**: `bizopsbank/scanner`
- **Artifactory URL**: Points to scanner JAR
- **Java Version**: 21
- **Scaffolding**: `mastercard/scaffolding_java_standalone`

### Step 2: Building the Package

#### Local Build (Habitat Studio)

```bash
# Navigate to project root
cd Mohit_support

# Enter Habitat Studio (creates isolated build environment)
hab studio enter

# Build the package
build

# Exit studio
exit
```

**What Happens:**
1. Habitat Studio creates a clean Linux container
2. Downloads build dependencies (Maven, JDK)
3. Scaffolding downloads JAR from Artifactory URL
4. Packages everything into `.hart` file
5. Output saved to `results/` directory

#### Build Output

After build, you'll find:
```
results/
└── bizopsbank-scanner-0.1.0-SNAPSHOT-YYYYMMDDHHMMSS-x86_64-linux.hart
```

### Step 3: Testing Locally

#### Using Test Kitchen

```bash
# From habitat directory
cd habitat

# Run full test cycle
kitchen test

# Or step by step:
kitchen create    # Create test instances
kitchen converge  # Install package
kitchen verify    # Run tests
kitchen destroy   # Clean up
```

#### Manual Testing

```bash
# Install package locally
hab pkg install results/bizopsbank-scanner-*.hart

# Start service
hab svc start bizopsbank/scanner

# Check status
hab svc status

# View logs
hab svc logs bizopsbank/scanner

# Stop service
hab svc stop bizopsbank/scanner
```

### Step 4: Configuration

#### View Default Configuration

```bash
hab config show bizopsbank/scanner default
```

#### Override Configuration at Runtime

**Method 1: Config File**
```bash
# Create custom config file
cat > my-config.toml << EOF
[standalone.application.serverConfiguration.jvm]
minHeap = "1024m"
maxHeap = "2048m"
EOF

# Start with custom config
hab svc start bizopsbank/scanner --config my-config.toml
```

**Method 2: Environment Variable**
```bash
hab svc start bizopsbank/scanner \
  --bind database:postgresql.default \
  --peer 192.168.1.100
```

**Method 3: Runtime Update**
```bash
# Update config without restarting
hab config apply bizopsbank/scanner 1 /path/to/config.toml
```

### Step 5: Publishing to Habitat Builder

```bash
# Authenticate with Habitat Builder
hab cli setup

# Upload package
hab pkg upload results/bizopsbank-scanner-*.hart

# Verify upload
hab pkg search bizopsbank/scanner
```

### Step 6: Deployment

#### Using Habitat Supervisor

```bash
# On target server, start supervisor
hab sup run

# Install and start service
hab svc load bizopsbank/scanner

# Or from Habitat Builder
hab svc load bizopsbank/scanner --channel stable
```

#### Service Group (Multi-node)

```bash
# First node
hab svc load bizopsbank/scanner \
  --group production \
  --topology leader

# Additional nodes
hab svc load bizopsbank/scanner \
  --group production \
  --topology follower \
  --peer first-node-ip
```

---

## Build and Deploy Workflow

### Complete CI/CD Pipeline

```mermaid
graph LR
    A[Source Code] --> B[Maven Build]
    B --> C[Publish to Artifactory]
    C --> D[Build Habitat Package]
    D --> E[Test with Kitchen]
    E --> F[Publish to Habitat Builder]
    F --> G[Deploy to Environment]
```

### Step-by-Step Workflow

1. **Source Code Changes**
   ```bash
   git checkout -b feature/my-feature
   # Make changes
   git commit -m "Update scanner logic"
   git push
   ```

2. **Jenkins Pipeline** (automated)
   - Builds scanner JAR
   - Publishes to Artifactory
   - Builds Habitat package
   - Runs tests
   - Publishes to Habitat Builder

3. **Habitat Build**
   ```bash
   hab studio build
   # Reads plan.sh
   # Downloads JAR from Artifactory
   # Creates .hart file
   ```

4. **Testing**
   ```bash
   kitchen test
   # Validates package works
   ```

5. **Publishing**
   ```bash
   hab pkg upload results/*.hart
   ```

6. **Deployment**
   ```bash
   hab svc load bizopsbank/scanner
   ```

---

## Best Practices

### 1. Version Management

**Static Versioning (Current)**
```bash
pkg_version='0.1.0-SNAPSHOT'
```
- Simple and explicit
- Good for development

**Dynamic Versioning**
```bash
pkg_version() {
  pushd /src > /dev/null
  $(pkg_path_for mastercard/maven)/bin/mvn help:evaluate \
    -Dexpression=project.version -q -DforceStdout
  popd > /dev/null
}
```
- Extracts version from Maven POM
- Ensures consistency

### 2. Configuration Management

- **Keep defaults minimal**: Only essential values in `default.toml`
- **Use comments**: Document optional configurations
- **Environment-specific**: Override at deployment time
- **Secret management**: Use Habitat's secret management for sensitive data

### 3. Health Checks

- **Make health checks fast**: < 1 second ideally
- **Check dependencies**: Database, external APIs
- **Proper exit codes**: 0 = healthy, 1 = unhealthy
- **Log health check results**: Helpful for debugging

### 4. Hooks

- **Keep hooks simple**: Delegate complex logic to scaffolding
- **Use exec in run hook**: Ensures proper signal handling
- **Error handling**: Exit with non-zero on failure
- **Logging**: Output useful information

### 5. Scaffolding Usage

- **Prefer scaffolding**: Don't reinvent the wheel
- **Customize when needed**: Override hooks if necessary
- **Stay updated**: Keep scaffolding version current

### 6. Testing

- **Test before publish**: Always run `kitchen test`
- **Multiple environments**: Test in dev, staging, production-like
- **Version testing**: Test with actual Artifactory versions

---

## Troubleshooting

### Common Issues and Solutions

#### Build Failures

**Issue: Cannot download JAR from Artifactory**
```
Error: Failed to download standalone_app_artifactory_url
```
**Solution:**
- Verify Artifactory URL is correct
- Check credentials are configured
- Verify JAR exists at specified path
- Check network connectivity

**Issue: Maven dependencies not found**
```
Error: Could not resolve dependencies
```
**Solution:**
- Ensure Maven settings.xml configured
- Check internal repository access
- Verify dependency versions in pom.xml

#### Runtime Issues

**Issue: Service won't start**
```bash
hab svc logs bizopsbank/scanner
```
**Solution:**
- Check logs for error messages
- Verify configuration is valid TOML
- Check file permissions
- Ensure JAR file exists

**Issue: Health check failing**
```bash
hab svc status
# Shows health check status
```
**Solution:**
- Verify health endpoint is accessible
- Check port configuration
- Ensure application is actually running
- Review health_check hook logic

#### Configuration Issues

**Issue: Configuration not applied**
**Solution:**
- Verify TOML syntax
- Check Handlebars template syntax
- Ensure scaffolding reads your config keys
- Restart service after config changes

#### Testing Issues

**Issue: Test Kitchen times out**
**Solution:**
- Increase timeout values
- Check Docker is running
- Verify image is accessible
- Check network connectivity

**Issue: InSpec tests fail**
**Solution:**
- Review test expectations
- Check service is actually running
- Verify ports are correct
- Review test output

### Debugging Commands

```bash
# Check package contents
hab pkg contents bizopsbank/scanner

# View plan
hab pkg path bizopsbank/scanner
cat $(hab pkg path bizopsbank/scanner)/plan.sh

# Check service status
hab svc status

# View detailed logs
hab svc logs bizopsbank/scanner --follow

# Check configuration
hab config show bizopsbank/scanner default

# Inspect .hart file
hab pkg info results/*.hart

# Test connectivity
hab pkg exec bizopsbank/scanner curl http://localhost:8080/health
```

### Getting Help

1. **Habitat Documentation**: https://www.habitat.sh/docs/
2. **Mastercard Internal Docs**: Check internal wiki
3. **Scaffolding Docs**: Review `mastercard/scaffolding_java_standalone` documentation
4. **Team Support**: Contact BizOps Bank team

---

## Additional Resources

### Habitat CLI Commands Reference

```bash
# Package Management
hab pkg build .              # Build package
hab pkg install <package>    # Install package
hab pkg upload <file>        # Upload to builder
hab pkg search <name>        # Search packages

# Service Management
hab svc start <package>      # Start service
hab svc stop <package>       # Stop service
hab svc load <package>       # Load and start
hab svc unload <package>     # Stop and unload
hab svc status               # List services
hab svc logs <package>       # View logs

# Configuration
hab config show <package>    # Show config
hab config apply <package>   # Apply config

# Studio
hab studio enter             # Enter build studio
hab studio run <command>     # Run command in studio
```

### File Locations

When service is running:

```
/hab/svc/<package-name>/
├── config/          # Runtime configuration
├── data/            # Application data
├── logs/            # Application logs
├── static/          # Static files
└── var/             # Variable files
```

### Environment Variables

Habitat sets these automatically:
- `HAB_SVC` - Service name
- `HAB_GROUP` - Service group
- `HAB_TOPOLOGY` - Topology type
- `HAB_UPDATE_STRATEGY` - Update strategy

---

## Conclusion

This guide covers the Habitat components for the scanner application. The setup uses Mastercard's scaffolding pattern, similar to t3 scheduler, providing a consistent and maintainable deployment approach.

**Key Takeaways:**
- Habitat packages applications with dependencies
- Scaffolding simplifies Java standalone app packaging
- Configuration is flexible and runtime-overridable
- Hooks manage application lifecycle
- Test Kitchen enables integration testing

For questions or issues, refer to the troubleshooting section or contact the BizOps Bank team.

