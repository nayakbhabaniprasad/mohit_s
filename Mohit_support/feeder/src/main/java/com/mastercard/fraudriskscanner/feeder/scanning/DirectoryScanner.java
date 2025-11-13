package com.mastercard.fraudriskscanner.feeder.scanning;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

/**
 * Service for scanning directories and finding candidate files.
 * 
 * Uses Java Streams to provide a functional, lightweight approach.
 * Filters out directories, hidden files, and temporary files.
 * 
 * @author BizOps Bank Team
 */
public final class DirectoryScanner {
	
	private static final Logger logger = LoggerFactory.getLogger(DirectoryScanner.class);
	
	/**
	 * Scan all configured directories and return a stream of candidate file paths.
	 * 
	 * Uses flatMap to combine streams from multiple directories.
	 * 
	 * @param directoryPaths list of directory paths to scan
	 * @return stream of candidate file paths
	 */
	public Stream<Path> scanDirectories(List<String> directoryPaths) {
		logger.info("FRS_0430 Starting directory scan for {} directory(ies)", directoryPaths.size());
		
		return directoryPaths.stream()
			.flatMap(this::scanDirectoryToStream)
			.onClose(() -> logger.info("FRS_0431 Directory scan stream closed"));
	}
	
	/**
	 * Scan a single directory and return a stream of candidate file paths.
	 * 
	 * @param directoryPath path to directory to scan
	 * @return stream of candidate file paths (empty stream if directory doesn't exist or can't be read)
	 */
	private Stream<Path> scanDirectoryToStream(String directoryPath) {
		Path path = Paths.get(directoryPath);
		
		// Validate directory exists
		if (!Files.exists(path)) {
			logger.warn("FRS_0432 Directory does not exist: {}", directoryPath);
			return Stream.empty();
		}
		
		// Validate it's actually a directory
		if (!Files.isDirectory(path)) {
			logger.warn("FRS_0432 Path is not a directory: {}", directoryPath);
			return Stream.empty();
		}
		
		// Validate directory is readable
		if (!Files.isReadable(path)) {
			logger.warn("FRS_0432 Directory is not readable: {}", directoryPath);
			return Stream.empty();
		}
		
		// Scan directory for files using Stream
		try {
			Stream<Path> fileStream = Files.list(path)
				.filter(this::isCandidateFile)
				.onClose(() -> logger.debug("FRS_0433 Finished scanning directory: {}", directoryPath));
			
			return fileStream;
		} catch (IOException e) {
			logger.error("FRS_0432 Failed to scan directory: {}", directoryPath, e);
			return Stream.empty();
		}
	}
	
	/**
	 * Determine if a file is a candidate for processing.
	 * 
	 * @param filePath path to file
	 * @return true if file should be processed
	 */
	private boolean isCandidateFile(Path filePath) {
		// Must be a regular file (not a directory)
		if (!Files.isRegularFile(filePath)) {
			return false;
		}
		
		// Skip hidden files (Unix/Linux)
		String fileName = filePath.getFileName().toString();
		if (fileName.startsWith(".")) {
			return false;
		}
		
		// Skip temporary files
		if (fileName.endsWith(".tmp") || fileName.endsWith(".temp") || 
		    fileName.startsWith("~") || fileName.endsWith("~")) {
			return false;
		}
		
		// Check if file is readable
		if (!Files.isReadable(filePath)) {
			logger.debug("File is not readable, skipping: {}", filePath);
			return false;
		}
		
		// Optional: Check file extension (if configured)
		// For now, accept all readable regular files
		return true;
	}
}

