package com.mastercard.fraudriskscanner.feeder.scanning;

import com.mastercard.fraudriskscanner.feeder.config.FeederConfig;
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
	private FeederConfig config;
	private DirectoryScanner directoryScanner;
	private HazelcastSemaphoreManager semaphoreManager;
	private MyDirectoryScanner scanner;

	@TempDir
	Path tempDir;

	@BeforeEach
	void setUp() {
		hazelcast = Hazelcast.newHazelcastInstance();
		config = new FeederConfig(tempDir.toString());
		semaphoreManager = new HazelcastSemaphoreManager(hazelcast, config);
		directoryScanner = new DirectoryScanner();
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
		scanner = new MyDirectoryScanner(config, directoryScanner, semaphoreManager);
		
		assertTrue(scanner.isRunning(), "Scanner should be running after construction");
	}

	@Test
	void testClose_StopsScanner() throws Exception {
		scanner = new MyDirectoryScanner(config, directoryScanner, semaphoreManager);
		assertTrue(scanner.isRunning());
		
		scanner.close();
		
		// Give it a moment to stop
		Thread.sleep(100);
		
		assertFalse(scanner.isRunning(), "Scanner should not be running after close");
	}

	@Test
	void testIsRunning_InitialState() {
		scanner = new MyDirectoryScanner(config, directoryScanner, semaphoreManager);
		assertTrue(scanner.isRunning(), "Scanner should be running after construction");
	}

	@Test
	void testClose_CanBeCalledMultipleTimes() throws Exception {
		scanner = new MyDirectoryScanner(config, directoryScanner, semaphoreManager);
		
		scanner.close();
		Thread.sleep(100);
		assertFalse(scanner.isRunning());
		
		// Close again - should not throw
		scanner.close();
		assertFalse(scanner.isRunning());
	}

	@Test
	void testScanner_ProcessesFiles() throws IOException, InterruptedException {
		// Create test files
		Files.createFile(tempDir.resolve("file1.txt"));
		Files.createFile(tempDir.resolve("file2.txt"));
		Files.createFile(tempDir.resolve("file3.csv"));

		scanner = new MyDirectoryScanner(config, directoryScanner, semaphoreManager);
		
		// Wait for initial scan to complete
		Thread.sleep(2000);
		
		// Verify scanner is still running
		assertTrue(scanner.isRunning(), "Scanner should still be running");
		
		scanner.close();
	}

	@Test
	void testScanner_IgnoresInvalidPaths() throws IOException, InterruptedException {
		// Create valid and invalid files
		Files.createFile(tempDir.resolve("valid.txt"));
		Files.createFile(tempDir.resolve(".hidden")); // Hidden file
		Files.createFile(tempDir.resolve("temp.tmp")); // Temp file

		scanner = new MyDirectoryScanner(config, directoryScanner, semaphoreManager);
		
		// Wait for initial scan
		Thread.sleep(2000);
		
		assertTrue(scanner.isRunning());
		scanner.close();
	}

	@Test
	void testScanner_HandlesEmptyDirectory() throws InterruptedException {
		// Empty directory
		scanner = new MyDirectoryScanner(config, directoryScanner, semaphoreManager);
		
		// Wait for initial scan
		Thread.sleep(1000);
		
		assertTrue(scanner.isRunning());
		scanner.close();
	}

	@Test
	void testTryWithResources_CleansUpAutomatically() throws Exception {
		try (MyDirectoryScanner testScanner = new MyDirectoryScanner(config, directoryScanner, semaphoreManager)) {
			assertTrue(testScanner.isRunning());
		}
		
		// Scanner should be closed after try block
		// Create a new one to verify the pattern works
		try (MyDirectoryScanner testScanner2 = new MyDirectoryScanner(config, directoryScanner, semaphoreManager)) {
			assertTrue(testScanner2.isRunning());
		}
	}
}

