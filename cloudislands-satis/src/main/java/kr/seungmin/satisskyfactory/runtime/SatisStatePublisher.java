package kr.seungmin.satisskyfactory.runtime;

import kr.lunaf.cloudislands.api.CloudIslandsApi;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

public final class SatisStatePublisher {
    private final Logger logger;

    public SatisStatePublisher(Logger logger) {
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public void publishStartupHydrationState(
            CloudIslandsApi api,
            String addonId,
            String reason,
            int islandCount,
            String backendName,
            String tickStartPolicy,
            String stateOwnerPolicy
    ) {
        publishAddonState(
                api,
                addonId,
                startupHydrationState(reason, islandCount, backendName, tickStartPolicy, stateOwnerPolicy, Instant.now()),
                "startup hydration"
        );
    }

    public void publishAddonState(CloudIslandsApi api, String addonId, Map<String, String> state, String context) {
        if (api == null || addonId == null || addonId.isBlank() || state == null || state.isEmpty()) {
            return;
        }
        String safeContext = context == null || context.isBlank() ? "state" : context;
        api.addons().putState(addonId, state).exceptionally(error -> {
            logger.warning("Failed to publish CloudIslands Satis " + safeContext + " state: " + error.getMessage());
            return Map.of();
        });
    }

    public static Map<String, String> startupHydrationState(
            String reason,
            int islandCount,
            String backendName,
            String tickStartPolicy,
            String stateOwnerPolicy,
            Instant now
    ) {
        String safeReason = reason == null || reason.isBlank() ? "startup" : reason;
        Map<String, String> state = new LinkedHashMap<>();
        state.put("last-startup-hydrate-reason", safeReason);
        state.put("last-startup-hydrate-islands", Integer.toString(Math.max(0, islandCount)));
        state.put("last-startup-hydrate-backend", backendName == null || backendName.isBlank() ? "unknown" : backendName);
        state.put("last-startup-hydrate-policy", tickStartPolicy == null || tickStartPolicy.isBlank() ? "unknown" : tickStartPolicy);
        state.put("last-startup-hydrate-state-owner-policy", stateOwnerPolicy == null || stateOwnerPolicy.isBlank() ? "unknown" : stateOwnerPolicy);
        state.put("last-startup-hydrate-at", (now == null ? Instant.EPOCH : now).toString());
        return state;
    }
}
