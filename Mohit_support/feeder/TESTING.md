# Testing Directory Scanning Locally

## Quick Start

### 1. Create Test Directory

Create a test directory with some files:

```powershell
# Windows PowerShell
mkdir test-reports
echo "test content" > test-reports\report1.txt
echo "test content" > test-reports\report2.csv
echo "test content" > test-reports\report3.dat
```

### 2. Run the Application

**Option A: Using environment variable**
```powershell
$env:FEEDER_SOURCE_DIRECTORIES = ".\test-reports"
mvn exec:java
```

**Option B: Using system property**
```powershell
mvn exec:java -Dexec.args="-DFEEDER_SOURCE_DIRECTORIES=.\test-reports"
```

**Option C: Set in code (for quick testing)**
The default configuration uses `./test-reports` if no environment variable is set.

### 3. Verify It Works

You should see logs like:
```
FRS_0400 Feeder configuration loaded:
FRS_0400   Source directories: [./test-reports]
FRS_0400   Scan interval: 2 minutes
FRS_0430 Starting directory scan for 1 directory(ies)
FRS_0433 Found 3 file(s) in directory: ./test-reports
FRS_0431 Directory scan completed. Total files found: 3
FRS_0443 Scan cycle completed. Found 3 file(s)
```

### 4. Test Multiple Directories

```powershell
$env:FEEDER_SOURCE_DIRECTORIES = ".\test-reports,.\test-reports2"
mvn exec:java
```

### 5. Test Different Scan Intervals

```powershell
$env:FEEDER_SCAN_INTERVAL_MINUTES = "1"
$env:FEEDER_SOURCE_DIRECTORIES = ".\test-reports"
mvn exec:java
```

## Running Unit Tests

```powershell
mvn test
```

The unit tests use `@TempDir` to create temporary directories automatically, so no setup needed.

## Package Structure

The code is organized by functionality (not phases):

```
com.mastercard.fraudriskscanner.feeder/
├── config/              # Configuration management
│   └── FeederConfig.java
├── scanning/            # Directory scanning
│   ├── DirectoryScanner.java
│   └── ScheduledDirectoryScanner.java
├── hazelcast/          # Hazelcast integration
│   └── HazelcastProvider.java
└── FeederApplication.java
```

## What's Implemented

✅ **Phase 1: Configuration**
- `FeederConfig` - Reads from environment variables
- Supports multiple source directories
- Configurable scan interval

✅ **Phase 3: Directory Scanning**
- `DirectoryScanner` - Scans directories for files
- Filters out hidden files, temp files, directories
- `ScheduledDirectoryScanner` - Periodic scanning
- Runs initial scan immediately, then every N minutes

✅ **Integration**
- `FeederApplication` - Wires everything together
- Graceful shutdown handling

## Next Steps

The directory scanning is complete and ready for testing. Next phases:
- Phase 2: Expiring Semaphore Logic (file fingerprinting)
- Phase 4: File Processing (scanner invocation, file movement)

