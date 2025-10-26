# Gap Analysis: Fraud Risk Scanner Implementation

## Overview

This document analyzes the gaps between the current stub implementation and the architectural requirements documented in Confluence.

## Current State

The project currently contains **stub implementations** that provide basic structure but no actual functionality. All three components (Alert Bridge, Feeder, Scanner) are placeholders that:
- Start and log messages
- Sleep indefinitely
- Have no actual business logic

## Required vs Actual Implementation

### ✅ What's Correct

| Aspect | Status | Notes |
|--------|--------|-------|
| Java 21 | ✅ | All components use Java 21 |
| Maven Structure | ✅ | Proper Maven project setup |
| Packaging | ✅ | Shade plugin for executable JARs |
| Logging | ✅ | SLF4J/Logback setup |
| Component Separation | ✅ | Three separate JARs |
| Basic Health Checks | ✅ | Components run without errors |

### ❌ What's Missing

#### 1. Alert Bridge Component

**Required Functionality:**
- Route Risk objects to Splunk, Netcool, or both
- Decision based on Risk object properties
- Configuration-driven routing
- Intercept Scanner output

**Current State:**
- ❌ No Risk object model
- ❌ No routing logic
- ❌ No Splunk integration
- ❌ No Netcool integration
- ❌ No configuration support

**Dependencies Missing:**
```xml
<!-- Missing in pom.xml -->
<dependency>
    <groupId>com.splunk</groupId>
    <artifactId>splunk</artifactId>
    <version>X.X</version>
</dependency>
<!-- Netcool client -->
```

**Required Classes:**
- `Risk.java` - Risk data model
- `RiskRoutingStrategy.java` - Routing decision logic
- `SplunkPublisher.java` - Splunk integration
- `NetcoolPublisher.java` - Netcool integration
- `AlertBridgeService.java` - Main service implementation

---

#### 2. Feeder Component

**Required Functionality:**
- Monitor NGFT download directories
- Apply Expiring Semaphore Logic using Hazelcast
- Ensure single processing in cluster
- Invoke Scanner via ProcessBuilder
- Manage file lifecycle (success/error/skip)
- Handle Scanner exit status codes

**Current State:**
- ❌ No directory monitoring
- ❌ No Hazelcast integration
- ❌ No semaphore logic
- ❌ No ProcessBuilder for Scanner invocation
- ❌ No file management
- ❌ No configuration from .toml files

**Dependencies Missing:**
```xml
<!-- Missing in pom.xml -->
<dependency>
    <groupId>com.hazelcast</groupId>
    <artifactId>hazelcast</artifactId>
    <version>5.4.X</version>
</dependency>
<dependency>
    <groupId>com.typesafe.config</groupId>
    <artifactId>config</artifactId>
    <version>1.4.3</version>
</dependency>
```

**Required Classes:**
- `DirectoryMonitor.java` - Watch for new files
- `HazelcastSemaphoreManager.java` - Distributed semaphore
- `FileFingerprint.java` - SHA-256 based fingerprinting
- `ReportLifecycleManager.java` - File movement logic
- `ScannerInvoker.java` - ProcessBuilder integration
- `FeederService.java` - Main service logic

**Algorithm Required:**
```
1. SHA-256 of file name
2. Bytes 0-3 for map key (bounded by 2^16)
3. Bytes 23-31 for fingerprint
4. Atomic check in Hazelcast
5. If present → skip
6. If not present → process
```

**TOML Configuration Needed:**
- Source directories
- Scanner executable path
- Destination directories (success/error/skip)
- Hazelcast cluster config

---

#### 3. Scanner Component

**Required Functionality:**
- Implement TextReportScanner interface
- Dynamic loading via ServiceLoader
- Line-by-line processing with analyze() callback
- Final processing with finalize() callback
- State machine for report parsing
- Return Risk objects to Alert Bridge

**Current State:**
- ❌ No TextReportScanner interface
- ❌ No ServiceLoader setup
- ❌ No file parsing
- ❌ No pattern detection
- ❌ No Risk object generation

**Dependencies Missing:**
```xml
<!-- Missing in pom.xml -->
<dependency>
    <groupId>com.github.magh-448</groupId>
    <artifactId>scanner-sharedlib</artifactId>
    <version>X.X</version>
    <!-- From: https://github.com/magh-448/106854-bzbfrscan-scanner-sharedlib -->
</dependency>
```

**Required Classes:**
- `TextReportScanner.java` - Interface (in shared lib)
- `TextReportScannerMain.java` - Main class (in shared lib)
- `ReportScannerImplementation.java` - Actual scanner (per report type)
- `StateMachine.java` - Report parsing state machine (in shared lib)
- `Risk.java` - Risk data model

**Interface Required:**
```java
public interface TextReportScanner {
    Optional<Stream<Risk>> analyze(String line);
    Optional<Stream<Risk>> finalize();
}
```

**ServiceLoader Setup:**
- `META-INF/services/TextReportScanner` file
- Module declaration for JPMS support
- Dynamic loading of implementations

---

## Critical Gaps Summary

### Architecture Level Gaps

| Component | Missing Feature | Impact |
|-----------|----------------|--------|
| **All** | No configuration management | Cannot run in production |
| **Feeder** | No Hazelcast | Cannot work in cluster |
| **Feeder** | No semaphore logic | Duplicate processing will occur |
| **Feeder** | No directory monitoring | Won't detect new reports |
| **Scanner** | No ServiceLoader pattern | Cannot support multiple report types |
| **Scanner** | No file parsing | Cannot analyze reports |
| **Alert Bridge** | No risk routing | Cannot send alerts anywhere |

### Data Model Missing

- ❌ `Risk` class/interface
- ❌ Risk categories (information/warning/critical)
- ❌ Risk severity levels
- ❌ Routing requirements in Risk object

### Integration Missing

- ❌ Splunk client integration
- ❌ Netcool client integration
- ❌ Hazelcast cluster setup
- ❌ NGFT directory structure
- ❌ ProcessBuilder for Scanner invocation

### Operational Missing

- ❌ Health check endpoints
- ❌ Metrics/observability
- ❌ Error handling and recovery
- ❌ Graceful shutdown
- ❌ Configuration via Habitat

---

## Recommended Implementation Plan

### Phase 1: Foundation (Week 1-2)

1. **Create Risk Model**
   - Implement `Risk.java` interface/class
   - Add routing properties
   - Add severity categories

2. **Add Missing Dependencies**
   - Hazelcast to feeder pom.xml
   - Splunk client to alert-bridge pom.xml
   - Netcool client to alert-bridge pom.xml
   - Config/Toml parser to all components

3. **Setup ServiceLoader for Scanner**
   - Create TextReportScanner interface
   - Create META-INF/services files
   - Setup module declarations

### Phase 2: Feeder Implementation (Week 3-4)

1. **Directory Monitoring**
   - Implement directory watcher
   - Support TOML configuration
   - Handle new file detection

2. **Hazelcast Integration**
   - Setup Hazelcast client
   - Implement semaphore manager
   - Implement fingerprint algorithm
   - Atomic check-and-set operations

3. **Scanner Invocation**
   - Implement ProcessBuilder
   - Handle exit codes (0, 3)
   - Manage file lifecycle

4. **File Management**
   - Move to success directory
   - Move to error directory
   - Move to skip directory

### Phase 3: Scanner Implementation (Week 5-6)

1. **ServiceLoader Setup**
   - Create TextReportScanner interface
   - Create TextReportScannerMain
   - Setup dynamic loading

2. **Basic Parser**
   - Implement analyze() method
   - Implement finalize() method
   - Line-by-line processing

3. **State Machine**
   - Implement report state machine
   - Parse headers/details/summaries
   - Pattern detection logic

4. **Risk Generation**
   - Create Risk objects
   - Categorize risks
   - Set severity levels

### Phase 4: Alert Bridge Implementation (Week 7)

1. **Risk Routing**
   - Implement routing logic
   - Support Splunk routing
   - Support Netcool routing
   - Support both routing

2. **Publisher Integration**
   - Splunk publisher
   - Netcool publisher
   - Error handling

3. **Configuration**
   - TOML config support
   - Routing rules

### Phase 5: Testing & Integration (Week 8)

1. **Unit Tests**
   - All components
   - Semaphore logic
   - Routing logic

2. **Integration Tests**
   - End-to-end flow
   - Cluster testing
   - Failure scenarios

3. **Deployment**
   - Habitat packaging
   - Jenkins pipeline
   - Production readiness

---

## Files That Need to Be Created

### Feeder Component
```
feeder/src/main/java/com/mastercard/fraudriskscanner/feeder/
├── DirectoryMonitor.java
├── HazelcastSemaphoreManager.java
├── FileFingerprint.java
├── ReportLifecycleManager.java
├── ScannerInvoker.java
├── FeederService.java
└── config/
    └── FeederConfig.java
```

### Scanner Component
```
scanner/src/main/java/com/mastercard/fraudriskscanner/scanner/
├── TextReportScanner.java (interface)
├── TextReportScannerMain.java
├── scannerimpl/ (report-specific implementations)
└── META-INF/services/
    └── com.mastercard.fraudriskscanner.scanner.TextReportScanner
```

### Alert Bridge Component
```
alert-bridge/src/main/java/com/mastercard/fraudriskscanner/alertbridge/
├── model/
│   └── Risk.java
├── routing/
│   ├── RiskRoutingStrategy.java
│   └── RoutingRule.java
├── publishers/
│   ├── SplunkPublisher.java
│   └── NetcoolPublisher.java
└── AlertBridgeService.java
```

### Shared/Common
```
src/main/java/com/mastercard/fraudriskscanner/common/
├── model/
│   └── Risk.java
└── config/
    └── TOMLConfigReader.java
```

---

## Configuration Files Needed

### feeder/default.toml
```toml
[source_directory]
path = "$HOME/ngft-agent-data/downloaded_files/bizopsbank-dev@mastercard/filex-app@mastercard/"

[directories]
success = "$HOME/frscanner/successful"
error = "$HOME/frscanner/error"
skip = "$HOME/frscanner/skip"

[scanner]
executable = "/path/to/scanner.jar"
```

### scanner/default.toml
```toml
[alert_bridge]
host = "localhost"
port = 8080
```

---

## Conclusion

The current project is a **stub implementation** that provides the basic structure but is missing **all actual functionality**. Significant development is required to implement the architecture described in the Confluence documentation.

**Estimated Effort**: 8 weeks for basic implementation, 12 weeks for production-ready system with comprehensive testing.

**Immediate Next Steps**:
1. Add missing dependencies to all components
2. Create Risk model (shared)
3. Implement TextReportScanner interface
4. Implement directory monitoring in Feeder
5. Setup Hazelcast integration

