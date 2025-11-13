package com.mastercard.fraudriskscanner.feeder.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for FeederConfig.
 */
class FeederConfigTest {

	@BeforeEach
	void setUp() {
		// Clear environment variables before each test
		clearEnvVars();
	}

	@AfterEach
	void tearDown() {
		// Clear environment variables after each test
		clearEnvVars();
	}

	@Test
	void testDefaultConfiguration() {
		// Create config with no environment variables set
		FeederConfig config = new FeederConfig();

		// Verify defaults
		assertNotNull(config.getSourceDirectories());
		assertFalse(config.getSourceDirectories().isEmpty());
		assertEquals(2, config.getScanIntervalMinutes());
		assertEquals("feeder-file-semaphore", config.getHazelcastMapName());
	}

	@Test
	void testSourceDirectories_FromEnvironment() {
		// Set environment variable
		System.setProperty("FEEDER_SOURCE_DIRECTORIES", "/path1,/path2;/path3");

		FeederConfig config = new FeederConfig();
		List<String> dirs = config.getSourceDirectories();

		// Verify directories are parsed correctly
		assertEquals(3, dirs.size());
		assertTrue(dirs.contains("/path1"));
		assertTrue(dirs.contains("/path2"));
		assertTrue(dirs.contains("/path3"));
	}

	@Test
	void testScanInterval_FromEnvironment() {
		// Set environment variable
		System.setProperty("FEEDER_SCAN_INTERVAL_MINUTES", "5");

		FeederConfig config = new FeederConfig();

		// Verify scan interval
		assertEquals(5, config.getScanIntervalMinutes());
	}

	@Test
	void testScanInterval_InvalidValue() {
		// Set invalid environment variable
		System.setProperty("FEEDER_SCAN_INTERVAL_MINUTES", "invalid");

		FeederConfig config = new FeederConfig();

		// Should use default value
		assertEquals(2, config.getScanIntervalMinutes());
	}

	@Test
	void testScanInterval_NegativeValue() {
		// Set negative value
		System.setProperty("FEEDER_SCAN_INTERVAL_MINUTES", "-1");

		FeederConfig config = new FeederConfig();

		// Should use default value
		assertEquals(2, config.getScanIntervalMinutes());
	}

	@Test
	void testHazelcastMapName_FromEnvironment() {
		// Set environment variable
		System.setProperty("FEEDER_HAZELCAST_MAP_NAME", "custom-map-name");

		FeederConfig config = new FeederConfig();

		// Verify map name
		assertEquals("custom-map-name", config.getHazelcastMapName());
	}

	@Test
	void testSourceDirectories_EmptyString() {
		// Set empty environment variable
		System.setProperty("FEEDER_SOURCE_DIRECTORIES", "");

		FeederConfig config = new FeederConfig();

		// Should use default
		assertNotNull(config.getSourceDirectories());
		assertFalse(config.getSourceDirectories().isEmpty());
	}

	@Test
	void testSourceDirectories_FromCommandLine() {
		// Set environment variable (should be overridden by command-line)
		System.setProperty("FEEDER_SOURCE_DIRECTORIES", "/env/path1,/env/path2");

		// Create config with command-line argument
		FeederConfig config = new FeederConfig("/cmd/path1,/cmd/path2");
		List<String> dirs = config.getSourceDirectories();

		// Verify command-line directories are used (not environment variable)
		assertEquals(2, dirs.size());
		assertTrue(dirs.contains("/cmd/path1"));
		assertTrue(dirs.contains("/cmd/path2"));
		assertFalse(dirs.contains("/env/path1"));
	}

	@Test
	void testSourceDirectories_CommandLineOverridesEnvironment() {
		// Set environment variable
		System.setProperty("FEEDER_SOURCE_DIRECTORIES", "/env/path");

		// Command-line should take precedence
		FeederConfig config = new FeederConfig("/cmd/path");
		List<String> dirs = config.getSourceDirectories();

		assertEquals(1, dirs.size());
		assertTrue(dirs.contains("/cmd/path"));
		assertFalse(dirs.contains("/env/path"));
	}

	@Test
	void testSourceDirectories_CommandLineWithMultipleDirectories() {
		FeederConfig config = new FeederConfig("/path1,/path2;/path3");
		List<String> dirs = config.getSourceDirectories();

		// Verify all directories are parsed (comma and semicolon separated)
		assertEquals(3, dirs.size());
		assertTrue(dirs.contains("/path1"));
		assertTrue(dirs.contains("/path2"));
		assertTrue(dirs.contains("/path3"));
	}

	@Test
	void testSourceDirectories_CommandLineNullFallsBackToEnvironment() {
		// Set environment variable
		System.setProperty("FEEDER_SOURCE_DIRECTORIES", "/env/path");

		// Pass null (no command-line argument)
		FeederConfig config = new FeederConfig(null);
		List<String> dirs = config.getSourceDirectories();

		// Should use environment variable
		assertEquals(1, dirs.size());
		assertTrue(dirs.contains("/env/path"));
	}

	@Test
	void testSourceDirectories_CommandLineEmptyFallsBackToEnvironment() {
		// Set environment variable
		System.setProperty("FEEDER_SOURCE_DIRECTORIES", "/env/path");

		// Pass empty string (no command-line argument)
		FeederConfig config = new FeederConfig("");
		List<String> dirs = config.getSourceDirectories();

		// Should use environment variable
		assertEquals(1, dirs.size());
		assertTrue(dirs.contains("/env/path"));
	}

	/**
	 * Clear environment variables used in tests.
	 */
	private void clearEnvVars() {
		System.clearProperty("FEEDER_SOURCE_DIRECTORIES");
		System.clearProperty("FEEDER_SCAN_INTERVAL_MINUTES");
		System.clearProperty("FEEDER_HAZELCAST_MAP_NAME");
	}
}

