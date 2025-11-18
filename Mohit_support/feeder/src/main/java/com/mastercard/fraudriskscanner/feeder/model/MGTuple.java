package com.mastercard.fraudriskscanner.feeder.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/**
 * Immutable representation of a file identity for distributed processing.
 * Implemented as a Java record for simplicity.
 * 
 * @author Mohit Gujar
 */
public record MGTuple(int boundingHash, byte[] fingerprintHash) {
	
	/**
	 * Compact constructor to ensure defensive copying of byte array.
	 * 
	 * @param boundingHash the bounding hash value
	 * @param fingerprintHash the fingerprint hash bytes
	 */
	public MGTuple {
		// Defensive copy for fingerprintHash
		fingerprintHash = Arrays.copyOf(fingerprintHash, fingerprintHash.length);
	}
	
	/**
	 * Create an MGTuple from a file path.
	 * 
	 * @param path the file path
	 * @return MGTuple instance
	 * @throws IllegalArgumentException if path is null or empty
	 * @throws IllegalStateException if SHA-256 algorithm is unavailable
	 */
	public static MGTuple fromPath(String path) {
		if (path == null || path.trim().isEmpty()) {
			throw new IllegalArgumentException("Path cannot be null or empty");
		}
		
		try {
			// Compute SHA-256 hash of the string path
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(path.getBytes(StandardCharsets.UTF_8));
			
			// Extract bounding hash (first 4 bytes, bounded to 16 bits)
			int boundingHash = extractBoundingHash(hash);
			
			// Extract fingerprint hash (bytes 23-31, 8 bytes)
			byte[] fingerprintHash = extractFingerprint(hash);
			
			// Return new MGTuple with bounding hash and fingerprint
			return new MGTuple(boundingHash, fingerprintHash);
			
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 algorithm not available", e);
		}
	}
	
	/**
	 * Extract bounding hash from SHA-256 hash.
	 * Uses first 4 bytes and bounds to 16 bits (0-65535).
	 * 
	 * @param hash SHA-256 hash bytes
	 * @return bounding hash (0-65535)
	 */
	private static int extractBoundingHash(byte[] hash) {
		int key = ((hash[0] & 0xFF) << 24) | ((hash[1] & 0xFF) << 16) | ((hash[2] & 0xFF) << 8) | (hash[3] & 0xFF);
		return key & 0xFFFF; // Bound to 16 bits
	}
	
	/**
	 * Extract fingerprint hash from SHA-256 hash.
	 * Uses bytes 23-31 (8 bytes).
	 * 
	 * @param hash SHA-256 hash bytes
	 * @return fingerprint hash bytes (8 bytes)
	 */
	private static byte[] extractFingerprint(byte[] hash) {
		return Arrays.copyOfRange(hash, 23, 32); // 8 bytes
	}
	
	/**
	 * Get a defensive copy of the fingerprint hash.
	 * 
	 * @return copy of fingerprint hash bytes
	 */
	@Override
	public byte[] fingerprintHash() {
		return Arrays.copyOf(fingerprintHash, fingerprintHash.length); // Defensive copy
	}
}

