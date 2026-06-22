package kr.lunaf.cloudislands.testkit;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record ClusterSmokeEvidence(
    Set<String> components,
    Map<String, List<String>> evidenceByGate,
    Set<String> failureInjections
) {
    public static final Set<String> REQUIRED_COMPONENTS = Set.of(
        "core-1",
        "core-2",
        "velocity",
        "lobby-paper",
        "island-paper-1",
        "island-paper-2",
        "postgres",
        "redis",
        "object-storage",
        "player-protocol-client"
    );
    private static final Map<String, Set<String>> COMPONENT_ALIASES = Map.of(
        "player-protocol-client", Set.of("protocol-client", "virtual-player", "virtual-player-client", "simulated-player", "bot-client")
    );

    public ClusterSmokeEvidence {
        components = copySet(components);
        evidenceByGate = copyEvidence(evidenceByGate);
        failureInjections = copySet(failureInjections);
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean hasComponent(String component) {
        if (component == null || component.isBlank()) {
            return false;
        }
        String normalized = component.trim();
        if (components.contains(normalized)) {
            return true;
        }
        return COMPONENT_ALIASES.getOrDefault(normalized, Set.of()).stream().anyMatch(components::contains);
    }

    public boolean hasEvidence(String gate, String evidence) {
        if (gate == null || evidence == null) {
            return false;
        }
        return evidenceByGate.getOrDefault(gate, List.of()).contains(evidence);
    }

    public boolean injected(String failureInjection) {
        return failureInjection != null && failureInjections.contains(failureInjection);
    }

    private static Set<String> copySet(Set<String> values) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        LinkedHashSet<String> copy = new LinkedHashSet<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                copy.add(value.trim());
            }
        }
        return Set.copyOf(copy);
    }

    private static Map<String, List<String>> copyEvidence(Map<String, List<String>> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, List<String>> copy = new LinkedHashMap<>();
        source.forEach((gate, evidence) -> {
            if (gate == null || gate.isBlank()) {
                return;
            }
            List<String> values = new ArrayList<>();
            if (evidence != null) {
                for (String value : evidence) {
                    if (value != null && !value.isBlank()) {
                        values.add(value.trim());
                    }
                }
            }
            copy.put(gate.trim(), List.copyOf(values));
        });
        return Map.copyOf(copy);
    }

    public static final class Builder {
        private final Set<String> components = new LinkedHashSet<>();
        private final Map<String, List<String>> evidenceByGate = new LinkedHashMap<>();
        private final Set<String> failureInjections = new LinkedHashSet<>();

        private Builder() {
        }

        public Builder component(String component) {
            if (component != null && !component.isBlank()) {
                components.add(component.trim());
            }
            return this;
        }

        public Builder components(Set<String> components) {
            if (components != null) {
                components.forEach(this::component);
            }
            return this;
        }

        public Builder evidence(String gate, String evidence) {
            if (gate != null && !gate.isBlank() && evidence != null && !evidence.isBlank()) {
                evidenceByGate.computeIfAbsent(gate.trim(), ignored -> new ArrayList<>()).add(evidence.trim());
            }
            return this;
        }

        public Builder evidence(String gate, List<String> evidence) {
            if (evidence != null) {
                evidence.forEach(value -> evidence(gate, value));
            }
            return this;
        }

        public Builder failureInjection(String failureInjection) {
            if (failureInjection != null && !failureInjection.isBlank()) {
                failureInjections.add(failureInjection.trim());
            }
            return this;
        }

        public Builder failureInjections(Set<String> failureInjections) {
            if (failureInjections != null) {
                failureInjections.forEach(this::failureInjection);
            }
            return this;
        }

        public ClusterSmokeEvidence build() {
            return new ClusterSmokeEvidence(components, evidenceByGate, failureInjections);
        }
    }
}
