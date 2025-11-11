package com.mastercard.fraudriskscanner.feeder.hazelcast;

import com.hazelcast.config.ClasspathYamlConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;

/**
 * Provides a singleton Hazelcast instance for the Feeder application.
 * Loads configuration from classpath resource: hazelcast.yaml
 */
public final class HazelcastProvider {

	private static final Logger logger = LoggerFactory.getLogger(HazelcastProvider.class);
	private static final String CONFIG_RESOURCE = "hazelcast.yaml";
	private static volatile HazelcastInstance instance;

	private HazelcastProvider() {
		// Prevent instantiation
	}

	/**
	 * Get or create the Hazelcast instance.
	 * 
	 * @return the Hazelcast instance
	 * @throws RuntimeException if Hazelcast fails to start
	 */
	public static HazelcastInstance getInstance() {
		if (instance == null) {
			synchronized (HazelcastProvider.class) {
				if (instance == null) {
					instance = createInstance();
				}
			}
		}
		return instance;
	}

	/**
	 * Shutdown Hazelcast if running.
	 */
	public static void shutdown() {
		if (instance != null) {
			try {
				logger.info("FRS_0452 Shutting down Hazelcast instance");
				instance.shutdown();
			} finally {
				instance = null;
			}
		}
	}

	private static HazelcastInstance createInstance() {
		validateConfigResource();
		try {
			logger.info("FRS_0450 Starting Hazelcast from classpath config '{}'", CONFIG_RESOURCE);
			ClasspathYamlConfig config = new ClasspathYamlConfig(CONFIG_RESOURCE);
			HazelcastInstance created = Hazelcast.newHazelcastInstance(config);
			if (created == null) {
				throw new IllegalStateException("FRS_0451 Hazelcast failed to initialize (instance is null)");
			}
			logger.info("FRS_0450 Hazelcast member started. name='{}'", created.getName());
			return created;
		} catch (IllegalStateException e) {
			// Re-throw IllegalStateException as-is (already has FRS code)
			throw e;
		} catch (Exception e) {
			logger.error("FRS_0451 Failed to start Hazelcast instance", e);
			throw new RuntimeException("FRS_0451 Hazelcast startup failed", e);
		}
	}

	/**
	 * Validates that the Hazelcast configuration resource exists on the classpath.
	 * This prevents cryptic errors later and satisfies SonarQube resource validation requirements.
	 * 
	 * @throws IllegalStateException if the resource is not found
	 */
	private static void validateConfigResource() {
		ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
		if (classLoader == null) {
			classLoader = HazelcastProvider.class.getClassLoader();
		}
		
		try (InputStream stream = classLoader.getResourceAsStream(CONFIG_RESOURCE)) {
			if (stream == null) {
				throw new IllegalStateException(
					String.format("FRS_0451 Hazelcast configuration resource '%s' not found on classpath", CONFIG_RESOURCE));
			}
			// Resource exists and stream is properly closed by try-with-resources
		} catch (IllegalStateException e) {
			// Re-throw IllegalStateException as-is
			throw e;
		} catch (Exception e) {
			logger.error("FRS_0451 Error validating Hazelcast configuration resource '{}'", CONFIG_RESOURCE, e);
			throw new IllegalStateException(
				String.format("FRS_0451 Failed to validate Hazelcast configuration resource '%s'", CONFIG_RESOURCE), e);
		}
	}
}

