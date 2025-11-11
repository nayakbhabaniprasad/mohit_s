# Feeder Implementation Plan - Ticket F1b

## Overview

**Ticket:** #F1b: Feeder: Scan NGFT directory for files and add to Hazelcast database

**Objective:** Implement the Feeder component that:
1. Scans NGFT directories every couple of minutes for report files
2. Adds found files to Hazelcast database using Expiring Semaphore Logic
3. Ensures single processing across cluster nodes
4. Manages file lifecycle (process/skip/error)

**Reference:** [Feeder Component Documentation](https://confluence.mastercard.int/spaces/bzb/pages/1755215339/Feeder+Component)

---

## Current State

### ✅ Already Implemented
- ✅ Hazelcast database integration (`HazelcastProvider.java`)
- ✅ Basic application structure (`FeederApplication.java`)
- ✅ Logging with FRS message IDs
- ✅ SonarQube-compliant resource handling

### ❌ To Be Implemented
- ❌ Directory scanning mechanism
- ❌ File fingerprint calculation (SHA-256 based)
- ❌ Expiring Semaphore Logic
- ❌ Atomic check-and-set operations in Hazelcast
- ❌ File lifecycle management
- ❌ Scanner invocation via ProcessBuilder
- ❌ Configuration management (TOML)
- ❌ File movement operations

---

## Implementation Phases

### Phase 1: Foundation - Configuration and Models
**Goal:** Set up configuration management and data models

#### Task 1.1: Create Configuration Class
**File:** `src/main/java/com/mastercard/fraudriskscanner/feeder/config/FeederConfig.java`

**Requirements:**
- Read configuration from environment variables (Habitat-compatible)
- Support multiple NGFT source directories
- Configurable scan interval (default: 2 minutes)
- Source directories for reports
- Destination directories: success, error, skip
- Scanner executable path/registry
- Hazelcast map name configuration

**Configuration Properties:**
```java
- List<String> sourceDirectories
- int scanIntervalMinutes (default: 2)
- String successDirectory
- String errorDirectory
- String skipDirectory
- String scannerExecutablePath
- String hazelcastMapName (default: "feeder-file-semaphore")
```

**Best Practices:**
- ✅ Validate all required properties
- ✅ Provide sensible defaults
- ✅ Log configuration on startup with FRS message ID
- ✅ Use environment variable fallback pattern

**FRS Message IDs:**
- `FRS_0400` - Configuration loaded successfully
- `FRS_0401` - Configuration validation failed

---

#### Task 1.2: Create File Fingerprint Model
**File:** `src/main/java/com/mastercard/fraudriskscanner/feeder/model/FileFingerprint.java`

**Requirements:**
- Immutable class representing file fingerprint
- Calculate SHA-256 hash of filename
- Extract map key (bytes 0-3, bounded by 2^16)
- Extract fingerprint (bytes 23-31)
- Serializable for Hazelcast storage

**Methods:**
```java
public static FileFingerprint fromFileName(String fileName)
public int getMapKey()  // bytes 0-3 & 0xFFFF
public byte[] getFingerprint()  // bytes 23-31
public String getFileName()
public boolean equals(Object o)
public int hashCode()
```

**Best Practices:**
- ✅ Immutable class (final, private final fields)
- ✅ Thread-safe
- ✅ Proper equals/hashCode implementation
- ✅ Input validation (null checks)
- ✅ Unit tests for hash calculation correctness

**FRS Message IDs:**
- `FRS_0410` - File fingerprint calculated
- `FRS_0411` - File fingerprint calculation failed

---

#### Task 1.3: Create File Metadata Model
**File:** `src/main/java/com/mastercard/fraudriskscanner/feeder/model/FileMetadata.java`

**Requirements:**
- Store file information (path, size, modification time)
- Serializable for Hazelcast
- Include fingerprint information

**Fields:**
```java
- String filePath
- String fileName
- long fileSize
- Instant lastModified
- FileFingerprint fingerprint
- Instant discoveredAt
```

**Best Practices:**
- ✅ Immutable or builder pattern
- ✅ Proper serialization
- ✅ Validation of file metadata

---

### Phase 2: Expiring Semaphore Logic
**Goal:** Implement the core semaphore mechanism for single processing

#### Task 2.1: Create Semaphore Manager Service
**File:** `src/main/java/com/mastercard/fraudriskscanner/feeder/semaphore/HazelcastSemaphoreManager.java`

**Requirements:**
- Manage the bounded fingerprint map in Hazelcast
- Atomic check-and-set operations
- Determine if file should be processed or skipped

**Key Methods:**
```java
public boolean shouldProcessFile(FileFingerprint fingerprint)
public void markFileAsProcessed(FileFingerprint fingerprint)
public boolean isFileProcessed(FileFingerprint fingerprint)
```

**Implementation Details:**
- Use Hazelcast `IMap<Integer, byte[]>` where:
  - Key: Map key from fingerprint (0-65535)
  - Value: Fingerprint bytes (23-31)
- Atomic operations using `IMap.putIfAbsent()` or `IMap.replace()`
- Check: `key exists AND fingerprint matches` → skip
- Otherwise: add fingerprint → process

**Best Practices:**
- ✅ Thread-safe operations
- ✅ Proper exception handling (don't swallow)
- ✅ Log all operations with FRS message IDs
- ✅ Handle Hazelcast connection failures gracefully
- ✅ Use Hazelcast transactions for atomicity

**FRS Message IDs:**
- `FRS_0420` - File semaphore check performed
- `FRS_0421` - File marked as processed
- `FRS_0422` - Semaphore operation failed
- `FRS_0423` - File already processed (skip)

**Algorithm:**
```java
1. Calculate FileFingerprint from fileName
2. Get map key: fingerprint.getMapKey()
3. Get fingerprint bytes: fingerprint.getFingerprint()
4. Atomic check in Hazelcast IMap:
   - If map.containsKey(key) AND map.get(key).equals(fingerprint):
     → return false (skip file)
   - Else:
     → map.put(key, fingerprint) (atomic)
     → return true (process file)
```

---

#### Task 2.2: Unit Tests for Semaphore Logic
**File:** `src/test/java/com/mastercard/fraudriskscanner/feeder/semaphore/HazelcastSemaphoreManagerTest.java`

**Test Cases:**
- ✅ New file should be processed
- ✅ Same file should be skipped on second check
- ✅ Different files with same map key (collision) should both be processed
- ✅ Atomic operations work correctly under concurrency
- ✅ Exception handling when Hazelcast unavailable

---

### Phase 3: Directory Scanning
**Goal:** Implement periodic directory scanning

#### Task 3.1: Create Directory Scanner Service
**File:** `src/main/java/com/mastercard/fraudriskscanner/feeder/scanning/DirectoryScanner.java`

**Requirements:**
- Scan configured directories periodically
- Find all files in directories
- Filter files (e.g., ignore hidden files, specific extensions)
- Return list of candidate files

**Key Methods:**
```java
public List<File> scanDirectories()
public List<File> scanDirectory(String directoryPath)
private boolean isCandidateFile(File file)
```

**Best Practices:**
- ✅ Use `Files.walk()` or `Files.list()` with try-with-resources
- ✅ Handle directory access errors gracefully
- ✅ Log scan operations with FRS message IDs
- ✅ Filter out directories, hidden files, temp files
- ✅ Validate directory exists and is readable

**FRS Message IDs:**
- `FRS_0430` - Directory scan started
- `FRS_0431` - Directory scan completed
- `FRS_0432` - Directory scan failed
- `FRS_0433` - Files found in directory

---

#### Task 3.2: Create Scheduled Scanner Service
**File:** `src/main/java/com/mastercard/fraudriskscanner/feeder/scanning/ScheduledDirectoryScanner.java`

**Requirements:**
- Schedule periodic directory scans
- Integrate with semaphore manager
- Trigger file processing workflow

**Key Methods:**
```java
public void start()
public void stop()
private void performScan()
```

**Best Practices:**
- ✅ Use `ScheduledExecutorService` for periodic tasks
- ✅ Proper shutdown handling
- ✅ Handle exceptions in scheduled tasks
- ✅ Configurable scan interval
- ✅ Run initial scan on startup

**FRS Message IDs:**
- `FRS_0440` - Scheduled scanner started
- `FRS_0441` - Scheduled scanner stopped
- `FRS_0442` - Scan cycle started
- `FRS_0443` - Scan cycle completed

---

### Phase 4: File Processing Workflow
**Goal:** Implement file processing and lifecycle management

#### Task 4.1: Create File Processor Service
**File:** `src/main/java/com/mastercard/fraudriskscanner/feeder/processing/FileProcessor.java`

**Requirements:**
- Process individual files through the workflow
- Check semaphore
- Invoke scanner if needed
- Move files to appropriate directories

**Key Methods:**
```java
public ProcessingResult processFile(File file)
private ProcessingResult processNewFile(File file)
private ProcessingResult skipFile(File file)
```

**ProcessingResult Enum:**
```java
PROCESSED, SKIPPED, ERROR
```

**Best Practices:**
- ✅ Single responsibility (one file at a time)
- ✅ Proper exception handling
- ✅ Log each step with FRS message IDs
- ✅ Idempotent operations

**FRS Message IDs:**
- `FRS_0450` - File processing started
- `FRS_0451` - File processing completed
- `FRS_0452` - File processing failed
- `FRS_0453` - File skipped (already processed)

---

#### Task 4.2: Create Scanner Invoker
**File:** `src/main/java/com/mastercard/fraudriskscanner/feeder/processing/ScannerInvoker.java`

**Requirements:**
- Invoke Scanner component via ProcessBuilder
- Handle exit codes (0 = success, 3 = error)
- Capture stdout/stderr for logging
- Timeout handling

**Key Methods:**
```java
public int invokeScanner(File reportFile)
private ProcessBuilder createProcessBuilder(File reportFile)
```

**Best Practices:**
- ✅ Use ProcessBuilder (not Runtime.exec)
- ✅ Redirect stdout/stderr for logging
- ✅ Set timeout for process execution
- ✅ Handle process failures gracefully
- ✅ Validate scanner executable exists

**FRS Message IDs:**
- `FRS_0460` - Scanner invocation started
- `FRS_0461` - Scanner invocation completed (exit code 0)
- `FRS_0462` - Scanner invocation failed (exit code 3)
- `FRS_0463` - Scanner invocation error (exception/timeout)

**ProcessBuilder Setup:**
```java
ProcessBuilder pb = new ProcessBuilder(
    config.getScannerExecutablePath(),
    reportFile.getAbsolutePath()
);
pb.directory(new File(config.getWorkingDirectory()));
pb.redirectErrorStream(true);
Process process = pb.start();
int exitCode = process.waitFor(timeout, TimeUnit.SECONDS);
```

---

#### Task 4.3: Create File Lifecycle Manager
**File:** `src/main/java/com/mastercard/fraudriskscanner/feeder/processing/FileLifecycleManager.java`

**Requirements:**
- Move files to success/error/skip directories
- Create destination directories if needed
- Atomic file moves (if possible)
- Handle file move failures

**Key Methods:**
```java
public void moveToSuccess(File file)
public void moveToError(File file)
public void moveToSkip(File file)
private void moveFile(File source, File destination)
```

**Best Practices:**
- ✅ Use `Files.move()` for atomic operations
- ✅ Create destination directories if missing
- ✅ Handle file system errors
- ✅ Preserve file permissions
- ✅ Log all file movements

**FRS Message IDs:**
- `FRS_0470` - File moved to success directory
- `FRS_0471` - File moved to error directory
- `FRS_0472` - File moved to skip directory
- `FRS_0473` - File move operation failed

---

### Phase 5: Integration and Orchestration
**Goal:** Wire everything together

#### Task 5.1: Update FeederApplication
**File:** `src/main/java/com/mastercard/fraudriskscanner/feeder/FeederApplication.java`

**Requirements:**
- Initialize all services
- Start scheduled scanner
- Handle graceful shutdown
- Wire dependencies

**Initialization Order:**
1. Load configuration
2. Initialize Hazelcast
3. Create semaphore manager
4. Create file processor
5. Create scheduled scanner
6. Start scanner
7. Register shutdown hook

**Best Practices:**
- ✅ Dependency injection pattern
- ✅ Proper initialization order
- ✅ Graceful shutdown of all services
- ✅ Error handling during startup

**FRS Message IDs:**
- `FRS_0200` - Feeder application started
- `FRS_0201` - Feeder application running
- `FRS_0202` - Feeder application shutdown initiated
- `FRS_0203` - Feeder application startup failed

---

#### Task 5.2: Create Main Processing Service
**File:** `src/main/java/com/mastercard/fraudriskscanner/feeder/service/FeederService.java`

**Requirements:**
- Orchestrate the entire workflow
- Coordinate scanning, semaphore checks, and processing
- Handle errors and retries

**Key Methods:**
```java
public void processScanCycle()
private void processFile(File file)
```

**Workflow:**
```
1. Scan directories → get candidate files
2. For each file:
   a. Calculate fingerprint
   b. Check semaphore (should process?)
   c. If yes: process file → invoke scanner → move to success/error
   d. If no: move to skip
3. Log cycle completion
```

**Best Practices:**
- ✅ Single responsibility
- ✅ Error isolation (one file failure doesn't stop others)
- ✅ Comprehensive logging
- ✅ Metrics/statistics tracking

---

### Phase 6: Configuration and Deployment
**Goal:** Complete configuration setup

#### Task 6.1: Update Habitat Configuration
**File:** `habitat/default.toml`

**Add Configuration:**
```toml
[feeder]
# Source directories (comma-separated)
source_directories = [
  "/home/bizopsbank/ngft/reports",
  "/home/bizopsbank/ngft/archive"
]

# Scan interval in minutes
scan_interval_minutes = 2

# Destination directories
success_directory = "/home/bizopsbank/frscanner/successful"
error_directory = "/home/bizopsbank/frscanner/error"
skip_directory = "/home/bizopsbank/frscanner/skip"

# Scanner configuration
scanner_executable_path = "/opt/scanner/scanner.jar"
scanner_timeout_seconds = 300

# Hazelcast configuration
hazelcast_map_name = "feeder-file-semaphore"
```

---

#### Task 6.2: Environment Variable Mapping
**Documentation:** Update README with environment variables

**Environment Variables:**
- `FEEDER_SOURCE_DIRECTORIES` - Comma-separated list
- `FEEDER_SCAN_INTERVAL_MINUTES` - Default: 2
- `FEEDER_SUCCESS_DIRECTORY`
- `FEEDER_ERROR_DIRECTORY`
- `FEEDER_SKIP_DIRECTORY`
- `FEEDER_SCANNER_EXECUTABLE_PATH`
- `FEEDER_SCANNER_TIMEOUT_SECONDS`
- `FEEDER_HAZELCAST_MAP_NAME`

---

### Phase 7: Testing
**Goal:** Comprehensive testing

#### Task 7.1: Unit Tests
**Files:**
- `FileFingerprintTest.java` - Test hash calculation
- `HazelcastSemaphoreManagerTest.java` - Test semaphore logic
- `DirectoryScannerTest.java` - Test directory scanning
- `FileProcessorTest.java` - Test file processing
- `ScannerInvokerTest.java` - Test scanner invocation
- `FileLifecycleManagerTest.java` - Test file movements

**Coverage Goals:**
- ✅ Minimum 80% code coverage
- ✅ All edge cases covered
- ✅ Exception scenarios tested

---

#### Task 7.2: Integration Tests
**File:** `src/test/java/com/mastercard/fraudriskscanner/feeder/integration/FeederIntegrationTest.java`

**Test Scenarios:**
- ✅ End-to-end file processing workflow
- ✅ Multiple files in directory
- ✅ File collision (same map key, different fingerprints)
- ✅ Scanner invocation with different exit codes
- ✅ File movement operations
- ✅ Hazelcast cluster scenario (if applicable)

---

#### Task 7.3: Manual Testing Checklist
- [ ] Application starts successfully
- [ ] Hazelcast connects properly
- [ ] Directories are scanned periodically
- [ ] Files are added to Hazelcast
- [ ] Duplicate files are skipped
- [ ] Scanner is invoked correctly
- [ ] Files are moved to correct directories
- [ ] Configuration is read correctly
- [ ] Shutdown is graceful
- [ ] Logs contain proper FRS message IDs

---

## Implementation Checklist

### Phase 1: Foundation
- [ ] Task 1.1: Create FeederConfig class
- [ ] Task 1.2: Create FileFingerprint model
- [ ] Task 1.3: Create FileMetadata model

### Phase 2: Semaphore Logic
- [ ] Task 2.1: Create HazelcastSemaphoreManager
- [ ] Task 2.2: Unit tests for semaphore logic

### Phase 3: Directory Scanning
- [ ] Task 3.1: Create DirectoryScanner
- [ ] Task 3.2: Create ScheduledDirectoryScanner

### Phase 4: File Processing
- [ ] Task 4.1: Create FileProcessor
- [ ] Task 4.2: Create ScannerInvoker
- [ ] Task 4.3: Create FileLifecycleManager

### Phase 5: Integration
- [ ] Task 5.1: Update FeederApplication
- [ ] Task 5.2: Create FeederService

### Phase 6: Configuration
- [ ] Task 6.1: Update Habitat configuration
- [ ] Task 6.2: Document environment variables

### Phase 7: Testing
- [ ] Task 7.1: Unit tests (all components)
- [ ] Task 7.2: Integration tests
- [ ] Task 7.3: Manual testing

---

## Best Practices and Standards

### SonarQube Compliance
- ✅ Use try-with-resources for all streams
- ✅ Validate resources before use
- ✅ Don't swallow exceptions (re-throw or handle properly)
- ✅ Use constants for magic strings
- ✅ Proper null checks
- ✅ Thread-safe implementations
- ✅ Avoid code duplication

### Exception Handling
- ✅ Never swallow exceptions silently
- ✅ Log with FRS message IDs before re-throwing
- ✅ Use specific exception types
- ✅ Provide meaningful error messages

### Logging Standards
- ✅ All log messages must have FRS_XXXX format
- ✅ Use appropriate log levels (INFO, WARN, ERROR)
- ✅ Include context in log messages
- ✅ Log actionable information only

### Code Quality
- ✅ Follow Java naming conventions
- ✅ Use final where possible
- ✅ Prefer immutable objects
- ✅ Single responsibility principle
- ✅ Proper Javadoc comments
- ✅ Unit tests for all public methods

### Resource Management
- ✅ Close all resources (try-with-resources)
- ✅ Validate resources exist before use
- ✅ Handle resource cleanup in finally blocks
- ✅ Proper shutdown hooks

---

## FRS Message ID Registry

### Application Lifecycle
- `FRS_0200` - Application started
- `FRS_0201` - Application running
- `FRS_0202` - Application shutdown
- `FRS_0203` - Application startup failed

### Configuration
- `FRS_0400` - Configuration loaded
- `FRS_0401` - Configuration validation failed

### File Fingerprinting
- `FRS_0410` - File fingerprint calculated
- `FRS_0411` - File fingerprint calculation failed

### Semaphore Operations
- `FRS_0420` - File semaphore check performed
- `FRS_0421` - File marked as processed
- `FRS_0422` - Semaphore operation failed
- `FRS_0423` - File already processed (skip)

### Directory Scanning
- `FRS_0430` - Directory scan started
- `FRS_0431` - Directory scan completed
- `FRS_0432` - Directory scan failed
- `FRS_0433` - Files found in directory

### Scheduled Scanning
- `FRS_0440` - Scheduled scanner started
- `FRS_0441` - Scheduled scanner stopped
- `FRS_0442` - Scan cycle started
- `FRS_0443` - Scan cycle completed

### File Processing
- `FRS_0450` - File processing started
- `FRS_0451` - File processing completed
- `FRS_0452` - File processing failed
- `FRS_0453` - File skipped (already processed)

### Scanner Invocation
- `FRS_0460` - Scanner invocation started
- `FRS_0461` - Scanner invocation completed (exit 0)
- `FRS_0462` - Scanner invocation failed (exit 3)
- `FRS_0463` - Scanner invocation error

### File Lifecycle
- `FRS_0470` - File moved to success
- `FRS_0471` - File moved to error
- `FRS_0472` - File moved to skip
- `FRS_0473` - File move operation failed

### Hazelcast (Already Defined)
- `FRS_0450` - Hazelcast started
- `FRS_0451` - Hazelcast startup failed
- `FRS_0452` - Hazelcast shutdown

---

## Architecture Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                    FeederApplication                        │
│                                                             │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐ │
│  │ FeederConfig │───▶│Hazelcast     │───▶│Hazelcast     │ │
│  │              │    │Provider      │    │Instance      │ │
│  └──────────────┘    └──────────────┘    └──────┬───────┘ │
│                                                   │         │
│  ┌───────────────────────────────────────────────┼───────┐ │
│  │      ScheduledDirectoryScanner                 │       │ │
│  │  ┌──────────────────────────────────────────┐ │       │ │
│  │  │ DirectoryScanner                         │ │       │ │
│  │  │  - scanDirectories()                     │ │       │ │
│  │  └──────────────┬───────────────────────────┘ │       │ │
│  └─────────────────┼─────────────────────────────┘       │ │
│                    │                                       │ │
│  ┌─────────────────┼─────────────────────────────────────┐ │
│  │      FeederService                                    │ │
│  │  ┌─────────────────────────────────────────────────┐ │ │
│  │  │ FileProcessor                                   │ │ │
│  │  │  ┌──────────────────────────────────────────┐  │ │ │
│  │  │  │ HazelcastSemaphoreManager                │──┼─┼─┼─┐
│  │  │  │  - shouldProcessFile()                   │  │ │ │ │
│  │  │  └──────────────────────────────────────────┘  │ │ │ │
│  │  │  ┌──────────────────────────────────────────┐  │ │ │ │
│  │  │  │ ScannerInvoker                           │  │ │ │ │
│  │  │  │  - invokeScanner()                       │  │ │ │ │
│  │  │  └──────────────────────────────────────────┘  │ │ │ │
│  │  │  ┌──────────────────────────────────────────┐  │ │ │ │
│  │  │  │ FileLifecycleManager                     │  │ │ │ │
│  │  │  │  - moveToSuccess/Error/Skip()            │  │ │ │ │
│  │  │  └──────────────────────────────────────────┘  │ │ │ │
│  │  └─────────────────────────────────────────────────┘ │ │
│  └───────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
                          │
                          ▼
              ┌───────────────────────┐
              │   Hazelcast Cluster   │
              │                       │
              │  IMap<Integer, byte[]>│
              │  Key: Map Key (0-65535)│
              │  Value: Fingerprint   │
              │  (bytes 23-31)        │
              └───────────────────────┘
```

---

## Timeline Estimate

| Phase | Tasks | Estimated Time |
|-------|-------|----------------|
| Phase 1 | Configuration and Models | 4-6 hours |
| Phase 2 | Semaphore Logic | 6-8 hours |
| Phase 3 | Directory Scanning | 4-6 hours |
| Phase 4 | File Processing | 8-10 hours |
| Phase 5 | Integration | 4-6 hours |
| Phase 6 | Configuration | 2-3 hours |
| Phase 7 | Testing | 8-12 hours |
| **Total** | | **36-51 hours** |

---

## Next Steps

1. **Start with Phase 1, Task 1.1** - Create FeederConfig
2. **Implement one task at a time** - Complete and test before moving on
3. **Follow the checklist** - Mark tasks as complete
4. **Run SonarQube analysis** - After each phase
5. **Write tests** - Alongside implementation (TDD approach)

---

## References

- Ticket: #F1b - Feeder: Scan NGFT directory for files and add to Hazelcast database
- Confluence: [Feeder Component](https://confluence.mastercard.int/spaces/bzb/pages/1755215339/Feeder+Component)
- Hazelcast Documentation: https://docs.hazelcast.com/
- SonarQube Java Rules: https://rules.sonarsource.com/java/

---

**Document Version:** 2.0  
**Last Updated:** 2025-01-02  
**Author:** Development Team
