package com.mastercard.fraudriskscanner.feeder.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Objects;

/**
 * Immutable class representing a file fingerprint for semaphore logic.
 * 
 * Uses SHA-256 hash of the filename to create:
 * - Map Key: Bytes 0-3 of SHA-256, bounded by 2^16 (0-65535)
 * - Fingerprint: Bytes 23-31 of SHA-256 (8 bytes)
 * 
 * This ensures single processing across cluster nodes while preventing
 * false positives and controlling map growth.
 * 
 * @author BizOps Bank Team
 */
public final class FileFingerprint implements Serializable {
	
	private static final Logger logger = LoggerFactory.getLogger(FileFingerprint.class);
	private static final long serialVersionUID = 1L;
	
	private final String fileName;
	private final int mapKey;
	private final byte[] fingerprint;
	
	/**
	 * Private constructor. Use factory method instead.
	 */
	private FileFingerprint(String fileName, int mapKey, byte[] fingerprint) {
		this.fileName = fileName;
		this.mapKey = mapKey;
		this.fingerprint = Arrays.copyOf(fingerprint, fingerprint.length); // Defensive copy
	}
	
	/**
	 * Create a FileFingerprint from a filename.
	 * 
	 * @param fileName the filename to fingerprint
	 * @return FileFingerprint instance
	 * @throws IllegalArgumentException if fileName is null or empty
	 * @throws IllegalStateException if SHA-256 algorithm is unavailable
	 */
	public static FileFingerprint fromFileName(String fileName) {
		if (fileName == null || fileName.trim().isEmpty()) {
			throw new IllegalArgumentException("FRS_0411 File name cannot be null or empty");
		}
		
		try {
			// Calculate SHA-256 hash of filename
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(fileName.getBytes(StandardCharsets.UTF_8));
			
			// Extract map key: bytes 0-3, bounded by 2^16
			int mapKey = extractMapKey(hash);
			
			// Extract fingerprint: bytes 23-31 (8 bytes)
			byte[] fingerprint = extractFingerprint(hash);
			
			logger.debug("FRS_0410 File fingerprint calculated for: {}", fileName);
			
			return new FileFingerprint(fileName, mapKey, fingerprint);
			
		} catch (NoSuchAlgorithmException e) {
			logger.error("FRS_0411 SHA-256 algorithm not available", e);
			throw new IllegalStateException("FRS_0411 SHA-256 algorithm not available", e);
		}
	}
	
	/**
	 * Extract map key from SHA-256 hash.
	 * Uses bytes 0-3 and bounds by 2^16 (0xFFFF).
	 * 
	 * @param hash SHA-256 hash bytes
	 * @return map key (0-65535)
	 */
	private static int extractMapKey(byte[] hash) {
		if (hash.length < 4) {
			throw new IllegalStateException("FRS_0411 Hash too short for map key extraction");
		}
		
		// Bytes 0-3 as int, then bound by 2^16
		int key = ((hash[0] & 0xFF) << 24) |
		          ((hash[1] & 0xFF) << 16) |
		          ((hash[2] & 0xFF) << 8) |
		          (hash[3] & 0xFF);
		
		return key & 0xFFFF; // Bound by 2^16
	}
	
	/**
	 * Extract fingerprint from SHA-256 hash.
	 * Uses bytes 23-31 (8 bytes).
	 * 
	 * @param hash SHA-256 hash bytes
	 * @return fingerprint bytes (8 bytes)
	 */
	private static byte[] extractFingerprint(byte[] hash) {
		if (hash.length < 32) {
			throw new IllegalStateException("FRS_0411 Hash too short for fingerprint extraction");
		}
		
		// Extract bytes 23-31 (8 bytes)
		return Arrays.copyOfRange(hash, 23, 32);
	}
	
	/**
	 * Get the original filename.
	 * 
	 * @return filename
	 */
	public String getFileName() {
		return fileName;
	}
	
	/**
	 * Get the map key (0-65535).
	 * This is used as the key in the Hazelcast IMap.
	 * 
	 * @return map key
	 */
	public int getMapKey() {
		return mapKey;
	}
	
	/**
	 * Get the fingerprint bytes (8 bytes).
	 * This is used as the value in the Hazelcast IMap.
	 * 
	 * @return fingerprint bytes (defensive copy)
	 */
	public byte[] getFingerprint() {
		return Arrays.copyOf(fingerprint, fingerprint.length);
	}
	
	/**
	 * Check if this fingerprint matches another.
	 * 
	 * @param other fingerprint to compare
	 * @return true if fingerprints match
	 */
	public boolean matches(FileFingerprint other) {
		if (other == null) {
			return false;
		}
		return Arrays.equals(this.fingerprint, other.fingerprint);
	}
	
	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		FileFingerprint that = (FileFingerprint) o;
		return mapKey == that.mapKey &&
		       Objects.equals(fileName, that.fileName) &&
		       Arrays.equals(fingerprint, that.fingerprint);
	}
	
	@Override
	public int hashCode() {
		int result = Objects.hash(fileName, mapKey);
		result = 31 * result + Arrays.hashCode(fingerprint);
		return result;
	}
	
	@Override
	public String toString() {
		return String.format("FileFingerprint{fileName='%s', mapKey=%d, fingerprint=%s}",
			fileName, mapKey, Arrays.toString(fingerprint));
	}
}

