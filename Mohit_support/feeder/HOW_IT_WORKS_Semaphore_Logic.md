# How Semaphore Logic Works - Adding Files to Hazelcast

## Overview for Beginners

This document explains how the Feeder application uses **semaphore logic** to add files to **Hazelcast** (a distributed database). It's written for beginners, so we'll explain each concept step by step.

---

## Table of Contents

1. [What is Semaphore Logic?](#what-is-semaphore-logic)
2. [What is Hazelcast?](#what-is-hazelcast)
3. [Why Do We Need This?](#why-do-we-need-this)
4. [How It Works - Step by Step](#how-it-works---step-by-step)
5. [Code Walkthrough](#code-walkthrough)
6. [Dry Run Example](#dry-run-example)
7. [Understanding the Fingerprint](#understanding-the-fingerprint)
8. [Visual Diagrams](#visual-diagrams)

---

## What is Semaphore Logic?

**Simple Explanation:**
Think of a semaphore like a **traffic light** or a **lock on a door**.

- **Green light / Unlocked door** = You can go through (process the file)
- **Red light / Locked door** = You must wait (skip the file)

**In our application:**
- When we find a file, we check: "Has this file been processed before?"
- If **NO** → Add it to Hazelcast and process it
- If **YES** → Skip it (don't process again)

**Why is this important?**
- In a cluster (multiple servers running the same application), we need to make sure:
  - Only **ONE** server processes each file
  - We don't process the same file **twice**
  - All servers know which files have been processed

---

## What is Hazelcast?

**Simple Explanation:**
Hazelcast is like a **shared memory** or **shared whiteboard** that multiple computers can access.

**Real-world analogy:**
- Imagine a **shared Google Doc** that multiple people can edit
- Everyone sees the same data
- When one person adds something, everyone else can see it

**In our application:**
- Hazelcast stores a **map** (like a dictionary) of processed files
- All servers in the cluster can read and write to this map
- This ensures all servers know which files have been processed

**What does it store?**
- **Key:** A number (0 to 65535) - like a file ID
- **Value:** A fingerprint (8 bytes) - like a unique signature of the file

---

## Why Do We Need This?

### Problem: Multiple Servers, Same Files

**Scenario:**
- You have 3 servers running the Feeder application
- All 3 servers scan the same directory: `./test-reports`
- A new file appears: `report.txt`

**Without semaphore logic:**
```
Server 1: "I found report.txt! I'll process it."
Server 2: "I found report.txt! I'll process it."  ← Duplicate!
Server 3: "I found report.txt! I'll process it."  ← Duplicate!
Result: File processed 3 times (wasteful, causes errors)
```

**With semaphore logic:**
```
Server 1: "I found report.txt! Let me check Hazelcast..."
         "Not in Hazelcast. I'll add it and process it."
Server 2: "I found report.txt! Let me check Hazelcast..."
         "Already in Hazelcast! I'll skip it."
Server 3: "I found report.txt! Let me check Hazelcast..."
         "Already in Hazelcast! I'll skip it."
Result: File processed only once (correct!)
```

---

## How It Works - Step by Step

### Step 1: Find a File

When the directory scanner finds a file, it passes it to the semaphore manager.

**Example:**
```
File found: "report.txt"
Location: "./test-reports/report.txt"
```

### Step 2: Calculate Fingerprint

We create a **fingerprint** (unique signature) from the filename using SHA-256 hash.

**What is SHA-256?**
- It's a mathematical function that converts any text into a unique 256-bit (32-byte) number
- Same input → Same output (always!)
- Different input → Different output (almost always)

**Example:**
```
Filename: "report.txt"
SHA-256 Hash: [a1, b2, c3, d4, e5, f6, ... 32 bytes total]
```

### Step 3: Extract Map Key and Fingerprint

From the 32-byte hash, we extract:
- **Map Key:** Bytes 0-3, bounded by 2^16 (0-65535)
- **Fingerprint:** Bytes 23-31 (8 bytes)

**Why?**
- **Map Key:** Keeps the map small (only 65,536 possible keys)
- **Fingerprint:** Ensures we can tell if two files are really the same

**Example:**
```
SHA-256 Hash: [a1, b2, c3, d4, ..., x1, x2, x3, x4, x5, x6, x7, x8]
                ↑  ↑  ↑  ↑              ↑  ↑  ↑  ↑  ↑  ↑  ↑  ↑
                |  |  |  |              |  |  |  |  |  |  |  |
              Map Key (4 bytes)      Fingerprint (8 bytes)
```

### Step 4: Check Hazelcast

We check if this file is already in Hazelcast:

**Algorithm:**
```
1. Get the map key (e.g., 12345)
2. Check if key exists in Hazelcast map
3. If key exists:
   - Get the stored fingerprint
   - Compare with our fingerprint
   - If they match → File already processed (SKIP)
   - If they don't match → Different file, same key (COLLISION - process it)
4. If key doesn't exist:
   - Add our fingerprint to Hazelcast
   - Process the file
```

### Step 5: Add to Hazelcast (if new)

If the file is new, we add it to Hazelcast:

```
Hazelcast Map:
Key: 12345
Value: [x1, x2, x3, x4, x5, x6, x7, x8]  (fingerprint bytes)
```

---

## Code Walkthrough

Let's look at the actual code that does this:

### Step 1: FileFingerprint.java - Creating the Fingerprint

```java
public final class FileFingerprint {
    
    // Create fingerprint from filename
    public static FileFingerprint fromFileName(String fileName) {
        // 1. Calculate SHA-256 hash of filename
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(fileName.getBytes(StandardCharsets.UTF_8));
        
        // 2. Extract map key (bytes 0-3, bounded by 2^16)
        int mapKey = extractMapKey(hash);
        
        // 3. Extract fingerprint (bytes 23-31, 8 bytes)
        byte[] fingerprint = extractFingerprint(hash);
        
        return new FileFingerprint(fileName, mapKey, fingerprint);
    }
}
```

**Explanation:**
- `MessageDigest.getInstance("SHA-256")` - Gets the SHA-256 algorithm
- `digest.digest(...)` - Calculates the hash
- `extractMapKey()` - Gets bytes 0-3 and bounds by 2^16
- `extractFingerprint()` - Gets bytes 23-31

**Example:**
```java
FileFingerprint fp = FileFingerprint.fromFileName("report.txt");
// Result:
//   fileName: "report.txt"
//   mapKey: 12345 (0-65535)
//   fingerprint: [x1, x2, x3, x4, x5, x6, x7, x8]
```

---

### Step 2: HazelcastSemaphoreManager.java - Checking and Adding

```java
public final class HazelcastSemaphoreManager {
    
    // Check if file should be processed
    public boolean shouldProcessFile(FileFingerprint fingerprint) {
        // 1. Get map key and fingerprint bytes
        int mapKey = fingerprint.getMapKey();
        byte[] fingerprintBytes = fingerprint.getFingerprint();
        
        // 2. Atomic check-and-set operation
        // putIfAbsent: returns null if key didn't exist (we added it)
        //               returns existing value if key exists
        byte[] existingFingerprint = semaphoreMap.putIfAbsent(mapKey, fingerprintBytes);
        
        if (existingFingerprint == null) {
            // Key didn't exist - we added it, so process the file
            return true;
        }
        
        // Key exists - check if fingerprint matches
        if (Arrays.equals(existingFingerprint, fingerprintBytes)) {
            // Fingerprint matches - file already processed
            return false;  // Skip
        }
        
        // Key exists but fingerprint doesn't match - collision!
        // Different file, same map key (rare but possible)
        // We should still process this file
        semaphoreMap.put(mapKey, fingerprintBytes);  // Overwrite
        return true;  // Process
    }
}
```

**Explanation:**
- `putIfAbsent()` - This is an **atomic operation** (happens all at once, no interruptions)
  - If key doesn't exist: Add it and return `null`
  - If key exists: Return the existing value (don't change it)
- `Arrays.equals()` - Compares two byte arrays
- If fingerprints match → File already processed (skip)
- If fingerprints don't match → Different file, same key (collision - process it)

**Why is `putIfAbsent()` important?**
- It's **atomic** - no other thread can interrupt it
- This prevents **race conditions** (two servers trying to add the same file at the same time)

---

### Step 3: ScheduledDirectoryScanner.java - Using the Semaphore

```java
private void performScan() {
    // 1. Scan directories for files
    List<File> files = directoryScanner.scanDirectories(config.getSourceDirectories());
    
    // 2. For each file found
    for (File file : files) {
        // 3. Calculate fingerprint from filename
        FileFingerprint fingerprint = FileFingerprint.fromFileName(file.getName());
        
        // 4. Check if file should be processed (adds to Hazelcast if new)
        boolean shouldProcess = semaphoreManager.shouldProcessFile(fingerprint);
        
        if (shouldProcess) {
            // File is new - added to Hazelcast
            logger.info("FRS_0421 File added to Hazelcast: {}", file.getName());
        } else {
            // File already in Hazelcast - skip
            logger.debug("FRS_0423 File already in Hazelcast (skip): {}", file.getName());
        }
    }
}
```

**Explanation:**
- For each file found during scan:
  1. Calculate fingerprint
  2. Check semaphore (this adds to Hazelcast if new)
  3. Log the result

---

## Dry Run Example

Let's trace through what happens when files are found:

### Scenario: First Scan (Time: 0:00)

**Directory contains:**
- `report1.txt`
- `report2.txt`

---

#### File 1: `report1.txt`

**Step 1:** Calculate fingerprint
```
Filename: "report1.txt"
SHA-256 Hash: [a1, b2, c3, d4, e5, f6, ..., x1, x2, x3, x4, x5, x6, x7, x8]
Map Key: 12345 (from bytes 0-3)
Fingerprint: [x1, x2, x3, x4, x5, x6, x7, x8] (from bytes 23-31)
```

**Step 2:** Check Hazelcast
```
Hazelcast Map (before):
(empty)

Check: Does key 12345 exist?
Answer: NO
```

**Step 3:** Add to Hazelcast
```
semaphoreMap.putIfAbsent(12345, [x1, x2, x3, x4, x5, x6, x7, x8])
Result: null (key didn't exist, we added it)

Hazelcast Map (after):
Key: 12345
Value: [x1, x2, x3, x4, x5, x6, x7, x8]
```

**Step 4:** Return result
```
shouldProcessFile() returns: true
Log: "FRS_0421 File added to Hazelcast: report1.txt (mapKey: 12345)"
```

---

#### File 2: `report2.txt`

**Step 1:** Calculate fingerprint
```
Filename: "report2.txt"
SHA-256 Hash: [z1, z2, z3, z4, z5, z6, ..., y1, y2, y3, y4, y5, y6, y7, y8]
Map Key: 67890 (from bytes 0-3)
Fingerprint: [y1, y2, y3, y4, y5, y6, y7, y8] (from bytes 23-31)
```

**Step 2:** Check Hazelcast
```
Hazelcast Map (current):
Key: 12345 → [x1, x2, x3, x4, x5, x6, x7, x8]

Check: Does key 67890 exist?
Answer: NO
```

**Step 3:** Add to Hazelcast
```
semaphoreMap.putIfAbsent(67890, [y1, y2, y3, y4, y5, y6, y7, y8])
Result: null (key didn't exist, we added it)

Hazelcast Map (after):
Key: 12345 → [x1, x2, x3, x4, x5, x6, x7, x8]
Key: 67890 → [y1, y2, y3, y4, y5, y6, y7, y8]
```

**Step 4:** Return result
```
shouldProcessFile() returns: true
Log: "FRS_0421 File added to Hazelcast: report2.txt (mapKey: 67890)"
```

**Summary:**
```
Scan cycle summary: 2 new files added to Hazelcast, 0 files skipped
```

---

### Scenario: Second Scan (Time: 2:00)

**Directory still contains:**
- `report1.txt` (same file)
- `report2.txt` (same file)

---

#### File 1: `report1.txt` (again)

**Step 1:** Calculate fingerprint
```
Filename: "report1.txt"
SHA-256 Hash: [a1, b2, c3, d4, e5, f6, ..., x1, x2, x3, x4, x5, x6, x7, x8]
Map Key: 12345 (same as before!)
Fingerprint: [x1, x2, x3, x4, x5, x6, x7, x8] (same as before!)
```

**Step 2:** Check Hazelcast
```
Hazelcast Map (current):
Key: 12345 → [x1, x2, x3, x4, x5, x6, x7, x8]
Key: 67890 → [y1, y2, y3, y4, y5, y6, y7, y8]

Check: Does key 12345 exist?
Answer: YES

Check: Does stored fingerprint match our fingerprint?
Stored: [x1, x2, x3, x4, x5, x6, x7, x8]
Ours:   [x1, x2, x3, x4, x5, x6, x7, x8]
Answer: YES (they match!)
```

**Step 3:** Don't add to Hazelcast
```
semaphoreMap.putIfAbsent(12345, [x1, x2, x3, x4, x5, x6, x7, x8])
Result: [x1, x2, x3, x4, x5, x6, x7, x8] (existing value returned)

Hazelcast Map (unchanged):
Key: 12345 → [x1, x2, x3, x4, x5, x6, x7, x8]
Key: 67890 → [y1, y2, y3, y4, y5, y6, y7, y8]
```

**Step 4:** Return result
```
shouldProcessFile() returns: false
Log: "FRS_0423 File already in Hazelcast (skip): report1.txt"
```

---

#### File 2: `report2.txt` (again)

**Step 1:** Calculate fingerprint
```
Filename: "report2.txt"
Map Key: 67890 (same as before)
Fingerprint: [y1, y2, y3, y4, y5, y6, y7, y8] (same as before)
```

**Step 2:** Check Hazelcast
```
Check: Does key 67890 exist?
Answer: YES

Check: Does stored fingerprint match?
Answer: YES (they match!)
```

**Step 3:** Don't add to Hazelcast
```
Result: File already processed (skip)
```

**Step 4:** Return result
```
shouldProcessFile() returns: false
Log: "FRS_0423 File already in Hazelcast (skip): report2.txt"
```

**Summary:**
```
Scan cycle summary: 0 new files added to Hazelcast, 2 files skipped
```

---

### Scenario: New File Appears (Time: 4:00)

**Directory now contains:**
- `report1.txt` (old)
- `report2.txt` (old)
- `report3.txt` (NEW!)

---

#### File 3: `report3.txt` (new file)

**Step 1:** Calculate fingerprint
```
Filename: "report3.txt"
SHA-256 Hash: [m1, m2, m3, m4, m5, m6, ..., n1, n2, n3, n4, n5, n6, n7, n8]
Map Key: 54321 (different from others)
Fingerprint: [n1, n2, n3, n4, n5, n6, n7, n8] (different from others)
```

**Step 2:** Check Hazelcast
```
Hazelcast Map (current):
Key: 12345 → [x1, x2, x3, x4, x5, x6, x7, x8]
Key: 67890 → [y1, y2, y3, y4, y5, y6, y7, y8]

Check: Does key 54321 exist?
Answer: NO
```

**Step 3:** Add to Hazelcast
```
semaphoreMap.putIfAbsent(54321, [n1, n2, n3, n4, n5, n6, n7, n8])
Result: null (key didn't exist, we added it)

Hazelcast Map (after):
Key: 12345 → [x1, x2, x3, x4, x5, x6, x7, x8]
Key: 67890 → [y1, y2, y3, y4, y5, y6, y7, y8]
Key: 54321 → [n1, n2, n3, n4, n5, n6, n7, n8]  ← NEW!
```

**Step 4:** Return result
```
shouldProcessFile() returns: true
Log: "FRS_0421 File added to Hazelcast: report3.txt (mapKey: 54321)"
```

**Summary:**
```
Scan cycle summary: 1 new file added to Hazelcast, 2 files skipped
```

---

## Understanding the Fingerprint

### Why Use SHA-256?

**Properties of SHA-256:**
1. **Deterministic:** Same input always produces same output
   - `"report.txt"` → Always same hash
   - `"report.txt"` → Always same hash (even on different servers)

2. **Unique:** Different inputs produce different outputs (almost always)
   - `"report1.txt"` → Different hash than `"report2.txt"`

3. **One-way:** Can't reverse it (can't get filename from hash)
   - Hash → Filename? Impossible!

**Example:**
```java
FileFingerprint fp1 = FileFingerprint.fromFileName("report.txt");
FileFingerprint fp2 = FileFingerprint.fromFileName("report.txt");
// fp1 and fp2 have the SAME fingerprint (deterministic)

FileFingerprint fp3 = FileFingerprint.fromFileName("other.txt");
// fp3 has a DIFFERENT fingerprint
```

---

### Why Extract Map Key and Fingerprint?

**Problem:** SHA-256 produces 32 bytes (256 bits)
- That's 2^256 possible values (HUGE number!)
- We can't store that many keys in Hazelcast

**Solution:** Use a bounded map key
- Extract bytes 0-3 → 4 bytes = 2^32 possible values
- Bound by 2^16 → Only 65,536 possible keys (manageable!)
- Use fingerprint (8 bytes) to verify it's really the same file

**Why this works:**
- **Map Key:** Quickly finds the entry (like a phone book index)
- **Fingerprint:** Verifies it's the right entry (like checking the name matches)

**Example:**
```
Filename: "report.txt"
SHA-256: [a1, b2, c3, d4, e5, f6, ..., x1, x2, x3, x4, x5, x6, x7, x8]
          ↑  ↑  ↑  ↑              ↑  ↑  ↑  ↑  ↑  ↑  ↑  ↑
          |  |  |  |              |  |  |  |  |  |  |  |
        Map Key (4 bytes)      Fingerprint (8 bytes)

Map Key: 12345 (0-65535)
Fingerprint: [x1, x2, x3, x4, x5, x6, x7, x8]
```

---

### What is a Collision?

**Collision:** Two different files have the same map key (but different fingerprints)

**Example:**
```
File 1: "report1.txt"
  Map Key: 12345
  Fingerprint: [x1, x2, x3, x4, x5, x6, x7, x8]

File 2: "report2.txt"
  Map Key: 12345  ← Same key! (collision)
  Fingerprint: [y1, y2, y3, y4, y5, y6, y7, y8]  ← Different fingerprint
```

**How we handle it:**
1. Check if key exists → YES
2. Check if fingerprint matches → NO (different file!)
3. Overwrite with new fingerprint → Process the new file

**Why this is OK:**
- The old file was already processed
- The new file is different (different fingerprint)
- We can overwrite because we only care about the most recent file with that key

---

## Visual Diagrams

### Flow Diagram

```
┌─────────────────────────────────────────────────────────────┐
│ Directory Scanner finds file: "report.txt"                  │
└───────────────────────┬───────────────────────────────────────┘
                        │
                        ▼
┌─────────────────────────────────────────────────────────────┐
│ Calculate FileFingerprint                                   │
│   Input: "report.txt"                                        │
│   Process: SHA-256 hash                                     │
│   Output:                                                   │
│     - Map Key: 12345                                         │
│     - Fingerprint: [x1, x2, x3, x4, x5, x6, x7, x8]        │
└───────────────────────┬───────────────────────────────────────┘
                        │
                        ▼
┌─────────────────────────────────────────────────────────────┐
│ Check Hazelcast Semaphore Map                               │
│   Key: 12345                                                │
│                                                              │
│   Does key exist?                                           │
│   ┌─────────┬─────────┐                                     │
│   │   NO    │   YES   │                                     │
│   └────┬────┴────┬────┘                                     │
│        │         │                                           │
│        ▼         ▼                                           │
│   Add to Map   Check Fingerprint                            │
│   Return: true │                                             │
│                │                                             │
│                ▼                                             │
│         Fingerprint matches?                                │
│         ┌─────────┬─────────┐                                │
│         │   YES   │   NO    │                                │
│         └────┬────┴────┬────┘                                │
│              │         │                                      │
│              ▼         ▼                                      │
│         Return: false  Overwrite & Return: true              │
│         (Skip)        (Process - collision)                 │
└─────────────────────────────────────────────────────────────┘
```

---

### Hazelcast Map Structure

```
┌─────────────────────────────────────────────────────────────┐
│                    Hazelcast IMap                            │
│                                                              │
│  Type: IMap<Integer, byte[]>                                │
│  Name: "feeder-file-semaphore"                               │
│                                                              │
│  ┌──────────┬──────────────────────────────────────────┐   │
│  │   Key    │              Value                        │   │
│  ├──────────┼──────────────────────────────────────────┤   │
│  │  12345   │  [x1, x2, x3, x4, x5, x6, x7, x8]       │   │
│  │          │  (Fingerprint for "report1.txt")        │   │
│  ├──────────┼──────────────────────────────────────────┤   │
│  │  67890   │  [y1, y2, y3, y4, y5, y6, y7, y8]       │   │
│  │          │  (Fingerprint for "report2.txt")         │   │
│  ├──────────┼──────────────────────────────────────────┤   │
│  │  54321   │  [n1, n2, n3, n4, n5, n6, n7, n8]       │   │
│  │          │  (Fingerprint for "report3.txt")         │   │
│  └──────────┴──────────────────────────────────────────┘   │
│                                                              │
│  Key Range: 0 - 65535 (2^16)                                │
│  Value Size: 8 bytes (fingerprint)                          │
└─────────────────────────────────────────────────────────────┘
```

---

### Cluster Scenario

```
┌─────────────────────────────────────────────────────────────┐
│                    Cluster Setup                             │
│                                                              │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐  │
│  │   Server 1   │    │   Server 2   │    │   Server 3   │  │
│  │  (STL)       │    │  (KSC)       │    │  (Backup)    │  │
│  └──────┬───────┘    └──────┬───────┘    └──────┬───────┘  │
│         │                   │                   │          │
│         └───────────────────┼───────────────────┘          │
│                             │                               │
│                             ▼                               │
│              ┌──────────────────────────┐                   │
│              │   Hazelcast Cluster      │                   │
│              │   (Shared Map)          │                   │
│              │                          │                   │
│              │  IMap<Integer, byte[]>   │                   │
│              │  Key: 12345              │                   │
│              │  Value: [x1, x2, ...]    │                   │
│              └──────────────────────────┘                   │
│                                                              │
│  All servers can read/write to the same map                  │
│  All servers see the same data                               │
└─────────────────────────────────────────────────────────────┘
```

**Example:**
1. Server 1 finds `report.txt`
2. Server 1 checks Hazelcast → Not found
3. Server 1 adds to Hazelcast (key: 12345)
4. Server 2 finds `report.txt` (same file)
5. Server 2 checks Hazelcast → Found! (key: 12345 exists)
6. Server 2 skips the file (already processed by Server 1)

---

## Key Concepts Explained

### 1. **Atomic Operations**

**What is atomic?**
- An operation that happens **all at once** (can't be interrupted)
- Like a transaction: either it all happens, or none of it happens

**Why is it important?**
- Without atomic operations, two servers might both think a file is new
- Both would try to process it → Duplicate processing!

**Example:**
```java
// NOT atomic (BAD):
if (!map.containsKey(key)) {  // Check
    map.put(key, value);      // Add
}
// Problem: Another thread could add between check and put!

// Atomic (GOOD):
byte[] existing = map.putIfAbsent(key, value);
// This happens all at once - no interruption possible!
```

---

### 2. **Bounded Map**

**What is a bounded map?**
- A map with a limited number of possible keys
- In our case: 0 to 65,535 (2^16)

**Why bound it?**
- Prevents unlimited growth
- Makes lookups faster
- Uses less memory

**Trade-off:**
- Different files might have the same map key (collision)
- We use fingerprints to handle collisions

---

### 3. **Fingerprint vs Map Key**

**Map Key:**
- Purpose: Quick lookup (like an index)
- Size: 0-65535 (small, bounded)
- Collisions: Possible (different files can have same key)

**Fingerprint:**
- Purpose: Verify it's the right file
- Size: 8 bytes (unique signature)
- Collisions: Extremely rare (almost impossible)

**Together:**
- Map key finds the entry quickly
- Fingerprint verifies it's the correct entry

---

## Testing It Yourself

### Step 1: Create Test Files

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

**First scan:**
```
[INFO] FRS_0421 File added to Hazelcast: file1.txt (mapKey: 12345)
[INFO] FRS_0421 File added to Hazelcast: file2.txt (mapKey: 67890)
[INFO] FRS_0443 Scan cycle summary: 2 new files added to Hazelcast, 0 files skipped
```

**Second scan (2 minutes later):**
```
[DEBUG] FRS_0423 File already in Hazelcast (skip): file1.txt
[DEBUG] FRS_0423 File already in Hazelcast (skip): file2.txt
[INFO] FRS_0443 Scan cycle summary: 0 new files added to Hazelcast, 2 files skipped
```

### Step 4: Add a New File

While the app is running, add a new file:
```powershell
echo "new" > test-reports\file3.txt
```

**Next scan (2 minutes later):**
```
[INFO] FRS_0421 File added to Hazelcast: file3.txt (mapKey: 54321)
[DEBUG] FRS_0423 File already in Hazelcast (skip): file1.txt
[DEBUG] FRS_0423 File already in Hazelcast (skip): file2.txt
[INFO] FRS_0443 Scan cycle summary: 1 new file added to Hazelcast, 2 files skipped
```

---

## Summary

**How it works:**
1. Directory scanner finds files
2. For each file, calculate fingerprint (SHA-256)
3. Extract map key (0-65535) and fingerprint (8 bytes)
4. Check Hazelcast: Does key exist? Does fingerprint match?
5. If new: Add to Hazelcast and process
6. If exists: Skip (already processed)

**Key benefits:**
- ✅ Prevents duplicate processing
- ✅ Works in cluster (multiple servers)
- ✅ Atomic operations (thread-safe)
- ✅ Bounded map (efficient storage)

**The magic:**
- SHA-256 creates unique fingerprints
- Bounded map keys keep it efficient
- Atomic operations prevent race conditions
- Hazelcast provides shared storage for all servers

---

## Questions?

**Q: What if two files have the same map key?**
A: That's a collision. We check the fingerprint - if it's different, we know it's a different file and process it.

**Q: What if SHA-256 produces the same hash for two different files?**
A: This is extremely rare (almost impossible). If it happens, we'd skip the second file, but this is acceptable.

**Q: Can I see what's in Hazelcast?**
A: Yes! You can use Hazelcast Management Center or query the map programmatically.

**Q: What happens if Hazelcast is down?**
A: The application will throw an exception. We should handle this gracefully (will be added in future phases).

---

**Document Version:** 1.0  
**Last Updated:** 2025-01-02  
**Target Audience:** Beginners/Freshers

