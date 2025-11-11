# Feeder Project - Implementation Plan

## Overview

This document outlines the plan to modify the Feeder project to:
1. Meet the Jira requirement: **F1d: Feeder: BZB Rpt Monitoring: Report to Netcool when no files present**
2. Integrate Hazelcast for distributed file tracking
3. Track files in Hazelcast when they are discovered in monitored directories

---

## Current Implementation Analysis

### ✅ What's Already Implemented

#### 1. **Report Directory Monitoring** (`ReportDirectoryMonitor.java`)
- ✅ Scans configured directories at regular intervals (default: 5 minutes)
- ✅ Supports multiple report directories (comma/semicolon separated)
- ✅ Detects when no files are present or files are older than threshold (24 hours)
- ✅ Calculates time since last file modification
- ✅ Logs scan results and file counts

#### 2. **Netcool Alert Service** (`NetcoolAlertService.java`)
- ✅ Sends alerts to Netcool via HTTP POST
- ✅ Uses FRS_0253 alert specification
- ✅ Builds JSON payload with alert details
- ✅ Handles HTTP errors and timeouts
- ✅ Includes connectivity testing

#### 3. **Configuration Management** (`FeederConfig.java`)
- ✅ Reads configuration from environment variables
- ✅ Supports multiple report directories
- ✅ Configurable scan interval (default: 5 minutes)
- ✅ Configurable alert threshold (default: 24 hours)
- ✅ Configurable Netcool URL and timeout
- ✅ Configuration validation

#### 4. **Application Structure** (`FeederApplication.java`)
- ✅ Proper application lifecycle management
- ✅ Shutdown hooks for graceful termination
- ✅ Service initialization and startup
- ✅ Error handling

#### 5. **Dependencies** (`pom.xml`)
- ✅ SLF4J/Logback for logging
- ✅ Gson for JSON processing
- ✅ Java 21 HTTP client (built-in)
- ✅ Maven Shade plugin for executable JAR

---

### ❌ What's Missing

#### 1. **Hazelcast Integration**
- ❌ No Hazelcast dependency in `pom.xml`
- ❌ No Hazelcast client/instance initialization
- ❌ No Hazelcast configuration
- ❌ No Hazelcast service/manager class

#### 2. **File Tracking in Hazelcast**
- ❌ Files are not stored in Hazelcast when discovered
- ❌ No file metadata tracking (name, path, modification time, etc.)
- ❌ No distributed cache for file information
- ❌ No mechanism to share file state across cluster nodes

#### 3. **Configuration in Habitat TOML**
- ❌ `feeder.monitoring` section is commented out in `default.toml`
- ❌ No Hazelcast cluster configuration
- ❌ No environment variable mappings for Habitat

#### 4. **File Metadata Model**
- ❌ No data class/model to represent file information
- ❌ No serialization support for Hazelcast storage

#### 5. **Integration Points**
- ❌ `ReportDirectoryMonitor` doesn't interact with Hazelcast
- ❌ File discovery doesn't trigger Hazelcast storage
- ❌ No Hazelcast-based deduplication logic

---

## Requirements Analysis

### Jira Requirement: F1d
**Title:** Feeder: BZB Rpt Monitoring: Report to Netcool when no files present

**Requirements:**
1. ✅ Add location of reports directory to environment TOML (supports multiple directories)
2. ✅ Every five minutes, scan the directories
3. ✅ If there have not been any files in 24 hours, send an alert to Netcool
4. ✅ Use FRS_0253

**Status:** ✅ **FULLY IMPLEMENTED** (except TOML configuration needs to be uncommented)

### Additional Requirement: Hazelcast Integration
**Requirement:** When files are found in directories, they should be added to Hazelcast

**Status:** ❌ **NOT IMPLEMENTED**

---

## Implementation Plan

### Phase 1: Add Hazelcast Dependencies and Configuration

#### 1.1 Update `pom.xml`
**File:** `feeder/pom.xml`

**Changes:**
- Add Hazelcast dependency (version 5.4.x or latest stable)
- Add Hazelcast client dependency if using client mode
- Ensure compatibility with Java 21

**Dependencies to Add:**
```xml
<dependency>
    <groupId>com.hazelcast</groupId>
    <artifactId>hazelcast</artifactId>
    <version>5.4.0</version>
</dependency>
```

#### 1.2 Create Hazelcast Configuration Class
**New File:** `feeder/src/main/java/com/mastercard/fraudriskscanner/feeder/config/HazelcastConfig.java`

**Purpose:**
- Initialize Hazelcast instance (embedded or client mode)
- Configure cluster settings
- Configure maps for file tracking
- Handle Hazelcast lifecycle

**Key Features:**
- Support for embedded mode (default) and client mode
- Configurable cluster name
- Configurable map names
- Connection timeout and retry logic
- Graceful shutdown handling

#### 1.3 Update `FeederConfig.java`
**File:** `feeder/src/main/java/com/mastercard/fraudriskscanner/feeder/config/FeederConfig.java`

**Changes:**
- Add Hazelcast configuration properties:
  - `hazelcast.cluster.name` (default: "fraud-risk-scanner-feeder")
  - `hazelcast.mode` (embedded/client, default: "embedded")
  - `hazelcast.cluster.members` (for client mode)
  - `hazelcast.map.name` (default: "feeder-file-tracker")
  - `hazelcast.connection.timeout.seconds` (default: 30)

---

### Phase 2: Create File Tracking Model

#### 2.1 Create File Metadata Model
**New File:** `feeder/src/main/java/com/mastercard/fraudriskscanner/feeder/model/TrackedFile.java`

**Purpose:**
- Represent file information to be stored in Hazelcast
- Serializable for Hazelcast storage
- Include all relevant file metadata

**Fields:**
- `fileName` (String) - Name of the file
- `filePath` (String) - Full path to the file
- `directoryPath` (String) - Directory where file was found
- `lastModifiedTime` (Instant) - File modification timestamp
- `fileSize` (long) - File size in bytes
- `discoveredAt` (Instant) - When file was first discovered
- `lastSeenAt` (Instant) - Last time file was seen during scan
- `fileHash` (String, optional) - SHA-256 hash of file name for deduplication

**Methods:**
- Getters and setters
- `equals()` and `hashCode()` based on file path
- `toString()` for logging
- Serialization support (implements `Serializable`)

---

### Phase 3: Create Hazelcast File Tracking Service

#### 3.1 Create File Tracking Service
**New File:** `feeder/src/main/java/com/mastercard/fraudriskscanner/feeder/hazelcast/FileTrackingService.java`

**Purpose:**
- Manage file tracking in Hazelcast
- Provide methods to add, update, and query files
- Handle distributed cache operations

**Key Methods:**
- `addFile(TrackedFile file)` - Add or update file in Hazelcast
- `getFile(String filePath)` - Retrieve file information
- `getAllFiles()` - Get all tracked files
- `getFilesInDirectory(String directoryPath)` - Get files for a specific directory
- `removeFile(String filePath)` - Remove file from tracking
- `fileExists(String filePath)` - Check if file is tracked
- `updateLastSeen(String filePath, Instant timestamp)` - Update last seen time

**Implementation Details:**
- Use Hazelcast `IMap<String, TrackedFile>` for storage
- Key: Full file path
- Value: `TrackedFile` object
- Thread-safe operations
- Handle Hazelcast exceptions gracefully

---

### Phase 4: Integrate File Tracking with Directory Monitor

#### 4.1 Update `ReportDirectoryMonitor.java`
**File:** `feeder/src/main/java/com/mastercard/fraudriskscanner/feeder/monitoring/ReportDirectoryMonitor.java`

**Changes:**
1. **Add FileTrackingService dependency:**
   - Inject `FileTrackingService` via constructor
   - Store as instance variable

2. **Modify `scanDirectory()` method:**
   - When files are found, create `TrackedFile` objects
   - Add/update files in Hazelcast via `FileTrackingService`
   - Track both new files and existing files (update last seen time)

3. **Enhanced file discovery:**
   - For each file found:
     - Create `TrackedFile` object with metadata
     - Check if file already exists in Hazelcast
     - If new: add to Hazelcast with `discoveredAt` timestamp
     - If existing: update `lastSeenAt` timestamp
     - Log file tracking operations

4. **Logging:**
   - Log when files are added to Hazelcast
   - Log when files are updated in Hazelcast
   - Include file count statistics

**Example Integration:**
```java
// In scanDirectory() method, after finding files:
for (File file : files) {
    if (file.isFile()) {
        TrackedFile trackedFile = new TrackedFile();
        trackedFile.setFileName(file.getName());
        trackedFile.setFilePath(file.getAbsolutePath());
        trackedFile.setDirectoryPath(dirPath);
        trackedFile.setLastModifiedTime(Files.getLastModifiedTime(file.toPath()).toInstant());
        trackedFile.setFileSize(file.length());
        trackedFile.setLastSeenAt(Instant.now());
        
        // Add or update in Hazelcast
        fileTrackingService.addFile(trackedFile);
    }
}
```

---

### Phase 5: Update Application Initialization

#### 5.1 Update `FeederApplication.java`
**File:** `feeder/src/main/java/com/mastercard/fraudriskscanner/feeder/FeederApplication.java`

**Changes:**
1. **Initialize Hazelcast:**
   - Create `HazelcastConfig` instance
   - Initialize Hazelcast instance
   - Create `FileTrackingService` with Hazelcast instance

2. **Wire dependencies:**
   - Pass `FileTrackingService` to `ReportDirectoryMonitor`
   - Ensure proper initialization order

3. **Shutdown handling:**
   - Shutdown Hazelcast instance gracefully in shutdown hook
   - Ensure all services are stopped in correct order

**Initialization Order:**
1. Load `FeederConfig`
2. Initialize Hazelcast (via `HazelcastConfig`)
3. Create `FileTrackingService`
4. Create `NetcoolAlertService`
5. Create `ReportDirectoryMonitor` (with `FileTrackingService`)
6. Start monitoring

---

### Phase 6: Update Habitat Configuration

#### 6.1 Update `default.toml`
**File:** `habitat/default.toml`

**Changes:**
1. **Uncomment and configure `feeder.monitoring` section:**
```toml
[feeder.monitoring]
report_directories = [
  "/home/bizopsbank/reports",
  "/home/bizopsbank/archive/reports"
]
scan_interval_minutes = 5
alert_threshold_hours = 24
netcool_url = "https://netcool.mastercard.int/api/alerts"
netcool_timeout_seconds = 30
```

2. **Add Hazelcast configuration section:**
```toml
[feeder.hazelcast]
cluster_name = "fraud-risk-scanner-feeder"
mode = "embedded"  # or "client"
# For client mode, uncomment and configure:
# cluster_members = [
#   "server1:5701",
#   "server2:5701"
# ]
map_name = "feeder-file-tracker"
connection_timeout_seconds = 30
```

3. **Map environment variables:**
   - Ensure Habitat maps TOML values to environment variables
   - Update `FeederConfig` to read from these environment variables

---

### Phase 7: Testing and Validation

#### 7.1 Unit Tests
**New Files:**
- `FileTrackingServiceTest.java`
- `TrackedFileTest.java`
- `HazelcastConfigTest.java`
- Update `ReportDirectoryMonitorTest.java` to include Hazelcast integration

**Test Scenarios:**
- Add file to Hazelcast
- Update existing file in Hazelcast
- Retrieve files from Hazelcast
- Query files by directory
- Handle Hazelcast connection failures
- Test concurrent access

#### 7.2 Integration Tests
**Test Scenarios:**
- End-to-end file discovery and tracking
- Multiple directory monitoring with Hazelcast
- Cluster scenario (if applicable)
- Hazelcast persistence and recovery

#### 7.3 Manual Testing
- Start application with Hazelcast
- Monitor directories with files
- Verify files are added to Hazelcast
- Check Hazelcast management center (if available)
- Verify alerting still works correctly

---

## Implementation Checklist

### Dependencies and Configuration
- [ ] Add Hazelcast dependency to `pom.xml`
- [ ] Create `HazelcastConfig.java`
- [ ] Update `FeederConfig.java` with Hazelcast properties
- [ ] Update `default.toml` with feeder.monitoring and hazelcast sections

### Data Model
- [ ] Create `TrackedFile.java` model class
- [ ] Ensure proper serialization support
- [ ] Add equals/hashCode/toString methods

### Services
- [ ] Create `FileTrackingService.java`
- [ ] Implement all file tracking methods
- [ ] Add error handling and logging

### Integration
- [ ] Update `ReportDirectoryMonitor.java` to use `FileTrackingService`
- [ ] Add file tracking logic in `scanDirectory()` method
- [ ] Update `FeederApplication.java` to initialize Hazelcast
- [ ] Wire all dependencies correctly
- [ ] Add proper shutdown handling

### Testing
- [ ] Write unit tests for `FileTrackingService`
- [ ] Write unit tests for `TrackedFile`
- [ ] Update existing tests for `ReportDirectoryMonitor`
- [ ] Write integration tests
- [ ] Manual testing in development environment

### Documentation
- [ ] Update README with Hazelcast setup instructions
- [ ] Document configuration options
- [ ] Add code comments for complex logic

---

## Architecture Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                    FeederApplication                        │
│                                                             │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐ │
│  │ FeederConfig │───▶│HazelcastConfig│───▶│Hazelcast     │ │
│  └──────────────┘    └──────────────┘    │Instance      │ │
│                                           └──────┬───────┘ │
│                                                  │         │
│  ┌──────────────────────────────────────────────┼───────┐ │
│  │         FileTrackingService                   │       │ │
│  │  (Uses Hazelcast IMap<String, TrackedFile>)  │       │ │
│  └──────────────────────────────────────────────┼───────┘ │
│                                                  │         │
│  ┌──────────────────────────────────────────────┼───────┐ │
│  │      ReportDirectoryMonitor                   │       │ │
│  │  ┌────────────────────────────────────────┐  │       │ │
│  │  │ scanDirectory()                        │  │       │ │
│  │  │  - Find files                          │  │       │ │
│  │  │  - Create TrackedFile objects          │──┼───────┼─┤
│  │  │  - Add/Update in Hazelcast             │  │       │ │
│  │  └────────────────────────────────────────┘  │       │ │
│  └──────────────────────────────────────────────┘       │ │
│                                                          │ │
│  ┌────────────────────────────────────────────────────┐ │
│  │         NetcoolAlertService                        │ │
│  │  (Sends alerts when no files found)                │ │
│  └────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────┘
                          │
                          ▼
              ┌───────────────────────┐
              │   Hazelcast Cluster   │
              │                       │
              │  IMap:                │
              │  "feeder-file-tracker"│
              │  Key: file path       │
              │  Value: TrackedFile   │
              └───────────────────────┘
```

---

## Configuration Example

### Environment Variables (via Habitat)
```bash
FEEDER_REPORT_DIRECTORIES=/home/bizopsbank/reports,/home/bizopsbank/archive/reports
FEEDER_SCAN_INTERVAL_MINUTES=5
FEEDER_ALERT_THRESHOLD_HOURS=24
FEEDER_NETCOOL_URL=https://netcool.mastercard.int/api/alerts
FEEDER_NETCOOL_TIMEOUT_SECONDS=30
FEEDER_HAZELCAST_CLUSTER_NAME=fraud-risk-scanner-feeder
FEEDER_HAZELCAST_MODE=embedded
FEEDER_HAZELCAST_MAP_NAME=feeder-file-tracker
FEEDER_HAZELCAST_CONNECTION_TIMEOUT_SECONDS=30
```

### TOML Configuration
```toml
[feeder.monitoring]
report_directories = [
  "/home/bizopsbank/reports",
  "/home/bizopsbank/archive/reports"
]
scan_interval_minutes = 5
alert_threshold_hours = 24
netcool_url = "https://netcool.mastercard.int/api/alerts"
netcool_timeout_seconds = 30

[feeder.hazelcast]
cluster_name = "fraud-risk-scanner-feeder"
mode = "embedded"
map_name = "feeder-file-tracker"
connection_timeout_seconds = 30
```

---

## Risk and Considerations

### Risks
1. **Hazelcast Connection Failures:**
   - **Mitigation:** Implement retry logic and fallback behavior
   - **Impact:** File tracking may fail, but monitoring continues

2. **Performance Impact:**
   - **Mitigation:** Use async operations where possible
   - **Impact:** Minimal - file operations are infrequent (every 5 minutes)

3. **Memory Usage:**
   - **Mitigation:** Implement TTL (Time To Live) for tracked files
   - **Impact:** Old files should be cleaned up automatically

4. **Cluster Configuration:**
   - **Mitigation:** Support both embedded and client modes
   - **Impact:** Flexible deployment options

### Considerations
1. **File Deduplication:**
   - Consider using file hash (SHA-256 of filename) as additional key
   - Prevent duplicate entries for same file

2. **TTL for Tracked Files:**
   - Set expiration time for files (e.g., 7 days)
   - Automatically clean up old entries

3. **Monitoring and Metrics:**
   - Track number of files in Hazelcast
   - Monitor Hazelcast map size
   - Alert on Hazelcast connection issues

4. **Backward Compatibility:**
   - Ensure existing monitoring functionality continues to work
   - Hazelcast integration should be optional (graceful degradation)

---

## Success Criteria

### Functional Requirements
- ✅ Files discovered during directory scan are added to Hazelcast
- ✅ File metadata (name, path, timestamps) is stored correctly
- ✅ Existing files are updated (last seen time) on subsequent scans
- ✅ Multiple directories are supported
- ✅ Alerting to Netcool continues to work (FRS_0253)
- ✅ Configuration via TOML/environment variables works

### Non-Functional Requirements
- ✅ Hazelcast operations don't block directory scanning
- ✅ Graceful handling of Hazelcast connection failures
- ✅ Proper logging of all file tracking operations
- ✅ Thread-safe operations
- ✅ Clean shutdown of Hazelcast instance

### Testing Requirements
- ✅ Unit tests for all new components
- ✅ Integration tests for file tracking flow
- ✅ Manual testing in development environment
- ✅ Verification of Hazelcast data persistence

---

## Timeline Estimate

| Phase | Tasks | Estimated Time |
|-------|-------|----------------|
| Phase 1 | Dependencies and Configuration | 2-3 hours |
| Phase 2 | File Tracking Model | 1-2 hours |
| Phase 3 | File Tracking Service | 3-4 hours |
| Phase 4 | Directory Monitor Integration | 2-3 hours |
| Phase 5 | Application Initialization | 1-2 hours |
| Phase 6 | Habitat Configuration | 1 hour |
| Phase 7 | Testing and Validation | 4-6 hours |
| **Total** | | **14-21 hours** |

---

## Next Steps

1. **Review and Approve Plan**
   - Review this plan with the team
   - Get approval for implementation approach

2. **Start Implementation**
   - Begin with Phase 1 (Dependencies)
   - Follow the checklist sequentially

3. **Iterative Development**
   - Implement and test each phase
   - Get feedback before moving to next phase

4. **Documentation**
   - Update code comments
   - Update README
   - Document configuration options

---

## References

- Jira Ticket: F1d - Feeder: BZB Rpt Monitoring: Report to Netcool when no files present
- Hazelcast Documentation: https://docs.hazelcast.com/
- Confluence Documentation: `CONFLUENCE_DOCUMENTATION.md`
- Gap Analysis: `GAP_ANALYSIS.md`
- Implementation Status: `IMPLEMENTATION_STATUS.md`

---

**Document Version:** 1.0  
**Last Updated:** 2025-01-02  
**Author:** Development Team

