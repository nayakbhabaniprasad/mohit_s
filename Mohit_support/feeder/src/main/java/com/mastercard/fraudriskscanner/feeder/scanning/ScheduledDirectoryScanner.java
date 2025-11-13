package com.mastercard.fraudriskscanner.feeder.scanning;

import com.mastercard.fraudriskscanner.feeder.config.FeederConfig;
import com.mastercard.fraudriskscanner.feeder.model.FileFingerprint;
import com.mastercard.fraudriskscanner.feeder.semaphore.HazelcastSemaphoreManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

/**
 * Scheduled service that periodically scans directories for files.
 * 
 * Runs scans at configured intervals and can be started/stopped gracefully.
 * Integrates with Hazelcast semaphore manager to add files to Hazelcast.
 * 
 * @author BizOps Bank Team
 */
public final class ScheduledDirectoryScanner {
	
	private static final Logger logger = LoggerFactory.getLogger(ScheduledDirectoryScanner.class);
	
	private final FeederConfig config;
	private final DirectoryScanner directoryScanner;
	private final HazelcastSemaphoreManager semaphoreManager;
	private final ScheduledExecutorService scheduler;
	private final AtomicBoolean running = new AtomicBoolean(false);
	
	/**
	 * Create a scheduled directory scanner.
	 * 
	 * @param config feeder configuration
	 * @param directoryScanner directory scanner instance
	 * @param semaphoreManager Hazelcast semaphore manager
	 */
	public ScheduledDirectoryScanner(FeederConfig config, DirectoryScanner directoryScanner,
	                                  HazelcastSemaphoreManager semaphoreManager) {
		this.config = config;
		this.directoryScanner = directoryScanner;
		this.semaphoreManager = semaphoreManager;
		this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
			Thread t = new Thread(r, "ScheduledDirectoryScanner");
			t.setDaemon(false);
			return t;
		});
	}
	
	/**
	 * Start the scheduled scanner.
	 * Performs an initial scan immediately, then schedules periodic scans.
	 */
	public void start() {
		if (running.getAndSet(true)) {
			logger.warn("FRS_0440 Scheduled scanner is already running");
			return;
		}
		
		logger.info("FRS_0440 Starting scheduled directory scanner");
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
	 * Stop the scheduled scanner.
	 * Waits for current scan to complete, then shuts down scheduler.
	 */
	public void stop() {
		if (!running.getAndSet(false)) {
			return;
		}
		
		logger.info("FRS_0441 Stopping scheduled directory scanner");
		
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
		
		logger.info("FRS_0441 Scheduled directory scanner stopped");
	}
	
	/**
	 * Perform a single scan cycle using Clay's streaming approach.
	 * 
	 * Pipeline:
	 * 1. Stream<Path> from directory scanner
	 * 2. Map Path to FileFingerprint (bound file name object)
	 * 3. Consumer adds to Hazelcast via semaphore manager
	 * 
	 * This method is called by the scheduler.
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
			
			// Clay's streaming approach:
			// Stream<Path> -> map to FileFingerprint -> consumer adds to Hazelcast
			try (Stream<Path> fileStream = directoryScanner.scanDirectories(config.getSourceDirectories())) {
				fileStream
					// Map Path to FileFingerprint (bound file name object)
					.map(this::pathToFingerprint)
					// Filter out nulls (files that failed fingerprint creation)
					.filter(java.util.Objects::nonNull)
					// Consumer: add to Hazelcast via semaphore manager
					.forEach(fingerprint -> processFingerprint(fingerprint, processedCount, skippedCount));
			}
			
			logger.info("FRS_0443 Scan cycle summary: {} new files added to Hazelcast, {} files skipped", 
				processedCount.get(), skippedCount.get());
			
		} catch (Exception e) {
			logger.error("FRS_0442 Scan cycle failed", e);
			// Don't re-throw - allow scheduler to continue
		}
	}
	
	/**
	 * Map Path to FileFingerprint (bound file name object).
	 * 
	 * @param path file path
	 * @return FileFingerprint or null if creation fails
	 */
	private FileFingerprint pathToFingerprint(Path path) {
		try {
			String fileName = path.getFileName().toString();
			return FileFingerprint.fromFileName(fileName);
		} catch (Exception e) {
			logger.error("FRS_0422 Failed to create fingerprint for file: {}", path, e);
			return null;
		}
	}
	
	/**
	 * Consumer that processes a fingerprint and adds it to Hazelcast.
	 * 
	 * @param fingerprint file fingerprint
	 * @param processedCount counter for processed files
	 * @param skippedCount counter for skipped files
	 */
	private void processFingerprint(FileFingerprint fingerprint, AtomicLong processedCount, AtomicLong skippedCount) {
		try {
			// Check if file should be processed (adds to Hazelcast if new)
			boolean shouldProcess = semaphoreManager.shouldProcessFile(fingerprint);
			
			if (shouldProcess) {
				// File is new - added to Hazelcast
				processedCount.incrementAndGet();
				logger.info("FRS_0421 File added to Hazelcast: {} (mapKey: {})", 
					fingerprint.getFileName(), fingerprint.getMapKey());
			} else {
				// File already in Hazelcast - skip
				skippedCount.incrementAndGet();
				logger.debug("FRS_0423 File already in Hazelcast (skip): {}", fingerprint.getFileName());
			}
		} catch (Exception e) {
			logger.error("FRS_0422 Failed to process file: {}", fingerprint.getFileName(), e);
			// Continue with next file (exception already logged)
		}
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

