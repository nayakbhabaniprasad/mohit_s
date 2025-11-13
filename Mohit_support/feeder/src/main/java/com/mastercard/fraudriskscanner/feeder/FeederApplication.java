package com.mastercard.fraudriskscanner.feeder;

import com.hazelcast.core.HazelcastInstance;
import com.mastercard.fraudriskscanner.feeder.config.FeederConfig;
import com.mastercard.fraudriskscanner.feeder.hazelcast.HazelcastProvider;
import com.mastercard.fraudriskscanner.feeder.scanning.DirectoryScanner;
import com.mastercard.fraudriskscanner.feeder.scanning.ScheduledDirectoryScanner;
import com.mastercard.fraudriskscanner.feeder.semaphore.HazelcastSemaphoreManager;
import com.mastercard.fraudriskscanner.feeder.util.CommandLineParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Feeder Application
 * 
 * Scans NGFT directories for files and adds them to Hazelcast database.
 * 
 * @author BizOps Bank Team
 */
public class FeederApplication {

	private static final Logger logger = LoggerFactory.getLogger(FeederApplication.class);
	
	private FeederConfig config;
	private ScheduledDirectoryScanner scheduledScanner;

	public static void main(String[] args) {
		FeederApplication app = new FeederApplication();
		app.start(args);
		app.run();
	}

	/**
	 * Initialize and start the application.
	 * 
	 * @param args command-line arguments
	 */
	private void start(String[] args) {
		logger.info("FRS_0200 Starting Feeder Application");

		try {
			// 1) Parse command-line arguments and load configuration
			String sourceDirectories = CommandLineParser.parseSourceDirectories(args);
			config = new FeederConfig(sourceDirectories);

			// 2) Start Hazelcast (embedded)
			HazelcastInstance hazelcast = HazelcastProvider.getInstance();
			logger.info("Hazelcast started. Member: {}", hazelcast.getName());
			logger.info("Hazelcast cluster: {}", hazelcast.getCluster().getClusterState());

			// 3) Initialize semaphore manager (for adding files to Hazelcast)
			HazelcastSemaphoreManager semaphoreManager = new HazelcastSemaphoreManager(hazelcast, config);
			
			// 4) Initialize directory scanning
			DirectoryScanner directoryScanner = new DirectoryScanner();
			scheduledScanner = new ScheduledDirectoryScanner(config, directoryScanner, semaphoreManager);
			scheduledScanner.start();

			// 4) Register graceful shutdown
			Runtime.getRuntime().addShutdownHook(new Thread(() -> {
				logger.info("FRS_0202 Shutdown signal received");
				shutdown();
			}, "feeder-shutdown"));

			logger.info("FRS_0201 Feeder Application is running. Directory scanning started.");

		} catch (Exception e) {
			logger.error("FRS_0203 Failed to start Feeder Application", e);
			throw new RuntimeException("FRS_0203 Feeder startup failed", e);
		}
	}

	/**
	 * Keep application running until interrupted.
	 */
	private void run() {
		try {
			// Sleep indefinitely, waiting for shutdown signal
			Thread.sleep(Long.MAX_VALUE);
		} catch (InterruptedException e) {
			logger.info("Feeder Application interrupted");
			shutdown();
			Thread.currentThread().interrupt();
		}
	}

	/**
	 * Graceful shutdown of all services.
	 */
	private void shutdown() {
		if (scheduledScanner != null) {
			scheduledScanner.stop();
		}
		HazelcastProvider.shutdown();
		logger.info("FRS_0202 Feeder Application stopped");
	}
}
