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
        Optional<PlayerRouteSession> session = find(playerUuid, nodeId);
        session.ifPresent(value -> byPlayer.remove(playerUuid, value));
        return session;
    }
}
