package kr.lunaf.cloudislands.migration.verify;

import kr.lunaf.cloudislands.migration.MigrationManifest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MigrationVerifierTest {
    private static final UUID ISLAND_A = UUID.fromString("00000000-0000-0000-0000-000000000701");
    private static final UUID ISLAND_B = UUID.fromString("00000000-0000-0000-0000-000000000702");
    private static final UUID OWNER_A = UUID.fromString("00000000-0000-0000-0000-000000000801");
    private static final UUID OWNER_B = UUID.fromString("00000000-0000-0000-0000-000000000802");

    @Test
    void verifyPassesWhenImportedManifestMatchesExpectedManifest() {
        MigrationVerifier verifier = new MigrationVerifier();
        MigrationManifest manifest = manifest(ISLAND_A, OWNER_A, 300, 120L, "125000");

        MigrationVerifier.VerificationResult result = verifier.verify(List.of(manifest), List.of(manifest));

        assertTrue(result.passed());
        assertTrue(result.issues().isEmpty());
        assertEquals(1, result.report().manifests());
        assertTrue(result.report().canImport());
    }

    @Test
    void verifyReportsMissingDuplicateAndMismatchedImportedState() {
        MigrationVerifier verifier = new MigrationVerifier();
        MigrationManifest expectedA = manifest(ISLAND_A, OWNER_A, 300, 120L, "125000");
        MigrationManifest expectedB = manifest(ISLAND_B, OWNER_B, 500, 220L, "225000");
        MigrationManifest wrongA = manifest(ISLAND_A, OWNER_B, 300, 120L, "125000");

        MigrationVerifier.VerificationResult result = verifier.verify(List.of(expectedA, expectedB), List.of(wrongA, wrongA));

        assertFalse(result.passed());
        assertTrue(result.issues().stream().anyMatch(issue -> issue.code().equals("DUPLICATE_IMPORTED_ID")));
        assertTrue(result.issues().stream().anyMatch(issue -> issue.code().equals("MISSING_IMPORTED_ISLAND")));
        assertTrue(result.issues().stream().anyMatch(issue -> issue.code().equals("OWNER_MISMATCH")));
        assertFalse(result.report().canImport());
    }

    @Test
    void verifyHandlesNullInputsAsEmptyLists() {
        MigrationVerifier verifier = new MigrationVerifier();

        MigrationVerifier.VerificationResult result = verifier.verify(null, null);

        assertTrue(result.passed());
        assertTrue(result.issues().isEmpty());
        assertEquals(0, result.report().manifests());
    }

    private static MigrationManifest manifest(UUID islandId, UUID ownerId, int size, long level, String worth) {
        return new MigrationManifest(
                islandId,
                ownerId,
                List.of(ownerId),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                "minecraft:plains",
                "1000",
                true,
                false,
                size,
                level,
                worth,
                "/superior/islands/" + islandId
        );
    }
}
