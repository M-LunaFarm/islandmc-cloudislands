package kr.lunaf.cloudislands.common.observability;

import java.util.List;

public record ProductionGaDrill(
    String gate,
    String scenario,
    List<String> requiredEvidence,
    List<String> failureInjections,
    String acceptance
) {
    public ProductionGaDrill {
        gate = normalize(gate, "unknown-gate");
        scenario = normalize(scenario, "unspecified");
        requiredEvidence = requiredEvidence == null ? List.of() : List.copyOf(requiredEvidence);
        failureInjections = failureInjections == null ? List.of() : List.copyOf(failureInjections);
        acceptance = normalize(acceptance, "not-certified");
    }

    public boolean completeWith(List<String> evidence) {
        if (evidence == null) {
            return false;
        }
        return evidence.containsAll(requiredEvidence);
    }

    public String evidenceSummary() {
        return gate + "=" + String.join("+", requiredEvidence);
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
