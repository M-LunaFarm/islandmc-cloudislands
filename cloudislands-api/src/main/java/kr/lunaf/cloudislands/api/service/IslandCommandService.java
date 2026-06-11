package kr.lunaf.cloudislands.api.service;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.model.CreateIslandResult;
import kr.lunaf.cloudislands.api.model.DeleteIslandResult;
import kr.lunaf.cloudislands.api.model.IslandFlag;
import kr.lunaf.cloudislands.api.model.IslandLocation;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.api.model.IslandRole;

public interface IslandCommandService {
    CompletableFuture<CreateIslandResult> createIsland(UUID ownerUuid, String templateId);
    CompletableFuture<DeleteIslandResult> deleteIsland(UUID requesterUuid, UUID islandId);
    CompletableFuture<Void> invite(UUID islandId, UUID inviterUuid, UUID targetUuid);
    CompletableFuture<Void> acceptInvite(UUID inviteId, UUID playerUuid);
    CompletableFuture<Void> declineInvite(UUID inviteId, UUID playerUuid);
    CompletableFuture<Void> kick(UUID islandId, UUID actorUuid, UUID targetUuid);
    CompletableFuture<Void> setRole(UUID islandId, UUID actorUuid, UUID targetUuid, IslandRole role);
    CompletableFuture<Void> setFlag(UUID islandId, UUID actorUuid, IslandFlag flag, String value);
    CompletableFuture<Void> setPermission(UUID islandId, UUID actorUuid, IslandRole role, IslandPermission permission, boolean allowed);
    CompletableFuture<Void> setHome(UUID islandId, UUID actorUuid, IslandLocation location);
    CompletableFuture<Void> createWarp(UUID islandId, UUID actorUuid, String name, IslandLocation location);
    CompletableFuture<Void> purchaseUpgrade(UUID islandId, UUID actorUuid, String upgradeKey);
    CompletableFuture<Void> depositBank(UUID islandId, UUID actorUuid, BigDecimal amount);
    CompletableFuture<Void> withdrawBank(UUID islandId, UUID actorUuid, BigDecimal amount);
}
