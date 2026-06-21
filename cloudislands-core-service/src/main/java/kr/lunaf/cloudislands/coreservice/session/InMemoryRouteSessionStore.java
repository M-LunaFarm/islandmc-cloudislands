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
        return byPlayer.compute(ticket.playerUuid(), (_playerUuid, current) -> {
            if (current == null || expired(current) || !current.expiresAt().isAfter(session.expiresAt())) {
                return session;
            }
            return current;
        });
    }

    @Override
    public Optional<PlayerRouteSession> find(UUID playerUuid, String nodeId) {
        PlayerRouteSession session = byPlayer.get(playerUuid);
        if (session == null) {
            return Optional.empty();
        }
        if (expired(session)) {
            byPlayer.remove(playerUuid, session);
            return Optional.empty();
        }
        if (!session.targetNode().equals(nodeId)) {
            return Optional.empty();
        }
        return Optional.of(session);
    }

    @Override
    public Optional<PlayerRouteSession> consume(UUID playerUuid, String nodeId) {
        return consume(playerUuid, nodeId, null, null);
    }

    @Override
    public Optional<PlayerRouteSession> consume(UUID playerUuid, String nodeId, UUID ticketId, String nonce) {
        java.util.concurrent.atomic.AtomicReference<PlayerRouteSession> consumed = new java.util.concurrent.atomic.AtomicReference<>();
        byPlayer.computeIfPresent(playerUuid, (_playerUuid, session) -> {
            if (session.expiresAt().isBefore(clock.instant())) {
                return null;
            }
            if (!session.targetNode().equals(nodeId)) {
                return session;
            }
            if (ticketId != null && !session.ticketId().equals(ticketId)) {
                return session;
            }
            if (nonce != null && !session.nonce().equals(nonce)) {
                return session;
            }
            consumed.set(session);
            return null;
        });
        return Optional.ofNullable(consumed.get());
    }

    @Override
    public Optional<PlayerRouteSession> findAny(UUID playerUuid) {
        PlayerRouteSession session = byPlayer.get(playerUuid);
        if (session == null) {
            return Optional.empty();
        }
        if (expired(session)) {
            byPlayer.remove(playerUuid, session);
            return Optional.empty();
        }
        return Optional.of(session);
    }

    public boolean clear(UUID playerUuid) {
        return byPlayer.remove(playerUuid) != null;
    }

    @Override
    public int clearForNode(String nodeId) {
        int cleared = 0;
        for (PlayerRouteSession session : byPlayer.values()) {
            if (expired(session) || session.targetNode().equals(nodeId)) {
                if (byPlayer.remove(session.playerUuid(), session)) {
                    cleared++;
                }
            }
        }
        return cleared;
    }

    public int clearAll() {
        int cleared = byPlayer.size();
        byPlayer.clear();
        return cleared;
    }

    public String toJson() {
        java.util.List<PlayerRouteSession> sessions = new java.util.ArrayList<>();
        for (PlayerRouteSession session : byPlayer.values()) {
            if (expired(session)) {
                byPlayer.remove(session.playerUuid(), session);
                continue;
            }
            sessions.add(session);
        }
        return RouteSessionJson.snapshot(sessions);
    }

    private boolean expired(PlayerRouteSession session) {
        return session.expiresAt().isBefore(clock.instant());
    }
}
