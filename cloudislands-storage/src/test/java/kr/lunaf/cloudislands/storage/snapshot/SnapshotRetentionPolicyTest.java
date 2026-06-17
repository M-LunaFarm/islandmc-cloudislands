package kr.lunaf.cloudislands.storage.snapshot;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnapshotRetentionPolicyTest {
    @Test
    void defaultPolicyKeepsExpectedAutomaticAndManualSnapshots() {
        SnapshotRetentionPolicy policy = SnapshotRetentionPolicy.defaultPolicy();

        assertEquals(24, policy.keepHourly());
        assertEquals(7, policy.keepDaily());
        assertEquals(4, policy.keepWeekly());
        assertEquals(50, policy.keepManual());
        assertTrue(policy.compress());
        assertEquals("SHA-256", policy.checksumAlgorithm());
        assertEquals(35, policy.retainedAutomaticSnapshotCount());
        assertEquals(50, policy.retainedManualSnapshotCount());
        assertEquals(85, policy.retainedSnapshotCount());
    }

    @Test
    void normalizedPolicyClampsRetentionAndDefaultsChecksum() {
        SnapshotRetentionPolicy policy = new SnapshotRetentionPolicy(-10, -3, -2, -1, false, "   ");

        SnapshotRetentionPolicy normalized = policy.normalized();

        assertEquals(1, normalized.keepHourly());
        assertEquals(0, normalized.keepDaily());
        assertEquals(0, normalized.keepWeekly());
        assertEquals(0, normalized.keepManual());
        assertEquals("SHA-256", normalized.checksumAlgorithm());
        assertEquals(1, normalized.retainedAutomaticSnapshotCount());
        assertEquals(0, normalized.retainedManualSnapshotCount());
        assertEquals(1, normalized.retainedSnapshotCount());
    }

    @Test
    void normalizedPolicyUppercasesExplicitChecksumAlgorithm() {
        SnapshotRetentionPolicy policy = new SnapshotRetentionPolicy(1, 2, 3, 4, true, " sha-256 ");

        assertEquals("SHA-256", policy.normalized().checksumAlgorithm());
    }

    @Test
    void retainedSnapshotCountSaturatesInsteadOfOverflowing() {
        SnapshotRetentionPolicy policy = new SnapshotRetentionPolicy(
                Integer.MAX_VALUE,
                Integer.MAX_VALUE,
                Integer.MAX_VALUE,
                Integer.MAX_VALUE,
                true,
                "SHA-256"
        );

        assertEquals(Integer.MAX_VALUE, policy.retainedAutomaticSnapshotCount());
        assertEquals(Integer.MAX_VALUE, policy.retainedSnapshotCount());
    }
}
