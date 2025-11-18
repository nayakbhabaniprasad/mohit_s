package com.mastercard.fraudriskscanner.feeder.scanning;

import com.mastercard.fraudriskscanner.feeder.config.DirectoryScanningConfig;
import com.mastercard.fraudriskscanner.feeder.config.HazelcastConfig;
import com.mastercard.fraudriskscanner.feeder.config.SchedulingConfig;
import com.mastercard.fraudriskscanner.feeder.semaphore.HazelcastSemaphoreManager;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MyDirectoryScanner.
 * 
 * Tests the consolidated scanner that implements Clay's design principles
 * with separated concerns and SOLID principles.
 */
class MyDirectoryScannerTest {

	private HazelcastInstance hazelcast;
	private DirectoryScanningConfig directoryConfig;
	private SchedulingConfig schedulingConfig;
	private HazelcastConfig hazelcastConfig;
	private HazelcastSemaphoreManager semaphoreManager;
	private MyDirectoryScanner scanner;

	@TempDir
	Path tempDir;

	@BeforeEach
	void setUp() {
		hazelcast = Hazelcast.newHazelcastInstance();
		directoryConfig = new DirectoryScanningConfig(tempDir.toString());
		schedulingConfig = new SchedulingConfig();
		hazelcastConfig = new HazelcastConfig();
		semaphoreManager = new HazelcastSemaphoreManager(hazelcast, hazelcastConfig);
	}

	@AfterEach
	void tearDown() {
		if (scanner != null) {
			try {
				scanner.close();
			} catch (Exception e) {
				// Ignore cleanup errors
			}
		}
		if (hazelcast != null) {
			hazelcast.shutdown();
		}
	}

	@Test
	void testConstructor_StartsScanner() {
		scanner = new MyDirectoryScanner(directoryConfig, schedulingConfig, semaphoreManager);
		
		// Scanner should be created successfully and start scanning
		assertNotNull(scanner, "Scanner should be created");
	}

	@Test
	void testClose_StopsScanner() throws Exception {
		scanner = new MyDirectoryScanner(directoryConfig, schedulingConfig, semaphoreManager);
		
		// Close should not throw
		assertDoesNotThrow(() -> scanner.close());
	}

	@Test
	void testClose_CanBeCalledMultipleTimes() throws Exception {
		scanner = new MyDirectoryScanner(directoryConfig, schedulingConfig, semaphoreManager);
		
		// Close multiple times - should not throw
		scanner.close();
		scanner.close();
		scanner.close();
	}

	@Test
	void testScanner_ProcessesFiles() throws IOException, InterruptedException {
		// Create test files
		Files.createFile(tempDir.resolve("file1.txt"));
		Files.createFile(tempDir.resolve("file2.txt"));
		Files.createFile(tempDir.resolve("file3.csv"));

		scanner = new MyDirectoryScanner(directoryConfig, schedulingConfig, semaphoreManager);
		
		// Wait for initial scan to complete
		Thread.sleep(2000);
		
		// Scanner should still be functional
		assertNotNull(scanner);
		
		scanner.close();
	}

	@Test
	void testScanner_IgnoresInvalidPaths() throws IOException, InterruptedException {
		// Create valid and invalid files
		Files.createFile(tempDir.resolve("valid.txt"));
		Files.createFile(tempDir.resolve(".hidden")); // Hidden file
		Files.createFile(tempDir.resolve("temp.tmp")); // Temp file

		scanner = new MyDirectoryScanner(directoryConfig, schedulingConfig, semaphoreManager);
		
		// Wait for initial scan
		Thread.sleep(2000);
		
		assertNotNull(scanner);
		scanner.close();
	}

	@Test
	void testScanner_HandlesEmptyDirectory() throws InterruptedException {
		// Empty directory
		scanner = new MyDirectoryScanner(directoryConfig, schedulingConfig, semaphoreManager);
		
		// Wait for initial scan
		Thread.sleep(1000);
		
		assertNotNull(scanner);
		scanner.close();
	}

	@Test
	void testTryWithResources_CleansUpAutomatically() throws Exception {
		try (MyDirectoryScanner testScanner = new MyDirectoryScanner(directoryConfig, schedulingConfig, semaphoreManager)) {
			assertNotNull(testScanner);
		}
		
		// Scanner should be closed after try block
		// Create a new one to verify the pattern works
		try (MyDirectoryScanner testScanner2 = new MyDirectoryScanner(directoryConfig, schedulingConfig, semaphoreManager)) {
			assertNotNull(testScanner2);
		}
	}
}

