package kr.lunaf.cloudislands.testkit;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public record ClusterSmokeReport(
    String certificationLevel,
    List<String> missingComponents,
    List<String> incompleteGates,
    List<String> missingFailureInjections,
    Map<String, List<String>> missingEvidenceByGate
) {
    public ClusterSmokeReport {
        certificationLevel = certificationLevel == null || certificationLevel.isBlank() ? ClusterSmokeVerifier.CERTIFICATION_LEVEL : certificationLevel;
        missingComponents = missingComponents == null ? List.of() : List.copyOf(missingComponents);
        incompleteGates = incompleteGates == null ? List.of() : List.copyOf(incompleteGates);
        missingFailureInjections = missingFailureInjections == null ? List.of() : List.copyOf(missingFailureInjections);
        missingEvidenceByGate = missingEvidenceByGate == null ? Map.of() : Map.copyOf(missingEvidenceByGate);
    }

    public boolean certified() {
        return missingComponents.isEmpty() && incompleteGates.isEmpty() && missingFailureInjections.isEmpty() && missingEvidenceByGate.isEmpty();
    }

    public List<String> failures() {
        List<String> failures = new ArrayList<>();
        if (!missingComponents.isEmpty()) {
            failures.add("missing-components:" + String.join(",", missingComponents));
        }
        if (!incompleteGates.isEmpty()) {
            failures.add("incomplete-gates:" + String.join(",", incompleteGates));
        }
        if (!missingFailureInjections.isEmpty()) {
            failures.add("missing-failure-injections:" + String.join(",", missingFailureInjections));
        }
        if (!missingEvidenceByGate.isEmpty()) {
            failures.add("missing-evidence:" + missingEvidenceSummary());
        }
        return List.copyOf(failures);
    }

    public String missingEvidenceSummary() {
        List<String> summaries = new ArrayList<>();
        missingEvidenceByGate.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> summaries.add(entry.getKey() + "=" + String.join("+", entry.getValue())));
        return String.join(",", summaries);
    }

    public void requireCertified() {
        if (!certified()) {
            throw new IllegalStateException("CloudIslands cluster smoke certification failed: " + String.join("; ", failures()));
        }
    }
}
