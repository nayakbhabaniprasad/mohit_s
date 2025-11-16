package com.mastercard.fraudriskscanner.feeder.scanning;

import com.mastercard.fraudriskscanner.feeder.config.FeederConfig;
import com.mastercard.fraudriskscanner.feeder.semaphore.HazelcastSemaphoreManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Wrapper for ScheduledDirectoryScanner that implements AutoCloseable.
 * 
 * Hides thread management and provides automatic cleanup via try-with-resources.
 * Follows the same pattern as MyHazelcast - threads are created internally
 * and managed automatically.
 * 
 * @author Clay Atkins
 */
public final class MyDirectoryScanner implements AutoCloseable {
	
	private static final Logger logger = LoggerFactory.getLogger(MyDirectoryScanner.class);
	
	private final ScheduledDirectoryScanner scheduledScanner;
	
	/**
	 * Create and start a scheduled directory scanner.
	 * 
	 * Thread management is hidden inside this class - the scheduler thread
	 * is created and started automatically.
	 * 
	 * @param config feeder configuration
	 * @param directoryScanner directory scanner instance
	 * @param semaphoreManager Hazelcast semaphore manager
	 */
	public MyDirectoryScanner(FeederConfig config,
	                         DirectoryScanner directoryScanner,
	                         HazelcastSemaphoreManager semaphoreManager) {
		this.scheduledScanner = new ScheduledDirectoryScanner(config, directoryScanner, semaphoreManager);
		
		// Start the scanner (creates and starts threads internally)
		scheduledScanner.start();
		
		logger.info("FRS_0201 Directory scanning started");
		
		// Defensive programming: just in case the close() method isn't called.
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			if (scheduledScanner != null) {
				logger.info("FRS_0441 Stopping scheduled directory scanner (shutdown hook)");
				scheduledScanner.stop();
			}
		}, "directory-scanner-shutdown-hook"));
	}
	
	/**
	 * Stop the scheduled directory scanner.
	 * Called automatically when used in try-with-resources.
	 */
	@Override
	public void close() throws Exception {
		if (scheduledScanner != null) {
			logger.info("FRS_0441 Stopping scheduled directory scanner");
			scheduledScanner.stop();
		}
	}
}

