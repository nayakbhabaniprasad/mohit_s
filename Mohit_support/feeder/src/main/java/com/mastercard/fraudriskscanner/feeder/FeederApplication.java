package com.mastercard.fraudriskscanner.feeder;

import com.mastercard.fraudriskscanner.feeder.config.FeederConfig;
import com.mastercard.fraudriskscanner.feeder.hazelcast.MyHazelcast;
import com.mastercard.fraudriskscanner.feeder.scanning.DirectoryScanner;
import com.mastercard.fraudriskscanner.feeder.scanning.MyDirectoryScanner;
import com.mastercard.fraudriskscanner.feeder.semaphore.HazelcastSemaphoreManager;
import com.mastercard.fraudriskscanner.feeder.util.CommandLineParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Feeder Application
 * 
 * Scans NGFT directories for files and adds them to Hazelcast database.
 * 
 * Uses try-with-resources pattern to automatically manage resource lifecycle.
 * Thread management is hidden inside wrapper classes (MyHazelcast, MyDirectoryScanner).
 * 
 * @author BizOps Bank Team
 */
public class FeederApplication {

	private static final Logger logger = LoggerFactory.getLogger(FeederApplication.class);

	public static void main(String[] args) {
		logger.info("FRS_0200 Starting Feeder Application");

		try {
			// 1) Parse command-line arguments and load configuration
			String sourceDirectories = CommandLineParser.parseSourceDirectories(args);
			FeederConfig config = new FeederConfig(sourceDirectories);

			// 2) Use try-with-resources for automatic cleanup
			// All threads are created and managed inside these wrapper classes
			try (MyHazelcast myHazelcast = new MyHazelcast();
			     MyDirectoryScanner myDirectoryScanner = createDirectoryScanner(config, myHazelcast)) {

				logger.info("FRS_0201 Feeder Application is running. Directory scanning started.");
				logger.info("Hazelcast started. Member: {}", myHazelcast.getHazelcastInstance().getName());
				logger.info("Hazelcast cluster: {}", myHazelcast.getHazelcastInstance().getCluster().getClusterState());

				// Keep application running until interrupted
				// Automatic cleanup happens when try block exits
				Thread.sleep(Long.MAX_VALUE);
			}

		} catch (InterruptedException e) {
			logger.info("FRS_0202 Feeder Application interrupted");
			Thread.currentThread().interrupt();
		} catch (Exception e) {
			logger.error("FRS_0203 Failed to start Feeder Application", e);
			throw new RuntimeException("FRS_0203 Feeder startup failed", e);
		}

		logger.info("FRS_0202 Feeder Application stopped");
	}

	/**
	 * Create and configure the directory scanner.
	 * 
	 * Pushes details of scanner and Hazelcast interaction to this helper method,
	 * keeping the main method clean and focused on orchestration.
	 * 
	 * @param config feeder configuration
	 * @param myHazelcast Hazelcast wrapper instance
	 * @return configured directory scanner
	 * @throws Exception if scanner creation fails
	 */
	private static MyDirectoryScanner createDirectoryScanner(FeederConfig config, MyHazelcast myHazelcast) throws Exception {
		// Initialize semaphore manager (for adding files to Hazelcast)
		HazelcastSemaphoreManager semaphoreManager =
			new HazelcastSemaphoreManager(myHazelcast.getHazelcastInstance(), config);

		// Initialize directory scanner
		DirectoryScanner directoryScanner = new DirectoryScanner();

		// Create and return wrapper (starts threads internally)
		return new MyDirectoryScanner(config, directoryScanner, semaphoreManager);
	}
}
