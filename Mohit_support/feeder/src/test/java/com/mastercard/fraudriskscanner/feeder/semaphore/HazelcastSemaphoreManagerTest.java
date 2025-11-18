package com.mastercard.fraudriskscanner.feeder.semaphore;

import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.mastercard.fraudriskscanner.feeder.config.HazelcastConfig;
import com.mastercard.fraudriskscanner.feeder.model.MGTuple;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for HazelcastSemaphoreManager.
 * Uses embedded Hazelcast instance for testing.
 */
class HazelcastSemaphoreManagerTest {

	private HazelcastInstance hazelcast;
	private HazelcastSemaphoreManager semaphoreManager;
	private HazelcastConfig hazelcastConfig;

	@BeforeEach
	void setUp() {
		// Create embedded Hazelcast instance for testing
		Config cfg = new Config();
		cfg.setClusterName("test-cluster");
		hazelcast = Hazelcast.newHazelcastInstance(cfg);
		
		// Create config
		hazelcastConfig = new HazelcastConfig();
		
		// Create semaphore manager
		semaphoreManager = new HazelcastSemaphoreManager(hazelcast, hazelcastConfig);
	}

	@AfterEach
	void tearDown() {
		if (hazelcast != null) {
			hazelcast.shutdown();
		}
	}

	@Test
	void testShouldProcessFile_NewFile_ReturnsTrue() {
		// Create tuple for new file
		MGTuple tuple = MGTuple.fromPath("newfile.txt");
		
		// Should process (file not in Hazelcast yet)
		boolean shouldProcess = semaphoreManager.shouldProcessFile(tuple);
		
		assertTrue(shouldProcess, "New file should be processed");
	}

	@Test
	void testShouldProcessFile_SameFileTwice_SecondTimeReturnsFalse() {
		// Create tuple
		MGTuple tuple = MGTuple.fromPath("report.txt");
		
		// First time - should process
		boolean first = semaphoreManager.shouldProcessFile(tuple);
		assertTrue(first, "First check should return true");
		
		// Second time - should skip (already in Hazelcast)
		boolean second = semaphoreManager.shouldProcessFile(tuple);
		assertFalse(second, "Second check should return false (skip)");
	}

	@Test
	void testShouldProcessFile_DifferentFiles_SameMapKey_ShouldProcessBoth() {
		// Create two different files that might have same map key (collision)
		// Note: We can't guarantee collision, but we test the logic
		MGTuple tuple1 = MGTuple.fromPath("file1.txt");
		MGTuple tuple2 = MGTuple.fromPath("file2.txt");
		
		// Both should be processable
		boolean shouldProcess1 = semaphoreManager.shouldProcessFile(tuple1);
		boolean shouldProcess2 = semaphoreManager.shouldProcessFile(tuple2);
		
		assertTrue(shouldProcess1, "First file should be processed");
		assertTrue(shouldProcess2, "Second file should be processed (even if same map key)");
	}

	@Test
	void testMarkFileAsProcessed() {
		// Create tuple
		MGTuple tuple = MGTuple.fromPath("report.txt");
		
		// Mark as processed
		assertDoesNotThrow(() -> {
			semaphoreManager.markFileAsProcessed(tuple);
		});
		
		// Check if it's processed
		boolean isProcessed = semaphoreManager.isFileProcessed(tuple);
		assertTrue(isProcessed, "File should be marked as processed");
	}

	@Test
	void testIsFileProcessed_NewFile_ReturnsFalse() {
		// Create tuple for new file
		MGTuple tuple = MGTuple.fromPath("newfile.txt");
		
		// Should not be processed yet
		boolean isProcessed = semaphoreManager.isFileProcessed(tuple);
		assertFalse(isProcessed, "New file should not be processed");
	}

	@Test
	void testIsFileProcessed_AfterProcessing_ReturnsTrue() {
		// Create tuple
		MGTuple tuple = MGTuple.fromPath("report.txt");
		
		// Process file (adds to Hazelcast)
		semaphoreManager.shouldProcessFile(tuple);
		
		// Check if processed
		boolean isProcessed = semaphoreManager.isFileProcessed(tuple);
		assertTrue(isProcessed, "File should be processed after shouldProcessFile");
	}

	@Test
	void testShouldProcessFile_NullTuple_ThrowsException() {
		// Should throw exception for null tuple
		assertThrows(IllegalArgumentException.class, () -> {
			semaphoreManager.shouldProcessFile(null);
		});
	}

	@Test
	void testMarkFileAsProcessed_NullTuple_ThrowsException() {
		// Should throw exception for null tuple
		assertThrows(IllegalArgumentException.class, () -> {
			semaphoreManager.markFileAsProcessed(null);
		});
	}

	@Test
	void testIsFileProcessed_NullTuple_ReturnsFalse() {
		// Should return false for null tuple
		boolean isProcessed = semaphoreManager.isFileProcessed(null);
		assertFalse(isProcessed);
	}

	@Test
	void testGetSemaphoreMap() {
		// Should return the Hazelcast map
		assertNotNull(semaphoreManager.getSemaphoreMap());
	}

	@Test
	void testGetMapName() {
		// Should return the map name
		assertNotNull(semaphoreManager.getMapName());
		assertEquals("feeder-file-semaphore", semaphoreManager.getMapName());
	}
}

