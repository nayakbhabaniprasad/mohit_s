package com.mastercard.fraudriskscanner.feeder;

import com.hazelcast.core.HazelcastInstance;
import com.mastercard.fraudriskscanner.feeder.hazelcast.HazelcastProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Feeder Application - Hazelcast bootstrap
 *
 * First step: Provide a running Hazelcast database for distributed coordination.
 */
public class FeederApplication {

	private static final Logger logger = LoggerFactory.getLogger(FeederApplication.class);

	public static void main(String[] args) {
		logger.info("FRS_0200 Starting Feeder Application (Hazelcast bootstrap)");

		// 1) Start Hazelcast (embedded)
		HazelcastInstance hazelcast = HazelcastProvider.getInstance();
		logger.info("Hazelcast started. Member: {}", hazelcast.getName());
		logger.info("Hazelcast cluster: {}", hazelcast.getCluster().getClusterState());

		// 2) Register graceful shutdown
		Runtime.getRuntime().addShutdownHook(new Thread(HazelcastProvider::shutdown, "hazelcast-shutdown"));

		logger.info("FRS_0201 Feeder Application is running. Hazelcast is ready.");

		// Keep the application running
		try {
			Thread.sleep(Long.MAX_VALUE);
		} catch (InterruptedException e) {
			logger.info("Feeder Application interrupted");
			Thread.currentThread().interrupt();
		}
	}
}
