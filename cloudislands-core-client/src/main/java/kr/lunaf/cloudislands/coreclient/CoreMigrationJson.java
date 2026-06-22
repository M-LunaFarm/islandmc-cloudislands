package kr.lunaf.cloudislands.coreclient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kr.lunaf.cloudislands.api.model.MigrationIssueSnapshot;
import kr.lunaf.cloudislands.api.model.MigrationRunSnapshot;
import kr.lunaf.cloudislands.common.json.SimpleJson;

public final class CoreMigrationJson {
    private CoreMigrationJson() {
    }

    public static MigrationRunSnapshot run(String body) {
        Map<?, ?> root = CoreJson.object(body);
        String code = CoreJson.text(root, "code");
        if (!code.isBlank()) {
            return new MigrationRunSnapshot(code, CoreJson.text(root, "path"), 0, false, false, 0, false, 0, false, 0, List.of(new MigrationIssueSnapshot(code, CoreJson.text(root, "message"), true)));
        }
        long manifests = CoreJson.number(root, "manifests");
        if (manifests == 0L && root.containsKey("scanManifests")) {
            manifests = CoreJson.number(root, "scanManifests");
        }
        return new MigrationRunSnapshot(
            CoreJson.text(root, "state"),
            CoreJson.text(root, "path"),
            CoreJson.text(root, "manifestPath"),
            CoreJson.text(root, "reportPath"),
            CoreJson.text(root, "approvalToken"),
            CoreJson.text(root, "sourceFingerprint"),
            (int) manifests,
            bool(root, "canImport"),
            bool(root, "imported"),
            (int) CoreJson.number(root, "importedIslands"),
            bool(root, "passed"),
            (int) CoreJson.number(root, "expected"),
            bool(root, "rolledBack"),
            (int) CoreJson.number(root, "removedIslands"),
            bool(root, "rollbackPlanAvailable"),
            bool(root, "rollbackPlanConsumed"),
            (int) CoreJson.number(root, "extractedBundles"),
            CoreJson.number(root, "extractedFiles"),
            CoreJson.number(root, "extractedBytes"),
            (int) CoreJson.number(root, "activationTested"),
            (int) CoreJson.number(root, "activationTestPassed"),
            (int) CoreJson.number(root, "members"),
            (int) CoreJson.number(root, "bannedVisitors"),
            (int) CoreJson.number(root, "homes"),
            (int) CoreJson.number(root, "warps"),
            (int) CoreJson.number(root, "flags"),
            (int) CoreJson.number(root, "permissions"),
            (int) CoreJson.number(root, "upgrades"),
            (int) CoreJson.number(root, "limits"),
            (int) CoreJson.number(root, "completedMissions"),
            (int) CoreJson.number(root, "blockValues"),
            (int) CoreJson.number(root, "blockCounts"),
            (int) CoreJson.number(root, "blockingIssues"),
            (int) CoreJson.number(root, "warningIssues"),
            issues(root)
        );
    }

    public static String toJson(MigrationRunSnapshot snapshot) {
        if (snapshot == null) {
            return "{}";
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("state", snapshot.state());
        root.put("path", snapshot.path());
        root.put("manifestPath", snapshot.manifestPath());
        root.put("reportPath", snapshot.reportPath());
        root.put("approvalToken", snapshot.approvalToken());
        root.put("sourceFingerprint", snapshot.sourceFingerprint());
        root.put("manifests", snapshot.manifests());
        root.put("canImport", snapshot.canImport());
        root.put("imported", snapshot.imported());
        root.put("importedIslands", snapshot.importedIslands());
        root.put("passed", snapshot.passed());
        root.put("expected", snapshot.expected());
        root.put("rolledBack", snapshot.rolledBack());
        root.put("removedIslands", snapshot.removedIslands());
        root.put("rollbackPlanAvailable", snapshot.rollbackPlanAvailable());
        root.put("rollbackPlanConsumed", snapshot.rollbackPlanConsumed());
        root.put("extractedBundles", snapshot.extractedBundles());
        root.put("extractedFiles", snapshot.extractedFiles());
        root.put("extractedBytes", snapshot.extractedBytes());
        root.put("activationTested", snapshot.activationTested());
        root.put("activationTestPassed", snapshot.activationTestPassed());
        root.put("members", snapshot.members());
        root.put("bannedVisitors", snapshot.bannedVisitors());
        root.put("homes", snapshot.homes());
        root.put("warps", snapshot.warps());
        root.put("flags", snapshot.flags());
        root.put("permissions", snapshot.permissions());
        root.put("upgrades", snapshot.upgrades());
        root.put("limits", snapshot.limits());
        root.put("completedMissions", snapshot.completedMissions());
        root.put("blockValues", snapshot.blockValues());
        root.put("blockCounts", snapshot.blockCounts());
        root.put("blockingIssues", snapshot.blockingIssues());
        root.put("warningIssues", snapshot.warningIssues());
        root.put("issues", (snapshot.issues() == null ? List.<MigrationIssueSnapshot>of() : snapshot.issues()).stream()
            .map(issue -> Map.of("code", issue.code(), "message", issue.message(), "blocking", issue.blocking()))
            .toList());
        return SimpleJson.stringify(root);
    }

    private static List<MigrationIssueSnapshot> issues(Map<?, ?> root) {
        return CoreJson.objects(root, "issues").stream()
            .map(issue -> new MigrationIssueSnapshot(CoreJson.text(issue, "code"), CoreJson.text(issue, "message"), bool(issue, "blocking")))
            .toList();
    }

    private static boolean bool(Map<?, ?> root, String key) {
        Object value = root == null ? null : root.get(key);
        return value instanceof Boolean bool ? bool : Boolean.parseBoolean(SimpleJson.text(value));
    }
}
