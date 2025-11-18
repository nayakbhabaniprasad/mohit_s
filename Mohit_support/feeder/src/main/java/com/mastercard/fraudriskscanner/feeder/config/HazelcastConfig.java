package com.mastercard.fraudriskscanner.feeder.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Configuration for Hazelcast.
 * 
 * Handles Hazelcast map names and other Hazelcast-related settings.
 * 
 * @author Mohit Gujar
 */
public final class HazelcastConfig {
	
	private static final Logger logger = LoggerFactory.getLogger(HazelcastConfig.class);
	
	private static final String DEFAULT_HAZELCAST_MAP_NAME = "feeder-file-semaphore";
	
	private final String mapName;
	
	/**
	 * Create Hazelcast configuration.
	 */
	public HazelcastConfig() {
		// Load Hazelcast map name from environment variable
		String mapNameValue = getEnv("FEEDER_HAZELCAST_MAP_NAME", DEFAULT_HAZELCAST_MAP_NAME);
		
		// Validate - part of constructor
		if (mapNameValue == null || mapNameValue.trim().isEmpty()) {
			throw new IllegalStateException("FRS_0401 Hazelcast map name must be configured");
		}
		
		this.mapName = mapNameValue.trim();
		
		logger.info("FRS_0400 Hazelcast configuration: map name = {}", mapName);
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
	 * Get Hazelcast map name for semaphore storage.
	 * 
	 * @return Hazelcast map name
	 */
	public String getMapName() {
		return mapName;
	}
}

