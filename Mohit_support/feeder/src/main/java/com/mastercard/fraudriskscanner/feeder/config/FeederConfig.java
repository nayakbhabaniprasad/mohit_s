package com.mastercard.fraudriskscanner.feeder.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Configuration class for Feeder application.
 * Reads configuration from environment variables (Habitat-compatible).
 * 
 * @author BizOps Bank Team
 */
public final class FeederConfig {
	
	private static final Logger logger = LoggerFactory.getLogger(FeederConfig.class);
	
	// Default values
	private static final int DEFAULT_SCAN_INTERVAL_MINUTES = 2;
	private static final String DEFAULT_HAZELCAST_MAP_NAME = "feeder-file-semaphore";
	
	// Configuration properties
	private final List<String> sourceDirectories;
	private final int scanIntervalMinutes;
	private final String hazelcastMapName;
	private final String sourceDirectoriesSource; // Track where source directories came from
	
	/**
	 * Load configuration from command-line arguments, environment variables, or defaults.
	 * Priority: Command-line args > Environment variables > Defaults
	 * 
	 * @param commandLineSourceDirectories source directories from command line (can be null)
	 */
	public FeederConfig(String commandLineSourceDirectories) {
		SourceDirectoriesResult result = loadSourceDirectories(commandLineSourceDirectories);
		this.sourceDirectories = result.directories;
		this.sourceDirectoriesSource = result.source;
		this.scanIntervalMinutes = loadScanIntervalMinutes();
		this.hazelcastMapName = loadHazelcastMapName();
		
		validate();
		logConfiguration();
	}
	
	/**
	 * Result of loading source directories.
	 */
	private static class SourceDirectoriesResult {
		final List<String> directories;
		final String source;
		
		SourceDirectoriesResult(List<String> directories, String source) {
			this.directories = directories;
			this.source = source;
		}
	}
	
	/**
	 * Load configuration from environment variables (for backward compatibility).
	 * This constructor is equivalent to calling FeederConfig(null).
	 */
	public FeederConfig() {
		this(null);
	}
	
	/**
	 * Load source directories with priority: command-line > environment variable > default.
	 * Supports comma or semicolon separated values.
	 * 
	 * @param commandLineValue source directories from command line (can be null)
	 * @return result containing directories and source information
	 */
	private SourceDirectoriesResult loadSourceDirectories(String commandLineValue) {
		String sourceDirectoriesValue = null;
		String source = null;
		
		// Priority 1: Command-line argument
		if (commandLineValue != null && !commandLineValue.trim().isEmpty()) {
			sourceDirectoriesValue = commandLineValue.trim();
			source = "command-line";
			logger.debug("FRS_0400 Using source directories from command line: {}", sourceDirectoriesValue);
		} else {
			// Priority 2: Environment variable
			sourceDirectoriesValue = getEnv("FEEDER_SOURCE_DIRECTORIES", "");
			
			if (sourceDirectoriesValue != null && !sourceDirectoriesValue.trim().isEmpty()) {
				source = "environment variable";
				logger.debug("FRS_0400 Using source directories from environment variable: {}", sourceDirectoriesValue);
			} else {
				// Priority 3: Default
				source = "default";
				logger.warn("FRS_0401 No source directories configured. Using default: ./test-reports");
				return new SourceDirectoriesResult(
					Collections.singletonList("./test-reports"),
					source
				);
			}
		}
		
		// Parse comma or semicolon separated directories
		List<String> directories = new ArrayList<>();
		String[] dirs = sourceDirectoriesValue.split("[;,]");
		
		for (String dir : dirs) {
			String trimmed = dir.trim();
			if (!trimmed.isEmpty()) {
				directories.add(trimmed);
			}
		}
		
		return new SourceDirectoriesResult(
			Collections.unmodifiableList(directories),
			source
		);
	}
	
	/**
	 * Load scan interval from environment variable.
	 */
	private int loadScanIntervalMinutes() {
		String envValue = getEnv("FEEDER_SCAN_INTERVAL_MINUTES", String.valueOf(DEFAULT_SCAN_INTERVAL_MINUTES));
		
		try {
			int interval = Integer.parseInt(envValue);
			if (interval <= 0) {
				logger.warn("FRS_0401 Invalid scan interval: {}. Using default: {}", interval, DEFAULT_SCAN_INTERVAL_MINUTES);
				return DEFAULT_SCAN_INTERVAL_MINUTES;
			}
			return interval;
		} catch (NumberFormatException e) {
			logger.warn("FRS_0401 Invalid scan interval format: {}. Using default: {}", envValue, DEFAULT_SCAN_INTERVAL_MINUTES);
			return DEFAULT_SCAN_INTERVAL_MINUTES;
		}
	}
	
	/**
	 * Load Hazelcast map name from environment variable.
	 */
	private String loadHazelcastMapName() {
		return getEnv("FEEDER_HAZELCAST_MAP_NAME", DEFAULT_HAZELCAST_MAP_NAME);
	}
	
	/**
	 * Get environment variable with fallback to system property, then default value.
	 */
	private String getEnv(String key, String defaultValue) {
		String value = System.getenv(key);
		if (value == null || value.isEmpty()) {
			value = System.getProperty(key, defaultValue);
		}
		return value;
	}
	
	/**
	 * Validate configuration.
	 * 
	 * @throws IllegalStateException if configuration is invalid
	 */
	private void validate() {
		if (sourceDirectories.isEmpty()) {
			throw new IllegalStateException("FRS_0401 At least one source directory must be configured");
		}
		
		if (scanIntervalMinutes <= 0) {
			throw new IllegalStateException("FRS_0401 Scan interval must be greater than 0");
		}
		
		if (hazelcastMapName == null || hazelcastMapName.trim().isEmpty()) {
			throw new IllegalStateException("FRS_0401 Hazelcast map name must be configured");
		}
	}
	
	/**
	 * Log configuration on startup.
	 */
	private void logConfiguration() {
		logger.info("FRS_0400 Feeder configuration loaded:");
		logger.info("FRS_0400   Source directories: {} (from {})", sourceDirectories, sourceDirectoriesSource);
		logger.info("FRS_0400   Scan interval: {} minutes", scanIntervalMinutes);
		logger.info("FRS_0400   Hazelcast map name: {}", hazelcastMapName);
	}
	
	/**
	 * Get source directories for scanning.
	 * 
	 * @return unmodifiable list of source directory paths
	 */
	public List<String> getSourceDirectories() {
		return sourceDirectories;
	}
	
	/**
	 * Get scan interval in minutes.
	 * 
	 * @return scan interval in minutes
	 */
	public int getScanIntervalMinutes() {
		return scanIntervalMinutes;
	}
	
	/**
	 * Get Hazelcast map name for semaphore storage.
	 * 
	 * @return Hazelcast map name
	 */
	public String getHazelcastMapName() {
		return hazelcastMapName;
	}
}

