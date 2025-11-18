package com.mastercard.fraudriskscanner.feeder.semaphore;

import com.hazelcast.map.IMap;
import com.mastercard.fraudriskscanner.feeder.config.HazelcastConfig;
import com.mastercard.fraudriskscanner.feeder.model.MGTuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

/**
 * Manages the expiring semaphore logic for single processing of files.
 * 
 * Uses a bounded fingerprint map in Hazelcast to ensure only one instance
 * processes a given file, even in a clustered environment.
 * 
 * Algorithm:
 * 1. Calculate MGTuple from file path
 * 2. Get map key (0-65535) and fingerprint bytes (8 bytes)
 * 3. Atomically check if key exists AND fingerprint matches
 * 4. If present → skip file (already processed)
 * 5. If not present → add fingerprint and process file
 * 
 * @author BizOps Bank Team
 */
public final class HazelcastSemaphoreManager {
	
	private static final Logger logger = LoggerFactory.getLogger(HazelcastSemaphoreManager.class);
	
	private final IMap<Integer, byte[]> semaphoreMap;
	private final String mapName;
	
	/**
	 * Create a semaphore manager.
	 * 
	 * @param hazelcast Hazelcast instance
	 * @param hazelcastConfig Hazelcast configuration
	 */
	public HazelcastSemaphoreManager(com.hazelcast.core.HazelcastInstance hazelcast, HazelcastConfig hazelcastConfig) {
		this.mapName = hazelcastConfig.getMapName();
		this.semaphoreMap = hazelcast.getMap(mapName);
		
		logger.info("FRS_0420 HazelcastSemaphoreManager initialized with map: {}", mapName);
	}
	
	/**
	 * Check if a file should be processed.
	 * 
	 * This performs an atomic check-and-set operation:
	 * - If file is already in the map (key exists AND fingerprint matches) → return false (skip)
	 * - If file is not in the map → add it and return true (process)
	 * 
	 * @param tuple file tuple (MGTuple)
	 * @return true if file should be processed, false if it should be skipped
	 * @throws IllegalStateException if Hazelcast operation fails
	 */
	public boolean shouldProcessFile(MGTuple tuple) {
		if (tuple == null) {
			throw new IllegalArgumentException("FRS_0422 Tuple cannot be null");
		}
		
		try {
			int mapKey = tuple.boundingHash();
			byte[] fingerprintBytes = tuple.fingerprintHash();
			
			logger.debug("FRS_0420 Checking semaphore (mapKey: {})", mapKey);
			
			// Atomic check-and-set operation
			// Use putIfAbsent: returns null if key didn't exist (we should process)
			// Returns existing value if key exists (we should check if it matches)
			byte[] existingFingerprint = semaphoreMap.putIfAbsent(mapKey, fingerprintBytes);
			
			if (existingFingerprint == null) {
				// Key didn't exist - we added it, so we should process
				logger.info("FRS_0421 File marked as processed (mapKey: {})", mapKey);
				return true;
			}
			
			// Key exists - check if fingerprint matches
			if (Arrays.equals(existingFingerprint, fingerprintBytes)) {
				// Fingerprint matches - file already processed
				logger.debug("FRS_0423 File already processed (skip) (mapKey: {})", mapKey);
				return false;
			}
			
			// Key exists but fingerprint doesn't match - collision!
			// This is expected with bounded map (different files can have same map key)
			// We should still process this file (it's a different file)
			logger.warn("FRS_0420 Map key collision detected for mapKey: {} (different fingerprints)", mapKey);
			
			// Replace with new fingerprint (overwrite collision)
			semaphoreMap.put(mapKey, fingerprintBytes);
			logger.info("FRS_0421 File marked as processed (collision resolved) (mapKey: {})", mapKey);
			return true;
			
		} catch (Exception e) {
			logger.error("FRS_0422 Semaphore operation failed", e);
			throw new IllegalStateException("FRS_0422 Semaphore operation failed", e);
		}
	}
	
	/**
	 * Explicitly mark a file as processed.
	 * 
	 * @param tuple file tuple (MGTuple)
	 */
	public void markFileAsProcessed(MGTuple tuple) {
		if (tuple == null) {
			throw new IllegalArgumentException("FRS_0422 Tuple cannot be null");
		}
		
		try {
			int mapKey = tuple.boundingHash();
			byte[] fingerprintBytes = tuple.fingerprintHash();
			
			semaphoreMap.put(mapKey, fingerprintBytes);
			logger.debug("FRS_0421 File explicitly marked as processed (mapKey: {})", mapKey);
			
		} catch (Exception e) {
			logger.error("FRS_0422 Failed to mark file as processed", e);
			throw new IllegalStateException("FRS_0422 Failed to mark file as processed", e);
		}
	}
	
	/**
	 * Check if a file is already processed (without modifying the map).
	 * 
	 * @param tuple file tuple (MGTuple)
	 * @return true if file is already processed
	 */
	public boolean isFileProcessed(MGTuple tuple) {
		if (tuple == null) {
			return false;
		}
		
		try {
			int mapKey = tuple.boundingHash();
			byte[] fingerprintBytes = tuple.fingerprintHash();
			byte[] existingFingerprint = semaphoreMap.get(mapKey);
			
			if (existingFingerprint == null) {
				return false;
			}
			
			return Arrays.equals(existingFingerprint, fingerprintBytes);
			
		} catch (Exception e) {
			logger.error("FRS_0422 Failed to check if file is processed", e);
			return false; // On error, assume not processed
		}
	}
	
	/**
	 * Get the Hazelcast map being used.
	 * 
	 * @return IMap instance
	 */
	public IMap<Integer, byte[]> getSemaphoreMap() {
		return semaphoreMap;
	}
	
	/**
	 * Get the map name.
	 * 
	 * @return map name
	 */
	public String getMapName() {
		return mapName;
	}
}

