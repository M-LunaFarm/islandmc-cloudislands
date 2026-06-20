package kr.lunaf.cloudislands.api.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ApiCompatibilityPolicyTest {
    @Test
    void acceptsSameMajorRuntimeThatIsAtLeastRequestedVersion() {
        ApiCompatibilityDecision decision = ApiCompatibilityPolicy.evaluate("1.0.0", "1.1.0");

        assertTrue(decision.compatible());
        assertEquals(ApiCompatibilityStatus.COMPATIBLE, decision.status());
    }

    @Test
    void rejectsRuntimeOlderThanRequestedMinorVersion() {
        ApiCompatibilityDecision decision = ApiCompatibilityPolicy.evaluate("1.2.0", "1.1.0");

        assertFalse(decision.compatible());
        assertEquals(ApiCompatibilityStatus.RUNTIME_TOO_OLD, decision.status());
    }

    @Test
    void rejectsMajorVersionMismatchAndInvalidVersions() {
        assertEquals(ApiCompatibilityStatus.MAJOR_VERSION_MISMATCH, ApiCompatibilityPolicy.evaluate("2.0.0", "1.1.0").status());
        assertEquals(ApiCompatibilityStatus.INVALID_VERSION, ApiCompatibilityPolicy.evaluate("1", "1.1.0").status());
    }

    @Test
    void enforcesOneMinorReleaseDeprecationRemovalWindow() {
        assertFalse(ApiCompatibilityPolicy.deprecationRemoval("1.0.1", "1.0.2").removable());
        assertTrue(ApiCompatibilityPolicy.deprecationRemoval("1.0.1", "1.1.0").removable());
        assertTrue(ApiCompatibilityPolicy.deprecationRemoval("1.4.0", "2.0.0").removable());
    }
}
