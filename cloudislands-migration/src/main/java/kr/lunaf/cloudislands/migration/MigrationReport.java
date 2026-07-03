package kr.lunaf.cloudislands.migration;

import java.util.List;

public record MigrationReport(
    int manifests,
    int members,
    int memberRoles,
    int bannedVisitors,
    int homes,
    int warps,
    int islandLocations,
    int sourceWorlds,
    int islandSizes,
    int levels,
    int worthValues,
    int biomes,
    int bankBalances,
    int flags,
    int permissions,
    int upgrades,
    int limits,
    int completedMissions,
    int blockValues,
    int blockCounts,
    int warehouseItems,
    int blockingIssues,
    int warningIssues,
    int importableIslandCount,
    int ownerMissingCount,
    int worldPathMissingCount,
    int homeMissingCount,
    int warpMissingCount,
    int homeConversionFailureCount,
    int warpConversionFailureCount,
    int permissionConversionFailureCount,
    int unknownFlagCount,
    int blockValueConversionFailureCount,
    int bankEconomyConversionFailureCount,
    int worldBundleChecksumFailureCount,
    int cloudIslandsPostImportDifferenceCount,
    int unsupportedFieldCount,
    boolean rollbackPossible,
    List<MigrationIssue> issues
) {
    public int totalIslands() {
        return manifests;
    }

    public boolean canImport() {
        return manifests > 0 && blockingIssues == 0;
    }

    public boolean hasIssues() {
        return blockingIssues > 0 || warningIssues > 0;
    }

    public boolean manifestGenerated() {
        return manifests > 0;
    }

    public boolean hasConflicts() {
        return issues.stream().anyMatch(MigrationReport::isConflictIssue);
    }

    public int conflictIssues() {
        return (int) issues.stream().filter(MigrationReport::isConflictIssue).count();
    }

    public String manifestStatus() {
        return manifestGenerated() ? "GENERATED" : "MISSING";
    }

    public String conflictStatus() {
        if (conflictIssues() == 0) {
            return "NONE";
        }
        return issues.stream().anyMatch(issue -> isConflictIssue(issue) && issue.blocking()) ? "BLOCKING" : "WARNING";
    }

    private static boolean isConflictIssue(MigrationIssue issue) {
        if (issue == null || issue.code() == null) {
            return false;
        }
        String code = issue.code();
        return code.contains("DUPLICATE")
            || code.contains("CONFLICT")
            || code.contains("COLLISION")
            || code.contains("EXISTS");
    }

    public int failedIslandCount() {
        return Math.min(totalIslands(), blockingIssues);
    }

    public int skippedIslandCount() {
        return Math.max(0, totalIslands() - importableIslandCount() - failedIslandCount());
    }

    public String toJson() {
        StringBuilder builder = new StringBuilder();
        builder.append('{');
        appendNumber(builder, "totalIslands", totalIslands()).append(',');
        appendNumber(builder, "importableIslands", importableIslandCount()).append(',');
        appendNumber(builder, "failedIslands", failedIslandCount()).append(',');
        appendNumber(builder, "skippedIslands", skippedIslandCount()).append(',');
        appendNumber(builder, "blockingIssues", blockingIssues()).append(',');
        appendNumber(builder, "warningIssues", warningIssues()).append(',');
        appendNumber(builder, "ownerMissing", ownerMissingCount()).append(',');
        appendNumber(builder, "worldMissing", worldPathMissingCount()).append(',');
        appendNumber(builder, "duplicateOrConflictIssues", conflictIssues()).append(',');
        appendNumber(builder, "unsupportedPermissions", permissionConversionFailureCount()).append(',');
        appendNumber(builder, "unsupportedMissions", completedMissions()).append(',');
        appendNumber(builder, "unsupportedUpgrades", upgrades()).append(',');
        appendNumber(builder, "bankBalanceMappings", bankBalances()).append(',');
        appendNumber(builder, "bankEconomyConversionFailures", bankEconomyConversionFailureCount()).append(',');
        appendNumber(builder, "blockWorthMappings", blockValues()).append(',');
        appendNumber(builder, "blockValueConversionFailures", blockValueConversionFailureCount()).append(',');
        appendNumber(builder, "warpMappings", warps()).append(',');
        appendNumber(builder, "warpFailures", warpConversionFailureCount()).append(',');
        appendNumber(builder, "roleMappings", memberRoles()).append(',');
        appendNumber(builder, "unsupportedFields", unsupportedFieldCount()).append(',');
        builder.append("\"rollbackPossible\":").append(rollbackPossible()).append(',');
        appendString(builder, "manifestStatus", manifestStatus()).append(',');
        appendString(builder, "conflictStatus", conflictStatus()).append(',');
        builder.append("\"issues\":[");
        for (int index = 0; index < issues().size(); index++) {
            if (index > 0) {
                builder.append(',');
            }
            MigrationIssue issue = issues().get(index);
            builder.append('{');
            appendString(builder, "code", issue.code()).append(',');
            appendString(builder, "message", issue.message()).append(',');
            builder.append("\"blocking\":").append(issue.blocking());
            builder.append('}');
        }
        builder.append("]}");
        return builder.toString();
    }

    public String toMarkdown() {
        StringBuilder builder = new StringBuilder();
        builder.append("# SuperiorSkyblock2 migration report\n\n");
        builder.append("| Metric | Value |\n");
        builder.append("| --- | ---: |\n");
        markdownMetric(builder, "Total islands", totalIslands());
        markdownMetric(builder, "Importable islands", importableIslandCount());
        markdownMetric(builder, "Failed islands", failedIslandCount());
        markdownMetric(builder, "Skipped islands", skippedIslandCount());
        markdownMetric(builder, "Blocking issues", blockingIssues());
        markdownMetric(builder, "Warning issues", warningIssues());
        markdownMetric(builder, "Owner missing", ownerMissingCount());
        markdownMetric(builder, "World missing", worldPathMissingCount());
        markdownMetric(builder, "Unsupported permissions", permissionConversionFailureCount());
        markdownMetric(builder, "Unsupported missions", completedMissions());
        markdownMetric(builder, "Unsupported upgrades", upgrades());
        markdownMetric(builder, "Bank balance mappings", bankBalances());
        markdownMetric(builder, "Block worth mappings", blockValues());
        markdownMetric(builder, "Warp mappings", warps());
        markdownMetric(builder, "Role mappings", memberRoles());
        markdownMetric(builder, "Unsupported fields", unsupportedFieldCount());
        builder.append("| Rollback possible | ").append(rollbackPossible()).append(" |\n");
        builder.append("| Manifest status | ").append(manifestStatus()).append(" |\n");
        builder.append("| Conflict status | ").append(conflictStatus()).append(" |\n");
        if (!issues().isEmpty()) {
            builder.append("\n## Issues\n\n");
            builder.append("| Code | Blocking | Message |\n");
            builder.append("| --- | --- | --- |\n");
            for (MigrationIssue issue : issues()) {
                builder.append("| ")
                    .append(markdownCell(issue.code()))
                    .append(" | ")
                    .append(issue.blocking())
                    .append(" | ")
                    .append(markdownCell(issue.message()))
                    .append(" |\n");
            }
        }
        return builder.toString();
    }

    private static StringBuilder appendNumber(StringBuilder builder, String key, int value) {
        return builder.append('"').append(jsonEscape(key)).append("\":").append(value);
    }

    private static StringBuilder appendString(StringBuilder builder, String key, String value) {
        return builder.append('"').append(jsonEscape(key)).append("\":\"").append(jsonEscape(value)).append('"');
    }

    private static void markdownMetric(StringBuilder builder, String label, int value) {
        builder.append("| ").append(label).append(" | ").append(value).append(" |\n");
    }

    private static String markdownCell(String value) {
        return value == null ? "" : value.replace("|", "\\|").replace('\n', ' ');
    }

    private static String jsonEscape(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> builder.append("\\\"");
                case '\\' -> builder.append("\\\\");
                case '\b' -> builder.append("\\b");
                case '\f' -> builder.append("\\f");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> {
                    if (character < 0x20) {
                        builder.append(String.format("\\u%04x", (int) character));
                    } else {
                        builder.append(character);
                    }
                }
            }
        }
        return builder.toString();
    }
}
