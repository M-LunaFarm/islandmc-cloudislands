package kr.lunaf.cloudislands.common.config;

import java.util.List;

public record ConfigValidationResult(List<ConfigIssue> issues) {
    public ConfigValidationResult {
        issues = issues == null ? List.of() : List.copyOf(issues);
    }

    public boolean valid() {
        return issues.isEmpty();
    }

    public boolean hasIssue(String code) {
        return issues.stream().anyMatch(issue -> issue.hasCode(code));
    }

    public String summary() {
        if (issues.isEmpty()) {
            return "valid";
        }
        return issues.stream()
            .map(issue -> issue.code() + ":" + issue.path())
            .sorted()
            .reduce((left, right) -> left + "," + right)
            .orElse("invalid");
    }
}
