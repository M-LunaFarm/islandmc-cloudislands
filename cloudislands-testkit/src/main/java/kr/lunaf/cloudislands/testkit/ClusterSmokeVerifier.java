package kr.lunaf.cloudislands.testkit;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kr.lunaf.cloudislands.common.observability.ProductionGaDrill;
import kr.lunaf.cloudislands.common.observability.ProductionGaDrillMatrix;

public final class ClusterSmokeVerifier {
    public static final String CERTIFICATION_LEVEL = "cloudislands-production-ga-cluster-smoke-1";
    public static final String MATRIX_POLICY = "certifies-two-core-velocity-lobby-two-paper-storage-route-fencing-event-chaos-and-restore-evidence";

    private ClusterSmokeVerifier() {
    }

    public static ClusterSmokeReport verify(ClusterSmokeEvidence evidence) {
        ClusterSmokeEvidence safeEvidence = evidence == null ? new ClusterSmokeEvidence(null, null, null) : evidence;
        return new ClusterSmokeReport(
            CERTIFICATION_LEVEL,
            missingComponents(safeEvidence),
            ProductionGaDrillMatrix.incompleteGates(safeEvidence.evidenceByGate()),
            missingFailureInjections(safeEvidence),
            missingEvidenceByGate(safeEvidence)
        );
    }

    public static List<String> requiredFailureInjections() {
        List<String> required = new ArrayList<>();
        for (ProductionGaDrill drill : ProductionGaDrillMatrix.drills()) {
            required.addAll(drill.failureInjections());
        }
        return List.copyOf(required);
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
}
