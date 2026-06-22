package kr.lunaf.cloudislands.paper.admin;

import java.util.ArrayList;
import java.util.List;
import kr.lunaf.cloudislands.api.model.MigrationIssueSnapshot;
import kr.lunaf.cloudislands.api.model.MigrationRunSnapshot;

final class AdminMigrationMessageFormatter {
    private final AdminText text;

    AdminMigrationMessageFormatter(AdminText text) {
        this.text = text == null ? (_key, fallback) -> fallback : text;
    }

    String format(MigrationRunSnapshot snapshot) {
        if (snapshot == null) {
            return text.get("admin-command-migration-no-response", "Migration: no response");
        }
        String code = failureCode(snapshot);
        if (!code.isBlank()) {
            if (code.equals("MIGRATION_DISABLED")) {
                return text.get("admin-command-migration-disabled", "SuperiorSkyblock2 migration is disabled by config.");
            }
            String message = failureMessage(snapshot, code);
            return text.get("admin-command-migration-failed-prefix", "Migration: failed code=") + code
                + (message.isBlank() ? "" : text.get("admin-command-migration-message-prefix", " message=") + message);
        }
        StringBuilder builder = new StringBuilder(text.get("admin-command-migration-state-prefix", "Migration: state="))
            .append(fallback(snapshot.state(), "UNKNOWN"))
            .append(text.get("admin-command-migration-manifests-prefix", " manifests="))
            .append(snapshot.manifests());
        appendText(builder, snapshot.path(), "admin-command-migration-path-prefix", " path=");
        appendText(builder, snapshot.manifestPath(), "admin-command-migration-manifest-prefix", " manifest=");
        appendText(builder, snapshot.reportPath(), "admin-command-migration-report-prefix", " report=");
        appendText(builder, snapshot.approvalToken(), "admin-command-migration-approval-prefix", " approval=");
        appendPlanningStatus(builder, snapshot);
        appendImportStatus(builder, snapshot);
        appendVerifyStatus(builder, snapshot);
        appendRollbackStatus(builder, snapshot);
        appendExtractionStatus(builder, snapshot);
        appendInventoryCounts(builder, snapshot);
        builder.append(text.get("admin-command-migration-blocking-prefix", " blocking=")).append(snapshot.blockingIssues())
            .append(text.get("admin-command-migration-warnings-prefix", " warnings=")).append(snapshot.warningIssues());
        builder.append(issuesSuffix(snapshot.issues()));
        return builder.toString();
    }

    private void appendPlanningStatus(StringBuilder builder, MigrationRunSnapshot snapshot) {
        builder.append(text.get("admin-command-migration-can-import-prefix", " canImport=")).append(snapshot.canImport())
            .append(text.get("admin-command-migration-rollback-plan-prefix", " rollbackPlan=")).append(snapshot.rollbackPlanAvailable());
    }

    private void appendImportStatus(StringBuilder builder, MigrationRunSnapshot snapshot) {
        builder.append(text.get("admin-command-migration-imported-prefix", " imported=")).append(snapshot.imported())
            .append(text.get("admin-command-migration-islands-prefix", " islands=")).append(snapshot.importedIslands());
    }

    private void appendVerifyStatus(StringBuilder builder, MigrationRunSnapshot snapshot) {
        builder.append(text.get("admin-command-migration-passed-prefix", " passed=")).append(snapshot.passed())
            .append(text.get("admin-command-migration-expected-prefix", " expected=")).append(snapshot.expected())
            .append(text.get("admin-command-migration-activation-tested-prefix", " activationTested=")).append(snapshot.activationTested())
            .append(text.get("admin-command-migration-activation-passed-prefix", " activationPassed=")).append(snapshot.activationTestPassed());
    }

    private void appendRollbackStatus(StringBuilder builder, MigrationRunSnapshot snapshot) {
        builder.append(text.get("admin-command-migration-rolled-back-prefix", " rolledBack=")).append(snapshot.rolledBack())
            .append(text.get("admin-command-migration-removed-prefix", " removed=")).append(snapshot.removedIslands());
    }

    private void appendExtractionStatus(StringBuilder builder, MigrationRunSnapshot snapshot) {
        builder.append(text.get("admin-command-migration-extracted-prefix", " extracted=")).append(snapshot.extractedBundles())
            .append(text.get("admin-command-migration-files-prefix", " files=")).append(snapshot.extractedFiles())
            .append(text.get("admin-command-migration-bytes-prefix", " bytes=")).append(snapshot.extractedBytes());
    }

    private void appendInventoryCounts(StringBuilder builder, MigrationRunSnapshot snapshot) {
        if (!hasInventoryCounts(snapshot)) {
            return;
        }
        builder.append(text.get("admin-command-migration-members-prefix", " members=")).append(snapshot.members())
            .append(text.get("admin-command-migration-bans-prefix", " bans=")).append(snapshot.bannedVisitors())
            .append(text.get("admin-command-migration-homes-prefix", " homes=")).append(snapshot.homes())
            .append(text.get("admin-command-migration-warps-prefix", " warps=")).append(snapshot.warps())
            .append(text.get("admin-command-migration-flags-prefix", " flags=")).append(snapshot.flags())
            .append(text.get("admin-command-migration-perms-prefix", " perms=")).append(snapshot.permissions())
            .append(text.get("admin-command-migration-upgrades-prefix", " upgrades=")).append(snapshot.upgrades())
            .append(text.get("admin-command-migration-limits-prefix", " limits=")).append(snapshot.limits())
            .append(text.get("admin-command-migration-missions-prefix", " missions=")).append(snapshot.completedMissions())
            .append(text.get("admin-command-migration-block-values-prefix", " blockValues=")).append(snapshot.blockValues())
            .append(text.get("admin-command-migration-block-counts-prefix", " blockCounts=")).append(snapshot.blockCounts());
    }

    private boolean hasInventoryCounts(MigrationRunSnapshot snapshot) {
        return snapshot.members() > 0
            || snapshot.bannedVisitors() > 0
            || snapshot.homes() > 0
            || snapshot.warps() > 0
            || snapshot.flags() > 0
            || snapshot.permissions() > 0
            || snapshot.upgrades() > 0
            || snapshot.limits() > 0
            || snapshot.completedMissions() > 0
            || snapshot.blockValues() > 0
            || snapshot.blockCounts() > 0;
    }

    private String issuesSuffix(List<MigrationIssueSnapshot> issues) {
        issues = issues == null ? List.of() : issues;
        if (issues.isEmpty()) {
            return text.get("admin-command-issues-zero", " issues=0");
        }
        int blocking = 0;
        List<String> samples = new ArrayList<>();
        for (MigrationIssueSnapshot issue : issues) {
            if (issue.blocking()) {
                blocking++;
            }
            if (samples.size() < 5) {
                String issueCode = issue.code();
                samples.add((issueCode == null || issueCode.isBlank() ? "UNKNOWN" : issueCode) + (issue.blocking() ? "(blocking)" : ""));
            }
        }
        return text.get("admin-command-issues-total-prefix", " issues=") + issues.size()
            + text.get("admin-command-issues-blocking-prefix", " blocking=") + blocking
            + (samples.isEmpty() ? "" : " [" + String.join(", ", samples) + "]");
    }

    private void appendText(StringBuilder builder, String value, String key, String fallback) {
        if (value != null && !value.isBlank()) {
            builder.append(text.get(key, fallback)).append(value);
        }
    }

    private static String fallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String failureCode(MigrationRunSnapshot snapshot) {
        String state = fallback(snapshot.state(), "");
        if (state.equals("MIGRATION_DISABLED") || state.startsWith("INVALID_")) {
            return state;
        }
        if (!snapshot.sourceScanned() && !state.isBlank() && state.contains("_") && snapshot.issues() != null && !snapshot.issues().isEmpty()) {
            return state;
        }
        return "";
    }

    private static String failureMessage(MigrationRunSnapshot snapshot, String code) {
        if (snapshot.issues() == null) {
            return "";
        }
        return snapshot.issues().stream()
            .filter(issue -> code.equals(issue.code()))
            .map(MigrationIssueSnapshot::message)
            .filter(message -> message != null && !message.isBlank())
            .findFirst()
            .orElse("");
    }

    @FunctionalInterface
    interface AdminText {
        String get(String key, String fallback);
    }
}
