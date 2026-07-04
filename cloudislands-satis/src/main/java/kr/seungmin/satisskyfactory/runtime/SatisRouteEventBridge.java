package kr.seungmin.satisskyfactory.runtime;

import kr.lunaf.cloudislands.api.CloudIslandsApi;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

public final class SatisRouteEventBridge {
    private final Logger logger;
    private final AtomicLong handled = new AtomicLong();
    private final AtomicLong blocked = new AtomicLong();
    private final AtomicLong publishFailures = new AtomicLong();
    private volatile String lastBlockReason = "";

    public SatisRouteEventBridge(Logger logger) {
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public void publish(
            CloudIslandsApi api,
            String addonId,
            boolean enabled,
            String blockReason,
            RouteEventSnapshot event,
            String routeAuthorityPolicy,
            String routeTicketPrivacyPolicy
    ) {
        if (!enabled || api == null) {
            recordBlocked(blockReason);
            return;
        }
        handled.incrementAndGet();
        RouteEventSnapshot safeEvent = event == null ? RouteEventSnapshot.empty() : event;
        Map<String, String> state = routeState(safeEvent, routeAuthorityPolicy, routeTicketPrivacyPolicy, counters());
        api.addons().putState(addonId, state).exceptionally(error -> {
            logger.warning("Failed to publish CloudIslands Satis route event state: " + error.getMessage());
            publishFailures.incrementAndGet();
            return Map.of();
        });
        if (safeEvent.islandId() != null) {
            api.addons().putIslandState(addonId, safeEvent.islandId(), state).exceptionally(error -> {
                logger.warning("Failed to publish CloudIslands Satis island route event state: " + error.getMessage());
                publishFailures.incrementAndGet();
                return Map.of();
            });
        }
    }

    public void publishNodeState(
            CloudIslandsApi api,
            String addonId,
            boolean enabled,
            String blockReason,
            NodeStateSnapshot event
    ) {
        if (!enabled || api == null) {
            recordBlocked(blockReason);
            return;
        }
        handled.incrementAndGet();
        Map<String, String> state = nodeState(event, counters());
        api.addons().putState(addonId, state).exceptionally(error -> {
            logger.warning("Failed to publish CloudIslands Satis node state: " + error.getMessage());
            publishFailures.incrementAndGet();
            return Map.of();
        });
    }

    public void recordBlocked(String blockReason) {
        blocked.incrementAndGet();
        lastBlockReason = safe(blockReason);
    }

    public RouteEventCounters counters() {
        return new RouteEventCounters(handled.get(), blocked.get(), publishFailures.get(), lastBlockReason == null ? "" : lastBlockReason);
    }

    public static Map<String, String> routeState(
            RouteEventSnapshot event,
            String routeAuthorityPolicy,
            String routeTicketPrivacyPolicy,
            RouteEventCounters counters
    ) {
        RouteEventSnapshot safeEvent = event == null ? RouteEventSnapshot.empty() : event;
        RouteEventCounters safeCounters = counters == null ? RouteEventCounters.empty() : counters;
        Map<String, String> state = new LinkedHashMap<>();
        state.put("last-route-event", safe(safeEvent.eventName()));
        state.put("last-route-ticket", safeEvent.ticketId() == null ? "" : safeEvent.ticketId().toString());
        state.put("last-route-player", safeEvent.playerUuid() == null ? "" : safeEvent.playerUuid().toString());
        state.put("last-route-action", safe(safeEvent.action()));
        state.put("last-route-target-node", safe(safeEvent.targetNode()));
        state.put("last-route-target-server", safe(safeEvent.targetServerName()));
        if (safeEvent.islandId() != null) {
            state.put("last-route-island", safeEvent.islandId().toString());
        }
        if (safeEvent.requestedNode() != null && !safeEvent.requestedNode().isBlank()) {
            state.put("last-route-requested-node", safeEvent.requestedNode());
        }
        if (safeEvent.reason() != null && !safeEvent.reason().isBlank()) {
            state.put("last-route-reason", safeEvent.reason());
        }
        if (safeEvent.detail() != null && !safeEvent.detail().isBlank()) {
            state.put("last-route-detail", safeEvent.detail());
        }
        state.put("last-route-at", safeEvent.occurredAt() == null ? Instant.now().toString() : safeEvent.occurredAt().toString());
        state.put("last-route-policy", safe(routeAuthorityPolicy));
        state.put("last-route-player-visible-destination", safeEvent.islandId() == null ? "logical-island" : "island:" + safeEvent.islandId());
        state.put("last-route-player-visible-action", safe(safeEvent.action()).isBlank() ? safe(safeEvent.eventName()) : safe(safeEvent.action()));
        state.put("last-route-player-visible-status", safe(safeEvent.eventName()));
        state.put("last-route-player-visible-topology", "hidden");
        state.put("last-route-ticket-player-visible", "hidden");
        state.put("last-route-player-visible-policy", safe(routeTicketPrivacyPolicy));
        state.put("route-event-handled-count", Long.toString(safeCounters.handled()));
        state.put("route-event-blocked-count", Long.toString(safeCounters.blocked()));
        state.put("route-event-publish-failures", Long.toString(safeCounters.publishFailures()));
        state.put("route-event-last-block-reason", safe(safeCounters.lastBlockReason()));
        return state;
    }

    public static Map<String, String> nodeState(NodeStateSnapshot event, RouteEventCounters counters) {
        NodeStateSnapshot safeEvent = event == null ? NodeStateSnapshot.empty() : event;
        RouteEventCounters safeCounters = counters == null ? RouteEventCounters.empty() : counters;
        Map<String, String> state = new LinkedHashMap<>();
        state.put("last-node-id", safe(safeEvent.nodeId()));
        state.put("last-node-state", safe(safeEvent.state()));
        state.put("last-node-operation", safe(safeEvent.operation()));
        state.put("last-node-reason", safe(safeEvent.reason()));
        state.put("last-node-recovery-required", Integer.toString(safeEvent.recoveryRequired()));
        state.put("last-node-cleared-sessions", Integer.toString(safeEvent.clearedSessions()));
        state.put("last-node-cleared-tickets", Integer.toString(safeEvent.clearedTickets()));
        state.put("last-node-at", safeEvent.occurredAt() == null ? Instant.now().toString() : safeEvent.occurredAt().toString());
        state.put("last-node-policy", "diagnostic-state-only-core-keeps-route-authority");
        state.put("route-event-handled-count", Long.toString(safeCounters.handled()));
        state.put("route-event-blocked-count", Long.toString(safeCounters.blocked()));
        state.put("route-event-publish-failures", Long.toString(safeCounters.publishFailures()));
        state.put("route-event-last-block-reason", safe(safeCounters.lastBlockReason()));
        return state;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    public record RouteEventSnapshot(
            String eventName,
            UUID ticketId,
            UUID islandId,
            UUID playerUuid,
            String action,
            String targetNode,
            String targetServerName,
            String requestedNode,
            String reason,
            String detail,
            Instant occurredAt
    ) {
        public static RouteEventSnapshot empty() {
            return new RouteEventSnapshot("", null, null, null, "", "", "", "", "", "", Instant.EPOCH);
        }
    }

    public record RouteEventCounters(long handled, long blocked, long publishFailures, String lastBlockReason) {
        public static RouteEventCounters empty() {
            return new RouteEventCounters(0L, 0L, 0L, "");
        }
    }

    public record NodeStateSnapshot(
            String nodeId,
            String state,
            String operation,
            String reason,
            int recoveryRequired,
            int clearedSessions,
            int clearedTickets,
            Instant occurredAt
    ) {
        public static NodeStateSnapshot empty() {
            return new NodeStateSnapshot("", "", "", "", 0, 0, 0, Instant.EPOCH);
        }
    }
}
