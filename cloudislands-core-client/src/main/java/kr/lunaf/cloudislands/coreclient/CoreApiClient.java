package kr.lunaf.cloudislands.coreclient;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.model.CreateIslandResult;
import kr.lunaf.cloudislands.api.model.RouteTicket;
import kr.lunaf.cloudislands.protocol.node.NodeHeartbeatRequest;
import kr.lunaf.cloudislands.protocol.session.PlayerRouteSession;

public interface CoreApiClient {
    CompletableFuture<CreateIslandResult> createIsland(UUID playerUuid, String templateId);
    CompletableFuture<RouteTicket> createHomeTicket(UUID playerUuid);
    CompletableFuture<RouteTicket> createVisitTicket(UUID visitorUuid, UUID targetIslandId);
    CompletableFuture<Void> publishRouteSession(RouteTicket ticket);
    CompletableFuture<Optional<PlayerRouteSession>> consumeRouteSession(UUID playerUuid, String nodeId);
    CompletableFuture<Optional<RouteTicket>> consumeTicket(UUID ticketId, UUID playerUuid, String nodeId, String nonce);
    CompletableFuture<Void> publishHeartbeat(NodeHeartbeatRequest request);
}
