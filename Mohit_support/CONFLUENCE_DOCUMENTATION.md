# Fraud and Risk Scanner - Confluence Documentation

## Overview

This document provides a comprehensive explanation of the Fraud and Risk Scanner application architecture based on the Confluence documentation provided. The application consists of three main components working together to process fraud and risk reports.

---

## Solution Architecture

The Fraud and Risk Scanner is a multi-component application designed to:
- Process fraud and risk reports from external systems
- Ensure single processing in clustered environments
- Route risk alerts to monitoring systems (Splunk/Netcool)
- Generate risk analysis reports

### Project Components

1. **Alert Bridge Component** (G1122-5697)
2. **Feeder Component** (G1122-5699)
3. **Scanner Component** (G1122-5698)

---

## Component 1: Alert Bridge

### Purpose
The Alert Bridge routes Risk objects to either Splunk, Netcool, or both. The routing requirement is specified within the Risk object itself.

### Key Responsibilities
- **Risk Routing**: Takes Risk objects and routes them to appropriate monitoring systems
- **Destination Management**: Supports multiple routing destinations:
  - Splunk (for logging and analysis)
  - Netcool (for incident management)
  - Both systems simultaneously
- **Configuration Driven**: Routing decisions are based on the Risk object properties

### Technical Details
- **Main Class**: `com.mastercard.fraudriskscanner.alertbridge.AlertBridgeApplication`
- **Technology**: Java 21, SLF4J/Logback for logging
- **Build**: Maven with Shade plugin for executable JAR
- **Artifact**: `fraud-risk-scanner-alert-bridge-0.1.0.jar`

### Integration Points
The Alert Bridge receives Risk objects from other components and acts as the final routing layer before sending data to external monitoring systems.

---

## Component 2: Feeder

### Purpose
The Feeder application processes report files originating from external processes. It manages file lifecycle and ensures single processing across a cluster using an expiring semaphore mechanism.



### Overview
The Feeder application reads report files from defined source directories (configured via `.toml` files) and manages their processing lifecycle through three possible outcomes:
- **Successfully Processed**: File is moved to a completion directory
- **Error**: File is moved to an error directory
- **Skipped**: File is moved to a skipped directory

### Expiring Semaphore Logic for Single Processing

The Feeder implements sophisticated logic to ensure that each report file is processed exactly once, even in a clustered environment where multiple Feeder instances are running.

#### Key Requirements
- All files are stored on all servers in a co-processing cluster
- Only one Feeder instance should process a given report file
- No duplicate alerts should be generated

#### Implementation Mechanism

**1. Fixed-Size Lossy Map (Bounded Fingerprint Map)**
The system uses a bounded data structure to track file processing:

- **File Name Representation**: The file name is converted to a bounded hash
- **Map Key**: Uses bytes 0-3 of the SHA-256 hash of the file name (bounded by reduction of 2^16)
- **Fingerprint**: Uses bytes 23-31 of the SHA-256 hash as a high avalanche hash for the map entry
- **Purpose**: Prevents false positives and controls map growth

**Mathematical Representation**:
```
File name → SHA-256
- Map Key = Bytes[0-3] of SHA-256 (reduced by 2^16)
- Fingerprint = Bytes[23-31] of SHA-256
```

**2. Database Storage**
- The map is stored in a **Hazelcast database**
- Provides transaction isolation
- Ensures consistency across cluster nodes

**3. Atomic Check Operation**
The algorithm performs an atomic check-and-set operation:

```
IF (key exists AND fingerprint matches):
    THEN: Skip file (already processed)
    ELSE: Add fingerprint to map AND proceed with processing
```

#### Processing Flow

1. **File Selection**: The Feeder selects a candidate report file
2. **Semaphore Check**: Atomically checks if file fingerprint exists in the map
3. **Skip Path**: If present, move file to skipped directory
4. **Process Path**: If not present, add fingerprint and proceed to:
   - Invoke Scanner component
   - Process the file based on scanner exit status
   - Move to appropriate destination directory

### Scanner Interaction

The Feeder dynamically invokes the Scanner component:

- **Lookup**: Retrieves scanner application from registry (configured via `.toml` file)
- **Execution**: Uses `ProcessBuilder` to execute scanner as separate process
- **Exit Status Handling**:
  - `0`: File successfully processed → move to completed directory
  - `3`: File not processed successfully → move to error directory
- **Error Handling**: Scanner failures result in file being moved to error directory

### File Lifecycle

```
Source Directory (External reports)
    ↓
Candidate File Selection
    ↓
Semaphore Check
    ↓
┌─────────────┬──────────────┐
│   Skip      │   Process    │
│   (Duplicate)│   (New File)│
└──────┬──────┴──────┬───────┘
       ↓             ↓
   Skipped Dir   Scanner Invocation
                      ↓
                 ┌────┴────┐
                 │ 0   │  3  │
                 └───┬─┴───┬─┘
                     ↓     ↓
                 Success Error
                     ↓     ↓
                 Complete Error
                 Directory Directory
```

### TOML Configuration

The Feeder is configured via `.toml` files that define:
- Source directories for report files
- Destination directories (success, error, skipped)
- Scanner application registry
- Processing parameters

### Technical Details
- **Main Class**: `com.mastercard.fraudriskscanner.feeder.FeederApplication`
- **Technology**: Java 21, SLF4J/Logback for logging
- **Build**: Maven with Shade plugin for executable JAR
- **Artifact**: `fraud-risk-scanner-feeder-0.1.0.jar`
- **Dependencies**: Hazelcast for distributed coordination

---

## Component 3: Scanner

### Purpose
The Scanner component performs the actual risk analysis on report files. It's invoked by the Feeder component to process individual reports. The Scanner is designed to process text-based reports with a modular architecture.

### Key Responsibilities
- Process fraud and risk report files (text-line format)
- Perform risk analysis using pattern matching
- Return processing status to Feeder
- Generate risk objects for Alert Bridge
- Process reports line by line using callbacks

### Text Report Scanner Design

**Report Format:**
- Mastercard reports use a classic text line report format
- Reports consist of multiple text lines
- Lines are logically organized into:
  - Pages
  - Headers
  - Details
  - Summaries
  - Footers

**Application Architecture:**
- Each scanner application processes only one type of report
- Uses **Inversion of Control (IoC) pattern** for consistent behavior
- Common foundation class `TextReportScannerMain` provides the main() method
- Dynamic loading mechanism to find report scanner implementations

### TextReportScanner Interface

The Scanner uses a standard interface for report processing:

```java
public interface TextReportScanner {
    Optional<Stream<Risk>> analyze(String line);
    Optional<Stream<Risk>> finalize();
}
```

**Interface Methods:**
- `analyze(String line)`: Called for each report line in order
  - Returns `Optional<Stream<Risk>>` - detected risks from the line
  - Called sequentially as lines are read from the report
- `finalize()`: Called when no more lines are available
  - Returns `Optional<Stream<Risk>>` - final risk detections
  - Used for summary-level analysis

### Dynamic Loading Mechanism

The Scanner uses **Java ServiceLoader** for plugin-based architecture:

**ServiceLoader Pattern:**
1. `scanner.jar` (core package) contains:
   - `TextReportScannerMain` with `public static void main(String args[])`
   - Calls `ServiceLoader.load(TextReportScanner.class)`
   
2. `report.jar` (report-specific package) contains:
   - Implementation of `TextReportScanner` interface
   - `META-INF/services/<scannercore_package>.TextReportScanner` file
   - Module declaration:
     ```java
     module report_package {
         uses scannercore_package.TextReportScanner;
         provides scannercore_package.TextReportScanner 
             with report_package.ImplementationClass;
     }
     ```

**Scanner Application Characteristics:**
- Processes only one type of report
- Executable `main()` method comes from `TextReportScannerMain` in scanner component
- Must have a class implementing `TextReportScanner` interface
- Dynamically loaded by scanner component using ServiceLoader

**Callbacks:**
- Scanner component uses `TextReportScanner::analyze` callback
  - Passes report lines to scanner application in order
  - Processes lines as they exist in the report
- Scanner component uses `TextReportScanner::finalize` callback
  - Called when no more lines remain in report
  - Allows for final processing and summary detection

### Design Pattern: State Machine

Each report scanner should use a **state machine** built for the scanner system:

- Defines sequential report components (title, header, detail) as states
- Built-in dispatching logic for state transitions
- Ensures consistent processing across different report types

**Source:** The state machine implementation is available at:
```
https://github.com/magh-448/106854-bzbfrscan-scanner-sharedlib
```

### Naming Convention

- Scanner naming must be unique
- Convention uses the report name from the "MCC Operational Reports" tile

### Exit Status Codes
- `0`: File processed successfully
- `3`: File processing failed

### Technical Details
- **Main Class**: `com.mastercard.fraudriskscanner.scanner.ScannerApplication`
- **Technology**: Java 21, SLF4J/Logback for logging
- **Build**: Maven with Shade plugin for executable JAR
- **Artifact**: `fraud-risk-scanner-scanner-0.1.0.jar`
- **Architecture**: Plugin-based with dynamic loading

### Integration Points
- Invoked by Feeder using `ProcessBuilder` as external process
- Accepts report file path as argument
- Outputs Risk objects through Alert Bridge coupling
- Feeder monitors scanner exit status to determine next action

---

## Owners Manual Section

### Application Overview

**Purpose:**
A byproduct of any Mastercard customer's daily activity is numerous reports. They document authorization, non-authorization, and configuration changes over a past period of time. For BizOps Bank, as a customer of Mastercard, these reports document indicators of potential risks and fraud.

To eliminate the toil and error from manually reviewing these reports, an automated system of scanners analyzes these reports as they become available. The analysis detects pre-defined patterns in the reports of fraud, potential fraud, and other risks to the stability, reliability and availability of the BizOps Bank service.

**Pattern Definitions:**
These patterns are defined by the Risk and Threat Management governance board.

**Catalog Information:**
- Mastercard Catalog: Fraud and Risk Scanner
- ID: 106854
- Tech Asset Tag: BZBFRSCAN

### MiniSOAR (Security Orchestration and Response)

The application functions as a MiniSOAR (Security Orchestration, Automation, and Response) system for BizOps Bank.

### Application Behavior

**Run Lifecycle:**

The application has two parallel processes:

**Main Process States:**
1. **Stop**: Service stopped
2. **Idle**: Waiting for reports
3. **File Search**: Scanning for new report files

**Scanner Process States:**
1. **Scanner Running**: Actively processing a report
2. **Scanner Finished**: Completed processing

**State Transitions (Signals):**
- `FRS_0200`: Feeder started
- `FRS_0201`: Transition signals
- `FRS_0210`: Directory scanned by feeder
- `FRS_0215`: Report was scanned successfully
- `FRS_0251`: Report scan failed
- `FRS_0253`: No reports found for extended period of time
- `FRS_0291`: Feeder shutting down

**Parallel Processing:**
- Processing of reports is done in parallel with the main process
- Multiple scanners can process different reports simultaneously
- Feeder manages the lifecycle of scanner processes

### Interaction Flow

```
Habitat Supervisor
       ↓
    Feeder → Reports (from NGFT)
       ↓
    Scanners (multiple instances)
       ↓
    Alert Bridge
       ↓
    ┌────┴────┐
  Splunk   Netcool
```

**Detailed Flow:**
1. Habitat controls the run-state of the service
2. Primary process managed by Habitat is the Feeder
3. Feeder monitors for new reports transferred via NGFT
4. When a new report arrives, Feeder determines report type
5. Feeder invokes appropriate Scanner for the report type
6. Feeder couples Scanner output to Alert Bridge input (subprocess created by Feeder)
7. Scanner reports analysis results
8. Alert Bridge interprets analysis:
   - Type of risk
   - Category/severity
   - Routes to Splunk or Netcool
9. **Splunk**: Non-actionable, research information
10. **Netcool**: Immediate, actionable events

---

## High Availability (HA) Architecture

### Deployment Topology

The system is deployed in a **replicated topology** across multiple servers:

**Servers:**
- **STL**: Production server in St. Louis
- **KSC**: Production server in Kansas City
- **Habitat**: Central management system

**Architecture:**
- Each server runs Habitat Supervisor
- Hazecast clusters connect STL and KSC servers
- Shared semaphore mechanism ensures single processing

**Key Points:**
- Habitat nodes run in "replicated" topology
- Hazelcast facilitates single processing using shared semaphore
- Requires establishing Hazelcast cluster between production servers
- Messages `FRS_04XX` pertain to Hazelcast operations

### Hazelcast Functionality

**Purpose:**
Hazelcast facilitates the single processing of reports by only one node using a shared semaphore for a report.

**Semaphore Logic:**
- Built into the Feeder application
- Ensures only one node processes a report
- Hazelcast database is automatically pruned to prevent indefinite growth
- Prevents duplicate alert generation

### Disaster Recovery (DR)

The HA architecture provides disaster recovery capabilities:
- Multiple servers in different locations (STL, KSC)
- Replicated data and state across nodes
- Automatic failover and load balancing

---

## File Handling

### Report File Sources

Report files come from NGFT download directories:

**Source Directory:**
```
$HOME/ngft-agent-data/downloaded_files/bizopsbank-dev@mastercard/filex-app@mastercard/
```

**Purpose:** Subdirectories here contain the reports transferred from Mastercard via NGFT to the BizOps Bank server.

### Post-Processing Directories

Once reports are scanned, they are moved to one of three post-processing directories:

| Directory | Meaning |
|-----------|---------|
| `$HOME/frscanner/successful` | Reports that were successfully scanned |
| `$HOME/frscanner/skip` | Reports that were processed by one of the other co-processing nodes |
| `$HOME/frscanner/error` | Reports that could not be processed |

---

## Alert Messages

The system generates various alert messages for monitoring and operational visibility:

| ID | Title | Location |
|----|-------|----------|
| `FRS_0200` | Feeder started | Splunk |
| `FRS_0210` | Directory scanned by feeder | Splunk |
| `FRS_0215` | A report was scanned successfully | Splunk |
| `FRS_0251` | Report scan failed | Splunk, Netcool |
| `FRS_0253` | No reports found for extended period of time | Splunk, Netcool |
| `FRS_0291` | Feeder shutting down | Splunk |
| `FRS_0301` | Report scan could not parse file | Splunk, Netcool |
| `FRS_0321` | Report scan detected an information "category condition" | Splunk |
| `FRS_0322` | Report scan detected a warning "category condition" | Splunk, Netcool |
| `FRS_0323` | Report scan detected a critical "category condition" | Splunk, Netcool |
| `FRS_0451` | Hazelcast has a service fault | Splunk, Netcool |

**Note:** Messages `FRS_04XX` pertain to Hazelcast operations.

---

## Deployment Pipeline

### Environments

The application follows a staged deployment process across multiple environments:

1. **Stage** (`lstl4jbs8172`): Development/staging environment
2. **MTF** (`lstl5jbs8601`): Integration testing environment
3. **Production STL** (`lstl2jbs7580`): Production in St. Louis
4. **Production KSC** (`lksc2jbs7661`): Production in Kansas City

### Deployment Process

**Configuration Sources:**
- **HCVAULT**: HashiCorp Vault for secrets management
- **Registry Files**: JSON configuration files for each environment
- **Chef Infra**: Configuration management

**Deployment Stages:**

1. **Development/Stage**:
   - Source: `https://github.com/magh-448/106854-bzbfrscan-feeder-batch`
   - Jenkins builds application
   - Deploys to Habitat Builder
   - Tests in Stage environment

2. **Promotion to MTF**:
   - Stage version promoted to MTF
   - Integration testing
   - Jenkins pipeline: `https://cd.mastercard.int/jenkins/job/.../Deploy+Chef+Infra+Habitat+to+MTF/`

3. **Promotion to Production**:
   - MTF version promoted to Prod
   - Deployed to both STL and KSC
   - Jenkins pipeline: `https://cd.mastercard.int/jenkins/job/.../Deploy+Chef+Infra+Habitat+to+PROD/`

### Managing Run State

Service run state is managed through Jenkins:

- **Starting the service**: Jenkins link for start operations
- **Stopping the service**: Jenkins link for stop operations
- **Checking status of service**: Jenkins link for status check

### Chef Habitat Integration

**Habitat Builder:**
- Builds and packages the application
- Manages configuration across environments
- Publishes Habitat packages to depot

**Chef Infra:**
- Manages installation of Chef Habitat
- Configures Habitat for the service
- Node definitions include channel and group settings
- Once configured, pipelines no longer need execution

**Related Components:**
- T3 Scheduler for task scheduling
- NGFT for file transfer
- BZB Repository for artifact storage

---

## Supporting Contacts

| Role | Contact |
|------|---------|
| NGFT | TBD |
| Splunk | TBD |
| Netcool | TBD |

See the Runbook 03 Contact List for a comprehensive list of contacts.

---

## Related Systems

### nGFT (Next Generation File Transfer)

**Purpose:** Provides file transfer for BizOps Bank.

**Location:** `https://confluence.mastercard.int/spaces/bzb/pages/906027606/nGFT+for+Biz+Ops+Bank`

**Integration:**
- Source of report files for Fraud and Risk Scanner
- Transfers reports from Mastercard to BizOps Bank servers
- Download directory: `$HOME/ngft-agent-data/downloaded_files/...`

### Habitat and Chef Infrastructure

**Chef Habitat for Biz Ops Bank:**
- Application packaging and deployment
- Managed workloads across environments
- Configuration management

**Forge for Biz Ops Bank:**
- Additional infrastructure automation

---

## System Architecture Flow

```
┌─────────────────────────────────────────────────────────┐
│                    External Systems                     │
│            (Report Generation & Delivery)             │
└─────────────────────┬───────────────────────────────────┘
                      ↓
                Report Files
                      ↓
┌─────────────────────────────────────────────────────────┐
│                    FEDER COMPONENT                      │
│  1. Monitor source directories                          │
│  2. Apply Expiring Semaphore Logic                      │
│  3. Ensure single processing in cluster                 │
│  4. Manage file lifecycle                              │
└─────────────────────┬───────────────────────────────────┘
                      ↓
                 Scanner Invocation
                      ↓
┌─────────────────────────────────────────────────────────┐
│                   SCANNER COMPONENT                     │
│  1. Process report files                                │
│  2. Perform risk analysis                              │
│  3. Generate Risk objects                              │
│  4. Return status to Feeder                            │
└─────────────────────┬───────────────────────────────────┘
                      ↓
                Risk Objects
                      ↓
┌─────────────────────────────────────────────────────────┐
│                  ALERT BRIDGE COMPONENT                 │
│  1. Receive Risk objects                                │
│  2. Determine routing destination                      │
│  3. Route to Splunk/Netcool/Both                        │
└─────────────────────┬───────────────────────────────────┘
                      ↓
         ┌────────────┴────────────┐
         ↓                         ↓
    ┌─────────┐              ┌──────────┐
    │ Splunk  │              │ Netcool  │
    │ Monitoring            │ Incident │
    │ & Analysis            │ Management
    └─────────┘              └──────────┘
```

---

## Clustered Environment

The system is designed to operate in a clustered environment where multiple instances of each component can run simultaneously:

### Feeder Cluster Coordination
- **Shared Storage**: All files are stored on all servers in the cluster
- **Hazelcast**: Provides distributed caching and coordination
- **Semaphore Logic**: Ensures exactly one instance processes each file
- **Transaction Isolation**: Prevents race conditions and duplicate processing

### Benefits
- **High Availability**: Multiple instances provide redundancy
- **Load Distribution**: Processing can be distributed across nodes
- **Scalability**: Can add more instances as load increases
- **Fault Tolerance**: If one instance fails, others can continue processing

---

## Configuration Management

The application uses TOML (Tom's Obvious, Minimal Language) files for configuration:

### Habitat Configuration
- **Plan File**: `habitat/plan.sh` - Defines Habitat package structure
- **Default Config**: `habitat/default.toml` - Default configuration values
- **Hooks**: 
  - `init` - Initialization hook
  - `run` - Runtime hook
  - `health_check` - Health check hook

### Component Configuration
Each component can be configured via:
- Environment variables
- TOML configuration files
- Habitat runtime configuration

---

## CI/CD Pipeline

The Jenkins pipeline automates the entire build and deployment process:

### Pipeline Stages

1. **Checkout**: Source code retrieval
2. **Build**: Maven compilation for all components
3. **Test**: Unit test execution
4. **Package Alert Bridge**: Create Alert Bridge JAR
5. **Package Scanner**: Create Scanner JAR
6. **Package Feeder**: Create Feeder JAR
7. **SonarQube Analysis**: Code quality analysis
8. **Publish to Artifactory**: Upload all JARs to Artifactory
9. **Build Habitat Package**: Create Habitat package
10. **Publish Habitat Package**: Upload to Habitat Depot

### Artifact Locations

All components are published to Artifactory at:
```
https://artifactory.mastercard.com/artifactory/libs-release-local/com/mastercard/
├── fraud-risk-scanner-alert-bridge/
├── fraud-risk-scanner-scanner/
└── fraud-risk-scanner-feeder/
```

---

## Technical Stack

### Language & Runtime
- **Java 21**: All components built with Java 21
- **Maven 3.6+**: Build tool
- **SLF4J/Logback**: Logging framework

### Infrastructure
- **Habitat**: Package and deployment management
- **Hazelcast**: Distributed caching and coordination
- **Jenkins**: CI/CD automation
- **SonarQube**: Code quality analysis
- **Artifactory**: Artifact repository

### Monitoring & Alerting
- **Splunk**: Logging and analysis
- **Netcool**: Incident management

---

## Success Criteria

✅ **G1122-5697**: Alert Bridge stub JAR created and published to Artifactory
✅ **G1122-5698**: Scanner stub JAR created and published to Artifactory
✅ **G1122-5699**: Feeder stub JAR created and published to Artifactory
✅ Jenkins job builds all three stub jars and pushes to Artifactory
✅ Can log into SonarQube and see results
✅ Habitat package builds successfully
✅ All components start and run with Java 21

---

## Team & Project Information

- **Team**: BizOps Bank
- **Organization**: Mastercard
- **Project**: B2B Projects
- **Maintainer**: BizOps Bank Team
- **Jira Tickets**: 
  - G1122-4669 (Project Setup)
  - G1122-5697 (Alert Bridge)
  - G1122-5698 (Scanner)
  - G1122-5699 (Feeder)

---

## Future Enhancements

The current stub implementation provides the foundation for:

1. **Fraud Detection Logic**: Actual risk detection algorithms
2. **Risk Assessment Algorithms**: Scoring and classification
3. **Database Integration**: Persistent storage for risk data
4. **Security Features**: Authentication, authorization, encryption
5. **Monitoring & Alerting**: Health checks, metrics, dashboards
6. **Performance Optimization**: Tuning for high-volume processing
7. **Advanced Routing**: More sophisticated routing logic in Alert Bridge
8. **Enhanced Semaphore Logic**: Fine-tuning the duplicate prevention mechanism

---

## Key Design Patterns

### 1. Expiring Semaphore Pattern
- Ensures single processing in distributed systems
- Uses bounded data structures to prevent unbounded growth
- Provides atomic operations for consistency

### 2. Component Separation
- Each component has a single, well-defined responsibility
- Components communicate through well-defined interfaces
- Enables independent scaling and deployment

### 3. External Process Invocation
- Feeder invokes Scanner as external process
- Allows independent lifecycle management
- Provides fault isolation

### 4. Configuration-Driven Routing
- Alert Bridge decisions based on Risk object properties
- No hardcoded routing rules
- Flexible and extensible design

---

## References

- **Confluence Documentation**: Mastercard Internal Confluence
- **Repository**: GitHub repository structure
- **Habitat Documentation**: Chef Habitat platform
- **Hazelcast Documentation**: Distributed data grid
- **Java 21 Documentation**: Oracle Java documentation

---

*Document generated from Confluence documentation and project structure analysis*
*Last Updated: October 2025*

