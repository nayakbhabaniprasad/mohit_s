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
	
	/**
	 * Load configuration from environment variables.
	 */
	public FeederConfig() {
		this.sourceDirectories = loadSourceDirectories();
		this.scanIntervalMinutes = loadScanIntervalMinutes();
		this.hazelcastMapName = loadHazelcastMapName();
		
		validate();
		logConfiguration();
	}
	
	/**
	 * Load source directories from environment variable.
	 * Supports comma or semicolon separated values.
	 */
	private List<String> loadSourceDirectories() {
		String envValue = getEnv("FEEDER_SOURCE_DIRECTORIES", "");
		
		if (envValue == null || envValue.trim().isEmpty()) {
			logger.warn("FRS_0401 No source directories configured. Using default: ./test-reports");
			return Collections.singletonList("./test-reports");
		}
		
		List<String> directories = new ArrayList<>();
		String[] dirs = envValue.split("[;,]");
		
		for (String dir : dirs) {
			String trimmed = dir.trim();
			if (!trimmed.isEmpty()) {
				directories.add(trimmed);
			}
		}
		
		return Collections.unmodifiableList(directories);
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
		logger.info("FRS_0400   Source directories: {}", sourceDirectories);
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

