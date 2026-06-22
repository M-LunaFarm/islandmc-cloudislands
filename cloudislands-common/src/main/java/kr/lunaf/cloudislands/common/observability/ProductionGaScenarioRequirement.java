package kr.lunaf.cloudislands.common.observability;

import java.util.List;

public record ProductionGaScenarioRequirement(
    String key,
    String gate,
    List<String> requiredEvidence,
    List<String> failureInjections
) {
    public ProductionGaScenarioRequirement {
        key = normalize(key, "unknown-scenario");
        gate = normalize(gate, "unknown-gate");
        requiredEvidence = requiredEvidence == null ? List.of() : List.copyOf(requiredEvidence);
        failureInjections = failureInjections == null ? List.of() : List.copyOf(failureInjections);
    }

    public boolean evidenceCompleteWith(List<String> evidence) {
        if (evidence == null) {
            return false;
        }
        return evidence.containsAll(requiredEvidence);
    }

    public boolean failureInjectionsCompleteWith(List<String> injectedFailures) {
        if (injectedFailures == null) {
            return false;
        }
        return injectedFailures.containsAll(failureInjections);
    }

    public String summary() {
        return key + "@" + gate + "=" + String.join("+", requiredEvidence) + "|" + String.join("+", failureInjections);
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
