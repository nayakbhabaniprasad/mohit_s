package com.mastercard.fraudriskscanner.feeder.scanning;

import com.mastercard.fraudriskscanner.feeder.config.FeederConfig;
import com.mastercard.fraudriskscanner.feeder.model.FileFingerprint;
import com.mastercard.fraudriskscanner.feeder.semaphore.HazelcastSemaphoreManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

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
	 * Perform a single scan cycle.
	 * This method is called by the scheduler.
	 */
	private void performScan() {
		if (!running.get()) {
			return;
		}
		
		try {
			logger.info("FRS_0442 Starting scan cycle");
			
			List<File> files = directoryScanner.scanDirectories(config.getSourceDirectories());
			
			logger.info("FRS_0443 Scan cycle completed. Found {} file(s)", files.size());
			
			// Process each file: add to Hazelcast using semaphore logic
			int processedCount = 0;
			int skippedCount = 0;
			
			for (File file : files) {
				try {
					// Calculate fingerprint from filename
					FileFingerprint fingerprint = FileFingerprint.fromFileName(file.getName());
					
					// Check if file should be processed (adds to Hazelcast if new)
					boolean shouldProcess = semaphoreManager.shouldProcessFile(fingerprint);
					
					if (shouldProcess) {
						// File is new - added to Hazelcast
						processedCount++;
						logger.info("FRS_0421 File added to Hazelcast: {} (mapKey: {})", 
							file.getName(), fingerprint.getMapKey());
					} else {
						// File already in Hazelcast - skip
						skippedCount++;
						logger.debug("FRS_0423 File already in Hazelcast (skip): {}", file.getName());
					}
					
				} catch (Exception e) {
					logger.error("FRS_0422 Failed to process file: {}", file.getName(), e);
					// Continue with next file
				}
			}
			
			logger.info("FRS_0443 Scan cycle summary: {} new files added to Hazelcast, {} files skipped", 
				processedCount, skippedCount);
			
		} catch (Exception e) {
			logger.error("FRS_0442 Scan cycle failed", e);
			// Don't re-throw - allow scheduler to continue
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

