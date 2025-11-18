package com.mastercard.fraudriskscanner.feeder.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Configuration for directory scanning.
 * 
 * Handles source directory paths and scanning-related settings.
 * 
 * @author Mohit Gujar
 */
public final class DirectoryScanningConfig {
	
	private static final Logger logger = LoggerFactory.getLogger(DirectoryScanningConfig.class);
	
	private final List<String> sourceDirectories;
	
	/**
	 * Create directory scanning configuration.
	 * 
	 * File paths come from command-line parameters only (per story requirements).
	 * 
	 * @param sourceDirectories source directories from command line (must not be null or empty)
	 * @throws IllegalArgumentException if sourceDirectories is null or empty
	 */
	public DirectoryScanningConfig(String sourceDirectories) {
		// Validate input - part of constructor
		if (sourceDirectories == null || sourceDirectories.trim().isEmpty()) {
			throw new IllegalArgumentException("FRS_0401 Source directories must be provided via command-line parameter");
		}
		
		// Parse comma or semicolon separated directories
		List<String> directories = new ArrayList<>();
		String[] dirs = sourceDirectories.trim().split("[;,]");
		
		for (String dir : dirs) {
			String trimmed = dir.trim();
			if (!trimmed.isEmpty()) {
				directories.add(trimmed);
			}
		}
		
		// Validate parsed result - part of constructor
		if (directories.isEmpty()) {
			throw new IllegalArgumentException("FRS_0401 At least one source directory must be provided");
		}
		
		this.sourceDirectories = Collections.unmodifiableList(directories);
		
		logger.info("FRS_0400 Directory scanning configuration: {} directory(ies) from command-line parameter", 
			this.sourceDirectories.size());
	}
	
	/**
	 * Get source directories for scanning.
	 * 
	 * @return unmodifiable list of source directory paths
	 */
	public List<String> getSourceDirectories() {
		return sourceDirectories;
	}
}

