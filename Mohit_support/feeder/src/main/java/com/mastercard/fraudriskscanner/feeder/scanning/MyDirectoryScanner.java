package com.mastercard.fraudriskscanner.feeder.scanning;

import com.mastercard.fraudriskscanner.feeder.config.FeederConfig;
import com.mastercard.fraudriskscanner.feeder.model.FileFingerprint;
import com.mastercard.fraudriskscanner.feeder.semaphore.HazelcastSemaphoreManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.AbstractMap;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
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
 * 
 * Uses internal private methods to maintain SOLID principles:
 * - Single Responsibility: Each private method has one job
 * - Clean separation of concerns through method organization
 * 
 * @author BizOps Bank Team
 */
public final class MyDirectoryScanner implements AutoCloseable {
	
	private static final Logger logger = LoggerFactory.getLogger(MyDirectoryScanner.class);
	
	// Configuration
	private final FeederConfig config;
	
	// Dependencies
	private final DirectoryScanner directoryScanner;
	private final HazelcastSemaphoreManager semaphoreManager;
	
	// Scheduling infrastructure
	private final ScheduledExecutorService scheduler;
	private final AtomicBoolean running = new AtomicBoolean(false);
	
	/**
	 * Create and start a directory scanner.
	 * 
	 * Thread management is hidden inside this class - the scheduler thread
	 * is created and started automatically. All concerns are separated internally.
	 * 
	 * @param config feeder configuration
	 * @param directoryScanner directory scanner instance
	 * @param semaphoreManager Hazelcast semaphore manager
	 */
	public MyDirectoryScanner(FeederConfig config,
	                         DirectoryScanner directoryScanner,
	                         HazelcastSemaphoreManager semaphoreManager) {
		this.config = config;
		this.directoryScanner = directoryScanner;
		this.semaphoreManager = semaphoreManager;
		
		// Initialize scheduler (thread management hidden)
		this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
			Thread t = new Thread(r, "MyDirectoryScanner");
			t.setDaemon(false);
			return t;
		});
		
		// Start scanning
		start();
		
		// Register shutdown hook for graceful cleanup
		registerShutdownHook();
		
		logger.info("FRS_0201 Directory scanning started");
	}
	
	/**
	 * Start the scheduled scanner.
	 * Performs an initial scan immediately, then schedules periodic scans.
	 */
	private void start() {
		if (running.getAndSet(true)) {
			logger.warn("FRS_0440 Scanner is already running");
			return;
		}
		
		logger.info("FRS_0440 Starting directory scanner");
		logger.info("FRS_0440   Scan interval: {} minutes", config.getScanIntervalMinutes());
		logger.info("FRS_0440   Source directories: {}", config.getSourceDirectories());
		
		// Perform initial scan immediately
		scheduler.execute(this::performScan);
		
		// Schedule periodic scans
		scheduler.scheduleAtFixedRate(
			this::performScan,
			config.getScanIntervalMinutes(),
			config.getScanIntervalMinutes(),
			TimeUnit.MINUTES
		);
	}
	
	/**
	 * Perform a single scan cycle.
	 * 
	 * This is the core operation pipeline with separated concerns:
	 * 1. Scan directories (DirectoryScanner)
	 * 2. Validate paths (isValidPath)
	 * 3. Create fingerprints (createFingerprint)
	 * 4. Add tuples to map (addTupleToMap - core operation)
	 * 
	 * No null filtering - uses Optional for explicit error handling.
	 */
	private void performScan() {
		if (!running.get()) {
			return;
		}
		
		try {
			logger.info("FRS_0442 Starting scan cycle");
			
			// Counters for summary
			AtomicLong processedCount = new AtomicLong(0);
			AtomicLong skippedCount = new AtomicLong(0);
			
			// Clean pipeline with separated concerns:
			// Stream<Path> -> filter valid -> map to Optional<Fingerprint> -> filter present -> add to map
			try (Stream<Path> fileStream = directoryScanner.scanDirectories(config.getSourceDirectories())) {
				fileStream
					// Filter valid paths early
					.filter(this::isValidPath)
					// Map Path to pair of (Path, Optional<FileFingerprint>) to keep path for logging
					.map(path -> new AbstractMap.SimpleEntry<>(path, createFingerprint(path)))
					// Filter out entries with empty Optionals (explicit failure handling - no silent nulls)
					.filter(entry -> entry.getValue().isPresent())
					// Extract fingerprint and add tuple to map (core operation)
					.forEach(entry -> {
						Path path = entry.getKey();
						FileFingerprint fingerprint = entry.getValue().get();
						boolean shouldProcess = addTupleToMap(path, fingerprint);
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
	 * Extract filename from a valid path.
	 * 
	 * @param path valid file path (must pass isValidPath check)
	 * @return filename string
	 * @throws IllegalArgumentException if path is invalid
	 */
	private String extractFileName(Path path) {
		if (!isValidPath(path)) {
			throw new IllegalArgumentException("Cannot extract filename from invalid path: " + path);
		}
		
		return path.getFileName().toString();
	}
	
	/**
	 * Create a FileFingerprint from a path.
	 * 
	 * Returns Optional.empty() if:
	 * - Path is invalid (no filename, etc.)
	 * - Fingerprint creation fails (SHA-256 unavailable, etc.)
	 * 
	 * @param path file path
	 * @return Optional containing FileFingerprint if successful, empty otherwise
	 */
	private Optional<FileFingerprint> createFingerprint(Path path) {
		if (!isValidPath(path)) {
			logger.debug("Cannot create fingerprint for invalid path: {}", path);
			return Optional.empty();
		}
		
		try {
			String fileName = extractFileName(path);
			FileFingerprint fingerprint = FileFingerprint.fromFileName(fileName);
			return Optional.of(fingerprint);
			
		} catch (IllegalArgumentException e) {
			logger.error("FRS_0422 Failed to create fingerprint for file: {} - {}", path, e.getMessage());
			return Optional.empty();
			
		} catch (IllegalStateException e) {
			logger.error("FRS_0422 Failed to create fingerprint for file: {} - {}", path, e.getMessage());
			return Optional.empty();
			
		} catch (Exception e) {
			logger.error("FRS_0422 Unexpected error creating fingerprint for file: {}", path, e);
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
	 * @param fingerprint file fingerprint (the tuple: bound hash + fingerprint)
	 * @return true if file was added (should be processed), false if already exists (should be skipped)
	 * @throws IllegalArgumentException if fingerprint is null
	 */
	private boolean addTupleToMap(Path path, FileFingerprint fingerprint) {
		if (fingerprint == null) {
			throw new IllegalArgumentException("Fingerprint cannot be null");
		}
		
		try {
			boolean shouldProcess = semaphoreManager.shouldProcessFile(fingerprint);
			
			if (shouldProcess) {
				logger.info("FRS_0421 File added to map: {} (mapKey: {})", 
					fingerprint.getFileName(), fingerprint.getMapKey());
			} else {
				logger.debug("FRS_0423 File already in map (skip): {}", fingerprint.getFileName());
			}
			
			return shouldProcess;
			
		} catch (Exception e) {
			logger.error("FRS_0422 Failed to add file to map: {}", path, e);
			throw new IllegalStateException("Failed to add file tuple to map", e);
		}
	}
	
	/**
	 * Register shutdown hook for graceful cleanup.
	 */
	private void registerShutdownHook() {
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			if (running.get()) {
				logger.info("FRS_0441 Stopping directory scanner (shutdown hook)");
				stop();
			}
		}, "directory-scanner-shutdown-hook"));
	}
	
	/**
	 * Stop the scanner.
	 * Waits for current scan to complete, then shuts down scheduler.
	 */
	private void stop() {
		if (!running.getAndSet(false)) {
			return;
		}
		
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
		stop();
	}
	
	/**
	 * Check if scanner is running.
	 * 
	 * @return true if scanner is running
	 */
	public boolean isRunning() {
		return running.get();
	}
}
