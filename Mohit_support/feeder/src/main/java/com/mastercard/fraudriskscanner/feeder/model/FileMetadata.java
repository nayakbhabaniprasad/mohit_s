package com.mastercard.fraudriskscanner.feeder.model;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * Immutable class representing file metadata.
 * 
 * Stores information about a file discovered during directory scanning.
 * This metadata can be stored in Hazelcast for tracking purposes.
 * 
 * @author BizOps Bank Team
 */
public final class FileMetadata implements Serializable {
	
	private static final long serialVersionUID = 1L;
	
	private final String filePath;
	private final String fileName;
	private final long fileSize;
	private final Instant lastModified;
	private final FileFingerprint fingerprint;
	private final Instant discoveredAt;
	
	/**
	 * Create FileMetadata from file information.
	 * 
	 * @param filePath full path to the file
	 * @param fileName name of the file
	 * @param fileSize size of the file in bytes
	 * @param lastModified last modification time
	 * @param fingerprint file fingerprint
	 * @param discoveredAt when the file was discovered
	 */
	public FileMetadata(String filePath, String fileName, long fileSize,
	                   Instant lastModified, FileFingerprint fingerprint, Instant discoveredAt) {
		this.filePath = Objects.requireNonNull(filePath, "File path cannot be null");
		this.fileName = Objects.requireNonNull(fileName, "File name cannot be null");
		this.fileSize = fileSize;
		this.lastModified = Objects.requireNonNull(lastModified, "Last modified time cannot be null");
		this.fingerprint = Objects.requireNonNull(fingerprint, "Fingerprint cannot be null");
		this.discoveredAt = Objects.requireNonNull(discoveredAt, "Discovered at time cannot be null");
	}
	
	/**
	 * Get the full file path.
	 * 
	 * @return file path
	 */
	public String getFilePath() {
		return filePath;
	}
	
	/**
	 * Get the file name.
	 * 
	 * @return file name
	 */
	public String getFileName() {
		return fileName;
	}
	
	/**
	 * Get the file size in bytes.
	 * 
	 * @return file size
	 */
	public long getFileSize() {
		return fileSize;
	}
	
	/**
	 * Get the last modification time.
	 * 
	 * @return last modified time
	 */
	public Instant getLastModified() {
		return lastModified;
	}
	
	/**
	 * Get the file fingerprint.
	 * 
	 * @return fingerprint
	 */
	public FileFingerprint getFingerprint() {
		return fingerprint;
	}
	
	/**
	 * Get when the file was discovered.
	 * 
	 * @return discovery time
	 */
	public Instant getDiscoveredAt() {
		return discoveredAt;
	}
	
	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		FileMetadata that = (FileMetadata) o;
		return fileSize == that.fileSize &&
		       Objects.equals(filePath, that.filePath) &&
		       Objects.equals(fileName, that.fileName) &&
		       Objects.equals(lastModified, that.lastModified) &&
		       Objects.equals(fingerprint, that.fingerprint) &&
		       Objects.equals(discoveredAt, that.discoveredAt);
	}
	
	@Override
	public int hashCode() {
		return Objects.hash(filePath, fileName, fileSize, lastModified, fingerprint, discoveredAt);
	}
	
	@Override
	public String toString() {
		return String.format("FileMetadata{filePath='%s', fileName='%s', fileSize=%d, " +
			"lastModified=%s, discoveredAt=%s}", filePath, fileName, fileSize, lastModified, discoveredAt);
	}
}

