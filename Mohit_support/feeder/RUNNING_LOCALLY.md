# Running Feeder Application Locally

This guide explains how to run the Feeder application locally with command-line arguments.

## Prerequisites

- Java 17 or higher
- Maven 3.6+ installed
- Test directory created (optional, for testing)

## Method 1: Using Maven Exec Plugin (Recommended for Development)

The `exec-maven-plugin` is already configured in `pom.xml`. Use it to run the application directly from Maven.

### Single Directory (Positional Argument)

```bash
# Windows PowerShell
cd Mohit_support/feeder
mvn exec:java -Dexec.args="./test-reports"

# Windows CMD
cd Mohit_support\feeder
mvn exec:java "-Dexec.args=./test-reports"

# Linux/Mac
cd Mohit_support/feeder
mvn exec:java -Dexec.args="./test-reports"
```

### Single Directory (With Flag)

```bash
# Windows PowerShell
mvn exec:java -Dexec.args="--source-directories ./test-reports"

# Windows CMD
mvn exec:java "-Dexec.args=--source-directories ./test-reports"

# Linux/Mac
mvn exec:java -Dexec.args="--source-directories ./test-reports"
```

### Multiple Directories

```bash
# Windows PowerShell
mvn exec:java -Dexec.args="--source-directories ./test-reports,./test-reports2"

# Windows CMD
mvn exec:java "-Dexec.args=--source-directories ./test-reports,./test-reports2"

# Linux/Mac
mvn exec:java -Dexec.args="--source-directories ./test-reports,./test-reports2"
```

### Short Flag Format

```bash
# Windows PowerShell
mvn exec:java -Dexec.args="-d ./test-reports"

# Windows CMD
mvn exec:java "-Dexec.args=-d ./test-reports"

# Linux/Mac
mvn exec:java -Dexec.args="-d ./test-reports"
```

## Method 2: Build and Run JAR File

First, build the application:

```bash
# Windows PowerShell
cd Mohit_support/feeder
mvn clean package

# Windows CMD
cd Mohit_support\feeder
mvn clean package

# Linux/Mac
cd Mohit_support/feeder
mvn clean package
```

This creates an executable JAR in `target/fraud-risk-scanner-feeder-0.1.0.jar`.

### Run with Command-Line Arguments

```bash
# Windows PowerShell
java -jar target/fraud-risk-scanner-feeder-0.1.0.jar ./test-reports

# Windows CMD
java -jar target\fraud-risk-scanner-feeder-0.1.0.jar ./test-reports

# Linux/Mac
java -jar target/fraud-risk-scanner-feeder-0.1.0.jar ./test-reports
```

### Run with Flag Format

```bash
# Windows PowerShell
java -jar target/fraud-risk-scanner-feeder-0.1.0.jar --source-directories ./test-reports

# Windows CMD
java -jar target\fraud-risk-scanner-feeder-0.1.0.jar --source-directories ./test-reports

# Linux/Mac
java -jar target/fraud-risk-scanner-feeder-0.1.0.jar --source-directories ./test-reports
```

### Run with Multiple Directories

```bash
# Windows PowerShell
java -jar target/fraud-risk-scanner-feeder-0.1.0.jar --source-directories ./test-reports,./test-reports2

# Windows CMD
java -jar target\fraud-risk-scanner-feeder-0.1.0.jar --source-directories ./test-reports,./test-reports2

# Linux/Mac
java -jar target/fraud-risk-scanner-feeder-0.1.0.jar --source-directories ./test-reports,./test-reports2
```

## Method 3: Using Environment Variables (Fallback)

If no command-line arguments are provided, the application will check environment variables:

```bash
# Windows PowerShell
$env:FEEDER_SOURCE_DIRECTORIES = ".\test-reports"
mvn exec:java

# Windows CMD
set FEEDER_SOURCE_DIRECTORIES=.\test-reports
mvn exec:java

# Linux/Mac
export FEEDER_SOURCE_DIRECTORIES="./test-reports"
mvn exec:java
```

## Method 4: Using IDE (IntelliJ IDEA / Eclipse)

### IntelliJ IDEA

1. Right-click on `FeederApplication.java`
2. Select "Run 'FeederApplication.main()'"
3. Click "Edit Configurations..."
4. In "Program arguments", enter:
   - `./test-reports` (positional)
   - OR `--source-directories ./test-reports` (flag)
   - OR `-d ./test-reports` (short flag)
5. Click "OK" and run

### Eclipse

1. Right-click on `FeederApplication.java`
2. Select "Run As" → "Java Application"
3. Right-click on the run configuration → "Run Configurations..."
4. Go to "Arguments" tab
5. In "Program arguments", enter:
   - `./test-reports` (positional)
   - OR `--source-directories ./test-reports` (flag)
6. Click "Run"

## Quick Test Setup

Create a test directory with some files:

```bash
# Windows PowerShell
mkdir test-reports
echo "test content" > test-reports\file1.txt
echo "test content" > test-reports\file2.txt

# Windows CMD
mkdir test-reports
echo test content > test-reports\file1.txt
echo test content > test-reports\file2.txt

# Linux/Mac
mkdir -p test-reports
echo "test content" > test-reports/file1.txt
echo "test content" > test-reports/file2.txt
```

## Example Output

When you run the application, you should see:

```
[INFO] FRS_0200 Starting Feeder Application
[INFO] FRS_0400 Feeder configuration loaded:
[INFO] FRS_0400   Source directories: [./test-reports] (from command-line)
[INFO] FRS_0400   Scan interval: 2 minutes
[INFO] FRS_0400   Hazelcast map name: feeder-file-semaphore
[INFO] Hazelcast started. Member: feeder-member
[INFO] FRS_0440 Starting scheduled directory scanner
[INFO] FRS_0442 Starting scan cycle
[INFO] FRS_0433 Found 2 file(s) in directory: ./test-reports
[INFO] FRS_0443 Scan cycle completed. Found 2 file(s)
```

## Stopping the Application

Press `Ctrl+C` to stop the application gracefully. You should see:

```
[INFO] FRS_0202 Shutdown signal received
[INFO] FRS_0441 Stopping scheduled directory scanner
[INFO] FRS_0202 Feeder Application stopped
```

## Troubleshooting

### Issue: "mvn: command not found"
**Solution**: Make sure Maven is installed and in your PATH.

### Issue: "java: command not found"
**Solution**: Make sure Java 17+ is installed and in your PATH.

### Issue: Directory not found
**Solution**: Make sure the directory path exists. Use absolute paths if relative paths don't work:
```bash
# Windows
mvn exec:java -Dexec.args="C:\full\path\to\directory"

# Linux/Mac
mvn exec:java -Dexec.args="/full/path/to/directory"
```

### Issue: Arguments not being parsed
**Solution**: Make sure to use quotes around arguments in PowerShell/CMD:
```bash
# Windows PowerShell - Use quotes
mvn exec:java -Dexec.args="./test-reports"

# Windows CMD - Use quotes
mvn exec:java "-Dexec.args=./test-reports"
```

## Command-Line Argument Formats Summary

| Format | Example | Description |
|--------|---------|-------------|
| Positional | `./test-reports` | First argument is treated as directory |
| Long flag | `--source-directories ./test-reports` | Explicit flag format |
| Short flag | `-d ./test-reports` | Short flag format |
| Multiple dirs | `--source-directories /path1,/path2` | Comma-separated |
| Multiple dirs | `--source-directories /path1;/path2` | Semicolon-separated |

## Priority Order

The application uses source directories in this priority:
1. **Command-line arguments** (highest priority)
2. **Environment variable** `FEEDER_SOURCE_DIRECTORIES`
3. **Default** `./test-reports` (lowest priority)

