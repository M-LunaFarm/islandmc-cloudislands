package kr.lunaf.cloudislands.velocity.message;

import static kr.lunaf.cloudislands.velocity.message.VelocityJsonFields.arrayValue;
import static kr.lunaf.cloudislands.velocity.message.VelocityJsonFields.boolValue;
import static kr.lunaf.cloudislands.velocity.message.VelocityJsonFields.jsonValue;
import static kr.lunaf.cloudislands.velocity.message.VelocityJsonFields.longValue;
import static kr.lunaf.cloudislands.velocity.message.VelocityJsonFields.matchingObjectEnd;

public final class VelocityMigrationMessageFormatter {
    public String format(String body) {
        if (body == null || body.isBlank()) {
            return "Migration: no response";
        }
        String code = topLevelJsonValue(body, "code");
        if (!code.isBlank()) {
            return failureMessage(body, code);
        }
        String state = jsonValue(body, "state");
        String path = jsonValue(body, "path");
        String manifestPath = jsonValue(body, "manifestPath");
        String reportPath = jsonValue(body, "reportPath");
        String approvalToken = jsonValue(body, "approvalToken");
        String issues = arrayValue(body, "issues");
        long manifests = longValue(body, "manifests");
        if (manifests == 0L && body.contains("\"scanManifests\"")) {
            manifests = longValue(body, "scanManifests");
        }
        long importedIslands = longValue(body, "importedIslands");
        long removedIslands = longValue(body, "removedIslands");
        StringBuilder builder = new StringBuilder("Migration: state=")
            .append(state.isBlank() ? "UNKNOWN" : state)
            .append(" manifests=")
            .append(manifests);
        if (!path.isBlank()) {
            builder.append(" path=").append(path);
        }
        if (!manifestPath.isBlank()) {
            builder.append(" manifest=").append(manifestPath);
        }
        if (!reportPath.isBlank()) {
            builder.append(" report=").append(reportPath);
        }
        if (!approvalToken.isBlank()) {
            builder.append(" approval=").append(approvalToken);
        }
        appendConfigFlags(body, builder);
        appendPlanningFields(body, builder);
        appendExecutionFields(body, builder, importedIslands, removedIslands);
        appendExtractedFields(body, builder);
        appendImportedEntityFields(body, builder);
        appendIssueCounts(body, builder);
        builder.append(issuesSuffix(issues));
        return builder.toString();
    }

    private String failureMessage(String body, String code) {
        if (code.equals("MIGRATION_DISABLED")) {
            StringBuilder disabled = new StringBuilder("SuperiorSkyblock2 migration is disabled by config.");
            appendConfigFlags(body, disabled);
            return disabled.toString();
        }
        String message = jsonValue(body, "message");
        return "Migration: failed code=" + code + (message.isBlank() ? "" : " message=" + message);
    }

    private String topLevelJsonValue(String body, String key) {
        String needle = "\"" + key + "\":\"";
        int index = body.indexOf(needle);
        while (index >= 0) {
            if (topLevelKey(body, index)) {
                int start = index + needle.length();
                int end = body.indexOf('"', start);
                return end < 0 ? "" : body.substring(start, end);
            }
            index = body.indexOf(needle, index + needle.length());
        }
        return "";
    }

    private boolean topLevelKey(String body, int keyOffset) {
        int objectDepth = 0;
        int arrayDepth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < keyOffset; i++) {
            char current = body.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (current == '\\' && inString) {
                escaped = true;
                continue;
            }
            if (current == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (current == '{') {
                objectDepth++;
            } else if (current == '}') {
                objectDepth--;
            } else if (current == '[') {
                arrayDepth++;
            } else if (current == ']') {
                arrayDepth--;
            }
        }
        return objectDepth == 1 && arrayDepth == 0;
    }

    private void appendConfigFlags(String body, StringBuilder builder) {
        if (body.contains("\"sourcePlugin\"")) {
            builder.append(" source=").append(jsonValue(body, "sourcePlugin"));
        }
        if (body.contains("\"migrationInputOnly\"")) {
            builder.append(" inputOnly=").append(boolValue(body, "migrationInputOnly"));
        }
        if (body.contains("\"runtimeDependency\"")) {
            builder.append(" runtimeDependency=").append(boolValue(body, "runtimeDependency"));
        }
        String targetRuntime = jsonValue(body, "targetRuntime");
        if (!targetRuntime.isBlank()) {
            builder.append(" targetRuntime=").append(targetRuntime);
        }
    }

    private void appendPlanningFields(String body, StringBuilder builder) {
        if (body.contains("\"canImport\"")) {
            builder.append(" canImport=").append(boolValue(body, "canImport"));
        }
        if (body.contains("\"planManifests\"")) {
            builder.append(" planManifests=").append(longValue(body, "planManifests"));
        }
        if (body.contains("\"rollbackPlanAvailable\"")) {
            builder.append(" rollbackPlan=").append(boolValue(body, "rollbackPlanAvailable"));
        }
        if (body.contains("\"approvalRequired\"")) {
            builder.append(" approvalRequired=").append(boolValue(body, "approvalRequired"));
        }
        if (body.contains("\"manifestStatus\"")) {
            builder.append(" manifestStatus=").append(jsonValue(body, "manifestStatus"))
                .append(" conflictStatus=").append(jsonValue(body, "conflictStatus"))
                .append(" conflicts=").append(longValue(body, "conflictIssues"));
        }
    }

    private void appendExecutionFields(String body, StringBuilder builder, long importedIslands, long removedIslands) {
        if (body.contains("\"imported\"")) {
            builder.append(" imported=").append(boolValue(body, "imported"))
                .append(" islands=")
                .append(importedIslands);
        }
        if (body.contains("\"passed\"")) {
            builder.append(" passed=").append(boolValue(body, "passed"))
                .append(" expected=")
                .append(longValue(body, "expected"));
        }
        if (body.contains("\"activationTested\"")) {
            builder.append(" activationTested=").append(longValue(body, "activationTested"))
                .append(" activationPassed=")
                .append(longValue(body, "activationTestPassed"));
        }
        if (body.contains("\"rolledBack\"")) {
            builder.append(" rolledBack=").append(boolValue(body, "rolledBack"))
                .append(" removed=")
                .append(removedIslands);
        }
    }

    private void appendExtractedFields(String body, StringBuilder builder) {
        if (body.contains("\"extractedBundles\"")) {
            builder.append(" extracted=")
                .append(longValue(body, "extractedBundles"))
                .append(" files=")
                .append(longValue(body, "extractedFiles"))
                .append(" bytes=")
                .append(longValue(body, "extractedBytes"));
        }
    }

    private void appendImportedEntityFields(String body, StringBuilder builder) {
        if (body.contains("\"members\"")) {
            builder.append(" members=").append(longValue(body, "members"))
                .append(" roles=").append(longValue(body, "memberRoles"))
                .append(" homes=").append(longValue(body, "homes"))
                .append(" warps=").append(longValue(body, "warps"))
                .append(" locations=").append(longValue(body, "islandLocations"))
                .append(" sourceWorlds=").append(longValue(body, "sourceWorlds"))
                .append(" sizes=").append(longValue(body, "islandSizes"))
                .append(" levels=").append(longValue(body, "levels"))
                .append(" worth=").append(longValue(body, "worthValues"))
                .append(" biomes=").append(longValue(body, "biomes"))
                .append(" bank=").append(longValue(body, "bankBalances"))
                .append(" perms=").append(longValue(body, "permissions"));
        }
    }

    private void appendIssueCounts(String body, StringBuilder builder) {
        if (body.contains("\"blockingIssues\"")) {
            builder.append(" blocking=").append(longValue(body, "blockingIssues"))
                .append(" warnings=").append(longValue(body, "warningIssues"));
        }
    }

    private String issuesSuffix(String issues) {
        if (issues.isBlank()) {
            return " issues=0";
        }
        int total = 0;
        int blocking = 0;
        java.util.List<String> samples = new java.util.ArrayList<>();
        int index = 0;
        while (index < issues.length()) {
            int objectStart = issues.indexOf('{', index);
            if (objectStart < 0) {
                break;
            }
            int objectEnd = matchingObjectEnd(issues, objectStart);
            if (objectEnd < 0) {
                break;
            }
            String object = issues.substring(objectStart, objectEnd + 1);
            total++;
            boolean blocked = boolValue(object, "blocking");
            if (blocked) {
                blocking++;
            }
            if (samples.size() < 5) {
                String issueCode = jsonValue(object, "code");
                samples.add((issueCode.isBlank() ? "UNKNOWN" : issueCode) + (blocked ? "(blocking)" : ""));
            }
            index = objectEnd + 1;
        }
        return " issues=" + total
            + " blocking=" + blocking
            + (samples.isEmpty() ? "" : " [" + String.join(", ", samples) + "]");
    }
}
