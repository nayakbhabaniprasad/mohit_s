package com.mastercard.fraudriskscanner.feeder.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MohitGujarTuple.
 */
class MohitGujarTupleTest {

	@Test
	void testConstructor_ValidInput() {
		byte[] fingerprint = new byte[]{1, 2, 3, 4, 5, 6, 7, 8};
		MohitGujarTuple tuple = new MohitGujarTuple(12345, fingerprint);
		
		assertEquals(12345, tuple.boundedHash());
		assertArrayEquals(new byte[]{1, 2, 3, 4, 5, 6, 7, 8}, tuple.fingerprintHash());
	}

	@Test
	void testConstructor_NullFingerprint() {
		assertThrows(IllegalArgumentException.class, () -> {
			new MohitGujarTuple(12345, null);
		}, "Should throw exception for null fingerprint");
	}

	@Test
	void testConstructor_WrongFingerprintSize() {
		byte[] wrongSize = new byte[]{1, 2, 3}; // Only 3 bytes, should be 8
		
		assertThrows(IllegalArgumentException.class, () -> {
			new MohitGujarTuple(12345, wrongSize);
		}, "Should throw exception for wrong fingerprint size");
	}

	@Test
	void testFingerprintHash_DefensiveCopy() {
		byte[] original = new byte[]{1, 2, 3, 4, 5, 6, 7, 8};
		MohitGujarTuple tuple = new MohitGujarTuple(12345, original);
		
		// Modify original array
		original[0] = 99;
		
		// Tuple should have defensive copy, so it shouldn't be affected
		assertEquals(1, tuple.fingerprintHash()[0], "Fingerprint hash should be a defensive copy");
	}

	@Test
	void testFingerprintHash_ReturnsDefensiveCopy() {
		byte[] fingerprint = new byte[]{1, 2, 3, 4, 5, 6, 7, 8};
		MohitGujarTuple tuple = new MohitGujarTuple(12345, fingerprint);
		
		byte[] copy1 = tuple.fingerprintHash();
		byte[] copy2 = tuple.fingerprintHash();
		
		// Should return different array instances (defensive copies)
		assertNotSame(copy1, copy2, "Each call should return a new defensive copy");
		assertArrayEquals(copy1, copy2, "But contents should be equal");
		
		// Modifying copy shouldn't affect tuple
		copy1[0] = 99;
		assertEquals(1, tuple.fingerprintHash()[0], "Modifying copy shouldn't affect tuple");
	}

	@Test
	void testMatches_SameFingerprint() {
		byte[] fingerprint = new byte[]{1, 2, 3, 4, 5, 6, 7, 8};
		MohitGujarTuple tuple1 = new MohitGujarTuple(12345, fingerprint);
		MohitGujarTuple tuple2 = new MohitGujarTuple(67890, fingerprint); // Different bounded hash
		
		assertTrue(tuple1.matches(tuple2), "Should match if fingerprint hashes are same");
	}

	@Test
	void testMatches_DifferentFingerprint() {
		byte[] fingerprint1 = new byte[]{1, 2, 3, 4, 5, 6, 7, 8};
		byte[] fingerprint2 = new byte[]{9, 10, 11, 12, 13, 14, 15, 16};
		MohitGujarTuple tuple1 = new MohitGujarTuple(12345, fingerprint1);
		MohitGujarTuple tuple2 = new MohitGujarTuple(12345, fingerprint2); // Same bounded hash
		
		assertFalse(tuple1.matches(tuple2), "Should not match if fingerprint hashes differ");
	}

	@Test
	void testMatches_Null() {
		byte[] fingerprint = new byte[]{1, 2, 3, 4, 5, 6, 7, 8};
		MohitGujarTuple tuple = new MohitGujarTuple(12345, fingerprint);
		
		assertFalse(tuple.matches(null), "Should not match null");
	}

	@Test
	void testFromFileFingerprint_Valid() {
		FileFingerprint fingerprint = FileFingerprint.fromFileName("test.txt");
		MohitGujarTuple tuple = MohitGujarTuple.fromFileFingerprint(fingerprint);
		
		assertEquals(fingerprint.getMapKey(), tuple.boundedHash());
		assertArrayEquals(fingerprint.getFingerprint(), tuple.fingerprintHash());
	}

	@Test
	void testFromFileFingerprint_Null() {
		assertThrows(IllegalArgumentException.class, () -> {
			MohitGujarTuple.fromFileFingerprint(null);
		}, "Should throw exception for null FileFingerprint");
	}

	@Test
	void testEquals_SameValues() {
		byte[] fingerprint = new byte[]{1, 2, 3, 4, 5, 6, 7, 8};
		MohitGujarTuple tuple1 = new MohitGujarTuple(12345, fingerprint);
		MohitGujarTuple tuple2 = new MohitGujarTuple(12345, fingerprint);
		
		assertEquals(tuple1, tuple2, "Records with same values should be equal");
		assertEquals(tuple1.hashCode(), tuple2.hashCode(), "Equal records should have same hashCode");
	}

	@Test
	void testEquals_DifferentBoundedHash() {
		byte[] fingerprint = new byte[]{1, 2, 3, 4, 5, 6, 7, 8};
		MohitGujarTuple tuple1 = new MohitGujarTuple(12345, fingerprint);
		MohitGujarTuple tuple2 = new MohitGujarTuple(67890, fingerprint);
		
		assertNotEquals(tuple1, tuple2, "Records with different bounded hash should not be equal");
	}

	@Test
	void testEquals_DifferentFingerprint() {
		byte[] fingerprint1 = new byte[]{1, 2, 3, 4, 5, 6, 7, 8};
		byte[] fingerprint2 = new byte[]{9, 10, 11, 12, 13, 14, 15, 16};
		MohitGujarTuple tuple1 = new MohitGujarTuple(12345, fingerprint1);
		MohitGujarTuple tuple2 = new MohitGujarTuple(12345, fingerprint2);
		
		assertNotEquals(tuple1, tuple2, "Records with different fingerprint should not be equal");
	}

	@Test
	void testToString() {
		byte[] fingerprint = new byte[]{1, 2, 3, 4, 5, 6, 7, 8};
		MohitGujarTuple tuple = new MohitGujarTuple(12345, fingerprint);
		
		String str = tuple.toString();
		assertNotNull(str);
		assertTrue(str.contains("12345"), "Should contain bounded hash");
	}

	@Test
	void testBoundedHash_Range() {
		byte[] fingerprint = new byte[]{1, 2, 3, 4, 5, 6, 7, 8};
		
		// Test minimum value
		MohitGujarTuple tuple1 = new MohitGujarTuple(0, fingerprint);
		assertEquals(0, tuple1.boundedHash());
		
		// Test maximum value
		MohitGujarTuple tuple2 = new MohitGujarTuple(65535, fingerprint);
		assertEquals(65535, tuple2.boundedHash());
		
		// Test middle value
		MohitGujarTuple tuple3 = new MohitGujarTuple(32767, fingerprint);
		assertEquals(32767, tuple3.boundedHash());
	}
}

