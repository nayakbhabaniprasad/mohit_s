package com.mastercard.fraudriskscanner.feeder.model;

import java.util.Arrays;

/**
 * Immutable record representing a tuple containing two hashes.
 * 
 * This is the "magical thing" that contains:
 * - Bounded hash: Small hash that will have duplicates (good - keeps map small)
 * - Fingerprint hash: Bigger hash where duplicates are highly unlikely (good)
 * 
 * These two things live together, are immutable, and are good. Very good.
 * 
 * @author BizOps Bank Team
 */
public record MohitGujarTuple(
	/**
	 * Bounded hash - small, will have duplicates.
	 * This is good because it keeps our map small.
	 * Range: 0-65535 (bounded by 2^16)
	 */
	int boundedHash,
	
	/**
	 * Fingerprint hash - bigger, duplicates highly unlikely.
	 * This is good because it ensures uniqueness.
	 * Size: 8 bytes
	 */
	byte[] fingerprintHash
) {
	/**
	 * Compact constructor to ensure defensive copying of byte array.
	 * 
	 * @param boundedHash the bounded hash value
	 * @param fingerprintHash the fingerprint hash bytes
	 */
	public MohitGujarTuple {
		if (fingerprintHash == null) {
			throw new IllegalArgumentException("Fingerprint hash cannot be null");
		}
		if (fingerprintHash.length != 8) {
			throw new IllegalArgumentException("Fingerprint hash must be exactly 8 bytes, got: " + fingerprintHash.length);
		}
		// Defensive copy to ensure immutability
		fingerprintHash = Arrays.copyOf(fingerprintHash, fingerprintHash.length);
	}
	
	/**
	 * Get a defensive copy of the fingerprint hash.
	 * 
	 * @return copy of fingerprint hash bytes
	 */
	public byte[] fingerprintHash() {
		return Arrays.copyOf(fingerprintHash, fingerprintHash.length);
	}
	
	/**
	 * Check if this tuple matches another based on fingerprint hash.
	 * 
	 * @param other tuple to compare
	 * @return true if fingerprint hashes match
	 */
	public boolean matches(MohitGujarTuple other) {
		if (other == null) {
			return false;
		}
		return Arrays.equals(this.fingerprintHash, other.fingerprintHash);
	}
	
	/**
	 * Create a MohitGujarTuple from a FileFingerprint.
	 * 
	 * @param fingerprint file fingerprint
	 * @return MohitGujarTuple instance
	 */
	public static MohitGujarTuple fromFileFingerprint(FileFingerprint fingerprint) {
		if (fingerprint == null) {
			throw new IllegalArgumentException("FileFingerprint cannot be null");
		}
		return new MohitGujarTuple(fingerprint.getMapKey(), fingerprint.getFingerprint());
	}
}


