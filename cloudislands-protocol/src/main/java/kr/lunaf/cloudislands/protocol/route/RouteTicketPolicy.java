package kr.lunaf.cloudislands.protocol.route;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.RouteTicket;
import kr.lunaf.cloudislands.api.model.RouteTicketState;

public final class RouteTicketPolicy {
    public static final Duration DEFAULT_TTL = Duration.ofSeconds(30);
    public static final String PUBLIC_TARGET_LABEL = "island";
    public static final boolean NONCE_REQUIRED = true;
    public static final boolean TARGET_NODE_REQUIRED = true;
    public static final boolean ONE_TIME_CONSUME = true;
    public static final boolean HIDE_PHYSICAL_NODE_NAMES_FROM_PLAYERS = true;
    public static final String ONE_TIME_POLICY = "ready-ticket-can-be-consumed-once-only-by-player-target-node-and-nonce-before-expiry";
    public static final String NONCE_POLICY = "nonce-required-for-ticket-status-session-publish-session-consume-and-ticket-consume";
    public static final String ARRIVAL_CONSUME_POLICY = "paper-join-consumes-route-session-then-route-ticket-before-teleport";
    public static final String DIRECT_ACCESS_POLICY = "island-node-requires-velocity-forwarding-proxy-source-allowlist-and-route-session";
    public static final String REPLAY_POLICY = "consumed-expired-cancelled-and-failed-tickets-are-terminal-and-not-reusable";

    private RouteTicketPolicy() {
    }

    public static boolean issuable(RouteTicket ticket, Instant now) {
        return ticket != null
            && ticket.ticketId() != null
            && ticket.playerUuid() != null
            && ticket.action() != null
            && ticket.islandId() != null
            && present(ticket.targetNode())
            && ticket.state() == RouteTicketState.PREPARING
            && present(ticket.nonce())
            && ticket.expiresAt() != null
            && now != null
            && ticket.expiresAt().isAfter(now);
    }

    public static boolean consumable(RouteTicket ticket, UUID playerUuid, String nodeId, String nonce, Instant now) {
        return ticket != null
            && ticket.state() == RouteTicketState.READY
            && ticket.expiresAt() != null
            && now != null
            && ticket.expiresAt().isAfter(now)
            && Objects.equals(ticket.playerUuid(), playerUuid)
            && Objects.equals(ticket.targetNode(), nodeId)
            && Objects.equals(ticket.nonce(), nonce)
            && present(nonce);
    }

    public static boolean terminal(RouteTicket ticket) {
        return ticket != null && ticket.terminal();
    }

    public static String playerVisibleTargetName(RouteTicket ticket) {
        if (ticket == null || HIDE_PHYSICAL_NODE_NAMES_FROM_PLAYERS) {
            return PUBLIC_TARGET_LABEL;
        }
        return ticket.targetNode();
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }
}
