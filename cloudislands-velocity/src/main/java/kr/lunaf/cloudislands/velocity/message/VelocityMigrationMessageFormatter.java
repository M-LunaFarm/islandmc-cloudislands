package kr.lunaf.cloudislands.velocity.message;

import java.util.ArrayList;
import java.util.List;
import kr.lunaf.cloudislands.api.model.MigrationIssueSnapshot;
import kr.lunaf.cloudislands.api.model.MigrationRunSnapshot;

public final class VelocityMigrationMessageFormatter {
    public String format(MigrationRunSnapshot snapshot) {
        if (snapshot == null) {
            return "Migration: no response";
        }
        String code = failureCode(snapshot);
        if (!code.isBlank()) {
            return failureMessage(snapshot, code);
        }
        StringBuilder builder = new StringBuilder("Migration: state=")
            .append(text(snapshot.state(), "UNKNOWN"))
            .append(" manifests=")
            .append(snapshot.manifests());
        appendText(builder, " path=", snapshot.path());
        appendText(builder, " manifest=", snapshot.manifestPath());
        appendText(builder, " report=", snapshot.reportPath());
        appendText(builder, " approval=", snapshot.approvalToken());
        builder.append(" canImport=").append(snapshot.canImport())
            .append(" rollbackPlan=").append(snapshot.rollbackPlanAvailable())
            .append(" imported=").append(snapshot.imported())
            .append(" islands=").append(snapshot.importedIslands())
            .append(" passed=").append(snapshot.passed())
            .append(" expected=").append(snapshot.expected())
            .append(" activationTested=").append(snapshot.activationTested())
            .append(" activationPassed=").append(snapshot.activationTestPassed())
            .append(" rolledBack=").append(snapshot.rolledBack())
            .append(" removed=").append(snapshot.removedIslands())
            .append(" extracted=").append(snapshot.extractedBundles())
            .append(" files=").append(snapshot.extractedFiles())
            .append(" bytes=").append(snapshot.extractedBytes());
        appendImportedEntityFields(builder, snapshot);
        builder.append(" blocking=").append(snapshot.blockingIssues())
            .append(" warnings=").append(snapshot.warningIssues())
            .append(issuesSuffix(snapshot.issues()));
        return builder.toString();
    }

    private String failureMessage(MigrationRunSnapshot snapshot, String code) {
        if (code.equals("MIGRATION_DISABLED")) {
            return "SuperiorSkyblock2 migration is disabled by config.";
        }
        String message = issueMessage(snapshot, code);
        return "Migration: failed code=" + code + (message.isBlank() ? "" : " message=" + message);
    }

    private void appendImportedEntityFields(StringBuilder builder, MigrationRunSnapshot snapshot) {
        if (!hasImportedEntityFields(snapshot)) {
            return;
        }
        builder.append(" members=").append(snapshot.members())
            .append(" bans=").append(snapshot.bannedVisitors())
            .append(" homes=").append(snapshot.homes())
            .append(" warps=").append(snapshot.warps())
            .append(" flags=").append(snapshot.flags())
            .append(" perms=").append(snapshot.permissions())
            .append(" upgrades=").append(snapshot.upgrades())
            .append(" limits=").append(snapshot.limits())
            .append(" missions=").append(snapshot.completedMissions())
            .append(" blockValues=").append(snapshot.blockValues())
            .append(" blockCounts=").append(snapshot.blockCounts());
    }

    private boolean hasImportedEntityFields(MigrationRunSnapshot snapshot) {
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
            return " issues=0";
        }
        int blocking = 0;
        List<String> samples = new ArrayList<>();
        for (MigrationIssueSnapshot issue : issues) {
            if (issue.blocking()) {
                blocking++;
            }
            if (samples.size() < 5) {
                String issueCode = text(issue.code(), "UNKNOWN");
                samples.add(issueCode + (issue.blocking() ? "(blocking)" : ""));
            }
        }
        return " issues=" + issues.size()
            + " blocking=" + blocking
            + (samples.isEmpty() ? "" : " [" + String.join(", ", samples) + "]");
    }

    private static void appendText(StringBuilder builder, String prefix, String value) {
        if (value != null && !value.isBlank()) {
            builder.append(prefix).append(value);
        }
    }

    private static String failureCode(MigrationRunSnapshot snapshot) {
        String state = text(snapshot.state(), "");
        if (state.equals("MIGRATION_DISABLED") || state.startsWith("INVALID_")) {
            return state;
        }
        if (!snapshot.sourceScanned() && !state.isBlank() && state.contains("_") && snapshot.issues() != null && !snapshot.issues().isEmpty()) {
            return state;
        }
        return "";
    }

    private static String issueMessage(MigrationRunSnapshot snapshot, String code) {
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

    private static String text(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
