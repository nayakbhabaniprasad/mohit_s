package com.mastercard.fraudriskscanner.feeder.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Configuration for scheduling/threading.
 * 
 * Handles scan interval and other scheduling-related settings.
 * 
 * @author Mohit Gujar
 */
public final class SchedulingConfig {
	
	private static final Logger logger = LoggerFactory.getLogger(SchedulingConfig.class);
	
	private static final int DEFAULT_SCAN_INTERVAL_MINUTES = 2;
	
	private final int scanIntervalMinutes;
	
	/**
	 * Create scheduling configuration.
	 */
	public SchedulingConfig() {
		// Load scan interval from environment variable
		String envValue = getEnv("FEEDER_SCAN_INTERVAL_MINUTES", String.valueOf(DEFAULT_SCAN_INTERVAL_MINUTES));
		
		int interval;
		try {
			interval = Integer.parseInt(envValue);
		} catch (NumberFormatException e) {
			logger.warn("FRS_0401 Invalid scan interval format: {}. Using default: {}", envValue, DEFAULT_SCAN_INTERVAL_MINUTES);
			interval = DEFAULT_SCAN_INTERVAL_MINUTES;
		}
		
		// Validate - part of constructor
		if (interval <= 0) {
			logger.warn("FRS_0401 Invalid scan interval: {}. Using default: {}", interval, DEFAULT_SCAN_INTERVAL_MINUTES);
			interval = DEFAULT_SCAN_INTERVAL_MINUTES;
		}
		
		this.scanIntervalMinutes = interval;
		
		logger.info("FRS_0400 Scheduling configuration: scan interval = {} minutes", scanIntervalMinutes);
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
	 * Get scan interval in minutes.
	 * 
	 * @return scan interval in minutes
	 */
	public int getScanIntervalMinutes() {
		return scanIntervalMinutes;
	}
}

