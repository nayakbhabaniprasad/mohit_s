# Refactoring to Java Streams - Tutorial and Explanation

## Overview

This document explains the refactoring from imperative (loop-based) code to functional (stream-based) code, following Clay's streaming approach. It also serves as a tutorial for understanding Java Streams, lambdas, and functional programming concepts.

---

## Table of Contents

1. [What Changed?](#what-changed)
2. [Why Change?](#why-change)
3. [Java Concepts Tutorial](#java-concepts-tutorial)
4. [Before and After Comparison](#before-and-after-comparison)
5. [Step-by-Step Explanation](#step-by-step-explanation)
6. [Common Patterns](#common-patterns)
7. [Best Practices](#best-practices)

---

## What Changed?

### Summary

We refactored the directory scanning and file processing code from:
- **Imperative style** (loops, mutable collections)
- **To functional style** (streams, lambdas, immutable operations)

### Files Changed

1. **DirectoryScanner.java**
   - Before: Returned `List<File>`
   - After: Returns `Stream<Path>`

2. **ScheduledDirectoryScanner.java**
   - Before: Used `for` loop to process files
   - After: Uses stream pipeline with `map`, `filter`, and `forEach`

3. **DirectoryScannerTest.java**
   - Updated all tests to work with streams

---

## Why Change?

### Benefits of Streams

1. **More Readable** - Code reads like a pipeline of transformations
2. **Less Boilerplate** - No need for manual loops and counters
3. **Lazy Evaluation** - Processes items only when needed
4. **Composable** - Easy to add/remove operations
5. **Parallelizable** - Can easily add `.parallel()` for multi-threading
6. **Functional Style** - Modern Java best practice

### Clay's Vision

Clay suggested:
> "you need a Stream <Path>"
> "then a lambda to map the Path to a bound file name object"
> "then consumer that can add them to hazelcast"
> "that's very light and easy to follow"

This approach is:
- **Light** - Minimal code, clear intent
- **Easy to follow** - Each step is obvious
- **Maintainable** - Easy to modify and extend

---

## Java Concepts Tutorial

### 1. What is a Stream?

**Simple Explanation:**
A Stream is like a **conveyor belt** that carries items through a series of operations.

**Key Characteristics:**
- Streams don't store data (they process it)
- Streams are **lazy** (operations happen only when needed)
- Streams can be **infinite** (generate items on demand)
- Streams are **consumable** (can only be used once)

**Example:**
```java
// Create a stream from a list
List<String> names = List.of("Alice", "Bob", "Charlie");
Stream<String> nameStream = names.stream();

// Process the stream
nameStream
    .filter(name -> name.startsWith("A"))
    .forEach(System.out::println);
// Output: Alice
```

---

### 2. Stream Operations

Streams have two types of operations:

#### Terminal Operations
- **Consume** the stream (trigger processing)
- Return a result or perform a side effect
- Examples: `forEach()`, `collect()`, `count()`, `findFirst()`

#### Intermediate Operations
- **Transform** the stream
- Return a new stream
- Examples: `map()`, `filter()`, `flatMap()`, `distinct()`

**Example:**
```java
List<Integer> numbers = List.of(1, 2, 3, 4, 5);

long count = numbers.stream()        // Create stream
    .filter(n -> n % 2 == 0)        // Intermediate: filter even numbers
    .map(n -> n * 2)                 // Intermediate: double each number
    .count();                        // Terminal: count results
// Result: 2 (numbers 2 and 4, doubled to 4 and 8, count = 2)
```

---

### 3. Lambda Expressions

**What is a Lambda?**
A lambda is a **short way to write a function** (method) without a name.

**Syntax:**
```java
(parameters) -> expression
(parameters) -> { statements }
```

**Examples:**

**Example 1: Simple lambda**
```java
// Old way (anonymous class)
Runnable r = new Runnable() {
    @Override
    public void run() {
        System.out.println("Hello");
    }
};

// New way (lambda)
Runnable r = () -> System.out.println("Hello");
```

**Example 2: Lambda with parameters**
```java
// Old way
Comparator<String> comp = new Comparator<String>() {
    @Override
    public int compare(String a, String b) {
        return a.length() - b.length();
    }
};

// New way (lambda)
Comparator<String> comp = (a, b) -> a.length() - b.length();
```

**Example 3: Lambda in streams**
```java
List<String> names = List.of("Alice", "Bob", "Charlie");

// Filter names longer than 3 characters
names.stream()
    .filter(name -> name.length() > 3)  // Lambda: (name) -> name.length() > 3
    .forEach(System.out::println);
```

**In Our Code:**
```java
// Map Path to FileFingerprint
.map(path -> FileFingerprint.fromFileName(path.getFileName().toString()))
//     ↑     ↑
//  parameter  expression (what to do with parameter)
```

---

### 4. Method References

**What is a Method Reference?**
A shorthand for a lambda that just calls a method.

**Syntax:**
```java
object::method        // Instance method
Class::staticMethod   // Static method
Class::instanceMethod // Instance method of first parameter
```

**Examples:**

**Example 1: Static method reference**
```java
// Lambda
.map(path -> FileFingerprint.fromFileName(path.getFileName().toString()))

// Method reference (if we had a helper method)
.map(this::pathToFingerprint)
```

**Example 2: Instance method reference**
```java
// Lambda
.filter(path -> path.toString().startsWith("/"))

// Method reference
.filter(path -> path.toString().startsWith("/"))
// Can't simplify this one easily, but could do:
.map(Path::toString)
.filter(s -> s.startsWith("/"))
```

**Example 3: System.out::println**
```java
// Lambda
.forEach(item -> System.out.println(item))

// Method reference
.forEach(System.out::println)
```

**In Our Code:**
```java
.filter(Objects::nonNull)  // Static method reference
// Equivalent to: .filter(obj -> Objects.nonNull(obj))
```

---

### 5. flatMap() - Combining Streams

**What is flatMap?**
`flatMap` takes each element, transforms it into a stream, then **flattens** all streams into one.

**Example:**
```java
List<List<Integer>> nested = List.of(
    List.of(1, 2),
    List.of(3, 4),
    List.of(5, 6)
);

// Without flatMap (gives Stream<List<Integer>>)
nested.stream()  // Stream of lists

// With flatMap (gives Stream<Integer>)
nested.stream()
    .flatMap(list -> list.stream())  // Flatten lists into one stream
    .forEach(System.out::println);
// Output: 1, 2, 3, 4, 5, 6
```

**In Our Code:**
```java
// Multiple directories, each returns a Stream<Path>
directoryPaths.stream()
    .flatMap(this::scanDirectoryToStream)  // Combine all streams into one
    // Result: Single Stream<Path> with files from all directories
```

**Visual:**
```
Directory 1: [file1.txt, file2.txt]  ┐
Directory 2: [file3.txt]              ├─ flatMap → [file1.txt, file2.txt, file3.txt]
Directory 3: [file4.txt, file5.txt]  ┘
```

---

### 6. Try-With-Resources for Streams

**Why?**
Some streams (like `Files.list()`) need to be closed to release resources.

**Syntax:**
```java
try (Stream<Path> stream = Files.list(path)) {
    // Use stream
    stream.forEach(...);
} // Stream automatically closed here
```

**In Our Code:**
```java
try (Stream<Path> fileStream = directoryScanner.scanDirectories(...)) {
    fileStream
        .map(...)
        .filter(...)
        .forEach(...);
} // Stream closed automatically, resources freed
```

---

## Before and After Comparison

### DirectoryScanner - Before

```java
public List<File> scanDirectories(List<String> directoryPaths) {
    logger.info("FRS_0430 Starting directory scan for {} directory(ies)", directoryPaths.size());
    
    List<File> allFiles = new ArrayList<>();  // Mutable collection
    
    for (String directoryPath : directoryPaths) {  // Imperative loop
        try {
            List<File> files = scanDirectory(directoryPath);
            allFiles.addAll(files);  // Mutating the list
            logger.info("FRS_0433 Found {} file(s) in directory: {}", files.size(), directoryPath);
        } catch (Exception e) {
            logger.error("FRS_0432 Failed to scan directory: {}", directoryPath, e);
        }
    }
    
    logger.info("FRS_0431 Directory scan completed. Total files found: {}", allFiles.size());
    return Collections.unmodifiableList(allFiles);
}
```

**Characteristics:**
- ❌ Mutable `ArrayList`
- ❌ Imperative `for` loop
- ❌ Manual exception handling in loop
- ❌ Eager evaluation (processes all files immediately)

---

### DirectoryScanner - After

```java
public Stream<Path> scanDirectories(List<String> directoryPaths) {
    logger.info("FRS_0430 Starting directory scan for {} directory(ies)", directoryPaths.size());
    
    return directoryPaths.stream()           // Create stream
        .flatMap(this::scanDirectoryToStream)  // Combine streams from all directories
        .onClose(() -> logger.info("FRS_0431 Directory scan stream closed"));
}
```

**Characteristics:**
- ✅ Returns `Stream<Path>` (immutable, lazy)
- ✅ Functional `flatMap` operation
- ✅ Exception handling in `scanDirectoryToStream` (returns empty stream on error)
- ✅ Lazy evaluation (processes files only when needed)

---

### ScheduledDirectoryScanner - Before

```java
private void performScan() {
    // ...
    List<File> files = directoryScanner.scanDirectories(config.getSourceDirectories());
    
    int processedCount = 0;  // Mutable counter
    int skippedCount = 0;    // Mutable counter
    
    for (File file : files) {  // Imperative loop
        try {
            FileFingerprint fingerprint = FileFingerprint.fromFileName(file.getName());
            boolean shouldProcess = semaphoreManager.shouldProcessFile(fingerprint);
            
            if (shouldProcess) {
                processedCount++;  // Mutating counter
                logger.info("FRS_0421 File added to Hazelcast: {}", file.getName());
            } else {
                skippedCount++;  // Mutating counter
                logger.debug("FRS_0423 File already in Hazelcast (skip): {}", file.getName());
            }
        } catch (Exception e) {
            logger.error("FRS_0422 Failed to process file: {}", file.getName(), e);
        }
    }
    
    logger.info("FRS_0443 Scan cycle summary: {} new files added, {} files skipped", 
        processedCount, skippedCount);
}
```

**Characteristics:**
- ❌ Mutable counters (`processedCount`, `skippedCount`)
- ❌ Imperative `for` loop
- ❌ Manual exception handling
- ❌ Eager evaluation

---

### ScheduledDirectoryScanner - After

```java
private void performScan() {
    // ...
    AtomicLong processedCount = new AtomicLong(0);  // Thread-safe counter
    AtomicLong skippedCount = new AtomicLong(0);
    
    // Clay's streaming approach
    try (Stream<Path> fileStream = directoryScanner.scanDirectories(...)) {
        fileStream
            .map(this::pathToFingerprint)           // Path → FileFingerprint
            .filter(Objects::nonNull)                // Filter out failures
            .forEach(fingerprint -> processFingerprint(fingerprint, processedCount, skippedCount));
    }
    
    logger.info("FRS_0443 Scan cycle summary: {} new files added, {} files skipped", 
        processedCount.get(), skippedCount.get());
}
```

**Characteristics:**
- ✅ Functional pipeline (map → filter → forEach)
- ✅ Method references (`this::pathToFingerprint`)
- ✅ Lambda expressions
- ✅ Lazy evaluation
- ✅ Proper resource management (try-with-resources)

---

## Step-by-Step Explanation

### Our Stream Pipeline

Let's break down the pipeline in `ScheduledDirectoryScanner.performScan()`:

```java
try (Stream<Path> fileStream = directoryScanner.scanDirectories(...)) {
    fileStream
        .map(this::pathToFingerprint)
        .filter(Objects::nonNull)
        .forEach(fingerprint -> processFingerprint(...));
}
```

#### Step 1: Create Stream
```java
Stream<Path> fileStream = directoryScanner.scanDirectories(...)
```
- **Input:** List of directory paths
- **Output:** Stream of `Path` objects (file paths)
- **What happens:** Scans directories and creates a stream of file paths

**Example:**
```
Input: ["./test-reports", "./archive"]
Output Stream: [Path("report1.txt"), Path("report2.txt"), Path("old.txt")]
```

---

#### Step 2: Map Path to FileFingerprint
```java
.map(this::pathToFingerprint)
```
- **Input:** `Stream<Path>`
- **Output:** `Stream<FileFingerprint>`
- **What happens:** Transforms each `Path` into a `FileFingerprint`

**Method Reference Explained:**
```java
// This is equivalent to:
.map(path -> this.pathToFingerprint(path))

// Where pathToFingerprint is:
private FileFingerprint pathToFingerprint(Path path) {
    String fileName = path.getFileName().toString();
    return FileFingerprint.fromFileName(fileName);
}
```

**Example:**
```
Input:  [Path("report1.txt"), Path("report2.txt")]
Output: [FileFingerprint("report1.txt"), FileFingerprint("report2.txt")]
```

---

#### Step 3: Filter Out Nulls
```java
.filter(Objects::nonNull)
```
- **Input:** `Stream<FileFingerprint>` (may contain nulls)
- **Output:** `Stream<FileFingerprint>` (no nulls)
- **What happens:** Removes any null values (from failed fingerprint creation)

**Method Reference Explained:**
```java
// This is equivalent to:
.filter(fingerprint -> Objects.nonNull(fingerprint))

// Objects.nonNull() returns true if object is not null
```

**Example:**
```
Input:  [FileFingerprint("report1.txt"), null, FileFingerprint("report2.txt")]
Output: [FileFingerprint("report1.txt"), FileFingerprint("report2.txt")]
```

---

#### Step 4: Process Each Fingerprint
```java
.forEach(fingerprint -> processFingerprint(fingerprint, processedCount, skippedCount))
```
- **Input:** `Stream<FileFingerprint>`
- **Output:** None (terminal operation, side effects)
- **What happens:** For each fingerprint, adds to Hazelcast and updates counters

**Lambda Explained:**
```java
// Lambda: fingerprint -> processFingerprint(...)
// For each fingerprint in the stream, call processFingerprint()

// Equivalent to:
for (FileFingerprint fingerprint : stream) {
    processFingerprint(fingerprint, processedCount, skippedCount);
}
```

**Example:**
```
Input: [FileFingerprint("report1.txt"), FileFingerprint("report2.txt")]
Action: 
  - processFingerprint(report1.txt) → adds to Hazelcast
  - processFingerprint(report2.txt) → adds to Hazelcast
```

---

## Common Patterns

### Pattern 1: Transform and Filter

```java
stream
    .map(item -> transform(item))    // Transform
    .filter(item -> isValid(item))   // Filter
    .forEach(item -> process(item)); // Process
```

**Our Example:**
```java
fileStream
    .map(this::pathToFingerprint)    // Transform Path → FileFingerprint
    .filter(Objects::nonNull)         // Filter out nulls
    .forEach(fingerprint -> processFingerprint(...)); // Process
```

---

### Pattern 2: Combine Multiple Streams

```java
listOfLists.stream()
    .flatMap(list -> list.stream())  // Flatten
    .forEach(item -> process(item));
```

**Our Example:**
```java
directoryPaths.stream()
    .flatMap(this::scanDirectoryToStream)  // Combine streams from all directories
    .forEach(path -> process(path));
```

---

### Pattern 3: Count or Collect Results

```java
// Count
long count = stream.count();

// Collect to list
List<Item> items = stream.collect(Collectors.toList());

// Sum
long sum = stream.mapToLong(Item::getValue).sum();
```

**Our Example:**
```java
// We use AtomicLong for thread-safe counting
AtomicLong count = new AtomicLong(0);
stream.forEach(item -> count.incrementAndGet());
```

---

## Best Practices

### 1. Always Close Streams from Files

**Good:**
```java
try (Stream<Path> stream = Files.list(path)) {
    stream.forEach(...);
} // Automatically closed
```

**Bad:**
```java
Stream<Path> stream = Files.list(path);
stream.forEach(...);
// Stream not closed! Resource leak!
```

---

### 2. Use Method References When Possible

**Good:**
```java
.map(this::pathToFingerprint)
.filter(Objects::nonNull)
.forEach(System.out::println)
```

**OK (but verbose):**
```java
.map(path -> this.pathToFingerprint(path))
.filter(obj -> Objects.nonNull(obj))
.forEach(item -> System.out.println(item))
```

---

### 3. Keep Lambdas Simple

**Good:**
```java
.map(this::pathToFingerprint)  // Extract to method if complex
```

**Bad:**
```java
.map(path -> {
    try {
        String fileName = path.getFileName().toString();
        FileFingerprint fp = FileFingerprint.fromFileName(fileName);
        logger.debug("Created fingerprint: {}", fp);
        return fp;
    } catch (Exception e) {
        logger.error("Failed", e);
        return null;
    }
})  // Too complex! Extract to method.
```

---

### 4. Use Descriptive Method Names

**Good:**
```java
private FileFingerprint pathToFingerprint(Path path) { ... }
private void processFingerprint(FileFingerprint fp, ...) { ... }
```

**Bad:**
```java
private FileFingerprint convert(Path p) { ... }  // Not descriptive
private void doIt(FileFingerprint f, ...) { ... }  // Not descriptive
```

---

### 5. Handle Exceptions Properly

**Good:**
```java
private Stream<Path> scanDirectoryToStream(String dirPath) {
    try {
        return Files.list(path).filter(this::isCandidateFile);
    } catch (IOException e) {
        logger.error("Failed to scan: {}", dirPath, e);
        return Stream.empty();  // Return empty stream, don't throw
    }
}
```

**Bad:**
```java
private Stream<Path> scanDirectoryToStream(String dirPath) {
    return Files.list(path).filter(this::isCandidateFile);
    // Exception not handled! Will propagate and break the stream.
}
```

---

## Visual Flow Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                    Stream Pipeline                          │
└─────────────────────────────────────────────────────────────┘

Step 1: Create Stream
┌─────────────────┐
│ Directory Paths │
│ ["./dir1",      │
│  "./dir2"]      │
└────────┬────────┘
         │ .stream()
         ▼
┌─────────────────┐
│ Stream<String>  │
└────────┬────────┘
         │ .flatMap(this::scanDirectoryToStream)
         ▼
┌─────────────────┐
│  Stream<Path>   │
│ [Path("f1.txt"),│
│  Path("f2.txt")]│
└────────┬────────┘
         │ .map(this::pathToFingerprint)
         ▼
┌──────────────────────────┐
│ Stream<FileFingerprint>  │
│ [FP("f1.txt"),           │
│  FP("f2.txt")]           │
└────────┬─────────────────┘
         │ .filter(Objects::nonNull)
         ▼
┌──────────────────────────┐
│ Stream<FileFingerprint>  │
│ (nulls removed)          │
└────────┬─────────────────┘
         │ .forEach(fingerprint -> processFingerprint(...))
         ▼
┌─────────────────┐
│   Side Effects  │
│ - Add to Hazel  │
│ - Update counts │
│ - Log results   │
└─────────────────┘
```

---

## Key Takeaways

1. **Streams are pipelines** - Data flows through transformations
2. **Lazy evaluation** - Operations happen only when needed
3. **Functional style** - Focus on "what" not "how"
4. **Composable** - Easy to add/remove operations
5. **Less boilerplate** - No manual loops and counters
6. **More readable** - Code reads like a recipe

---

## Practice Exercises

### Exercise 1: Simple Stream

Convert this loop to a stream:
```java
List<String> names = List.of("Alice", "Bob", "Charlie", "David");
List<String> longNames = new ArrayList<>();
for (String name : names) {
    if (name.length() > 4) {
        longNames.add(name.toUpperCase());
    }
}
```

**Solution:**
```java
List<String> longNames = names.stream()
    .filter(name -> name.length() > 4)
    .map(String::toUpperCase)
    .collect(Collectors.toList());
```

---

### Exercise 2: Count Files

Count files in multiple directories:
```java
List<String> dirs = List.of("./dir1", "./dir2");
// Use streams to count all files
```

**Solution:**
```java
long totalFiles = dirs.stream()
    .flatMap(dir -> {
        try {
            return Files.list(Paths.get(dir));
        } catch (IOException e) {
            return Stream.empty();
        }
    })
    .filter(Files::isRegularFile)
    .count();
```

---

### Exercise 3: Transform and Filter

Process a list of numbers:
```java
List<Integer> numbers = List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
// Get even numbers, square them, and sum them
```

**Solution:**
```java
int sum = numbers.stream()
    .filter(n -> n % 2 == 0)      // Even numbers
    .map(n -> n * n)               // Square them
    .mapToInt(Integer::intValue)   // Convert to int stream
    .sum();                        // Sum them
// Result: 2² + 4² + 6² + 8² + 10² = 4 + 16 + 36 + 64 + 100 = 220
```

---

## Additional Resources

### Java Streams Documentation
- [Oracle Java Streams Tutorial](https://docs.oracle.com/javase/tutorial/collections/streams/)
- [Java 8 Stream API](https://www.baeldung.com/java-8-streams)

### Lambda Expressions
- [Oracle Lambda Expressions Tutorial](https://docs.oracle.com/javase/tutorial/java/javaOO/lambdaexpressions.html)

### Method References
- [Oracle Method References](https://docs.oracle.com/javase/tutorial/java/javaOO/methodreferences.html)

---

## Summary

We refactored the code to use Java Streams because:
- ✅ **More readable** - Code reads like a pipeline
- ✅ **Less code** - No manual loops and counters
- ✅ **More maintainable** - Easy to modify and extend
- ✅ **Modern Java** - Follows current best practices
- ✅ **Lazy evaluation** - Processes only what's needed

The key concepts:
- **Streams** - Pipelines for data processing
- **Lambdas** - Short functions without names
- **Method References** - Shorthand for lambdas
- **flatMap** - Combine multiple streams
- **Try-with-resources** - Automatic resource management

---

**Document Version:** 1.0  
**Last Updated:** 2025-01-02  
**Target Audience:** Developers learning Java Streams and Functional Programming

