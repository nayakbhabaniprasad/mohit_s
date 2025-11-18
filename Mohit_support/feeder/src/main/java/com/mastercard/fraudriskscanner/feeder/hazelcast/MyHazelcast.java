package com.mastercard.fraudriskscanner.feeder.hazelcast;

import com.hazelcast.config.ClasspathYamlConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.mastercard.fraudriskscanner.feeder.config.HazelcastConfig;
import com.mastercard.fraudriskscanner.feeder.semaphore.HazelcastSemaphoreManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Wrapper for Hazelcast instance that implements AutoCloseable.
 * 
 * Hides thread management and provides automatic cleanup via try-with-resources.
 * Follows the same pattern as other resource wrappers (MyDirectoryScanner, etc.).
 * 
 * @author Clay Atkins
 */
public final class MyHazelcast implements AutoCloseable {
	
	private static final Logger logger = LoggerFactory.getLogger(MyHazelcast.class);
	private static final String CONFIG_RESOURCE = "hazelcast.yaml";
	
	/**
	 * Exception thrown when Hazelcast initialization fails.
	 */
	public static final class MyHazelcastException extends Exception {
		public MyHazelcastException(Throwable t) {
			super(t);
		}
	}
	
	private final HazelcastInstance hazelcastInstance;
	
	/**
	 * Create and start a Hazelcast instance.
	 * 
	 * @throws MyHazelcastException if Hazelcast fails to start
	 */
	public MyHazelcast() throws MyHazelcastException {
		try {
			logger.info("FRS_0450 Starting Hazelcast from classpath config '{}'", CONFIG_RESOURCE);
			ClasspathYamlConfig config = new ClasspathYamlConfig(CONFIG_RESOURCE);
			hazelcastInstance = Hazelcast.newHazelcastInstance(config);
			
			if (hazelcastInstance == null) {
				throw new IllegalStateException("FRS_0451 Hazelcast failed to initialize (instance is null)");
			}
			

		} catch (Throwable t) {
			throw new MyHazelcastException(t);
		}
		
		// Defensive programming: just in case the close() method isn't called.
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			if (hazelcastInstance != null) {
				hazelcastInstance.shutdown();
			}
		}, "hazelcast-shutdown-hook"));
	}
	
	/**
	 * Create a HazelcastSemaphoreManager using this Hazelcast instance.
	 * 
	 * This method encapsulates the creation of dependent objects, maintaining
	 * proper encapsulation and lifecycle management.
	 * 
	 * @param hazelcastConfig Hazelcast configuration
	 * @return HazelcastSemaphoreManager instance
	 */
	public HazelcastSemaphoreManager createSemaphoreManager(HazelcastConfig hazelcastConfig) {
		if (hazelcastInstance == null) {
			throw new IllegalStateException("Hazelcast instance is not initialized");
		}
		return new HazelcastSemaphoreManager(hazelcastInstance, hazelcastConfig);
	}
	

	
	/**
	 * Shutdown the Hazelcast instance.
	 * Called automatically when used in try-with-resources.
	 */
	@Override
	public void close() throws Exception {
		if (hazelcastInstance != null) {
			logger.info("FRS_0452 Shutting down Hazelcast instance");
			hazelcastInstance.shutdown();
		}
	}
}

