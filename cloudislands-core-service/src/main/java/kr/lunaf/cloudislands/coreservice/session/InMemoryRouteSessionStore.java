package kr.lunaf.cloudislands.coreservice.session;

import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import kr.lunaf.cloudislands.api.model.RouteTicket;
import kr.lunaf.cloudislands.protocol.session.PlayerRouteSession;

public final class InMemoryRouteSessionStore implements RouteSessionStore {
    private final Clock clock;
    private final Map<UUID, PlayerRouteSession> byPlayer = new ConcurrentHashMap<>();

    public InMemoryRouteSessionStore(Clock clock) {
        this.clock = clock;
    }

    @Override
    public PlayerRouteSession put(RouteTicket ticket) {
        PlayerRouteSession session = new PlayerRouteSession(
            ticket.playerUuid(),
            ticket.ticketId(),
            ticket.targetNode(),
            ticket.payload().getOrDefault("targetServerName", ticket.targetNode()),
            ticket.nonce(),
            ticket.expiresAt()
        );
        byPlayer.put(ticket.playerUuid(), session);
        return session;
    }

    @Override
    public Optional<PlayerRouteSession> find(UUID playerUuid, String nodeId) {
        PlayerRouteSession session = byPlayer.get(playerUuid);
        if (session == null || session.expiresAt().isBefore(clock.instant()) || !session.targetNode().equals(nodeId)) {
            return Optional.empty();
        }
        return Optional.of(session);
    }

    @Override
    public Optional<PlayerRouteSession> consume(UUID playerUuid, String nodeId) {
        java.util.concurrent.atomic.AtomicReference<PlayerRouteSession> consumed = new java.util.concurrent.atomic.AtomicReference<>();
        byPlayer.computeIfPresent(playerUuid, (_playerUuid, session) -> {
            if (session.expiresAt().isBefore(clock.instant())) {
                return null;
            }
            if (!session.targetNode().equals(nodeId)) {
                return session;
            }
            consumed.set(session);
            return null;
        });
        return Optional.ofNullable(consumed.get());
    }

    public Optional<PlayerRouteSession> findAny(UUID playerUuid) {
        PlayerRouteSession session = byPlayer.get(playerUuid);
        if (session == null || session.expiresAt().isBefore(clock.instant())) {
            return Optional.empty();
        }
        return Optional.of(session);
    }

    public boolean clear(UUID playerUuid) {
        return byPlayer.remove(playerUuid) != null;
    }

    public int clearAll() {
        int cleared = byPlayer.size();
        byPlayer.clear();
        return cleared;
    }

    public String toJson() {
        StringBuilder builder = new StringBuilder("{\"sessions\":[");
        boolean first = true;
        for (PlayerRouteSession session : byPlayer.values()) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append("{\"playerUuid\":\"").append(session.playerUuid())
                .append("\",\"ticketId\":\"").append(session.ticketId())
                .append("\",\"targetNode\":\"").append(session.targetNode())
                .append("\",\"targetServerName\":\"").append(session.targetServerName())
                .append("\",\"expiresAt\":\"").append(session.expiresAt())
                .append("\"}");
        }
        return builder.append("]}").toString();
    }
}
