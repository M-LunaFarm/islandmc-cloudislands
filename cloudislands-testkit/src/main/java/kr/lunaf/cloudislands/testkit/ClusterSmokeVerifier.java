package kr.lunaf.cloudislands.testkit;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import kr.lunaf.cloudislands.common.observability.ProductionGaDrill;
import kr.lunaf.cloudislands.common.observability.ProductionGaDrillMatrix;
import kr.lunaf.cloudislands.common.observability.ProductionGaScenarioRequirement;

public final class ClusterSmokeVerifier {
    public static final String CERTIFICATION_LEVEL = "cloudislands-production-ga-cluster-smoke-1";
    public static final String MATRIX_POLICY = "certifies-two-core-velocity-lobby-two-paper-storage-player-protocol-client-route-fencing-event-chaos-and-restore-evidence";

    private ClusterSmokeVerifier() {
    }

    public static ClusterSmokeReport verify(ClusterSmokeEvidence evidence) {
        ClusterSmokeEvidence safeEvidence = evidence == null ? new ClusterSmokeEvidence(null, null, null, null, null, null) : evidence;
        return new ClusterSmokeReport(
            CERTIFICATION_LEVEL,
            missingComponents(safeEvidence),
            ProductionGaDrillMatrix.incompleteGates(safeEvidence.evidenceByGate()),
            missingFailureInjections(safeEvidence),
            missingFailureInjectionEvidence(safeEvidence),
            missingEvidenceByGate(safeEvidence),
            missingScenarioEvidence(safeEvidence),
            missingScenarioFailureInjections(safeEvidence),
            missingEvidenceLinks(safeEvidence)
        );
    }

    public static List<String> requiredFailureInjections() {
        Set<String> required = new LinkedHashSet<>();
        for (ProductionGaDrill drill : ProductionGaDrillMatrix.drills()) {
            required.addAll(drill.failureInjections());
        }
        for (ProductionGaScenarioRequirement scenario : ProductionGaDrillMatrix.scenarioRequirements()) {
            required.addAll(scenario.failureInjections());
        }
        return List.copyOf(required);
    }

    public static List<String> requiredProductionGaScenarios() {
        return ProductionGaDrillMatrix.scenarioRequirements().stream()
            .map(ProductionGaScenarioRequirement::key)
            .toList();
    }

    public static ClusterSmokeEvidence completeEvidenceFixture() {
        ClusterSmokeEvidence.Builder builder = ClusterSmokeEvidence.builder()
            .components(ClusterSmokeEvidence.REQUIRED_COMPONENTS)
            .passedAssertion("fixture-production-ga-all-required-evidence-passed")
            .artifact("fixture/cluster-smoke.log", "0000000000000000000000000000000000000000000000000000000000000000", 1, 1);
        for (ProductionGaDrill drill : ProductionGaDrillMatrix.drills()) {
            builder.evidence(drill.gate(), drill.requiredEvidence());
            for (String failureInjection : drill.failureInjections()) {
                builder
                    .failureInjection(failureInjection)
                    .failureInjectionEvidence(failureInjection, "fixture/cluster-smoke.log:1-1");
            }
        }
        for (ProductionGaScenarioRequirement scenario : ProductionGaDrillMatrix.scenarioRequirements()) {
            builder.evidence(scenario.gate(), scenario.requiredEvidence());
            for (String failureInjection : scenario.failureInjections()) {
                builder
                    .failureInjection(failureInjection)
                    .failureInjectionEvidence(failureInjection, "fixture/cluster-smoke.log:1-1");
            }
        }
        return builder.build();
    }

    private static List<String> missingComponents(ClusterSmokeEvidence evidence) {
        List<String> missing = new ArrayList<>();
        for (String component : ClusterSmokeEvidence.REQUIRED_COMPONENTS) {
            if (!evidence.hasComponent(component)) {
                missing.add(component);
            }
        }
        return List.copyOf(missing);
    }

    private static List<String> missingFailureInjections(ClusterSmokeEvidence evidence) {
        List<String> missing = new ArrayList<>();
        for (String failureInjection : requiredFailureInjections()) {
            if (!evidence.injected(failureInjection)) {
                missing.add(failureInjection);
            }
        }
        return List.copyOf(missing);
    }

    private static List<String> missingFailureInjectionEvidence(ClusterSmokeEvidence evidence) {
        List<String> missing = new ArrayList<>();
        for (String failureInjection : requiredFailureInjections()) {
            if (evidence.injected(failureInjection) && !evidence.hasFailureInjectionEvidence(failureInjection)) {
                missing.add(failureInjection);
            }
        }
        return List.copyOf(missing);
    }

    public static Map<String, List<String>> missingEvidenceByGate(ClusterSmokeEvidence evidence) {
        ClusterSmokeEvidence safeEvidence = evidence == null ? new ClusterSmokeEvidence(null, null, null, null, null, null) : evidence;
        LinkedHashMap<String, List<String>> missingByGate = new LinkedHashMap<>();
        for (ProductionGaDrill drill : ProductionGaDrillMatrix.drills()) {
            List<String> observed = safeEvidence.evidenceByGate().getOrDefault(drill.gate(), List.of());
            List<String> missing = drill.requiredEvidence().stream()
                .filter(required -> !observed.contains(required))
                .toList();
            if (!missing.isEmpty()) {
                missingByGate.put(drill.gate(), missing);
            }
        }
        return Map.copyOf(missingByGate);
    }

    public static Map<String, List<String>> missingScenarioEvidence(ClusterSmokeEvidence evidence) {
        ClusterSmokeEvidence safeEvidence = evidence == null ? new ClusterSmokeEvidence(null, null, null, null, null, null) : evidence;
        LinkedHashMap<String, List<String>> missingByScenario = new LinkedHashMap<>();
        for (ProductionGaScenarioRequirement scenario : ProductionGaDrillMatrix.scenarioRequirements()) {
            List<String> observed = safeEvidence.evidenceByGate().getOrDefault(scenario.gate(), List.of());
            List<String> missing = scenario.requiredEvidence().stream()
                .filter(required -> !observed.contains(required))
                .toList();
            if (!missing.isEmpty()) {
                missingByScenario.put(scenario.key(), missing);
            }
        }
        return Map.copyOf(missingByScenario);
    }

    public static Map<String, List<String>> missingScenarioFailureInjections(ClusterSmokeEvidence evidence) {
        ClusterSmokeEvidence safeEvidence = evidence == null ? new ClusterSmokeEvidence(null, null, null, null, null, null) : evidence;
        LinkedHashMap<String, List<String>> missingByScenario = new LinkedHashMap<>();
        for (ProductionGaScenarioRequirement scenario : ProductionGaDrillMatrix.scenarioRequirements()) {
            List<String> missing = scenario.failureInjections().stream()
                .filter(required -> !safeEvidence.injected(required))
                .toList();
            if (!missing.isEmpty()) {
                missingByScenario.put(scenario.key(), missing);
            }
        }
        return Map.copyOf(missingByScenario);
    }

    private static List<String> missingEvidenceLinks(ClusterSmokeEvidence evidence) {
        if (evidence.evidenceByGate().isEmpty()) {
            return List.of();
        }
        List<String> missing = new ArrayList<>();
        if (!evidence.hasPassedAssertions()) {
            missing.add("passed-assertions");
        }
        if (!evidence.hasLinkedArtifact()) {
            missing.add("artifact-sha256-line-range");
        }
        return List.copyOf(missing);
    }
}
