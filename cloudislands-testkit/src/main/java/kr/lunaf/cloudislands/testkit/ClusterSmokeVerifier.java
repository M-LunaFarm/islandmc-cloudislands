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
        ClusterSmokeEvidence safeEvidence = evidence == null ? new ClusterSmokeEvidence(null, null, null) : evidence;
        return new ClusterSmokeReport(
            CERTIFICATION_LEVEL,
            missingComponents(safeEvidence),
            ProductionGaDrillMatrix.incompleteGates(safeEvidence.evidenceByGate()),
            missingFailureInjections(safeEvidence),
            missingEvidenceByGate(safeEvidence),
            missingScenarioEvidence(safeEvidence),
            missingScenarioFailureInjections(safeEvidence)
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
            .components(ClusterSmokeEvidence.REQUIRED_COMPONENTS);
        for (ProductionGaDrill drill : ProductionGaDrillMatrix.drills()) {
            builder.evidence(drill.gate(), drill.requiredEvidence());
            for (String failureInjection : drill.failureInjections()) {
                builder.failureInjection(failureInjection);
            }
        }
        for (ProductionGaScenarioRequirement scenario : ProductionGaDrillMatrix.scenarioRequirements()) {
            builder.evidence(scenario.gate(), scenario.requiredEvidence());
            for (String failureInjection : scenario.failureInjections()) {
                builder.failureInjection(failureInjection);
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

    public static Map<String, List<String>> missingEvidenceByGate(ClusterSmokeEvidence evidence) {
        ClusterSmokeEvidence safeEvidence = evidence == null ? new ClusterSmokeEvidence(null, null, null) : evidence;
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
        ClusterSmokeEvidence safeEvidence = evidence == null ? new ClusterSmokeEvidence(null, null, null) : evidence;
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
        ClusterSmokeEvidence safeEvidence = evidence == null ? new ClusterSmokeEvidence(null, null, null) : evidence;
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
}
