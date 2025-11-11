# How the Feeder Application Runs Every 2 Minutes

## Overview for Beginners

This document explains how the Feeder application automatically scans directories every 2 minutes (or any configured interval). It's written for beginners, so we'll explain each concept step by step.

---

## Table of Contents

1. [The Big Picture](#the-big-picture)
2. [Key Components](#key-components)
3. [Code Walkthrough](#code-walkthrough)
4. [Dry Run Example](#dry-run-example)
5. [How Scheduling Works](#how-scheduling-works)
6. [Visual Timeline](#visual-timeline)

---

## The Big Picture

**What does the application do?**
- The Feeder application scans directories (like `./test-reports`) every 2 minutes
- It finds files in those directories
- Later, it will process those files (we'll add that in next phases)

**Why every 2 minutes?**
- We don't want to scan continuously (wastes resources)
- We don't want to scan too rarely (miss new files)
- 2 minutes is a good balance (configurable)

---

## Key Components

### 1. **FeederConfig** - Configuration Manager
- Reads settings from environment variables
- Tells us: which directories to scan, how often to scan

### 2. **DirectoryScanner** - The File Finder
- Actually looks inside directories
- Finds all files
- Filters out hidden/temp files

### 3. **ScheduledDirectoryScanner** - The Timer
- **This is the key component!** It schedules the scanning
- Uses Java's `ScheduledExecutorService` to run tasks periodically

### 4. **FeederApplication** - The Main Program
- Starts everything up
- Keeps the application running

---

## Code Walkthrough

Let's look at the code that makes this work:

### Step 1: Configuration (FeederConfig.java)

```java
public final class FeederConfig {
    private final int scanIntervalMinutes;
    
    public FeederConfig() {
        // Read from environment variable, default to 2 minutes
        this.scanIntervalMinutes = loadScanIntervalMinutes();
    }
    
    private int loadScanIntervalMinutes() {
        String envValue = getEnv("FEEDER_SCAN_INTERVAL_MINUTES", "2");
        return Integer.parseInt(envValue);
    }
    
    public int getScanIntervalMinutes() {
        return scanIntervalMinutes;  // Returns 2 (or whatever is configured)
    }
}
```

**Explanation:**
- This class reads the scan interval from environment variables
- If not set, it defaults to 2 minutes
- Other parts of the code can ask: "How often should I scan?" and get the answer

---

### Step 2: Directory Scanner (DirectoryScanner.java)

```java
public final class DirectoryScanner {
    
    public List<File> scanDirectory(String directoryPath) throws IOException {
        Path path = Paths.get(directoryPath);
        
        // Check if directory exists
        if (!Files.exists(path)) {
            return Collections.emptyList();
        }
        
        // Get all files in the directory
        List<File> candidateFiles = new ArrayList<>();
        try (Stream<Path> paths = Files.list(path)) {
            paths.forEach(filePath -> {
                if (isCandidateFile(filePath)) {
                    candidateFiles.add(filePath.toFile());
                }
            });
        }
        
        return candidateFiles;
    }
}
```

**Explanation:**
- This method takes a directory path (like `"./test-reports"`)
- It opens the directory and lists all files
- It filters out hidden files, temp files, etc.
- Returns a list of files to process

**Example:**
```
Input:  "./test-reports"
Output: [report1.txt, report2.csv, report3.dat]
```

---

### Step 3: Scheduled Scanner (ScheduledDirectoryScanner.java)

**This is where the magic happens!** Let's break it down:

#### Part A: The Setup

```java
public final class ScheduledDirectoryScanner {
    private final ScheduledExecutorService scheduler;
    private final FeederConfig config;
    private final DirectoryScanner directoryScanner;
    
    public ScheduledDirectoryScanner(FeederConfig config, DirectoryScanner directoryScanner) {
        this.config = config;
        this.directoryScanner = directoryScanner;
        
        // Create a scheduler (like a timer)
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ScheduledDirectoryScanner");
            t.setDaemon(false);
            return t;
        });
    }
}
```

**Explanation:**
- `ScheduledExecutorService` is Java's built-in timer/scheduler
- It can run tasks at specific times or intervals
- We create one thread that will run our scanning tasks

#### Part B: Starting the Scheduler

```java
public void start() {
    logger.info("FRS_0440 Starting scheduled directory scanner");
    
    // 1. Run scan IMMEDIATELY (don't wait 2 minutes for first scan)
    scheduler.execute(this::performScan);
    
    // 2. Then schedule it to run every 2 minutes
    scheduler.scheduleAtFixedRate(
        this::performScan,                    // What to run
        config.getScanIntervalMinutes(),      // Initial delay: 2 minutes
        config.getScanIntervalMinutes(),      // Repeat every: 2 minutes
        TimeUnit.MINUTES                       // Time unit: minutes
    );
}
```

**Explanation:**
- `scheduler.execute(this::performScan)` - Runs scan immediately (right now!)
- `scheduler.scheduleAtFixedRate(...)` - Schedules scan to run every 2 minutes
- `this::performScan` - This is a method reference (calls the `performScan()` method)

**What happens:**
1. Application starts → `start()` is called
2. First scan runs immediately (0 seconds)
3. Then every 2 minutes, `performScan()` is called again

#### Part C: The Scan Method

```java
private void performScan() {
    if (!running.get()) {
        return;  // Don't scan if we're shutting down
    }
    
    try {
        logger.info("FRS_0442 Starting scan cycle");
        
        // Scan all configured directories
        List<File> files = directoryScanner.scanDirectories(
            config.getSourceDirectories()
        );
        
        logger.info("FRS_0443 Scan cycle completed. Found {} file(s)", files.size());
        
        // TODO: Process files (will be added in next phase)
        for (File file : files) {
            logger.debug("Found file: {}", file.getAbsolutePath());
        }
        
    } catch (Exception e) {
        logger.error("FRS_0442 Scan cycle failed", e);
        // Don't re-throw - allow scheduler to continue
    }
}
```

**Explanation:**
- This method is called every 2 minutes by the scheduler
- It calls `directoryScanner.scanDirectories()` to find files
- Logs the results
- Later, we'll add code here to process the files

---

### Step 4: Main Application (FeederApplication.java)

```java
public class FeederApplication {
    
    public static void main(String[] args) {
        FeederApplication app = new FeederApplication();
        app.start();  // Initialize everything
        app.run();    // Keep application running
    }
    
    private void start() {
        // 1. Load configuration
        config = new FeederConfig();
        
        // 2. Start Hazelcast
        HazelcastInstance hazelcast = HazelcastProvider.getInstance();
        
        // 3. Create scanner and scheduler
        DirectoryScanner directoryScanner = new DirectoryScanner();
        scheduledScanner = new ScheduledDirectoryScanner(config, directoryScanner);
        
        // 4. START THE SCHEDULER (this starts the 2-minute cycle)
        scheduledScanner.start();
        
        // 5. Register shutdown hook (clean up when app stops)
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            shutdown();
        }));
    }
    
    private void run() {
        // Keep application running forever (until Ctrl+C)
        try {
            Thread.sleep(Long.MAX_VALUE);
        } catch (InterruptedException e) {
            shutdown();
        }
    }
}
```

**Explanation:**
- `main()` method is the entry point
- `start()` initializes everything and starts the scheduler
- `run()` keeps the application alive (if we don't do this, the app would exit immediately)
- The scheduler runs in a separate thread, so it keeps working even though `run()` is sleeping

---

## Dry Run Example

Let's trace through what happens when you run the application:

### Setup Phase (Time: 0:00)

**Command:** `mvn exec:java`

**Step 1:** Application starts
```
[INFO] FRS_0200 Starting Feeder Application
```

**Step 2:** Load configuration
```
[INFO] FRS_0400 Feeder configuration loaded:
[INFO] FRS_0400   Source directories: [./test-reports]
[INFO] FRS_0400   Scan interval: 2 minutes
```

**Step 3:** Start Hazelcast
```
[INFO] FRS_0450 Starting Hazelcast from classpath config 'hazelcast.yaml'
[INFO] Hazelcast started. Member: feeder-member
```

**Step 4:** Create scanner and scheduler
```
[INFO] Creating DirectoryScanner...
[INFO] Creating ScheduledDirectoryScanner...
```

**Step 5:** Start scheduler (THIS IS KEY!)
```
[INFO] FRS_0440 Starting scheduled directory scanner
[INFO] FRS_0440   Scan interval: 2 minutes
[INFO] FRS_0440   Source directories: [./test-reports]
```

**What happens internally:**
- Scheduler thread is created
- `scheduler.execute(this::performScan)` is called → **First scan runs NOW!**

---

### First Scan (Time: 0:00 - immediately)

**Scheduler calls:** `performScan()`

**Step 1:** Log scan start
```
[INFO] FRS_0442 Starting scan cycle
```

**Step 2:** Call directory scanner
```
[INFO] FRS_0430 Starting directory scan for 1 directory(ies)
```

**Step 3:** Scan directory
```
Directory: ./test-reports
Files found:
  - report1.txt
  - report2.csv
  - report3.dat
```

**Step 4:** Log results
```
[INFO] FRS_0433 Found 3 file(s) in directory: ./test-reports
[INFO] FRS_0431 Directory scan completed. Total files found: 3
[INFO] FRS_0443 Scan cycle completed. Found 3 file(s)
```

**Step 5:** Scheduler schedules next scan
```
Scheduler: "I'll call performScan() again in 2 minutes"
```

**Application state:**
- Main thread: Sleeping (waiting for shutdown)
- Scheduler thread: Waiting for 2 minutes to pass

---

### Second Scan (Time: 2:00)

**2 minutes have passed...**

**Scheduler automatically calls:** `performScan()`

**Step 1:** Log scan start
```
[INFO] FRS_0442 Starting scan cycle
```

**Step 2:** Scan directory again
```
Directory: ./test-reports
Files found:
  - report1.txt (same file, still there)
  - report2.csv (same file, still there)
  - report3.dat (same file, still there)
  - report4.txt (NEW FILE! Added while we were waiting)
```

**Step 3:** Log results
```
[INFO] FRS_0433 Found 4 file(s) in directory: ./test-reports
[INFO] FRS_0431 Directory scan completed. Total files found: 4
[INFO] FRS_0443 Scan cycle completed. Found 4 file(s)
```

**Step 4:** Scheduler schedules next scan
```
Scheduler: "I'll call performScan() again in 2 minutes"
```

---

### Third Scan (Time: 4:00)

**Another 2 minutes have passed...**

**Scheduler automatically calls:** `performScan()`

**Step 1:** Scan directory
```
Directory: ./test-reports
Files found:
  - report2.csv (report1.txt was deleted)
  - report3.dat
  - report4.txt
```

**Step 2:** Log results
```
[INFO] FRS_0433 Found 3 file(s) in directory: ./test-reports
[INFO] FRS_0443 Scan cycle completed. Found 3 file(s)
```

**This continues every 2 minutes until the application is stopped...**

---

### Shutdown (Time: 10:00 - User presses Ctrl+C)

**User presses:** `Ctrl+C`

**Step 1:** Shutdown hook is triggered
```
[INFO] FRS_0202 Shutdown signal received
```

**Step 2:** Stop scheduler
```
[INFO] FRS_0441 Stopping scheduled directory scanner
Scheduler: "Stop scheduling new scans"
Scheduler: "Wait for current scan to finish (if any)"
Scheduler: "Shutdown complete"
```

**Step 3:** Shutdown Hazelcast
```
[INFO] FRS_0452 Shutting down Hazelcast instance
```

**Step 4:** Application exits
```
[INFO] FRS_0202 Feeder Application stopped
Application exits
```

---

## How Scheduling Works

### Understanding `ScheduledExecutorService`

Think of it like an alarm clock:

```java
ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
```

**What is this?**
- It's a "timer" that can run code at specific times
- It runs in a separate thread (doesn't block the main program)
- It can run tasks once, or repeatedly

### Understanding `scheduleAtFixedRate`

```java
scheduler.scheduleAtFixedRate(
    this::performScan,              // Task to run
    2,                               // Initial delay: 2 minutes
    2,                               // Repeat every: 2 minutes
    TimeUnit.MINUTES                 // Time unit
);
```

**What does this do?**
- **Task:** `this::performScan` - The method to call
- **Initial delay:** Wait 2 minutes before first execution
- **Repeat interval:** Run again every 2 minutes
- **Time unit:** Minutes (not seconds, not hours)

**Important:** We also call `scheduler.execute(this::performScan)` to run it immediately, so we don't wait 2 minutes for the first scan.

### Visual Representation

```
Time:  0:00    2:00    4:00    6:00    8:00    10:00
       |       |       |       |       |       |
       ▼       ▼       ▼       ▼       ▼       ▼
      Scan   Scan    Scan    Scan    Scan    (Stop)
       │       │       │       │       │
       └───────┴───────┴───────┴───────┘
           2 min   2 min   2 min   2 min
```

**Each arrow (▼) represents a scan running.**

---

## Visual Timeline

Here's a detailed timeline showing what happens:

```
┌─────────────────────────────────────────────────────────────┐
│ Application Startup (Time: 0:00)                            │
├─────────────────────────────────────────────────────────────┤
│ 1. Load configuration                                        │
│    → Reads: scanIntervalMinutes = 2                          │
│    → Reads: sourceDirectories = ["./test-reports"]           │
│                                                              │
│ 2. Create scheduler                                          │
│    → ScheduledExecutorService created                        │
│    → Single thread for running scans                         │
│                                                              │
│ 3. Start scheduler                                           │
│    → scheduler.execute(performScan)  ← Runs IMMEDIATELY      │
│    → scheduler.scheduleAtFixedRate(...)  ← Runs every 2 min │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│ First Scan (Time: 0:00)                                     │
├─────────────────────────────────────────────────────────────┤
│ performScan() called by scheduler                            │
│   ↓                                                          │
│ directoryScanner.scanDirectories(["./test-reports"])        │
│   ↓                                                          │
│ Files found: [report1.txt, report2.csv, report3.dat]        │
│   ↓                                                          │
│ Log: "Found 3 file(s)"                                      │
│   ↓                                                          │
│ Scheduler: "Schedule next scan in 2 minutes"                │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│ Waiting Period (Time: 0:00 - 2:00)                          │
├─────────────────────────────────────────────────────────────┤
│ Main thread: Sleeping (Thread.sleep(Long.MAX_VALUE))        │
│ Scheduler thread: Waiting for 2 minutes to pass              │
│                                                              │
│ (User could add/remove files during this time)               │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│ Second Scan (Time: 2:00)                                    │
├─────────────────────────────────────────────────────────────┤
│ Scheduler automatically calls performScan()                  │
│   ↓                                                          │
│ directoryScanner.scanDirectories(["./test-reports"])        │
│   ↓                                                          │
│ Files found: [report1.txt, report2.csv, report3.dat,       │
│               report4.txt]  ← NEW FILE!                      │
│   ↓                                                          │
│ Log: "Found 4 file(s)"                                      │
│   ↓                                                          │
│ Scheduler: "Schedule next scan in 2 minutes"                │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│ This pattern repeats every 2 minutes...                     │
│                                                              │
│ Time: 4:00 → Scan                                            │
│ Time: 6:00 → Scan                                            │
│ Time: 8:00 → Scan                                            │
│ ...                                                          │
│                                                              │
│ Until user stops application (Ctrl+C)                       │
└─────────────────────────────────────────────────────────────┘
```

---

## Key Concepts Explained

### 1. **Threads**

**What is a thread?**
- A thread is like a worker that can do tasks
- Your application can have multiple threads doing different things at the same time

**In our application:**
- **Main thread:** Keeps the application running (sleeps)
- **Scheduler thread:** Runs scans every 2 minutes

**Why do we need threads?**
- If we didn't use threads, the main program would have to wait for each scan to finish
- With threads, the scheduler can run scans in the background while the main program does other things

### 2. **Method References (`this::performScan`)**

**What is `this::performScan`?**
- It's a shorthand way to say "call the `performScan()` method"
- Equivalent to: `() -> this.performScan()`

**Example:**
```java
// These are equivalent:
scheduler.execute(this::performScan);
scheduler.execute(() -> this.performScan());
scheduler.execute(() -> performScan());
```

### 3. **ScheduledExecutorService**

**What is it?**
- Java's built-in class for scheduling tasks
- Like a smart alarm clock that can repeat

**Key methods:**
- `execute(Runnable)` - Run task immediately
- `scheduleAtFixedRate(...)` - Run task repeatedly at fixed intervals
- `shutdown()` - Stop scheduling new tasks

### 4. **Why Run Immediately AND Schedule?**

```java
// Run immediately
scheduler.execute(this::performScan);

// Also schedule for later
scheduler.scheduleAtFixedRate(this::performScan, 2, 2, TimeUnit.MINUTES);
```

**Why both?**
- `execute()` - Don't wait 2 minutes for the first scan
- `scheduleAtFixedRate()` - Then keep scanning every 2 minutes

**Without `execute()`:**
- First scan would happen at 2:00 (we'd wait 2 minutes)
- Then every 2 minutes after that

**With `execute()`:**
- First scan happens at 0:00 (immediately)
- Then every 2 minutes after that

---

## Testing It Yourself

### Step 1: Create Test Directory

```powershell
# Windows PowerShell
mkdir test-reports
echo "test" > test-reports\file1.txt
echo "test" > test-reports\file2.txt
```

### Step 2: Run Application

```powershell
mvn exec:java
```

### Step 3: Watch the Logs

You'll see:
```
[INFO] FRS_0440 Starting scheduled directory scanner
[INFO] FRS_0442 Starting scan cycle
[INFO] FRS_0433 Found 2 file(s) in directory: ./test-reports
[INFO] FRS_0443 Scan cycle completed. Found 2 file(s)
```

### Step 4: Add a File (While App is Running)

In another terminal:
```powershell
echo "new file" > test-reports\file3.txt
```

### Step 5: Wait 2 Minutes

Watch the logs - you'll see another scan:
```
[INFO] FRS_0442 Starting scan cycle
[INFO] FRS_0433 Found 3 file(s) in directory: ./test-reports  ← Now 3 files!
[INFO] FRS_0443 Scan cycle completed. Found 3 file(s)
```

---

## Summary

**How it works:**
1. Application starts and creates a scheduler
2. Scheduler runs first scan immediately
3. Scheduler schedules scans every 2 minutes
4. Each scan finds files in the configured directories
5. This continues until the application is stopped

**Key components:**
- `ScheduledExecutorService` - The timer/scheduler
- `scheduleAtFixedRate()` - Schedules repeated tasks
- `performScan()` - The method that runs every 2 minutes
- Threads - Allow scanning to happen in the background

**The magic:**
- The scheduler runs in a separate thread
- It automatically calls `performScan()` every 2 minutes
- The main program just sleeps, waiting for shutdown

---

## Questions?

**Q: What if I want to scan every 1 minute instead of 2?**
A: Set environment variable: `FEEDER_SCAN_INTERVAL_MINUTES=1`

**Q: What if a scan takes longer than 2 minutes?**
A: The next scan will start 2 minutes after the previous one started (not after it finished)

**Q: Can I stop the scheduler?**
A: Yes, call `scheduledScanner.stop()` - it will finish current scan and stop scheduling new ones

**Q: What happens if the directory doesn't exist?**
A: The scanner logs a warning and continues (doesn't crash the application)

---

**Document Version:** 1.0  
**Last Updated:** 2025-01-02  
**Target Audience:** Beginners/Freshers

