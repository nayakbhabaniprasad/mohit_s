package com.mastercard.fraudriskscanner.feeder.scanning;

import com.mastercard.fraudriskscanner.feeder.config.DirectoryScanningConfig;
import com.mastercard.fraudriskscanner.feeder.config.SchedulingConfig;
import com.mastercard.fraudriskscanner.feeder.model.MGTuple;
import com.mastercard.fraudriskscanner.feeder.semaphore.HazelcastSemaphoreManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.AbstractMap;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

/**
 * Directory scanner that periodically scans directories and adds file tuples to a map.
 * 
 * This class implements Clay's design principles:
 * - Core operation stands out: addTupleToMap(path, tuple)
 * - All underpinnings hidden in private methods (separated concerns)
 * - Clean, simple interface with AutoCloseable for resource management
 * - No null filtering - explicit error handling with Optional
 * - No state management - starts immediately in constructor, stops only on close
 * 
 * Uses internal private methods to maintain SOLID principles:
 * - Single Responsibility: Each private method has one job
 * - Clean separation of concerns through method organization
 * 
 * @author BizOps Bank Team
 */
public final class MyDirectoryScanner implements AutoCloseable {
	
	private static final Logger logger = LoggerFactory.getLogger(MyDirectoryScanner.class);
	
	// Configuration - separated concerns
	private final DirectoryScanningConfig directoryConfig;
	private final SchedulingConfig schedulingConfig;
	
	// Dependencies
	private final HazelcastSemaphoreManager semaphoreManager;
	
	// Scheduling infrastructure - created and started immediately in constructor
	private final ScheduledExecutorService scheduler;
	
	/**
	 * Create and start a directory scanner.
	 * 
	 * The scheduler is created and started immediately - no separate start() method needed.
	 * Thread management is hidden inside this class. The scanner runs until close() is called.
	 * 
	 * @param directoryConfig directory scanning configuration
	 * @param schedulingConfig scheduling configuration
	 * @param semaphoreManager Hazelcast semaphore manager
	 */
	public MyDirectoryScanner(DirectoryScanningConfig directoryConfig,
	                         SchedulingConfig schedulingConfig,
	                         HazelcastSemaphoreManager semaphoreManager) {
		this.directoryConfig = directoryConfig;
		this.schedulingConfig = schedulingConfig;
		this.semaphoreManager = semaphoreManager;
		
		// Create scheduler (thread management hidden)
		this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
			Thread t = new Thread(r, "MyDirectoryScanner");
			t.setDaemon(false);
			return t;
		});
		
		// Start scanning immediately - no separate start() method needed
		logger.info("FRS_0440 Starting directory scanner");
		logger.info("FRS_0440   Scan interval: {} minutes", this.schedulingConfig.getScanIntervalMinutes());
		logger.info("FRS_0440   Source directories: {}", directoryConfig.getSourceDirectories());
		
		// Perform initial scan immediately
		scheduler.execute(this::performScan);
		
		// Schedule periodic scans
		scheduler.scheduleAtFixedRate(
			this::performScan,
			this.schedulingConfig.getScanIntervalMinutes(),
			this.schedulingConfig.getScanIntervalMinutes(),
			TimeUnit.MINUTES
		);
		
		logger.info("FRS_0201 Directory scanning started");
		
		// Defensive programming: just in case the close() method isn't called.
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			logger.info("FRS_0441 Stopping directory scanner (shutdown hook)");
			shutdownScheduler();
		}, "directory-scanner-shutdown-hook"));
	}
	
	/**
	 * Perform a single scan cycle.
	 * 
	 * This is the core operation pipeline with separated concerns:
	 * 1. Scan directories (scanDirectories)
	 * 2. Validate paths (isValidPath)
	 * 3. Create tuples (createTuple)
	 * 4. Add tuples to map (addTupleToMap - core operation)
	 * 
	 * No null filtering - uses Optional for explicit error handling.
	 * No state checks - if this method is called, the scanner is running.
	 */
	private void performScan() {
		try {
			logger.info("FRS_0442 Starting scan cycle");
			
			// Counters for summary
			AtomicLong processedCount = new AtomicLong(0);
			AtomicLong skippedCount = new AtomicLong(0);
			
			// Clean pipeline with separated concerns:
			// Stream<Path> -> filter valid -> map to Optional<MGTuple> -> filter present -> add to map
			try (Stream<Path> fileStream = scanDirectories(directoryConfig.getSourceDirectories())) {
				fileStream
					// Filter valid paths early
					.filter(this::isValidPath)
					// Map Path to pair of (Path, Optional<MGTuple>) to keep path for logging
					.map(path -> new AbstractMap.SimpleEntry<>(path, createTuple(path)))
					// Filter out entries with empty Optionals (explicit failure handling - no silent nulls)
					.filter(entry -> entry.getValue().isPresent())
					// Extract tuple and add to map (core operation)
					.forEach(entry -> {
						Path path = entry.getKey();
						MGTuple tuple = entry.getValue().get();
						boolean shouldProcess = addTupleToMap(path, tuple);
						if (shouldProcess) {
							processedCount.incrementAndGet();
						} else {
							skippedCount.incrementAndGet();
						}
					});
			}
			
			logger.info("FRS_0443 Scan cycle summary: {} new files added to map, {} files skipped", 
				processedCount.get(), skippedCount.get());
			
		} catch (Exception e) {
			logger.error("FRS_0442 Scan cycle failed", e);
			// Don't re-throw - allow scheduler to continue
		}
	}
	
	/**
	 * Scan all configured directories and return a stream of candidate file paths.
	 * 
	 * Uses flatMap to combine streams from multiple directories.
	 * 
	 * @param directoryPaths list of directory paths to scan
	 * @return stream of candidate file paths
	 */
	private Stream<Path> scanDirectories(java.util.List<String> directoryPaths) {
		logger.info("FRS_0430 Starting directory scan for {} directory(ies)", directoryPaths.size());
		
		return directoryPaths.stream()
			.flatMap(this::scanDirectoryToStream)
			.onClose(() -> logger.info("FRS_0431 Directory scan stream closed"));
	}
	
	/**
	 * Scan a single directory and return a stream of candidate file paths.
	 * 
	 * @param directoryPath path to directory to scan
	 * @return stream of candidate file paths (empty stream if directory doesn't exist or can't be read)
	 */
	private Stream<Path> scanDirectoryToStream(String directoryPath) {
		Path path = Paths.get(directoryPath);
		
		// Validate directory exists
		if (!Files.exists(path)) {
			logger.warn("FRS_0432 Directory does not exist: {}", directoryPath);
			return Stream.empty();
		}
		
		// Validate it's actually a directory
		if (!Files.isDirectory(path)) {
			logger.warn("FRS_0432 Path is not a directory: {}", directoryPath);
			return Stream.empty();
		}
		
		// Validate directory is readable
		if (!Files.isReadable(path)) {
			logger.warn("FRS_0432 Directory is not readable: {}", directoryPath);
			return Stream.empty();
		}
		
		// Scan directory for files using Stream
		try {
			Stream<Path> fileStream = Files.list(path)
				.filter(this::isCandidateFile)
				.onClose(() -> logger.debug("FRS_0433 Finished scanning directory: {}", directoryPath));
			
			return fileStream;
		} catch (IOException e) {
			logger.error("FRS_0432 Failed to scan directory: {}", directoryPath, e);
			return Stream.empty();
		}
	}
	
	/**
	 * Determine if a file is a candidate for processing.
	 * 
	 * @param filePath path to file
	 * @return true if file should be processed
	 */
	private boolean isCandidateFile(Path filePath) {
		// Must be a regular file (not a directory)
		if (!Files.isRegularFile(filePath)) {
			return false;
		}
		
		// Skip hidden files (Unix/Linux)
		Path fileName = filePath.getFileName();
		if (fileName == null) {
			return false;
		}
		
		String fileNameStr = fileName.toString();
		if (fileNameStr.startsWith(".")) {
			return false;
		}
		
		// Skip temporary files
		if (fileNameStr.endsWith(".tmp") || fileNameStr.endsWith(".temp") || 
		    fileNameStr.startsWith("~") || fileNameStr.endsWith("~")) {
			return false;
		}
		
		// Check if file is readable
		if (!Files.isReadable(filePath)) {
			logger.debug("File is not readable, skipping: {}", filePath);
			return false;
		}
		
		// Optional: Check file extension (if configured)
		// For now, accept all readable regular files
		return true;
	}
	
	/**
	 * Validate a file path before processing.
	 * 
	 * A path is valid if:
	 * - It has a filename component (not a root path)
	 * - The filename is not null or empty
	 * 
	 * @param path file path to validate
	 * @return true if path is valid for processing
	 */
	private boolean isValidPath(Path path) {
		if (path == null) {
			logger.debug("Path is null, skipping");
			return false;
		}
		
		Path fileName = path.getFileName();
		if (fileName == null) {
			logger.debug("Path has no filename component (root path), skipping: {}", path);
			return false;
		}
		
		String fileNameStr = fileName.toString();
		if (fileNameStr == null || fileNameStr.trim().isEmpty()) {
			logger.debug("Path has empty filename, skipping: {}", path);
			return false;
		}
		
		return true;
	}
	
	/**
	 * Create an MGTuple from a path.
	 * 
	 * Returns Optional.empty() if:
	 * - Path is invalid (no filename, etc.)
	 * - Tuple creation fails (SHA-256 unavailable, etc.)
	 * 
	 * @param path file path
	 * @return Optional containing MGTuple if successful, empty otherwise
	 */
	private Optional<MGTuple> createTuple(Path path) {
		if (!isValidPath(path)) {
			logger.debug("Cannot create tuple for invalid path: {}", path);
			return Optional.empty();
		}
		
		try {
			// Use full path string for tuple creation
			MGTuple tuple = MGTuple.fromPath(path.toString());
			return Optional.of(tuple);
			
		} catch (IllegalArgumentException e) {
			logger.error("FRS_0422 Failed to create tuple for file: {} - {}", path, e.getMessage());
			return Optional.empty();
			
		} catch (IllegalStateException e) {
			logger.error("FRS_0422 Failed to create tuple for file: {} - {}", path, e.getMessage());
			return Optional.empty();
			
		} catch (Exception e) {
			logger.error("FRS_0422 Unexpected error creating tuple for file: {}", path, e);
			return Optional.empty();
		}
	}
	
	/**
	 * Add a file tuple to the map.
	 * 
	 * This is the core operation - clean and simple.
	 * The fact that the map is in Hazelcast is an implementation detail.
	 * 
	 * @param path file path (for logging/identification)
	 * @param tuple file tuple (MGTuple: bounding hash + fingerprint)
	 * @return true if file was added (should be processed), false if already exists (should be skipped)
	 * @throws IllegalArgumentException if tuple is null
	 */
	private boolean addTupleToMap(Path path, MGTuple tuple) {
		if (tuple == null) {
			throw new IllegalArgumentException("Tuple cannot be null");
		}
		
		try {
			boolean shouldProcess = semaphoreManager.shouldProcessFile(tuple);
			
			if (shouldProcess) {
				logger.info("FRS_0421 File added to map: {} (mapKey: {})", 
					path, tuple.boundingHash());
			} else {
				logger.debug("FRS_0423 File already in map (skip): {}", path);
			}
			
			return shouldProcess;
			
		} catch (Exception e) {
			logger.error("FRS_0422 Failed to add file to map: {}", path, e);
			throw new IllegalStateException("Failed to add file tuple to map", e);
		}
	}
	
	/**
	 * Shutdown the scheduler.
	 * Waits for current scan to complete, then shuts down scheduler.
	 */
	private void shutdownScheduler() {
		logger.info("FRS_0441 Stopping directory scanner");
		
		scheduler.shutdown();
		try {
			if (!scheduler.awaitTermination(30, TimeUnit.SECONDS)) {
				logger.warn("FRS_0441 Scanner did not terminate within timeout, forcing shutdown");
				scheduler.shutdownNow();
				if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
					logger.error("FRS_0441 Scanner did not terminate after forced shutdown");
				}
			}
		} catch (InterruptedException e) {
			logger.warn("FRS_0441 Interrupted while waiting for scanner shutdown");
			scheduler.shutdownNow();
			Thread.currentThread().interrupt();
		}
		
		logger.info("FRS_0441 Directory scanner stopped");
	}
	
	/**
	 * Stop the scanner.
	 * Called automatically when used in try-with-resources.
	 */
	@Override
	public void close() {
		shutdownScheduler();
	}
}
