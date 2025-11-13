package com.mastercard.fraudriskscanner.feeder.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for parsing command-line arguments.
 * 
 * Supports multiple formats:
 * - Positional argument: java -jar feeder.jar /path/to/directory
 * - Flag format: java -jar feeder.jar --source-directories /path/to/dir1,/path/to/dir2
 * - Short flag: java -jar feeder.jar -d /path/to/directory
 * 
 * This is a utility class with static methods. It should not be instantiated.
 * 
 * @author BizOps Bank Team
 */
public final class CommandLineParser {
	
	private static final Logger logger = LoggerFactory.getLogger(CommandLineParser.class);
	
	// Command-line argument flags
	private static final String SOURCE_DIRECTORIES_FLAG = "--source-directories";
	private static final String SOURCE_DIRECTORIES_SHORT_FLAG = "-d";
	
	/**
	 * Private constructor to prevent instantiation.
	 * This is a utility class with only static methods.
	 */
	private CommandLineParser() {
		throw new UnsupportedOperationException("Utility class cannot be instantiated");
	}
	
	/**
	 * Parse command-line arguments to extract source directories.
	 * 
	 * Supported formats:
	 * <ul>
	 *   <li>Positional: {@code java -jar feeder.jar /path/to/directory}</li>
	 *   <li>Flag: {@code java -jar feeder.jar --source-directories /path/to/dir1,/path/to/dir2}</li>
	 *   <li>Short flag: {@code java -jar feeder.jar -d /path/to/directory}</li>
	 * </ul>
	 * 
	 * @param args command-line arguments (can be null or empty)
	 * @return source directories string (comma-separated) or null if not provided
	 */
	public static String parseSourceDirectories(String[] args) {
		if (args == null || args.length == 0) {
			logger.debug("No command-line arguments provided");
			return null;
		}
		
		// Check for --source-directories or -d flag
		for (int i = 0; i < args.length; i++) {
			String arg = args[i];
			
			if (SOURCE_DIRECTORIES_FLAG.equals(arg) || SOURCE_DIRECTORIES_SHORT_FLAG.equals(arg)) {
				if (i + 1 < args.length) {
					String value = args[i + 1];
					logger.debug("Found source directories from flag {}: {}", arg, value);
					return value;
				} else {
					logger.warn("FRS_0401 {} flag provided but no value given. Ignoring.", arg);
					return null;
				}
			}
		}
		
		// If no flag found, treat first argument as directory path (positional argument)
		// This allows: java -jar feeder.jar /path/to/directory
		if (args.length > 0 && !args[0].startsWith("-")) {
			String value = args[0];
			logger.debug("Found source directories from positional argument: {}", value);
			return value;
		}
		
		logger.debug("No source directories found in command-line arguments");
		return null;
	}
}

