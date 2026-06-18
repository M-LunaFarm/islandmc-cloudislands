package kr.lunaf.cloudislands.migration.importer;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import kr.lunaf.cloudislands.migration.MigrationIssue;
import kr.lunaf.cloudislands.migration.MigrationManifest;
import kr.lunaf.cloudislands.migration.MigrationReport;
import kr.lunaf.cloudislands.migration.MigrationReportBuilder;
import kr.lunaf.cloudislands.migration.superior.MigrationSafetyPolicy;

public record MigrationImportPlan(List<MigrationManifest> manifests, List<MigrationIssue> issues, String sourceFingerprint, String approvalToken) {
    public MigrationImportPlan {
        manifests = manifests == null ? List.of() : List.copyOf(manifests);
        issues = issues == null ? List.of() : List.copyOf(issues);
        sourceFingerprint = sourceFingerprint == null || sourceFingerprint.isBlank() ? fingerprint(manifests) : sourceFingerprint;
        approvalToken = approvalToken == null ? "" : approvalToken;
    }

    public MigrationImportPlan(List<MigrationManifest> manifests, List<MigrationIssue> issues) {
        this(manifests, issues, fingerprint(manifests), "");
    }

    public boolean canImport() {
        return importPreflightSatisfied();
    }

    public boolean importPreflightSatisfied() {
        return MigrationSafetyPolicy.importPreflightSatisfied(report().canImport(), approved(), sourceFingerprintMatches());
    }

    public MigrationReport report() {
        return MigrationReportBuilder.build(manifests, issues);
    }

    public MigrationImportPlan approve(String token) {
        return new MigrationImportPlan(manifests, issues, sourceFingerprint, token);
    }

    public boolean approved() {
        return requiredApprovalToken().equals(approvalToken);
    }

    public boolean sourceFingerprintMatches() {
        return sourceFingerprint.equals(fingerprint(manifests));
    }

    public String requiredApprovalToken() {
        return "ci-migrate-" + sourceFingerprint;
    }

    public static String fingerprint(List<MigrationManifest> manifests) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            if (manifests != null) {
                manifests.stream()
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparing(manifest -> String.valueOf(manifest.islandId())))
                    .forEach(manifest -> update(digest, manifest));
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private static void update(MessageDigest digest, MigrationManifest manifest) {
        put(digest, manifest.islandId());
        put(digest, manifest.ownerUuid());
        put(digest, manifest.members());
        put(digest, manifest.memberRoles());
        put(digest, manifest.bannedVisitors());
        put(digest, manifest.homes());
        put(digest, manifest.warps());
        put(digest, manifest.flags());
        put(digest, manifest.permissions());
        put(digest, manifest.upgrades());
        put(digest, manifest.limits());
        put(digest, manifest.completedMissions());
        put(digest, manifest.blockValues());
        put(digest, manifest.blockCounts());
        put(digest, manifest.biomeKey());
        put(digest, manifest.bankBalance());
        put(digest, manifest.publicAccess());
        put(digest, manifest.locked());
        put(digest, manifest.size());
        put(digest, manifest.level());
        put(digest, manifest.worth());
        put(digest, manifest.islandLocation());
        put(digest, manifest.sourceWorldPath());
    }

    private static void put(MessageDigest digest, Object value) {
        digest.update(String.valueOf(value).getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
    }
}
