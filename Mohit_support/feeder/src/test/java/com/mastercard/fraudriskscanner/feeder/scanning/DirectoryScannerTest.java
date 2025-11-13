package com.mastercard.fraudriskscanner.feeder.scanning;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DirectoryScanner.
 * Uses temporary directories for testing.
 */
class DirectoryScannerTest {

	private DirectoryScanner scanner;
	
	@TempDir
	Path tempDir;

	@BeforeEach
	void setUp() {
		scanner = new DirectoryScanner();
	}

	@Test
	void testScanDirectories_WithFiles() throws IOException {
		// Create test files
		Path file1 = tempDir.resolve("report1.txt");
		Path file2 = tempDir.resolve("report2.csv");
		Files.createFile(file1);
		Files.createFile(file2);

		// Scan directory using stream
		try (var fileStream = scanner.scanDirectories(List.of(tempDir.toString()))) {
			List<Path> files = fileStream.toList();
			
			// Verify results
			assertEquals(2, files.size(), "Should find 2 files");
			assertTrue(files.stream().anyMatch(p -> p.getFileName().toString().equals("report1.txt")));
			assertTrue(files.stream().anyMatch(p -> p.getFileName().toString().equals("report2.csv")));
		}
	}

	@Test
	void testScanDirectories_EmptyDirectory() throws IOException {
		// Scan empty directory using stream
		try (var fileStream = scanner.scanDirectories(List.of(tempDir.toString()))) {
			long count = fileStream.count();
			// Verify results
			assertEquals(0, count, "Should find no files in empty directory");
		}
	}

	@Test
	void testScanDirectories_NonExistentDirectory() {
		// Scan non-existent directory using stream
		try (var fileStream = scanner.scanDirectories(List.of("/non/existent/path"))) {
			long count = fileStream.count();
			// Verify results
			assertEquals(0, count, "Should return empty stream for non-existent directory");
		}
	}

	@Test
	void testScanDirectories_IgnoresHiddenFiles() throws IOException {
		// Create visible and hidden files
		Path visibleFile = tempDir.resolve("visible.txt");
		Path hiddenFile = tempDir.resolve(".hidden");
		Files.createFile(visibleFile);
		Files.createFile(hiddenFile);

		// Scan directory using stream
		try (var fileStream = scanner.scanDirectories(List.of(tempDir.toString()))) {
			List<Path> files = fileStream.toList();
			
			// Verify results
			assertEquals(1, files.size(), "Should find only visible file");
			assertEquals("visible.txt", files.get(0).getFileName().toString());
		}
	}

	@Test
	void testScanDirectories_IgnoresTempFiles() throws IOException {
		// Create regular and temp files
		Path regularFile = tempDir.resolve("report.txt");
		Path tempFile = tempDir.resolve("report.tmp");
		Path tempFile2 = tempDir.resolve("~temp.txt");
		Files.createFile(regularFile);
		Files.createFile(tempFile);
		Files.createFile(tempFile2);

		// Scan directory using stream
		try (var fileStream = scanner.scanDirectories(List.of(tempDir.toString()))) {
			List<Path> files = fileStream.toList();
			
			// Verify results
			assertEquals(1, files.size(), "Should find only regular file");
			assertEquals("report.txt", files.get(0).getFileName().toString());
		}
	}

	@Test
	void testScanDirectories_IgnoresSubdirectories() throws IOException {
		// Create file and subdirectory
		Path file = tempDir.resolve("report.txt");
		Path subdir = tempDir.resolve("subdir");
		Files.createFile(file);
		Files.createDirectory(subdir);

		// Scan directory using stream
		try (var fileStream = scanner.scanDirectories(List.of(tempDir.toString()))) {
			List<Path> files = fileStream.toList();
			
			// Verify results
			assertEquals(1, files.size(), "Should find only file, not subdirectory");
			assertEquals("report.txt", files.get(0).getFileName().toString());
		}
	}

	@Test
	void testScanDirectories_MultipleDirectories() throws IOException {
		// Create multiple test directories
		Path dir1 = tempDir.resolve("dir1");
		Path dir2 = tempDir.resolve("dir2");
		Files.createDirectory(dir1);
		Files.createDirectory(dir2);

		// Create files in each directory
		Files.createFile(dir1.resolve("file1.txt"));
		Files.createFile(dir2.resolve("file2.txt"));

		// Scan multiple directories using stream
		try (var fileStream = scanner.scanDirectories(
			List.of(dir1.toString(), dir2.toString())
		)) {
			long count = fileStream.count();
			assertEquals(2, count, "Should find files from both directories");
		}
	}

	@Test
	void testScanDirectories_OneDirectoryFails() throws IOException {
		// Create one valid and one invalid directory
		Path validDir = tempDir.resolve("valid");
		Files.createDirectory(validDir);
		Files.createFile(validDir.resolve("file.txt"));

		// Scan directories (one valid, one invalid) using stream
		try (var fileStream = scanner.scanDirectories(
			List.of(validDir.toString(), "/non/existent/path")
		)) {
			long count = fileStream.count();
			// Verify results - should still find files from valid directory even if one fails
			assertEquals(1, count, "Should find files from valid directory even if one fails");
		}
	}
}

