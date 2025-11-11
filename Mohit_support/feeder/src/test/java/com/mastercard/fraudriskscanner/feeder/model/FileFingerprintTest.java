package com.mastercard.fraudriskscanner.feeder.model;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for FileFingerprint.
 */
class FileFingerprintTest {

	@Test
	void testFromFileName_ValidFileName() {
		// Create fingerprint from filename
		FileFingerprint fingerprint = FileFingerprint.fromFileName("report.txt");
		
		// Verify it's not null
		assertNotNull(fingerprint);
		assertEquals("report.txt", fingerprint.getFileName());
		
		// Verify map key is in valid range (0-65535)
		int mapKey = fingerprint.getMapKey();
		assertTrue(mapKey >= 0 && mapKey <= 65535, "Map key should be in range 0-65535");
		
		// Verify fingerprint is 8 bytes
		byte[] fingerprintBytes = fingerprint.getFingerprint();
		assertEquals(8, fingerprintBytes.length, "Fingerprint should be 8 bytes");
	}

	@Test
	void testFromFileName_SameFileName_SameFingerprint() {
		// Create two fingerprints from same filename
		FileFingerprint fp1 = FileFingerprint.fromFileName("report.txt");
		FileFingerprint fp2 = FileFingerprint.fromFileName("report.txt");
		
		// Should have same map key and fingerprint
		assertEquals(fp1.getMapKey(), fp2.getMapKey());
		assertArrayEquals(fp1.getFingerprint(), fp2.getFingerprint());
		assertTrue(fp1.matches(fp2));
		assertEquals(fp1, fp2);
	}

	@Test
	void testFromFileName_DifferentFileName_DifferentFingerprint() {
		// Create fingerprints from different filenames
		FileFingerprint fp1 = FileFingerprint.fromFileName("report1.txt");
		FileFingerprint fp2 = FileFingerprint.fromFileName("report2.txt");
		
		// Should have different fingerprints
		assertFalse(Arrays.equals(fp1.getFingerprint(), fp2.getFingerprint()));
		assertFalse(fp1.matches(fp2));
		assertNotEquals(fp1, fp2);
	}

	@Test
	void testFromFileName_NullFileName() {
		// Should throw exception for null filename
		assertThrows(IllegalArgumentException.class, () -> {
			FileFingerprint.fromFileName(null);
		});
	}

	@Test
	void testFromFileName_EmptyFileName() {
		// Should throw exception for empty filename
		assertThrows(IllegalArgumentException.class, () -> {
			FileFingerprint.fromFileName("");
		});
		
		assertThrows(IllegalArgumentException.class, () -> {
			FileFingerprint.fromFileName("   ");
		});
	}

	@Test
	void testMapKey_Bounded() {
		// Test multiple files to ensure map keys are bounded
		for (int i = 0; i < 100; i++) {
			FileFingerprint fp = FileFingerprint.fromFileName("file" + i + ".txt");
			int mapKey = fp.getMapKey();
			assertTrue(mapKey >= 0 && mapKey <= 65535, 
				"Map key should be bounded by 2^16: " + mapKey);
		}
	}

	@Test
	void testFingerprint_Immutable() {
		// Create fingerprint
		FileFingerprint fp = FileFingerprint.fromFileName("report.txt");
		byte[] original = fp.getFingerprint();
		
		// Modify the returned array
		byte[] copy = fp.getFingerprint();
		copy[0] = (byte) 0xFF;
		
		// Original should not be affected (defensive copy)
		byte[] newCopy = fp.getFingerprint();
		assertArrayEquals(original, newCopy, "Fingerprint should be immutable");
	}

	@Test
	void testEqualsAndHashCode() {
		FileFingerprint fp1 = FileFingerprint.fromFileName("report.txt");
		FileFingerprint fp2 = FileFingerprint.fromFileName("report.txt");
		FileFingerprint fp3 = FileFingerprint.fromFileName("other.txt");
		
		// Same fingerprints should be equal
		assertEquals(fp1, fp2);
		assertEquals(fp1.hashCode(), fp2.hashCode());
		
		// Different fingerprints should not be equal
		assertNotEquals(fp1, fp3);
		
		// Self equality
		assertEquals(fp1, fp1);
		
		// Null check
		assertNotEquals(fp1, null);
		
		// Different type
		assertNotEquals(fp1, "not a fingerprint");
	}
}

