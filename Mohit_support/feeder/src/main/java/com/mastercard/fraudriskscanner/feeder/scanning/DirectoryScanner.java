package com.mastercard.fraudriskscanner.feeder.scanning;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

/**
 * Service for scanning directories and finding candidate files.
 * 
 * Filters out directories, hidden files, and temporary files.
 * 
 * @author BizOps Bank Team
 */
public final class DirectoryScanner {
	
	private static final Logger logger = LoggerFactory.getLogger(DirectoryScanner.class);
	
	/**
	 * Scan all configured directories and return candidate files.
	 * 
	 * @param directoryPaths list of directory paths to scan
	 * @return list of candidate files found
	 */
	public List<File> scanDirectories(List<String> directoryPaths) {
		logger.info("FRS_0430 Starting directory scan for {} directory(ies)", directoryPaths.size());
		
		List<File> allFiles = new ArrayList<>();
		
		for (String directoryPath : directoryPaths) {
			try {
				List<File> files = scanDirectory(directoryPath);
				allFiles.addAll(files);
				logger.info("FRS_0433 Found {} file(s) in directory: {}", files.size(), directoryPath);
			} catch (Exception e) {
				logger.error("FRS_0432 Failed to scan directory: {}", directoryPath, e);
				// Continue scanning other directories even if one fails
			}
		}
		
		logger.info("FRS_0431 Directory scan completed. Total files found: {}", allFiles.size());
		return Collections.unmodifiableList(allFiles);
	}
	
	/**
	 * Scan a single directory for candidate files.
	 * 
	 * @param directoryPath path to directory to scan
	 * @return list of candidate files found
	 * @throws IOException if directory cannot be accessed
	 */
	public List<File> scanDirectory(String directoryPath) throws IOException {
		Path path = Paths.get(directoryPath);
		
		// Validate directory exists
		if (!Files.exists(path)) {
			logger.warn("FRS_0432 Directory does not exist: {}", directoryPath);
			return Collections.emptyList();
		}
		
		// Validate it's actually a directory
		if (!Files.isDirectory(path)) {
			logger.warn("FRS_0432 Path is not a directory: {}", directoryPath);
			return Collections.emptyList();
		}
		
		// Validate directory is readable
		if (!Files.isReadable(path)) {
			logger.warn("FRS_0432 Directory is not readable: {}", directoryPath);
			return Collections.emptyList();
		}
		
		// Scan directory for files
		List<File> candidateFiles = new ArrayList<>();
		
		try (Stream<Path> paths = Files.list(path)) {
			paths.forEach(filePath -> {
				if (isCandidateFile(filePath)) {
					candidateFiles.add(filePath.toFile());
				}
			});
		}
		
		return Collections.unmodifiableList(candidateFiles);
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

