package kr.lunaf.cloudislands.coreclient;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.model.CreateIslandResult;
import kr.lunaf.cloudislands.api.model.IslandFlag;
import kr.lunaf.cloudislands.api.model.IslandLocation;
import kr.lunaf.cloudislands.api.model.IslandRole;
import kr.lunaf.cloudislands.api.model.RouteTicket;
import kr.lunaf.cloudislands.protocol.job.IslandJob;
import kr.lunaf.cloudislands.protocol.job.IslandJobType;
import kr.lunaf.cloudislands.protocol.node.NodeHeartbeatRequest;
import kr.lunaf.cloudislands.protocol.session.PlayerRouteSession;

public interface CoreApiClient {
    CompletableFuture<CreateIslandResult> createIsland(UUID playerUuid, String templateId);
    CompletableFuture<String> listIslandMembers(UUID islandId);
    CompletableFuture<Void> setIslandMember(UUID islandId, UUID actorUuid, UUID playerUuid, IslandRole role);
    CompletableFuture<Void> removeIslandMember(UUID islandId, UUID actorUuid, UUID playerUuid);
    CompletableFuture<String> listIslandFlags(UUID islandId);
    CompletableFuture<Void> setIslandFlag(UUID islandId, UUID actorUuid, IslandFlag flag, String value);
    CompletableFuture<String> listIslandWarps(UUID islandId);
    CompletableFuture<Void> setIslandWarp(UUID islandId, UUID actorUuid, String name, IslandLocation location, boolean publicAccess);
    CompletableFuture<Void> deleteIslandWarp(UUID islandId, UUID actorUuid, String name);
    CompletableFuture<Void> setIslandPublicAccess(UUID islandId, UUID actorUuid, boolean publicAccess);
    CompletableFuture<RouteTicket> createHomeTicket(UUID playerUuid);
    CompletableFuture<RouteTicket> createVisitTicket(UUID visitorUuid, UUID targetIslandId);
    CompletableFuture<Void> publishRouteSession(RouteTicket ticket);
    CompletableFuture<Optional<PlayerRouteSession>> consumeRouteSession(UUID playerUuid, String nodeId);
    CompletableFuture<Optional<RouteTicket>> consumeTicket(UUID ticketId, UUID playerUuid, String nodeId, String nonce);
    CompletableFuture<List<IslandJob>> claimJobs(String nodeId, List<IslandJobType> supportedTypes, int maxJobs);
    CompletableFuture<Void> completeJob(String nodeId, UUID jobId);
    CompletableFuture<Void> completeJob(String nodeId, UUID jobId, Map<String, String> payload);
    CompletableFuture<Void> failJob(String nodeId, UUID jobId, String errorMessage);
    CompletableFuture<Void> publishHeartbeat(NodeHeartbeatRequest request);
}
