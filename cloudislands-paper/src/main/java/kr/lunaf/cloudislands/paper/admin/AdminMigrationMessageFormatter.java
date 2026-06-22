package kr.lunaf.cloudislands.paper.admin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import kr.lunaf.cloudislands.api.model.MigrationRunSnapshot;
import kr.lunaf.cloudislands.common.json.SimpleJson;
import kr.lunaf.cloudislands.coreclient.CoreMigrationJson;

final class AdminMigrationMessageFormatter {
    private final AdminText text;

    AdminMigrationMessageFormatter(AdminText text) {
        this.text = text == null ? (_key, fallback) -> fallback : text;
    }

    String format(MigrationRunSnapshot snapshot) {
        return format(CoreMigrationJson.toJson(snapshot));
    }

    String format(String body) {
        if (body == null || body.isBlank()) {
            return text.get("admin-command-migration-no-response", "Migration: no response");
        }
        Map<?, ?> root = SimpleJson.object(SimpleJson.parse(body));
        String code = text(root, "code");
        if (!code.isBlank()) {
            if (code.equals("MIGRATION_DISABLED")) {
                return text.get("admin-command-migration-disabled", "SuperiorSkyblock2 migration is disabled by config.");
            }
            String message = text(root, "message");
            return text.get("admin-command-migration-failed-prefix", "Migration: failed code=") + code
                + (message.isBlank() ? "" : text.get("admin-command-migration-message-prefix", " message=") + message);
        }
        long manifests = number(root, "manifests");
        if (manifests == 0L && root.containsKey("scanManifests")) {
            manifests = number(root, "scanManifests");
        }
        StringBuilder builder = new StringBuilder(text.get("admin-command-migration-state-prefix", "Migration: state="))
            .append(fallback(text(root, "state"), "UNKNOWN"))
            .append(text.get("admin-command-migration-manifests-prefix", " manifests="))
            .append(manifests);
        appendText(builder, root, "path", "admin-command-migration-path-prefix", " path=");
        appendText(builder, root, "manifestPath", "admin-command-migration-manifest-prefix", " manifest=");
        appendText(builder, root, "reportPath", "admin-command-migration-report-prefix", " report=");
        appendText(builder, root, "approvalToken", "admin-command-migration-approval-prefix", " approval=");
        appendIfPresent(builder, root, "sourcePlugin", "admin-command-migration-source-prefix", " source=");
        appendBoolIfPresent(builder, root, "migrationInputOnly", "admin-command-migration-input-only-prefix", " inputOnly=");
        appendBoolIfPresent(builder, root, "runtimeDependency", "admin-command-migration-runtime-dependency-prefix", " runtimeDependency=");
        appendText(builder, root, "migrationPipeline", "admin-command-migration-pipeline-prefix", " pipeline=");
        appendText(builder, root, "migrationChecksumPolicy", "admin-command-migration-checksum-policy-prefix", " checksumPolicy=");
        appendText(builder, root, "migrationActivationTestPolicy", "admin-command-migration-activation-policy-prefix", " activationPolicy=");
        appendText(builder, root, "targetRuntime", "admin-command-migration-target-runtime-prefix", " targetRuntime=");
        appendBoolIfPresent(builder, root, "canImport", "admin-command-migration-can-import-prefix", " canImport=");
        appendNumberIfPresent(builder, root, "planManifests", "admin-command-migration-plan-manifests-prefix", " planManifests=");
        appendBoolIfPresent(builder, root, "rollbackPlanAvailable", "admin-command-migration-rollback-plan-prefix", " rollbackPlan=");
        appendManifestStatus(builder, root);
        appendTargetFields(builder, root);
        appendImportStatus(builder, root);
        appendVerifyStatus(builder, root);
        appendRollbackStatus(builder, root);
        appendExtractionStatus(builder, root);
        appendInventoryCounts(builder, root);
        if (root.containsKey("blockingIssues")) {
            builder.append(text.get("admin-command-migration-blocking-prefix", " blocking=")).append(number(root, "blockingIssues"))
                .append(text.get("admin-command-migration-warnings-prefix", " warnings=")).append(number(root, "warningIssues"));
        }
        builder.append(issuesSuffix(SimpleJson.list(root.get("issues"))));
        return builder.toString();
    }

    private void appendManifestStatus(StringBuilder builder, Map<?, ?> root) {
        if (!root.containsKey("manifestStatus")) {
            return;
        }
        builder.append(text.get("admin-command-migration-manifest-status-prefix", " manifestStatus=")).append(text(root, "manifestStatus"))
            .append(text.get("admin-command-migration-conflict-status-prefix", " conflictStatus=")).append(text(root, "conflictStatus"))
            .append(text.get("admin-command-migration-conflicts-prefix", " conflicts=")).append(number(root, "conflictIssues"));
    }

    private void appendTargetFields(StringBuilder builder, Map<?, ?> root) {
        if (!root.containsKey("migrationTargetFields")) {
            return;
        }
        builder.append(text.get("admin-command-migration-target-fields-prefix", " targets=")).append(text(root, "migrationTargetFields"))
            .append(text.get("admin-command-migration-pipeline-prefix", " pipeline=")).append(text(root, "migrationPipelineSteps"))
            .append(text.get("admin-command-migration-commands-prefix", " commands=")).append(text(root, "migrationCommandSet"));
    }

    private void appendImportStatus(StringBuilder builder, Map<?, ?> root) {
        if (!root.containsKey("imported")) {
            return;
        }
        builder.append(text.get("admin-command-migration-imported-prefix", " imported=")).append(bool(root, "imported"))
            .append(text.get("admin-command-migration-islands-prefix", " islands=")).append(number(root, "importedIslands"));
    }

    private void appendVerifyStatus(StringBuilder builder, Map<?, ?> root) {
        if (root.containsKey("passed")) {
            builder.append(text.get("admin-command-migration-passed-prefix", " passed=")).append(bool(root, "passed"))
                .append(text.get("admin-command-migration-expected-prefix", " expected=")).append(number(root, "expected"));
        }
        if (root.containsKey("activationTested")) {
            builder.append(text.get("admin-command-migration-activation-tested-prefix", " activationTested=")).append(number(root, "activationTested"))
                .append(text.get("admin-command-migration-activation-passed-prefix", " activationPassed=")).append(number(root, "activationTestPassed"));
        }
    }

    private void appendRollbackStatus(StringBuilder builder, Map<?, ?> root) {
        if (!root.containsKey("rolledBack")) {
            return;
        }
        builder.append(text.get("admin-command-migration-rolled-back-prefix", " rolledBack=")).append(bool(root, "rolledBack"))
            .append(text.get("admin-command-migration-removed-prefix", " removed=")).append(number(root, "removedIslands"));
    }

    private void appendExtractionStatus(StringBuilder builder, Map<?, ?> root) {
        if (!root.containsKey("extractedBundles")) {
            return;
        }
        builder.append(text.get("admin-command-migration-extracted-prefix", " extracted=")).append(number(root, "extractedBundles"))
            .append(text.get("admin-command-migration-files-prefix", " files=")).append(number(root, "extractedFiles"))
            .append(text.get("admin-command-migration-bytes-prefix", " bytes=")).append(number(root, "extractedBytes"));
    }

    private void appendInventoryCounts(StringBuilder builder, Map<?, ?> root) {
        if (!root.containsKey("members")) {
            return;
        }
        builder.append(text.get("admin-command-migration-members-prefix", " members=")).append(number(root, "members"))
            .append(text.get("admin-command-migration-member-roles-prefix", " roles=")).append(number(root, "memberRoles"))
            .append(text.get("admin-command-migration-bans-prefix", " bans=")).append(number(root, "bannedVisitors"))
            .append(text.get("admin-command-migration-homes-prefix", " homes=")).append(number(root, "homes"))
            .append(text.get("admin-command-migration-warps-prefix", " warps=")).append(number(root, "warps"))
            .append(text.get("admin-command-migration-locations-prefix", " locations=")).append(number(root, "islandLocations"))
            .append(text.get("admin-command-migration-source-worlds-prefix", " sourceWorlds=")).append(number(root, "sourceWorlds"))
            .append(text.get("admin-command-migration-sizes-prefix", " sizes=")).append(number(root, "islandSizes"))
            .append(text.get("admin-command-migration-levels-prefix", " levels=")).append(number(root, "levels"))
            .append(text.get("admin-command-migration-worth-prefix", " worth=")).append(number(root, "worthValues"))
            .append(text.get("admin-command-migration-biomes-prefix", " biomes=")).append(number(root, "biomes"))
            .append(text.get("admin-command-migration-bank-prefix", " bank=")).append(number(root, "bankBalances"))
            .append(text.get("admin-command-migration-flags-prefix", " flags=")).append(number(root, "flags"))
            .append(text.get("admin-command-migration-perms-prefix", " perms=")).append(number(root, "permissions"))
            .append(text.get("admin-command-migration-upgrades-prefix", " upgrades=")).append(number(root, "upgrades"))
            .append(text.get("admin-command-migration-limits-prefix", " limits=")).append(number(root, "limits"))
            .append(text.get("admin-command-migration-missions-prefix", " missions=")).append(number(root, "completedMissions"))
            .append(text.get("admin-command-migration-block-values-prefix", " blockValues=")).append(number(root, "blockValues"))
            .append(text.get("admin-command-migration-block-counts-prefix", " blockCounts=")).append(number(root, "blockCounts"));
    }

    private String issuesSuffix(List<?> issues) {
        if (issues.isEmpty()) {
            return text.get("admin-command-issues-zero", " issues=0");
        }
        int blocking = 0;
        List<String> samples = new ArrayList<>();
        for (Object issue : issues) {
            Map<?, ?> object = SimpleJson.object(issue);
            boolean blocked = bool(object, "blocking");
            if (blocked) {
                blocking++;
            }
            if (samples.size() < 5) {
                String issueCode = text(object, "code");
                samples.add((issueCode.isBlank() ? "UNKNOWN" : issueCode) + (blocked ? "(blocking)" : ""));
            }
        }
        return text.get("admin-command-issues-total-prefix", " issues=") + issues.size()
            + text.get("admin-command-issues-blocking-prefix", " blocking=") + blocking
            + (samples.isEmpty() ? "" : " [" + String.join(", ", samples) + "]");
    }

    private void appendText(StringBuilder builder, Map<?, ?> root, String field, String key, String fallback) {
        String value = text(root, field);
        if (!value.isBlank()) {
            builder.append(text.get(key, fallback)).append(value);
        }
    }

    private void appendIfPresent(StringBuilder builder, Map<?, ?> root, String field, String key, String fallback) {
        if (root.containsKey(field)) {
            builder.append(text.get(key, fallback)).append(text(root, field));
        }
    }

    private void appendBoolIfPresent(StringBuilder builder, Map<?, ?> root, String field, String key, String fallback) {
        if (root.containsKey(field)) {
            builder.append(text.get(key, fallback)).append(bool(root, field));
        }
    }

    private void appendNumberIfPresent(StringBuilder builder, Map<?, ?> root, String field, String key, String fallback) {
        if (root.containsKey(field)) {
            builder.append(text.get(key, fallback)).append(number(root, field));
        }
    }

    private static String fallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String text(Map<?, ?> root, String field) {
        return SimpleJson.text(root.get(field));
    }

    private static long number(Map<?, ?> root, String field) {
        return SimpleJson.number(root.get(field));
    }

    private static boolean bool(Map<?, ?> root, String field) {
        Object value = root.get(field);
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(SimpleJson.text(value));
    }

    @FunctionalInterface
    interface AdminText {
        String get(String key, String fallback);
    }
}
