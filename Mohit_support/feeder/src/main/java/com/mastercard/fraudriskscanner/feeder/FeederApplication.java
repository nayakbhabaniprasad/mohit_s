package com.mastercard.fraudriskscanner.feeder;

import com.mastercard.fraudriskscanner.feeder.config.DirectoryScanningConfig;
import com.mastercard.fraudriskscanner.feeder.config.HazelcastConfig;
import com.mastercard.fraudriskscanner.feeder.config.SchedulingConfig;
import com.mastercard.fraudriskscanner.feeder.hazelcast.MyHazelcast;
import com.mastercard.fraudriskscanner.feeder.scanning.MyDirectoryScanner;
import com.mastercard.fraudriskscanner.feeder.util.CommandLineParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.locks.LockSupport;

/**
 * Entry point for the Fraud Scan Feeder Application.
 * 
 * This class is responsible for initializing the application context, setting up necessary 
 * configurations (e.g., logging, database connections), and starting the core business logic 
 * or batch processes for fraud data ingestion.
 * 
 * As an application main class, it should be kept lightweight, delegating complex startup 
 * logic to dedicated service or configuration classes.
 * 
 * Uses try-with-resources pattern to automatically manage resource lifecycle.
 * Thread management is hidden inside wrapper classes (MyHazelcast, MyDirectoryScanner).
 * 
 * @author Mohit Gujar
 * @version 1.0.0
 */
public class FeederApplication {

	private static final Logger log = LoggerFactory.getLogger("com.mastercard.fraudriskscanner.feeder.FeederApplication");

	/**
	 * Main entry point for the Java application.
	 * 
	 * This method handles the bootstrap process, including:
	 * - Parsing command-line arguments
	 * - Loading configuration
	 * - Initializing services (Hazelcast, directory scanner)
	 * - Starting the application lifecycle
	 * 
	 * @param args command-line arguments
	 */
	public static void main(String[] args) {
		log.info("FRS_0200 Starting Feeder Application");

		try {
			// 1) Parse command-line arguments and load configuration
			// Separate configuration classes for different concerns
			String sourceDirectories = CommandLineParser.parseSourceDirectories(args);
			DirectoryScanningConfig directoryConfig = new DirectoryScanningConfig(sourceDirectories);
			SchedulingConfig schedulingConfig = new SchedulingConfig();
			HazelcastConfig hazelcastConfig = new HazelcastConfig();

			// 2) Use try-with-resources for automatic cleanup
			// All threads are created and managed inside these wrapper classes
			// Consistent instantiation pattern: all classes created directly with 'new'
			// MyHazelcast.createSemaphoreManager() maintains encapsulation
			try (MyHazelcast myHazelcast = new MyHazelcast();
			     MyDirectoryScanner myDirectoryScanner = new MyDirectoryScanner(
					directoryConfig,
					schedulingConfig,
					myHazelcast.createSemaphoreManager(hazelcastConfig))) {

				// Keep application running until interrupted
				// Use LockSupport instead of Thread.sleep for better interrupt handling
				// LockSupport.park() blocks until thread is interrupted or unparked
				// Automatic cleanup happens when try block exits
				LockSupport.park();
				
				// Check if we were interrupted
				if (Thread.currentThread().isInterrupted()) {
					log.info("FRS_0202 Feeder Application interrupted");
				}
			}

		} catch (MyHazelcast.MyHazelcastException e) {
			// Handle Hazelcast-specific initialization failure
			// This is a known lifecycle exception from the decorator class
			log.error("FRS_0451 Failed to start Hazelcast", e);
			log.error("FRS_9901 Abnormal application termination due to Hazelcast initialization failure");
			System.exit(1);
		} catch (Exception e) {
			// Handle any other unexpected exceptions
			// This represents abnormal termination from unknown causes
			log.error("FRS_9901 Abnormal application termination", e);
			System.exit(1);
		}

		log.info("FRS_0202 Feeder Application stopped");
	}
}
