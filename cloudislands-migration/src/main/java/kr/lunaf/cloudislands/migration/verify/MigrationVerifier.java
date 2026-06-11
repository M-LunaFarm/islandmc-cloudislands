package kr.lunaf.cloudislands.migration.verify;

import java.util.ArrayList;
import java.util.List;
import kr.lunaf.cloudislands.migration.MigrationIssue;
import kr.lunaf.cloudislands.migration.MigrationManifest;

public final class MigrationVerifier {
    public VerificationResult verify(List<MigrationManifest> expected, List<MigrationManifest> imported) {
        List<MigrationIssue> issues = new ArrayList<>();
        if (expected.size() != imported.size()) {
            issues.add(new MigrationIssue("COUNT_MISMATCH", "expected " + expected.size() + " imported islands but found " + imported.size(), true));
        }
        for (MigrationManifest manifest : expected) {
            boolean found = imported.stream().anyMatch(candidate -> candidate.islandId().equals(manifest.islandId()) && candidate.ownerUuid().equals(manifest.ownerUuid()));
            if (!found) {
                issues.add(new MigrationIssue("MISSING_IMPORTED_ISLAND", "missing imported island " + manifest.islandId(), true));
            }
        }
        return new VerificationResult(issues.isEmpty(), issues);
    }

    public record VerificationResult(boolean passed, List<MigrationIssue> issues) {}
}
