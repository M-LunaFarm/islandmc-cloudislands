package kr.lunaf.cloudislands.common.config;

import java.util.List;

public record ConfigMigrationReport(
    boolean migrated,
    List<String> generatedFiles,
    List<ConfigIssue> conflicts
) {
    public ConfigMigrationReport {
        generatedFiles = generatedFiles == null ? List.of() : List.copyOf(generatedFiles);
        conflicts = conflicts == null ? List.of() : List.copyOf(conflicts);
    }

    public boolean canApply() {
        return conflicts.isEmpty();
    }

    public String summary() {
        if (!canApply()) {
            return "blocked:" + conflicts.stream()
                .map(issue -> issue.code() + ":" + issue.path())
                .sorted()
                .reduce((left, right) -> left + "," + right)
                .orElse("conflict");
        }
        return "migrated:" + String.join(",", generatedFiles);
    }
}
