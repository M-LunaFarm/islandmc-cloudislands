package kr.lunaf.cloudislands.testkit;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public record ClusterSmokeReport(
    String certificationLevel,
    List<String> missingComponents,
    List<String> incompleteGates,
    List<String> missingFailureInjections,
    List<String> missingFailureInjectionEvidence,
    Map<String, List<String>> missingEvidenceByGate,
    Map<String, List<String>> missingScenarioEvidence,
    Map<String, List<String>> missingScenarioFailureInjections,
    List<String> missingEvidenceLinks
) {
    public ClusterSmokeReport {
        certificationLevel = certificationLevel == null || certificationLevel.isBlank() ? ClusterSmokeVerifier.CERTIFICATION_LEVEL : certificationLevel;
        missingComponents = missingComponents == null ? List.of() : List.copyOf(missingComponents);
        incompleteGates = incompleteGates == null ? List.of() : List.copyOf(incompleteGates);
        missingFailureInjections = missingFailureInjections == null ? List.of() : List.copyOf(missingFailureInjections);
        missingFailureInjectionEvidence = missingFailureInjectionEvidence == null ? List.of() : List.copyOf(missingFailureInjectionEvidence);
        missingEvidenceByGate = missingEvidenceByGate == null ? Map.of() : Map.copyOf(missingEvidenceByGate);
        missingScenarioEvidence = missingScenarioEvidence == null ? Map.of() : Map.copyOf(missingScenarioEvidence);
        missingScenarioFailureInjections = missingScenarioFailureInjections == null ? Map.of() : Map.copyOf(missingScenarioFailureInjections);
        missingEvidenceLinks = missingEvidenceLinks == null ? List.of() : List.copyOf(missingEvidenceLinks);
    }

    public boolean certified() {
        return missingComponents.isEmpty()
            && incompleteGates.isEmpty()
            && missingFailureInjections.isEmpty()
            && missingFailureInjectionEvidence.isEmpty()
            && missingEvidenceByGate.isEmpty()
            && missingScenarioEvidence.isEmpty()
            && missingScenarioFailureInjections.isEmpty()
            && missingEvidenceLinks.isEmpty();
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
        if (!missingFailureInjectionEvidence.isEmpty()) {
            failures.add("missing-failure-injection-evidence:" + String.join(",", missingFailureInjectionEvidence));
        }
        if (!missingEvidenceByGate.isEmpty()) {
            failures.add("missing-evidence:" + missingEvidenceSummary());
        }
        if (!missingScenarioEvidence.isEmpty()) {
            failures.add("missing-scenario-evidence:" + missingScenarioEvidenceSummary());
        }
        if (!missingScenarioFailureInjections.isEmpty()) {
            failures.add("missing-scenario-failure-injections:" + missingScenarioFailureInjectionSummary());
        }
        if (!missingEvidenceLinks.isEmpty()) {
            failures.add("missing-evidence-links:" + String.join("+", missingEvidenceLinks));
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

    public String missingScenarioEvidenceSummary() {
        return summarize(missingScenarioEvidence);
    }

    public String missingScenarioFailureInjectionSummary() {
        return summarize(missingScenarioFailureInjections);
    }

    private static String summarize(Map<String, List<String>> valuesByKey) {
        List<String> summaries = new ArrayList<>();
        valuesByKey.entrySet().stream()
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
