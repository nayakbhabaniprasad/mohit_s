package com.mastercard.fraudriskscanner.feeder.model;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MGTuple.
 */
class MGTupleTest {

	@Test
	void testConstructor_ValidInput() {
		byte[] fingerprint = new byte[]{1, 2, 3, 4, 5, 6, 7, 8};
		MGTuple tuple = new MGTuple(12345, fingerprint);
		
		assertEquals(12345, tuple.boundingHash());
		assertArrayEquals(new byte[]{1, 2, 3, 4, 5, 6, 7, 8}, tuple.fingerprintHash());
	}

	@Test
	void testConstructor_NullFingerprint() {
		assertThrows(NullPointerException.class, () -> {
			new MGTuple(12345, null);
		}, "Should throw exception for null fingerprint");
	}

	@Test
	void testFingerprintHash_DefensiveCopy() {
		byte[] original = new byte[]{1, 2, 3, 4, 5, 6, 7, 8};
		MGTuple tuple = new MGTuple(12345, original);
		
		// Modify original array
		original[0] = 99;
		
		// Tuple should have defensive copy, so it shouldn't be affected
		assertEquals(1, tuple.fingerprintHash()[0], "Fingerprint hash should be a defensive copy");
	}

	@Test
	void testFingerprintHash_ReturnsDefensiveCopy() {
		byte[] fingerprint = new byte[]{1, 2, 3, 4, 5, 6, 7, 8};
		MGTuple tuple = new MGTuple(12345, fingerprint);
		
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
	void testFromPath_Valid() {
		MGTuple tuple = MGTuple.fromPath("test.txt");
		
		assertNotNull(tuple);
		assertTrue(tuple.boundingHash() >= 0 && tuple.boundingHash() <= 65535, "Bounding hash should be in range 0-65535");
		assertEquals(8, tuple.fingerprintHash().length, "Fingerprint hash should be 8 bytes");
	}

	@Test
	void testFromPath_Null() {
		assertThrows(IllegalArgumentException.class, () -> {
			MGTuple.fromPath(null);
		}, "Should throw exception for null path");
	}

	@Test
	void testFromPath_Empty() {
		assertThrows(IllegalArgumentException.class, () -> {
			MGTuple.fromPath("");
		}, "Should throw exception for empty path");
	}

	@Test
	void testFromPath_Whitespace() {
		assertThrows(IllegalArgumentException.class, () -> {
			MGTuple.fromPath("   ");
		}, "Should throw exception for whitespace-only path");
	}

	@Test
	void testFromPath_SamePath_SameTuple() {
		MGTuple tuple1 = MGTuple.fromPath("test.txt");
		MGTuple tuple2 = MGTuple.fromPath("test.txt");
		
		assertEquals(tuple1.boundingHash(), tuple2.boundingHash(), "Same path should produce same bounding hash");
		assertArrayEquals(tuple1.fingerprintHash(), tuple2.fingerprintHash(), "Same path should produce same fingerprint");
	}

	@Test
	void testFromPath_DifferentPath_DifferentTuple() {
		MGTuple tuple1 = MGTuple.fromPath("test1.txt");
		MGTuple tuple2 = MGTuple.fromPath("test2.txt");
		
		// Different paths should produce different tuples (very high probability)
		boolean different = tuple1.boundingHash() != tuple2.boundingHash() || 
		                   !Arrays.equals(tuple1.fingerprintHash(), tuple2.fingerprintHash());
		assertTrue(different, "Different paths should produce different tuples");
	}

	@Test
	void testEquals_SameValues() {
		byte[] fingerprint = new byte[]{1, 2, 3, 4, 5, 6, 7, 8};
		MGTuple tuple1 = new MGTuple(12345, fingerprint);
		MGTuple tuple2 = new MGTuple(12345, fingerprint);
		
		assertEquals(tuple1, tuple2, "Records with same values should be equal");
		assertEquals(tuple1.hashCode(), tuple2.hashCode(), "Equal records should have same hashCode");
	}

	@Test
	void testEquals_DifferentBoundingHash() {
		byte[] fingerprint = new byte[]{1, 2, 3, 4, 5, 6, 7, 8};
		MGTuple tuple1 = new MGTuple(12345, fingerprint);
		MGTuple tuple2 = new MGTuple(67890, fingerprint);
		
		assertNotEquals(tuple1, tuple2, "Records with different bounding hash should not be equal");
	}

	@Test
	void testEquals_DifferentFingerprint() {
		byte[] fingerprint1 = new byte[]{1, 2, 3, 4, 5, 6, 7, 8};
		byte[] fingerprint2 = new byte[]{9, 10, 11, 12, 13, 14, 15, 16};
		MGTuple tuple1 = new MGTuple(12345, fingerprint1);
		MGTuple tuple2 = new MGTuple(12345, fingerprint2);
		
		assertNotEquals(tuple1, tuple2, "Records with different fingerprint should not be equal");
	}

	@Test
	void testToString() {
		byte[] fingerprint = new byte[]{1, 2, 3, 4, 5, 6, 7, 8};
		MGTuple tuple = new MGTuple(12345, fingerprint);
		
		String str = tuple.toString();
		assertNotNull(str);
		assertTrue(str.contains("12345"), "Should contain bounding hash");
	}

	@Test
	void testBoundingHash_Range() {
		byte[] fingerprint = new byte[]{1, 2, 3, 4, 5, 6, 7, 8};
		
		// Test minimum value
		MGTuple tuple1 = new MGTuple(0, fingerprint);
		assertEquals(0, tuple1.boundingHash());
		
		// Test maximum value
		MGTuple tuple2 = new MGTuple(65535, fingerprint);
		assertEquals(65535, tuple2.boundingHash());
		
		// Test middle value
		MGTuple tuple3 = new MGTuple(32767, fingerprint);
		assertEquals(32767, tuple3.boundingHash());
	}
}

