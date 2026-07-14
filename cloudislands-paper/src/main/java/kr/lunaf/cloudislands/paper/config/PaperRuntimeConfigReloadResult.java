package kr.lunaf.cloudislands.paper.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record PaperRuntimeConfigReloadResult(
    boolean applied,
    List<String> liveChanges,
    List<String> restartRequiredChanges
) {
    public PaperRuntimeConfigReloadResult {
        liveChanges = liveChanges == null ? List.of() : List.copyOf(liveChanges);
        restartRequiredChanges = restartRequiredChanges == null ? List.of() : List.copyOf(restartRequiredChanges);
    }

    public static PaperRuntimeConfigReloadResult analyze(PaperRuntimeConfig current, PaperRuntimeConfig candidate) {
        PaperRuntimeConfig active = current == null ? PaperRuntimeConfig.defaults() : current;
        PaperRuntimeConfig requested = candidate == null ? PaperRuntimeConfig.defaults() : candidate;
        List<String> live = new ArrayList<>();
        List<String> restart = new ArrayList<>();
        changed(live, "service-name", active.serviceName(), requested.serviceName());
        changed(live, "messages", active.messages(), requested.messages());
        changed(restart, "node", active.node(), requested.node());
        changed(restart, "core-api", active.coreApi(), requested.coreApi());
        changed(restart, "redis", active.redis(), requested.redis());
        changed(restart, "security", active.security(), requested.security());
        changed(restart, "routing", active.routing(), requested.routing());
        changed(restart, "protection", active.protection(), requested.protection());
        changed(restart, "generator", active.generator(), requested.generator());
        changed(restart, "storage", active.storage(), requested.storage());
        changed(restart, "migration", active.migration(), requested.migration());
        changed(restart, "worker", active.worker(), requested.worker());
        changed(restart, "snapshots", active.snapshots(), requested.snapshots());
        changed(restart, "health", active.health(), requested.health());
        changed(restart, "heartbeat", active.heartbeat(), requested.heartbeat());
        changed(restart, "gui", active.gui(), requested.gui());
        return new PaperRuntimeConfigReloadResult(false, live, restart);
    }

    public PaperRuntimeConfigReloadResult appliedResult() {
        if (!restartRequiredChanges.isEmpty()) {
            throw new IllegalStateException("restart-required config cannot be marked applied");
        }
        return new PaperRuntimeConfigReloadResult(true, liveChanges, List.of());
    }

    private static void changed(List<String> changed, String section, Object current, Object candidate) {
        if (!Objects.equals(current, candidate)) {
            changed.add(section);
        }
    }
}
