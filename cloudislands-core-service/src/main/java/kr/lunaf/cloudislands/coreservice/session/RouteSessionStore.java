package kr.lunaf.cloudislands.coreservice.session;

import java.util.Optional;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.RouteTicket;
import kr.lunaf.cloudislands.protocol.session.PlayerRouteSession;

public interface RouteSessionStore {
    PlayerRouteSession put(RouteTicket ticket);
    Optional<PlayerRouteSession> find(UUID playerUuid, String nodeId);
    Optional<PlayerRouteSession> consume(UUID playerUuid, String nodeId);
    boolean clear(UUID playerUuid);
    int clearAll();
}
