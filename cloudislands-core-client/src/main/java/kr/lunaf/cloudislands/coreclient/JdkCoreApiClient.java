package kr.lunaf.cloudislands.coreclient;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import kr.lunaf.cloudislands.api.model.CreateIslandResult;
import kr.lunaf.cloudislands.api.model.DeleteIslandResult;
import kr.lunaf.cloudislands.api.model.IslandFlag;
import kr.lunaf.cloudislands.api.model.IslandLocation;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.api.model.IslandRole;
import kr.lunaf.cloudislands.api.model.RouteAction;
import kr.lunaf.cloudislands.api.model.RouteTicket;
import kr.lunaf.cloudislands.api.model.RouteTicketState;
import kr.lunaf.cloudislands.protocol.job.IslandJob;
import kr.lunaf.cloudislands.protocol.job.IslandJobType;
import kr.lunaf.cloudislands.protocol.job.json.IslandJobJson;
import kr.lunaf.cloudislands.protocol.node.NodeHeartbeatRequest;
import kr.lunaf.cloudislands.protocol.session.PlayerRouteSession;

public final class JdkCoreApiClient implements CoreApiClient {
    private final URI baseUri;
    private final String authToken;
    private final String adminToken;
    private final Duration timeout;
    private final HttpClient httpClient;

    public JdkCoreApiClient(URI baseUri, String authToken, Duration timeout) {
        this(baseUri, authToken, System.getenv().getOrDefault("CI_ADMIN_TOKEN", ""), timeout);
    }

    public JdkCoreApiClient(URI baseUri, String authToken, String adminToken, Duration timeout) {
        this.baseUri = baseUri;
        this.authToken = authToken;
        this.adminToken = adminToken == null ? "" : adminToken;
        this.timeout = timeout == null || timeout.isNegative() || timeout.isZero() ? Duration.ofSeconds(5) : timeout;
        this.httpClient = HttpClient.newBuilder().connectTimeout(this.timeout).build();
    }

    @Override
    public CompletableFuture<CreateIslandResult> createIsland(UUID playerUuid, String templateId) {
        return post("/v1/islands", "{\"playerUuid\":\"" + playerUuid + "\",\"templateId\":\"" + templateId + "\"}")
            .thenApply(body -> new CreateIslandResult(body.contains("\"accepted\":true"), text(body, "code", "FAILED"), null, RouteTicketJson.parseNested(body, "ticket")));
    }

    @Override
    public CompletableFuture<DeleteIslandResult> deleteIsland(UUID requesterUuid, UUID islandId) {
        return deleteWithResultBody("/v1/islands/" + islandId + "?requesterUuid=" + requesterUuid)
            .thenApply(body -> new DeleteIslandResult(body.contains("\"accepted\":true"), text(body, "code", "FAILED"), uuid(body, "islandId", islandId)));
    }

    @Override
    public CompletableFuture<String> resetIsland(UUID islandId, UUID actorUuid, String reason) {
        return resetIslandResult(islandId, actorUuid, reason);
    }

    @Override
    public CompletableFuture<String> resetIslandResult(UUID islandId, UUID actorUuid, String reason) {
        return postWithResultBody("/v1/islands/reset", "{\"islandId\":\"" + islandId + "\",\"actorUuid\":\"" + actorUuid + "\",\"reason\":\"" + escape(reason) + "\"}");
    }

    @Override
    public CompletableFuture<String> islandInfo(UUID islandId) {
        return post("/v1/islands/info", "{\"islandId\":\"" + islandId + "\"}");
    }

    @Override
    public CompletableFuture<String> islandInfoByOwner(UUID ownerUuid) {
        return post("/v1/islands/info", "{\"ownerUuid\":\"" + ownerUuid + "\"}");
    }

    @Override
    public CompletableFuture<String> islandInfoByName(String name) {
        return post("/v1/islands/info", "{\"name\":\"" + escape(name) + "\"}");
    }

    @Override
    public CompletableFuture<String> getIsland(UUID islandId) {
        return get("/v1/islands/" + islandId);
    }

    @Override
    public CompletableFuture<String> getIslandByOwner(UUID ownerUuid) {
        return get("/v1/islands/by-owner/" + ownerUuid);
    }

    @Override
    public CompletableFuture<String> getIslandMembers(UUID islandId) {
        return get("/v1/islands/" + islandId + "/members");
    }

    @Override
    public CompletableFuture<String> getIslandRuntime(UUID islandId) {
        return get("/v1/islands/" + islandId + "/runtime");
    }

    @Override
    public CompletableFuture<String> getIslandFlags(UUID islandId) {
        return get("/v1/islands/" + islandId + "/flags");
    }

    @Override
    public CompletableFuture<String> getPlayerProfile(UUID playerUuid) {
        return get("/v1/players/" + playerUuid + "/profile");
    }

    @Override
    public CompletableFuture<String> getPlayerIsland(UUID playerUuid) {
        return get("/v1/players/" + playerUuid + "/island");
    }

    @Override
    public CompletableFuture<String> getJoinedIslands(UUID playerUuid) {
        return get("/v1/players/" + playerUuid + "/islands");
    }

    @Override
    public CompletableFuture<Void> setIslandName(UUID islandId, UUID actorUuid, String name) {
        return setIslandNameResult(islandId, actorUuid, name).thenApply(_body -> null);
    }

    @Override
    public CompletableFuture<String> setIslandNameResult(UUID islandId, UUID actorUuid, String name) {
        return postWithResultBody("/v1/islands/name", "{\"islandId\":\"" + islandId + "\",\"actorUuid\":\"" + actorUuid + "\",\"name\":\"" + escape(name) + "\"}");
    }

    @Override
    public CompletableFuture<String> listIslandMembers(UUID islandId) {
        return post("/v1/islands/members", "{\"islandId\":\"" + islandId + "\"}");
    }

    @Override
    public CompletableFuture<Void> setIslandMember(UUID islandId, UUID actorUuid, UUID playerUuid, IslandRole role) {
        return setIslandMemberResult(islandId, actorUuid, playerUuid, role).thenApply(_body -> null);
    }

    @Override
    public CompletableFuture<String> setIslandMemberResult(UUID islandId, UUID actorUuid, UUID playerUuid, IslandRole role) {
        return postWithResultBody("/v1/islands/members/set", "{\"islandId\":\"" + islandId + "\",\"actorUuid\":\"" + actorUuid + "\",\"playerUuid\":\"" + playerUuid + "\",\"role\":\"" + role.name() + "\"}");
    }

    @Override
    public CompletableFuture<Void> transferIslandOwnership(UUID islandId, UUID actorUuid, UUID targetUuid) {
        return transferIslandOwnershipResult(islandId, actorUuid, targetUuid).thenApply(_body -> null);
    }

    @Override
    public CompletableFuture<String> transferIslandOwnershipResult(UUID islandId, UUID actorUuid, UUID targetUuid) {
        return postWithResultBody("/v1/islands/transfer", "{\"islandId\":\"" + islandId + "\",\"actorUuid\":\"" + actorUuid + "\",\"targetUuid\":\"" + targetUuid + "\"}");
    }

    @Override
    public CompletableFuture<Void> removeIslandMember(UUID islandId, UUID actorUuid, UUID playerUuid) {
        return removeIslandMemberResult(islandId, actorUuid, playerUuid).thenApply(_body -> null);
    }

    @Override
    public CompletableFuture<String> removeIslandMemberResult(UUID islandId, UUID actorUuid, UUID playerUuid) {
        return postWithResultBody("/v1/islands/members/remove", "{\"islandId\":\"" + islandId + "\",\"actorUuid\":\"" + actorUuid + "\",\"playerUuid\":\"" + playerUuid + "\"}");
    }

    @Override
    public CompletableFuture<String> createIslandInvite(UUID islandId, UUID inviterUuid, UUID targetUuid) {
        return postWithResultBody("/v1/islands/invites", "{\"islandId\":\"" + islandId + "\",\"inviterUuid\":\"" + inviterUuid + "\",\"targetUuid\":\"" + targetUuid + "\"}");
    }

    @Override
    public CompletableFuture<String> listPendingInvites(UUID playerUuid) {
        return post("/v1/players/invites", "{\"playerUuid\":\"" + playerUuid + "\"}");
    }

    @Override
    public CompletableFuture<String> listPlayerIslands(UUID playerUuid) {
        return post("/v1/players/islands", "{\"playerUuid\":\"" + playerUuid + "\"}");
    }

    @Override
    public CompletableFuture<Void> acceptIslandInvite(UUID inviteId, UUID playerUuid) {
        return acceptIslandInviteResult(inviteId, playerUuid).thenApply(_body -> null);
    }

    @Override
    public CompletableFuture<String> acceptIslandInviteResult(UUID inviteId, UUID playerUuid) {
        return postWithResultBody("/v1/islands/invites/accept", "{\"inviteId\":\"" + inviteId + "\",\"playerUuid\":\"" + playerUuid + "\"}");
    }

    @Override
    public CompletableFuture<Void> declineIslandInvite(UUID inviteId, UUID playerUuid) {
        return declineIslandInviteResult(inviteId, playerUuid).thenApply(_body -> null);
    }

    @Override
    public CompletableFuture<String> declineIslandInviteResult(UUID inviteId, UUID playerUuid) {
        return postWithResultBody("/v1/islands/invites/decline", "{\"inviteId\":\"" + inviteId + "\",\"playerUuid\":\"" + playerUuid + "\"}");
    }

    @Override
    public CompletableFuture<Void> banIslandVisitor(UUID islandId, UUID actorUuid, UUID playerUuid, String reason) {
        return banIslandVisitorResult(islandId, actorUuid, playerUuid, reason).thenApply(_body -> null);
    }

    @Override
    public CompletableFuture<String> banIslandVisitorResult(UUID islandId, UUID actorUuid, UUID playerUuid, String reason) {
        return postWithResultBody("/v1/islands/bans/set", "{\"islandId\":\"" + islandId + "\",\"actorUuid\":\"" + actorUuid + "\",\"playerUuid\":\"" + playerUuid + "\",\"reason\":\"" + escape(reason) + "\"}");
    }

    @Override
    public CompletableFuture<String> listIslandBans(UUID islandId) {
        return post("/v1/islands/bans", "{\"islandId\":\"" + islandId + "\"}");
    }

    @Override
    public CompletableFuture<Void> pardonIslandVisitor(UUID islandId, UUID actorUuid, UUID playerUuid) {
        return pardonIslandVisitorResult(islandId, actorUuid, playerUuid).thenApply(_body -> null);
    }

    @Override
    public CompletableFuture<String> pardonIslandVisitorResult(UUID islandId, UUID actorUuid, UUID playerUuid) {
        return postWithResultBody("/v1/islands/bans/remove", "{\"islandId\":\"" + islandId + "\",\"actorUuid\":\"" + actorUuid + "\",\"playerUuid\":\"" + playerUuid + "\"}");
    }

    @Override
    public CompletableFuture<Void> kickIslandVisitor(UUID islandId, UUID actorUuid, UUID playerUuid) {
        return kickIslandVisitorResult(islandId, actorUuid, playerUuid).thenApply(_body -> null);
    }

    @Override
    public CompletableFuture<String> kickIslandVisitorResult(UUID islandId, UUID actorUuid, UUID playerUuid) {
        return postWithResultBody("/v1/islands/visitors/kick", "{\"islandId\":\"" + islandId + "\",\"actorUuid\":\"" + actorUuid + "\",\"playerUuid\":\"" + playerUuid + "\"}");
    }

    @Override
    public CompletableFuture<String> listIslandFlags(UUID islandId) {
        return post("/v1/islands/flags", "{\"islandId\":\"" + islandId + "\"}");
    }

    @Override
    public CompletableFuture<Void> setIslandFlag(UUID islandId, UUID actorUuid, IslandFlag flag, String value) {
        return setIslandFlagResult(islandId, actorUuid, flag, value).thenApply(_body -> null);
    }

    @Override
    public CompletableFuture<String> setIslandFlagResult(UUID islandId, UUID actorUuid, IslandFlag flag, String value) {
        return postWithResultBody("/v1/islands/flags/set", "{\"islandId\":\"" + islandId + "\",\"actorUuid\":\"" + actorUuid + "\",\"flag\":\"" + flag.name() + "\",\"value\":\"" + escape(value) + "\"}");
    }

    @Override
    public CompletableFuture<String> islandBiome(UUID islandId) {
        return post("/v1/islands/biome", "{\"islandId\":\"" + islandId + "\"}");
    }

    @Override
    public CompletableFuture<Void> setIslandBiome(UUID islandId, UUID actorUuid, String biomeKey) {
        return setIslandBiomeResult(islandId, actorUuid, biomeKey).thenApply(_body -> null);
    }

    @Override
    public CompletableFuture<String> setIslandBiomeResult(UUID islandId, UUID actorUuid, String biomeKey) {
        return postWithResultBody("/v1/islands/biome/set", "{\"islandId\":\"" + islandId + "\",\"actorUuid\":\"" + actorUuid + "\",\"biomeKey\":\"" + escape(biomeKey) + "\"}");
    }

    @Override
    public CompletableFuture<String> listIslandHomes(UUID islandId) {
        return post("/v1/islands/homes", "{\"islandId\":\"" + islandId + "\"}");
    }

    @Override
    public CompletableFuture<Void> setIslandHome(UUID islandId, UUID actorUuid, String name, IslandLocation location) {
        return setIslandHomeResult(islandId, actorUuid, name, location).thenApply(_body -> null);
    }

    @Override
    public CompletableFuture<String> setIslandHomeResult(UUID islandId, UUID actorUuid, String name, IslandLocation location) {
        return postWithResultBody("/v1/islands/homes/set", "{\"islandId\":\"" + islandId + "\",\"actorUuid\":\"" + actorUuid + "\",\"name\":\"" + escape(name) + "\",\"worldName\":\"" + escape(location.worldName()) + "\",\"localX\":" + location.localX() + ",\"localY\":" + location.localY() + ",\"localZ\":" + location.localZ() + ",\"yaw\":" + location.yaw() + ",\"pitch\":" + location.pitch() + "}");
    }

    @Override
    public CompletableFuture<String> listIslandPermissions(UUID islandId) {
        return post("/v1/islands/permissions", "{\"islandId\":\"" + islandId + "\"}");
    }

    @Override
    public CompletableFuture<Void> setIslandPermission(UUID islandId, UUID actorUuid, IslandRole role, IslandPermission permission, boolean allowed) {
        return setIslandPermissionResult(islandId, actorUuid, role, permission, allowed).thenApply(_body -> null);
    }

    @Override
    public CompletableFuture<String> setIslandPermissionResult(UUID islandId, UUID actorUuid, IslandRole role, IslandPermission permission, boolean allowed) {
        return postWithResultBody("/v1/islands/permissions/set", "{\"islandId\":\"" + islandId + "\",\"actorUuid\":\"" + actorUuid + "\",\"role\":\"" + role.name() + "\",\"permission\":\"" + permission.name() + "\",\"allowed\":" + allowed + "}");
    }

    @Override
    public CompletableFuture<String> listIslandRoles(UUID islandId) {
        return post("/v1/islands/roles", "{\"islandId\":\"" + islandId + "\"}");
    }

    @Override
    public CompletableFuture<String> upsertIslandRole(UUID islandId, UUID actorUuid, IslandRole role, int weight, String displayName) {
        return postWithResultBody("/v1/islands/roles/upsert", "{\"islandId\":\"" + islandId + "\",\"actorUuid\":\"" + actorUuid + "\",\"role\":\"" + role.name() + "\",\"weight\":" + weight + ",\"displayName\":\"" + escape(displayName) + "\"}");
    }

    @Override
    public CompletableFuture<String> resetIslandRole(UUID islandId, UUID actorUuid, IslandRole role) {
        return postWithResultBody("/v1/islands/roles/reset", "{\"islandId\":\"" + islandId + "\",\"actorUuid\":\"" + actorUuid + "\",\"role\":\"" + role.name() + "\"}");
    }

    @Override
    public CompletableFuture<String> listIslandWarps(UUID islandId) {
        return post("/v1/islands/warps", "{\"islandId\":\"" + islandId + "\"}");
    }

    @Override
    public CompletableFuture<String> listPublicWarps(int limit) {
        return post("/v1/islands/public-warps", "{\"limit\":" + limit + "}");
    }

    @Override
    public CompletableFuture<Void> setIslandWarp(UUID islandId, UUID actorUuid, String name, IslandLocation location, boolean publicAccess) {
        return setIslandWarpResult(islandId, actorUuid, name, location, publicAccess).thenApply(_body -> null);
    }

    @Override
    public CompletableFuture<String> setIslandWarpResult(UUID islandId, UUID actorUuid, String name, IslandLocation location, boolean publicAccess) {
        return postWithResultBody("/v1/islands/warps/set", "{\"islandId\":\"" + islandId + "\",\"actorUuid\":\"" + actorUuid + "\",\"name\":\"" + escape(name) + "\",\"worldName\":\"" + escape(location.worldName()) + "\",\"localX\":" + location.localX() + ",\"localY\":" + location.localY() + ",\"localZ\":" + location.localZ() + ",\"yaw\":" + location.yaw() + ",\"pitch\":" + location.pitch() + ",\"publicAccess\":" + publicAccess + "}");
    }

    @Override
    public CompletableFuture<Void> deleteIslandWarp(UUID islandId, UUID actorUuid, String name) {
        return deleteIslandWarpResult(islandId, actorUuid, name).thenApply(_body -> null);
    }

    @Override
    public CompletableFuture<String> deleteIslandWarpResult(UUID islandId, UUID actorUuid, String name) {
        return postWithResultBody("/v1/islands/warps/delete", "{\"islandId\":\"" + islandId + "\",\"actorUuid\":\"" + actorUuid + "\",\"name\":\"" + escape(name) + "\"}");
    }

    @Override
    public CompletableFuture<Void> setIslandWarpPublicAccess(UUID islandId, UUID actorUuid, String name, boolean publicAccess) {
        return setIslandWarpPublicAccessResult(islandId, actorUuid, name, publicAccess).thenApply(_body -> null);
    }

    @Override
    public CompletableFuture<String> setIslandWarpPublicAccessResult(UUID islandId, UUID actorUuid, String name, boolean publicAccess) {
        return postWithResultBody("/v1/islands/warps/access", "{\"islandId\":\"" + islandId + "\",\"actorUuid\":\"" + actorUuid + "\",\"name\":\"" + escape(name) + "\",\"publicAccess\":" + publicAccess + "}");
    }

    @Override
    public CompletableFuture<Void> setIslandPublicAccess(UUID islandId, UUID actorUuid, boolean publicAccess) {
        return setIslandPublicAccessResult(islandId, actorUuid, publicAccess).thenApply(_body -> null);
    }

    @Override
    public CompletableFuture<String> setIslandPublicAccessResult(UUID islandId, UUID actorUuid, boolean publicAccess) {
        return postWithResultBody("/v1/islands/access", "{\"islandId\":\"" + islandId + "\",\"actorUuid\":\"" + actorUuid + "\",\"publicAccess\":" + publicAccess + "}");
    }

    @Override
    public CompletableFuture<Void> setIslandLocked(UUID islandId, UUID actorUuid, boolean locked) {
        return setIslandLockedResult(islandId, actorUuid, locked).thenApply(_body -> null);
    }

    @Override
    public CompletableFuture<String> setIslandLockedResult(UUID islandId, UUID actorUuid, boolean locked) {
        return postWithResultBody("/v1/islands/lock", "{\"islandId\":\"" + islandId + "\",\"actorUuid\":\"" + actorUuid + "\",\"locked\":" + locked + "}");
    }

    @Override
    public CompletableFuture<Void> recordBlockDelta(UUID islandId, String materialKey, long delta) {
        return recordBlockDeltaResult(islandId, materialKey, delta).thenApply(_body -> null);
    }

    @Override
    public CompletableFuture<String> recordBlockDeltaResult(UUID islandId, String materialKey, long delta) {
        return postWithResultBody("/v1/islands/blocks/delta", "{\"islandId\":\"" + islandId + "\",\"materialKey\":\"" + escape(materialKey) + "\",\"delta\":" + delta + "}");
    }

    @Override
    public CompletableFuture<String> replaceBlockCounts(UUID islandId, Map<String, Long> counts) {
        return postWithResultBody("/v1/islands/blocks/replace", "{\"islandId\":\"" + islandId + "\",\"counts\":\"" + escape(countsPayload(counts)) + "\"}");
    }

    @Override
    public CompletableFuture<String> recalculateIslandLevel(UUID islandId, UUID actorUuid) {
        return post("/v1/islands/level/recalculate", "{\"islandId\":\"" + islandId + "\",\"actorUuid\":\"" + actorUuid + "\"}");
    }

    @Override
    public CompletableFuture<String> topIslandsByLevel(int limit) {
        return post("/v1/rankings/level", "{\"limit\":" + limit + "}");
    }

    @Override
    public CompletableFuture<String> topIslandsByWorth(int limit) {
        return post("/v1/rankings/worth", "{\"limit\":" + limit + "}");
    }

    @Override
    public CompletableFuture<String> listPublicIslands(int limit) {
        return post("/v1/islands/public", "{\"limit\":" + limit + "}");
    }

    @Override
    public CompletableFuture<Void> setBlockValue(UUID actorUuid, String materialKey, String worth, long levelPoints, long limit) {
        return setBlockValueResult(actorUuid, materialKey, worth, levelPoints, limit).thenApply(_body -> null);
    }

    @Override
    public CompletableFuture<String> setBlockValueResult(UUID actorUuid, String materialKey, String worth, long levelPoints, long limit) {
        return postWithResultBody("/v1/admin/block-values", "{\"actorUuid\":\"" + actorUuid + "\",\"materialKey\":\"" + escape(materialKey) + "\",\"worth\":\"" + escape(worth) + "\",\"levelPoints\":" + levelPoints + ",\"limit\":" + limit + "}");
    }

    @Override
    public CompletableFuture<String> listBlockValues() {
        return postWithResultBody("/v1/admin/block-values/list", "{}");
    }

    @Override
    public CompletableFuture<String> listUpgradeRules() {
        return post("/v1/upgrades/rules", "{}");
    }

    @Override
    public CompletableFuture<String> listIslandUpgrades(UUID islandId) {
        return post("/v1/islands/upgrades", "{\"islandId\":\"" + islandId + "\"}");
    }

    @Override
    public CompletableFuture<String> purchaseIslandUpgrade(UUID islandId, UUID actorUuid, String upgradeKey) {
        return postWithResultBody("/v1/islands/upgrades/purchase", "{\"islandId\":\"" + islandId + "\",\"actorUuid\":\"" + actorUuid + "\",\"upgradeKey\":\"" + escape(upgradeKey) + "\"}");
    }

    @Override
    public CompletableFuture<String> listIslandMissions(UUID islandId, String kind) {
        return post("/v1/islands/missions", "{\"islandId\":\"" + islandId + "\",\"kind\":\"" + escape(kind) + "\"}");
    }

    @Override
    public CompletableFuture<String> completeIslandMission(UUID islandId, UUID actorUuid, String missionKey) {
        return completeIslandMission(islandId, actorUuid, missionKey, "MISSION");
    }

    @Override
    public CompletableFuture<String> completeIslandMission(UUID islandId, UUID actorUuid, String missionKey, String kind) {
        return postWithResultBody("/v1/islands/missions/complete", "{\"islandId\":\"" + islandId + "\",\"actorUuid\":\"" + actorUuid + "\",\"missionKey\":\"" + escape(missionKey) + "\",\"kind\":\"" + escape(kind) + "\"}");
    }

    @Override
    public CompletableFuture<String> progressIslandMission(UUID islandId, UUID actorUuid, String missionKey, String kind, long amount) {
        return postWithResultBody("/v1/islands/missions/progress", "{\"islandId\":\"" + islandId + "\",\"actorUuid\":\"" + actorUuid + "\",\"missionKey\":\"" + escape(missionKey) + "\",\"kind\":\"" + escape(kind) + "\",\"amount\":" + Math.max(0L, amount) + "}");
    }

    @Override
    public CompletableFuture<String> listIslandLimits(UUID islandId) {
        return post("/v1/islands/limits", "{\"islandId\":\"" + islandId + "\"}");
    }

    @Override
    public CompletableFuture<String> setIslandLimit(UUID islandId, UUID actorUuid, String limitKey, long value) {
        return post("/v1/islands/limits/set", "{\"islandId\":\"" + islandId + "\",\"actorUuid\":\"" + actorUuid + "\",\"limitKey\":\"" + escape(limitKey) + "\",\"value\":" + value + "}");
    }

    @Override
    public CompletableFuture<String> sendIslandChat(UUID islandId, UUID actorUuid, String channel, String message) {
        return post("/v1/islands/chat", "{\"islandId\":\"" + islandId + "\",\"actorUuid\":\"" + actorUuid + "\",\"channel\":\"" + escape(channel) + "\",\"message\":\"" + escape(message) + "\"}");
    }

    @Override
    public CompletableFuture<String> listIslandSnapshots(UUID islandId, int limit) {
        return post("/v1/islands/snapshots", "{\"islandId\":\"" + islandId + "\",\"limit\":" + limit + "}");
    }

    @Override
    public CompletableFuture<String> recordIslandSnapshot(UUID islandId, long snapshotNo, String storagePath, String reason, String checksum, long sizeBytes, String nodeId) {
        return postWithResultBody("/v1/islands/snapshots/record", "{\"islandId\":\"" + islandId + "\",\"snapshotNo\":" + snapshotNo + ",\"storagePath\":\"" + escape(storagePath) + "\",\"reason\":\"" + escape(reason) + "\",\"checksum\":\"" + escape(checksum) + "\",\"sizeBytes\":" + sizeBytes + ",\"nodeId\":\"" + escape(nodeId) + "\"}");
    }

    @Override
    public CompletableFuture<String> requestIslandSaveResult(UUID islandId, String reason) {
        return postWithResultBody("/v1/admin/islands/save", "{\"islandId\":\"" + islandId + "\",\"reason\":\"" + escape(reason) + "\"}");
    }

    @Override
    public CompletableFuture<Void> requestIslandSnapshot(UUID islandId, String reason) {
        return requestIslandSnapshotResult(islandId, reason).thenApply(_body -> null);
    }

    @Override
    public CompletableFuture<String> requestIslandSnapshotResult(UUID islandId, String reason) {
        return postWithResultBody("/v1/admin/islands/snapshot", "{\"islandId\":\"" + islandId + "\",\"reason\":\"" + escape(reason) + "\"}");
    }

    @Override
    public CompletableFuture<Void> restoreIslandSnapshot(UUID islandId, long snapshotNo) {
        return restoreIslandSnapshotResult(islandId, snapshotNo).thenApply(_body -> null);
    }

    @Override
    public CompletableFuture<String> restoreIslandSnapshotResult(UUID islandId, long snapshotNo) {
        return postWithResultBody("/v1/admin/islands/restore", "{\"islandId\":\"" + islandId + "\",\"snapshotNo\":" + snapshotNo + "}");
    }

    @Override
    public CompletableFuture<String> rollbackIslandSnapshotResult(UUID islandId, long snapshotNo) {
        return postWithResultBody("/v1/admin/islands/rollback", "{\"islandId\":\"" + islandId + "\",\"snapshotNo\":" + snapshotNo + "}");
    }

    @Override
    public CompletableFuture<String> listIslandLogs(UUID islandId, int limit) {
        return post("/v1/islands/logs", "{\"islandId\":\"" + islandId + "\",\"limit\":" + limit + "}");
    }

    @Override
    public CompletableFuture<String> islandBank(UUID islandId) {
        return post("/v1/islands/bank", "{\"islandId\":\"" + islandId + "\"}");
    }

    @Override
    public CompletableFuture<String> depositIslandBank(UUID islandId, UUID actorUuid, String amount) {
        return postWithResultBody("/v1/islands/bank/deposit", "{\"islandId\":\"" + islandId + "\",\"actorUuid\":\"" + actorUuid + "\",\"amount\":\"" + escape(amount) + "\"}");
    }

    @Override
    public CompletableFuture<String> withdrawIslandBank(UUID islandId, UUID actorUuid, String amount) {
        return postWithResultBody("/v1/islands/bank/withdraw", "{\"islandId\":\"" + islandId + "\",\"actorUuid\":\"" + actorUuid + "\",\"amount\":\"" + escape(amount) + "\"}");
    }

    @Override
    public CompletableFuture<RouteTicket> createHomeTicket(UUID playerUuid) {
        return createHomeTicket(playerUuid, "default");
    }

    @Override
    public CompletableFuture<RouteTicket> createHomeTicket(UUID playerUuid, String homeName) {
        return postWithResultBody("/v1/routes/home", "{\"playerUuid\":\"" + playerUuid + "\",\"homeName\":\"" + escape(homeName) + "\"}").thenApply(JdkCoreApiClient::parseRouteTicketResult);
    }

    @Override
    public CompletableFuture<RouteTicket> createVisitTicket(UUID visitorUuid, UUID targetIslandId) {
        return postWithResultBody("/v1/routes/visit", "{\"playerUuid\":\"" + visitorUuid + "\",\"islandId\":\"" + targetIslandId + "\"}").thenApply(JdkCoreApiClient::parseRouteTicketResult);
    }

    @Override
    public CompletableFuture<RouteTicket> createVisitTicket(UUID visitorUuid, String islandName) {
        return postWithResultBody("/v1/routes/visit", "{\"playerUuid\":\"" + visitorUuid + "\",\"islandName\":\"" + escape(islandName) + "\"}").thenApply(JdkCoreApiClient::parseRouteTicketResult);
    }

    @Override
    public CompletableFuture<RouteTicket> createVisitTicketForOwner(UUID visitorUuid, UUID ownerUuid) {
        return postWithResultBody("/v1/routes/visit", "{\"playerUuid\":\"" + visitorUuid + "\",\"ownerUuid\":\"" + ownerUuid + "\"}").thenApply(JdkCoreApiClient::parseRouteTicketResult);
    }

    @Override
    public CompletableFuture<RouteTicket> createRandomVisitTicket(UUID visitorUuid) {
        return postWithResultBody("/v1/routes/random", "{\"playerUuid\":\"" + visitorUuid + "\"}").thenApply(JdkCoreApiClient::parseRouteTicketResult);
    }

    @Override
    public CompletableFuture<RouteTicket> createWarpTicket(UUID playerUuid, UUID islandId, String warpName) {
        return postWithResultBody("/v1/routes/warp", "{\"playerUuid\":\"" + playerUuid + "\",\"islandId\":\"" + islandId + "\",\"warpName\":\"" + escape(warpName) + "\"}").thenApply(JdkCoreApiClient::parseRouteTicketResult);
    }

    @Override
    public CompletableFuture<RouteTicket> createMigrationReturnTicket(UUID playerUuid, UUID islandId, String targetNode, double localX, double localY, double localZ, float yaw, float pitch) {
        return postWithResultBody("/v1/routes/migration-return", "{\"playerUuid\":\"" + playerUuid + "\",\"islandId\":\"" + islandId + "\",\"targetNode\":\"" + escape(targetNode) + "\",\"localX\":" + localX + ",\"localY\":" + localY + ",\"localZ\":" + localZ + ",\"yaw\":" + yaw + ",\"pitch\":" + pitch + "}").thenApply(JdkCoreApiClient::parseRouteTicketResult);
    }

    @Override
    public CompletableFuture<Void> publishRouteSession(RouteTicket ticket) {
        return publishRouteSessionResult(ticket).thenApply(_body -> null);
    }

    @Override
    public CompletableFuture<String> publishRouteSessionResult(RouteTicket ticket) {
        String targetServerName = ticket.payload().getOrDefault("targetServerName", ticket.targetNode());
        return postWithResultBody("/v1/routes/session", "{\"playerUuid\":\"" + ticket.playerUuid() + "\",\"ticketId\":\"" + ticket.ticketId() + "\",\"targetNode\":\"" + escape(ticket.targetNode()) + "\",\"targetServerName\":\"" + escape(targetServerName) + "\",\"nonce\":\"" + escape(ticket.nonce()) + "\",\"expiresAt\":\"" + ticket.expiresAt() + "\"}");
    }

    @Override
    public CompletableFuture<Optional<PlayerRouteSession>> findRouteSession(UUID playerUuid, String nodeId) {
        return post("/v1/routes/session/find", "{\"playerUuid\":\"" + playerUuid + "\",\"nodeId\":\"" + escape(nodeId) + "\"}").thenApply(body -> body.isBlank() ? Optional.empty() : Optional.of(RouteSessionJson.parse(body)));
    }

    @Override
    public CompletableFuture<Optional<PlayerRouteSession>> findAnyRouteSession(UUID playerUuid) {
        return post("/v1/routes/session/find-any", "{\"playerUuid\":\"" + playerUuid + "\"}").thenApply(body -> body.isBlank() ? Optional.empty() : Optional.of(RouteSessionJson.parse(body)));
    }

    @Override
    public CompletableFuture<Optional<PlayerRouteSession>> consumeRouteSession(UUID playerUuid, String nodeId) {
        return consumeRouteSession(playerUuid, nodeId, true);
    }

    @Override
    public CompletableFuture<Optional<PlayerRouteSession>> consumeRouteSession(UUID playerUuid, String nodeId, boolean reportMissing) {
        return post("/v1/routes/session/consume", "{\"playerUuid\":\"" + playerUuid + "\",\"nodeId\":\"" + escape(nodeId) + "\",\"reportMissing\":" + reportMissing + "}").thenApply(body -> body.isBlank() ? Optional.empty() : Optional.of(RouteSessionJson.parse(body)));
    }

    @Override
    public CompletableFuture<Optional<RouteTicket>> routeTicketStatus(UUID ticketId, UUID playerUuid, String nonce) {
        return post("/v1/routes/ticket-status", "{\"ticketId\":\"" + ticketId + "\",\"playerUuid\":\"" + playerUuid + "\",\"nonce\":\"" + escape(nonce) + "\"}").thenApply(body -> body.isBlank() ? Optional.empty() : Optional.ofNullable(RouteTicketJson.parse(body)));
    }

    @Override
    public CompletableFuture<Optional<RouteTicket>> consumeTicket(UUID ticketId, UUID playerUuid, String nodeId, String nonce) {
        return post("/v1/routes/consume", "{\"ticketId\":\"" + ticketId + "\",\"playerUuid\":\"" + playerUuid + "\",\"nodeId\":\"" + escape(nodeId) + "\",\"nonce\":\"" + escape(nonce) + "\"}").thenApply(body -> body.isBlank() ? Optional.empty() : Optional.ofNullable(RouteTicketJson.parse(body)));
    }

    @Override
    public CompletableFuture<String> listNodes() {
        return postWithResultBody("/v1/admin/nodes/list", "{}");
    }

    @Override
    public CompletableFuture<String> nodeInfo(String nodeId) {
        return postWithResultBody("/v1/admin/nodes/info", "{\"nodeId\":\"" + escape(nodeId) + "\"}");
    }

    @Override
    public CompletableFuture<String> nodeIslands(String nodeId, int limit) {
        return postWithResultBody("/v1/admin/nodes/islands", "{\"nodeId\":\"" + escape(nodeId) + "\",\"limit\":" + Math.max(1, Math.min(limit, 200)) + "}");
    }

    @Override
    public CompletableFuture<String> drainNode(String nodeId) {
        return drainNodeResult(nodeId);
    }

    @Override
    public CompletableFuture<String> drainNodeResult(String nodeId) {
        return postWithResultBody("/v1/admin/nodes/drain", "{\"nodeId\":\"" + escape(nodeId) + "\"}");
    }

    @Override
    public CompletableFuture<String> undrainNode(String nodeId) {
        return undrainNodeResult(nodeId);
    }

    @Override
    public CompletableFuture<String> undrainNodeResult(String nodeId) {
        return postWithResultBody("/v1/admin/nodes/undrain", "{\"nodeId\":\"" + escape(nodeId) + "\"}");
    }

    @Override
    public CompletableFuture<String> sweepNode(String nodeId) {
        return sweepNodeResult(nodeId);
    }

    @Override
    public CompletableFuture<String> sweepNodeResult(String nodeId) {
        return postWithResultBody("/v1/admin/nodes/sweep", "{\"nodeId\":\"" + escape(nodeId) + "\"}");
    }

    @Override
    public CompletableFuture<String> kickAllNode(String nodeId, String reason) {
        return kickAllNodeResult(nodeId, reason);
    }

    @Override
    public CompletableFuture<String> kickAllNodeResult(String nodeId, String reason) {
        return postWithResultBody("/v1/admin/nodes/kickall", "{\"nodeId\":\"" + escape(nodeId) + "\",\"reason\":\"" + escape(reason) + "\"}");
    }

    @Override
    public CompletableFuture<String> shutdownNodeSafely(String nodeId, String reason) {
        return shutdownNodeSafelyResult(nodeId, reason);
    }

    @Override
    public CompletableFuture<String> shutdownNodeSafelyResult(String nodeId, String reason) {
        return postWithResultBody("/v1/admin/nodes/shutdown-safe", "{\"nodeId\":\"" + escape(nodeId) + "\",\"reason\":\"" + escape(reason) + "\"}");
    }

    @Override
    public CompletableFuture<String> activateIsland(UUID islandId) {
        return activateIslandResult(islandId);
    }

    @Override
    public CompletableFuture<String> activateIslandResult(UUID islandId) {
        return postWithResultBody("/v1/admin/islands/activate", "{\"islandId\":\"" + islandId + "\"}");
    }

    @Override
    public CompletableFuture<String> deactivateIsland(UUID islandId) {
        return deactivateIslandResult(islandId);
    }

    @Override
    public CompletableFuture<String> deactivateIslandResult(UUID islandId) {
        return postWithResultBody("/v1/admin/islands/deactivate", "{\"islandId\":\"" + islandId + "\"}");
    }

    @Override
    public CompletableFuture<String> migrateIsland(UUID islandId, String targetNode) {
        return migrateIslandResult(islandId, targetNode);
    }

    @Override
    public CompletableFuture<String> migrateIslandResult(UUID islandId, String targetNode) {
        return postWithResultBody("/v1/admin/islands/migrate", "{\"islandId\":\"" + islandId + "\",\"targetNode\":\"" + escape(targetNode) + "\"}");
    }

    @Override
    public CompletableFuture<String> quarantineIsland(UUID islandId, String reason) {
        return quarantineIslandResult(islandId, reason);
    }

    @Override
    public CompletableFuture<String> quarantineIslandResult(UUID islandId, String reason) {
        return postWithResultBody("/v1/admin/islands/" + islandId + "/quarantine", "{\"reason\":\"" + escape(reason) + "\"}");
    }

    @Override
    public CompletableFuture<String> adminIslandInfo(UUID lookupUuid) {
        return postWithResultBody("/v1/admin/islands/info", "{\"lookupUuid\":\"" + lookupUuid + "\"}");
    }

    @Override
    public CompletableFuture<String> adminIslandWhere(UUID islandId) {
        return postWithResultBody("/v1/admin/islands/where", "{\"islandId\":\"" + islandId + "\"}");
    }

    @Override
    public CompletableFuture<RouteTicket> adminIslandTeleport(UUID playerUuid, UUID islandId) {
        return postWithResultBody("/v1/admin/islands/tp", "{\"playerUuid\":\"" + playerUuid + "\",\"islandId\":\"" + islandId + "\"}").thenApply(JdkCoreApiClient::parseRouteTicketResult);
    }

    @Override
    public CompletableFuture<String> adminDeleteIsland(UUID islandId) {
        return adminDeleteIslandResult(islandId);
    }

    @Override
    public CompletableFuture<String> adminDeleteIslandResult(UUID islandId) {
        return postWithResultBody("/v1/admin/islands/" + islandId + "/delete", "{}");
    }

    @Override
    public CompletableFuture<String> repairIsland(UUID islandId, String reason) {
        return repairIslandResult(islandId, reason);
    }

    @Override
    public CompletableFuture<String> repairIslandResult(UUID islandId, String reason) {
        return postWithResultBody("/v1/admin/islands/" + islandId + "/repair", "{\"reason\":\"" + escape(reason) + "\"}");
    }

    @Override
    public CompletableFuture<String> debugRoutes(UUID playerUuid) {
        return postWithResultBody("/v1/admin/routes/debug", "{\"playerUuid\":\"" + playerUuid + "\"}");
    }

    @Override
    public CompletableFuture<String> routeTicket(UUID ticketId) {
        return postWithResultBody("/v1/admin/routes/ticket", "{\"ticketId\":\"" + ticketId + "\"}");
    }

    @Override
    public CompletableFuture<String> routeTicketForPlayer(UUID playerUuid) {
        return postWithResultBody("/v1/admin/routes/ticket", "{\"playerUuid\":\"" + playerUuid + "\"}");
    }

    @Override
    public CompletableFuture<String> clearRoute(UUID playerUuid, UUID ticketId) {
        return clearRouteResult(playerUuid, ticketId);
    }

    @Override
    public CompletableFuture<String> clearRouteResult(UUID playerUuid, UUID ticketId) {
        return clearRouteResult(playerUuid, ticketId, "MANUAL_CLEAR");
    }

    @Override
    public CompletableFuture<String> clearRoute(UUID playerUuid, UUID ticketId, String reason) {
        return clearRouteResult(playerUuid, ticketId, reason);
    }

    @Override
    public CompletableFuture<String> clearRouteResult(UUID playerUuid, UUID ticketId, String reason) {
        return postWithResultBody("/v1/admin/routes/clear", "{\"playerUuid\":\"" + playerUuid + "\",\"ticketId\":\"" + ticketId + "\",\"reason\":\"" + escape(reason == null || reason.isBlank() ? "MANUAL_CLEAR" : reason) + "\"}");
    }

    @Override
    public CompletableFuture<String> listEvents() {
        return postWithResultBody("/v1/events", "{}");
    }

    @Override
    public CompletableFuture<String> listEvents(int limit) {
        return postWithResultBody("/v1/events", "{\"limit\":" + Math.max(1, Math.min(limit, 4096)) + "}");
    }

    @Override
    public CompletableFuture<String> listEventsSince(long sinceSeq, int limit) {
        return postWithResultBody("/v1/events", "{\"sinceSeq\":" + Math.max(0L, sinceSeq) + ",\"limit\":" + Math.max(1, Math.min(limit, 4096)) + "}");
    }

    @Override
    public CompletableFuture<String> listAuditLogs() {
        return listAuditLogs(100);
    }

    @Override
    public CompletableFuture<String> listAuditLogs(int limit) {
        return postWithResultBody("/v1/admin/audit/list", "{\"limit\":" + Math.max(1, Math.min(limit, 500)) + "}");
    }

    @Override
    public CompletableFuture<String> metrics() {
        return postWithResultBody("/metrics", "{}");
    }

    @Override
    public CompletableFuture<String> coreConfig() {
        return postWithResultBody("/v1/admin/config", "{}");
    }

    @Override
    public CompletableFuture<String> storageStatus() {
        return postWithResultBody("/v1/admin/storage", "{}");
    }

    @Override
    public CompletableFuture<String> clearCache() {
        return clearCacheResult();
    }

    @Override
    public CompletableFuture<String> clearCacheResult() {
        return postWithResultBody("/v1/admin/cache/clear", "{}");
    }

    @Override
    public CompletableFuture<String> reload() {
        return reloadResult();
    }

    @Override
    public CompletableFuture<String> reloadResult() {
        return postWithResultBody("/v1/admin/reload", "{}");
    }

    @Override
    public CompletableFuture<String> addonStateSummary() {
        return post("/v1/admin/addons/state/summary", "{}");
    }

    @Override
    public CompletableFuture<String> addonState(String addonId) {
        if (blank(addonId)) {
            return invalidAddonState("Addon id is required");
        }
        return post("/v1/addons/state", "{\"addonId\":\"" + escape(addonId) + "\"}");
    }

    @Override
    public CompletableFuture<String> putAddonState(String addonId, String key, String value) {
        if (blank(addonId) || blank(key)) {
            return invalidAddonState("Addon id and key are required");
        }
        return postWithResultBody("/v1/addons/state/set", "{\"addonId\":\"" + escape(addonId) + "\",\"key\":\"" + escape(key) + "\",\"value\":\"" + escape(value) + "\"}");
    }

    @Override
    public CompletableFuture<String> putAddonState(String addonId, Map<String, String> values) {
        if (blank(addonId)) {
            return invalidAddonState("Addon id is required");
        }
        return postWithResultBody("/v1/addons/state/bulk", "{\"addonId\":\"" + escape(addonId) + "\",\"values\":" + stringMapJson(values) + "}");
    }

    @Override
    public CompletableFuture<String> putAddonState(String addonId, Map<String, String> values, Map<String, Map<String, String>> tables) {
        if (blank(addonId)) {
            return invalidAddonState("Addon id is required");
        }
        return postWithResultBody("/v1/addons/state/bulk", "{\"addonId\":\"" + escape(addonId) + "\",\"values\":" + stringMapJson(values == null ? Map.of() : values) + ",\"tables\":" + tableMapJson(tables) + "}");
    }

    @Override
    public CompletableFuture<String> saveAddonState(String addonId, Map<String, String> values) {
        return saveAddonState(addonId, values, Map.of());
    }

    @Override
    public CompletableFuture<String> saveAddonState(String addonId, Map<String, String> values, Map<String, Map<String, String>> tables) {
        if (blank(addonId)) {
            return invalidAddonState("Addon id is required");
        }
        return postWithResultBody("/v1/addons/state/save", "{\"addonId\":\"" + escape(addonId) + "\",\"values\":" + stringMapJson(values == null ? Map.of() : values) + ",\"tables\":" + tableMapJson(tables) + "}");
    }

    @Override
    public CompletableFuture<String> bulkSaveAddonState(String addonId, Map<String, String> values, Map<String, Map<String, String>> tables) {
        if (blank(addonId)) {
            return invalidAddonState("Addon id is required");
        }
        return postWithResultBody("/v1/addons/state/table/key-value/bulk-save", "{\"addonId\":\"" + escape(addonId) + "\",\"values\":" + stringMapJson(values == null ? Map.of() : values) + ",\"tables\":" + tableMapJson(tables) + "}");
    }

    @Override
    public CompletableFuture<String> putAddonTableState(String addonId, String table, Map<String, String> values) {
        if (blank(addonId) || blank(table)) {
            return invalidAddonState("Addon id and table are required");
        }
        return postWithResultBody("/v1/addons/state/table/bulk", "{\"addonId\":\"" + escape(addonId) + "\",\"table\":\"" + escape(table) + "\",\"values\":" + stringMapJson(values) + "}");
    }

    @Override
    public CompletableFuture<String> saveAddonTableState(String addonId, String table, Map<String, String> values) {
        return putAddonTableState(addonId, table, values);
    }

    @Override
    public CompletableFuture<String> replaceAddonTableState(String addonId, String table, Map<String, String> values) {
        if (blank(addonId) || blank(table)) {
            return invalidAddonState("Addon id and table are required");
        }
        return postWithResultBody("/v1/addons/state/table/replace", "{\"addonId\":\"" + escape(addonId) + "\",\"table\":\"" + escape(table) + "\",\"values\":" + stringMapJson(values) + "}");
    }

    @Override
    public CompletableFuture<String> clearAddonTableState(String addonId, String table) {
        if (blank(addonId) || blank(table)) {
            return invalidAddonState("Addon id and table are required");
        }
        return postWithResultBody("/v1/addons/state/table/clear", "{\"addonId\":\"" + escape(addonId) + "\",\"table\":\"" + escape(table) + "\"}");
    }

    @Override
    public CompletableFuture<String> removeAddonState(String addonId, String key) {
        if (blank(addonId) || blank(key)) {
            return invalidAddonState("Addon id and key are required");
        }
        return postWithResultBody("/v1/addons/state/remove", "{\"addonId\":\"" + escape(addonId) + "\",\"key\":\"" + escape(key) + "\"}");
    }

    @Override
    public CompletableFuture<String> clearAddonState(String addonId) {
        if (blank(addonId)) {
            return invalidAddonState("Addon id is required");
        }
        return postWithResultBody("/v1/addons/state/clear", "{\"addonId\":\"" + escape(addonId) + "\"}");
    }

    @Override
    public CompletableFuture<String> addonIslandState(String addonId, UUID islandId) {
        if (blank(addonId) || missingIslandId(islandId)) {
            return invalidAddonState("Addon id and island id are required");
        }
        return post("/v1/addons/islands/state", "{\"addonId\":\"" + escape(addonId) + "\",\"islandId\":\"" + islandId + "\"}");
    }

    @Override
    public CompletableFuture<String> putAddonIslandState(String addonId, UUID islandId, String key, String value) {
        if (blank(addonId) || missingIslandId(islandId) || blank(key)) {
            return invalidAddonState("Addon id, island id, and key are required");
        }
        return postWithResultBody("/v1/addons/islands/state/set", "{\"addonId\":\"" + escape(addonId) + "\",\"islandId\":\"" + islandId + "\",\"key\":\"" + escape(key) + "\",\"value\":\"" + escape(value) + "\"}");
    }

    @Override
    public CompletableFuture<String> putAddonIslandState(String addonId, UUID islandId, Map<String, String> values) {
        if (blank(addonId) || missingIslandId(islandId)) {
            return invalidAddonState("Addon id and island id are required");
        }
        return postWithResultBody("/v1/addons/islands/state/bulk", "{\"addonId\":\"" + escape(addonId) + "\",\"islandId\":\"" + islandId + "\",\"values\":" + stringMapJson(values) + "}");
    }

    @Override
    public CompletableFuture<String> putAddonIslandState(String addonId, UUID islandId, Map<String, String> values, Map<String, Map<String, String>> tables) {
        if (blank(addonId) || missingIslandId(islandId)) {
            return invalidAddonState("Addon id and island id are required");
        }
        return postWithResultBody("/v1/addons/islands/state/bulk", "{\"addonId\":\"" + escape(addonId) + "\",\"islandId\":\"" + islandId + "\",\"values\":" + stringMapJson(values == null ? Map.of() : values) + ",\"tables\":" + tableMapJson(tables) + "}");
    }

    @Override
    public CompletableFuture<String> saveAddonIslandState(String addonId, UUID islandId, Map<String, String> values) {
        return saveAddonIslandState(addonId, islandId, values, Map.of());
    }

    @Override
    public CompletableFuture<String> saveAddonIslandState(String addonId, UUID islandId, Map<String, String> values, Map<String, Map<String, String>> tables) {
        if (blank(addonId) || missingIslandId(islandId)) {
            return invalidAddonState("Addon id and island id are required");
        }
        return postWithResultBody("/v1/addons/islands/state/save", "{\"addonId\":\"" + escape(addonId) + "\",\"islandId\":\"" + islandId + "\",\"values\":" + stringMapJson(values == null ? Map.of() : values) + ",\"tables\":" + tableMapJson(tables) + "}");
    }

    @Override
    public CompletableFuture<String> bulkSaveAddonIslandState(String addonId, UUID islandId, Map<String, String> values, Map<String, Map<String, String>> tables) {
        if (blank(addonId) || missingIslandId(islandId)) {
            return invalidAddonState("Addon id and island id are required");
        }
        return postWithResultBody("/v1/addons/islands/state/table/key-value/bulk-save", "{\"addonId\":\"" + escape(addonId) + "\",\"islandId\":\"" + islandId + "\",\"values\":" + stringMapJson(values == null ? Map.of() : values) + ",\"tables\":" + tableMapJson(tables) + "}");
    }

    @Override
    public CompletableFuture<String> putAddonIslandTableState(String addonId, UUID islandId, String table, Map<String, String> values) {
        if (blank(addonId) || missingIslandId(islandId) || blank(table)) {
            return invalidAddonState("Addon id, island id, and table are required");
        }
        return postWithResultBody("/v1/addons/islands/state/table/bulk", "{\"addonId\":\"" + escape(addonId) + "\",\"islandId\":\"" + islandId + "\",\"table\":\"" + escape(table) + "\",\"values\":" + stringMapJson(values) + "}");
    }

    @Override
    public CompletableFuture<String> saveAddonIslandTableState(String addonId, UUID islandId, String table, Map<String, String> values) {
        return putAddonIslandTableState(addonId, islandId, table, values);
    }

    @Override
    public CompletableFuture<String> replaceAddonIslandTableState(String addonId, UUID islandId, String table, Map<String, String> values) {
        if (blank(addonId) || missingIslandId(islandId) || blank(table)) {
            return invalidAddonState("Addon id, island id, and table are required");
        }
        return postWithResultBody("/v1/addons/islands/state/table/replace", "{\"addonId\":\"" + escape(addonId) + "\",\"islandId\":\"" + islandId + "\",\"table\":\"" + escape(table) + "\",\"values\":" + stringMapJson(values) + "}");
    }

    @Override
    public CompletableFuture<String> clearAddonIslandTableState(String addonId, UUID islandId, String table) {
        if (blank(addonId) || missingIslandId(islandId) || blank(table)) {
            return invalidAddonState("Addon id, island id, and table are required");
        }
        return postWithResultBody("/v1/addons/islands/state/table/clear", "{\"addonId\":\"" + escape(addonId) + "\",\"islandId\":\"" + islandId + "\",\"table\":\"" + escape(table) + "\"}");
    }

    @Override
    public CompletableFuture<String> removeAddonIslandState(String addonId, UUID islandId, String key) {
        if (blank(addonId) || missingIslandId(islandId) || blank(key)) {
            return invalidAddonState("Addon id, island id, and key are required");
        }
        return postWithResultBody("/v1/addons/islands/state/remove", "{\"addonId\":\"" + escape(addonId) + "\",\"islandId\":\"" + islandId + "\",\"key\":\"" + escape(key) + "\"}");
    }

    @Override
    public CompletableFuture<String> clearAddonIslandState(String addonId, UUID islandId) {
        if (blank(addonId) || missingIslandId(islandId)) {
            return invalidAddonState("Addon id and island id are required");
        }
        return postWithResultBody("/v1/addons/islands/state/clear", "{\"addonId\":\"" + escape(addonId) + "\",\"islandId\":\"" + islandId + "\"}");
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean missingIslandId(UUID islandId) {
        return islandId == null || islandId.equals(new UUID(0L, 0L));
    }

    private static CompletableFuture<String> invalidAddonState(String message) {
        return CompletableFuture.completedFuture("{\"code\":\"INVALID_ADDON_STATE\",\"message\":\"" + escape(message) + "\"}");
    }

    private static String stringMapJson(Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return "{}";
        }
        return values.entrySet().stream()
                .filter(entry -> entry.getKey() != null && !entry.getKey().isBlank() && entry.getValue() != null)
                .map(entry -> "\"" + escape(entry.getKey()) + "\":\"" + escape(entry.getValue()) + "\"")
                .collect(Collectors.joining(",", "{", "}"));
    }

    @Override
    public CompletableFuture<String> migrateSuperiorSkyblock2(String action, String path) {
        String normalizedAction = action == null || action.isBlank() ? "scan" : action.toLowerCase();
        String endpoint = switch (normalizedAction) {
            case "scan" -> "scan";
            case "dryrun", "dry-run" -> "dryrun";
            case "extract", "extract-worlds", "world-extract" -> "extract";
            case "import" -> "import";
            case "verify" -> "verify";
            case "rollback" -> "rollback";
            default -> "";
        };
        if (endpoint.isBlank()) {
            return CompletableFuture.completedFuture("{\"code\":\"INVALID_MIGRATION_ACTION\",\"message\":\"Unknown SuperiorSkyblock2 migration action: " + escape(normalizedAction) + "\"}");
        }
        String value = path == null ? "" : path;
        return postWithResultBody("/v1/admin/migrations/superiorskyblock2/" + endpoint, "{\"path\":\"" + escape(value) + "\",\"approval\":\"" + escape(value) + "\"}");
    }

    @Override
    public CompletableFuture<String> playerInfo(UUID playerUuid) {
        return postWithResultBody("/v1/admin/players/info", "{\"playerUuid\":\"" + playerUuid + "\"}");
    }

    @Override
    public CompletableFuture<String> playerInfoByName(String lastName) {
        return post("/v1/players/info", "{\"lastName\":\"" + escape(lastName) + "\"}");
    }

    @Override
    public CompletableFuture<String> touchPlayerProfile(UUID playerUuid, String lastName) {
        return postWithResultBody("/v1/players/touch", "{\"playerUuid\":\"" + playerUuid + "\",\"lastName\":\"" + escape(lastName) + "\"}");
    }

    @Override
    public CompletableFuture<String> setPlayerIsland(UUID playerUuid, UUID islandId) {
        return postWithResultBody("/v1/admin/players/setisland", "{\"playerUuid\":\"" + playerUuid + "\",\"islandId\":\"" + islandId + "\"}");
    }

    @Override
    public CompletableFuture<String> clearPlayerIsland(UUID playerUuid) {
        return postWithResultBody("/v1/admin/players/clearisland", "{\"playerUuid\":\"" + playerUuid + "\"}");
    }

    @Override
    public CompletableFuture<String> listTemplates() {
        return postWithResultBody("/v1/admin/templates/list", "{}");
    }

    @Override
    public CompletableFuture<String> upsertTemplate(String templateId, String displayName, boolean enabled, String minNodeVersion) {
        return postWithResultBody("/v1/admin/templates/upsert", "{\"templateId\":\"" + escape(templateId) + "\",\"displayName\":\"" + escape(displayName) + "\",\"enabled\":" + enabled + ",\"minNodeVersion\":\"" + escape(minNodeVersion) + "\"}");
    }

    @Override
    public CompletableFuture<String> enableTemplate(String templateId) {
        return postWithResultBody("/v1/admin/templates/enable", "{\"templateId\":\"" + escape(templateId) + "\"}");
    }

    @Override
    public CompletableFuture<String> disableTemplate(String templateId) {
        return postWithResultBody("/v1/admin/templates/disable", "{\"templateId\":\"" + escape(templateId) + "\"}");
    }

    @Override
    public CompletableFuture<List<IslandJob>> claimJobs(String nodeId, List<IslandJobType> supportedTypes, int maxJobs) {
        String types = supportedTypes.stream().map(Enum::name).collect(Collectors.joining(","));
        return postWithResultBody("/v1/jobs/claim", "{\"nodeId\":\"" + escape(nodeId) + "\",\"supportedTypes\":\"" + escape(types) + "\",\"maxJobs\":" + maxJobs + "}").thenApply(IslandJobJson::readArray);
    }

    @Override
    public CompletableFuture<String> listJobs() {
        return postWithResultBody("/v1/admin/jobs/list", "{}");
    }

    @Override
    public CompletableFuture<String> retryJob(UUID jobId) {
        return retryJobResult(jobId);
    }

    @Override
    public CompletableFuture<String> retryJobResult(UUID jobId) {
        return postWithResultBody("/v1/admin/jobs/retry", "{\"jobId\":\"" + jobId + "\"}");
    }

    @Override
    public CompletableFuture<String> cancelJob(UUID jobId) {
        return cancelJobResult(jobId);
    }

    @Override
    public CompletableFuture<String> cancelJobResult(UUID jobId) {
        return postWithResultBody("/v1/admin/jobs/cancel", "{\"jobId\":\"" + jobId + "\"}");
    }

    @Override
    public CompletableFuture<String> recoverJobs(String nodeId, long minIdleMillis, int maxJobs) {
        return recoverJobsResult(nodeId, minIdleMillis, maxJobs);
    }

    @Override
    public CompletableFuture<String> recoverJobsResult(String nodeId, long minIdleMillis, int maxJobs) {
        return postWithResultBody("/v1/admin/jobs/recover", "{\"nodeId\":\"" + escape(nodeId) + "\",\"minIdleMillis\":" + minIdleMillis + ",\"maxJobs\":" + maxJobs + "}");
    }

    @Override
    public CompletableFuture<Void> completeJob(String nodeId, UUID jobId) {
        return completeJob(nodeId, jobId, Map.of());
    }

    @Override
    public CompletableFuture<Void> completeJob(String nodeId, UUID jobId, Map<String, String> payload) {
        return completeJobResult(nodeId, jobId, payload).thenApply(_body -> null);
    }

    @Override
    public CompletableFuture<String> completeJobResult(String nodeId, UUID jobId, Map<String, String> payload) {
        return postWithResultBody("/v1/jobs/complete", "{\"nodeId\":\"" + escape(nodeId) + "\",\"jobId\":\"" + jobId + "\",\"payload\":" + mapJson(payload) + "}");
    }

    @Override
    public CompletableFuture<Void> failJob(String nodeId, UUID jobId, String errorMessage) {
        return failJobResult(nodeId, jobId, errorMessage).thenApply(_body -> null);
    }

    @Override
    public CompletableFuture<String> failJobResult(String nodeId, UUID jobId, String errorMessage) {
        return postWithResultBody("/v1/jobs/fail", "{\"nodeId\":\"" + escape(nodeId) + "\",\"jobId\":\"" + jobId + "\",\"error\":\"" + escape(errorMessage) + "\"}");
    }

    @Override
    public CompletableFuture<Void> publishHeartbeat(NodeHeartbeatRequest request) {
        return publishHeartbeatResult(request).thenApply(_body -> null);
    }

    @Override
    public CompletableFuture<String> publishHeartbeatResult(NodeHeartbeatRequest request) {
        return postWithResultBody("/v1/nodes/heartbeat", "{\"protocolVersion\":" + request.protocolVersion() + ",\"nodeId\":\"" + escape(request.nodeId()) + "\",\"pool\":\"" + escape(request.pool()) + "\",\"velocityServerName\":\"" + escape(request.velocityServerName()) + "\",\"nodeVersion\":\"" + escape(request.nodeVersion()) + "\",\"state\":\"" + request.state() + "\",\"players\":" + request.players() + ",\"softPlayerCap\":" + request.softPlayerCap() + ",\"hardPlayerCap\":" + request.hardPlayerCap() + ",\"reservedSlots\":" + request.reservedSlots() + ",\"activeIslands\":" + request.activeIslands() + ",\"maxActiveIslands\":" + request.maxActiveIslands() + ",\"mspt\":" + request.mspt() + ",\"activationQueue\":" + request.activationQueue() + ",\"maxActivationQueue\":" + request.maxActivationQueue() + ",\"chunkLoadPressure\":" + request.chunkLoadPressure() + ",\"heapUsedMb\":" + request.heapUsedMb() + ",\"heapMaxMb\":" + request.heapMaxMb() + ",\"recentFailurePenalty\":" + request.recentFailurePenalty() + ",\"storageAvailable\":" + request.storageAvailable() + ",\"supportedTemplates\":\"" + escape(request.supportedTemplates()) + "\"}");
    }

    private String mapJson(Map<String, String> payload) {
        StringBuilder builder = new StringBuilder("{");
        boolean first = true;
        if (payload == null || payload.isEmpty()) {
            return builder.append("}").toString();
        }
        for (Map.Entry<String, String> entry : payload.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append("\"").append(escape(entry.getKey())).append("\":\"").append(escape(entry.getValue())).append("\"");
        }
        return builder.append("}").toString();
    }

    private String tableMapJson(Map<String, Map<String, String>> tables) {
        StringBuilder builder = new StringBuilder("{");
        boolean first = true;
        if (tables != null) {
            for (Map.Entry<String, Map<String, String>> entry : tables.entrySet()) {
                if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null) {
                    continue;
                }
                if (!first) {
                    builder.append(',');
                }
                first = false;
                builder.append("\"").append(escape(entry.getKey())).append("\":").append(stringMapJson(entry.getValue()));
            }
        }
        return builder.append("}").toString();
    }

    private CompletableFuture<String> post(String path, String body) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(baseUri.resolve(path))
            .timeout(timeout)
            .header("Authorization", "Bearer " + authToken)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body));
        addAdminHeaders(builder, path);
        HttpRequest request = builder.build();
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenApply(response -> response.statusCode() >= 200 && response.statusCode() < 300 ? response.body() : "");
    }

    private CompletableFuture<String> get(String path) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(baseUri.resolve(path))
            .timeout(timeout)
            .header("Authorization", "Bearer " + authToken)
            .GET();
        addAdminHeaders(builder, path);
        HttpRequest request = builder.build();
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenApply(response -> response.statusCode() >= 200 && response.statusCode() < 300 ? response.body() : "");
    }

    private CompletableFuture<String> postWithResultBody(String path, String body) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(baseUri.resolve(path))
            .timeout(timeout)
            .header("Authorization", "Bearer " + authToken)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body));
        addAdminHeaders(builder, path);
        HttpRequest request = builder.build();
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenApply(response -> response.statusCode() >= 200 && response.statusCode() < 500 ? response.body() : "");
    }

    private CompletableFuture<String> deleteWithResultBody(String path) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(baseUri.resolve(path))
            .timeout(timeout)
            .header("Authorization", "Bearer " + authToken)
            .DELETE();
        addAdminHeaders(builder, path);
        HttpRequest request = builder.build();
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenApply(response -> response.statusCode() >= 200 && response.statusCode() < 500 ? response.body() : "");
    }

    private static RouteTicket parseRouteTicketResult(String body) {
        if (body == null || body.isBlank()) {
            throw new CoreApiException("ROUTE_FAILED", "Route ticket could not be created");
        }
        if (!body.contains("\"ticketId\"")) {
            throw new CoreApiException(text(body, "code", "ROUTE_FAILED"), text(body, "message", "Route ticket could not be created"));
        }
        RouteTicket ticket = RouteTicketJson.parse(body);
        if (ticket == null) {
            throw new CoreApiException("ROUTE_FAILED", "Route ticket could not be parsed");
        }
        return ticket;
    }

    private boolean adminProtected(String path) {
        return path.startsWith("/v1/admin")
            || path.equals("/v1/audit")
            || path.equals("/v1/events")
            || path.equals("/metrics")
            || path.equals("/v1/jobs")
            || path.equals("/v1/jobs/claim")
            || path.equals("/v1/jobs/complete")
            || path.equals("/v1/jobs/fail")
            || path.equals("/v1/jobs/recover");
    }

    private void addAdminHeaders(HttpRequest.Builder builder, String path) {
        if (adminToken.isBlank() || !adminProtected(path)) {
            return;
        }
        builder.header("X-CloudIslands-Admin-Token", adminToken);
        String permission = adminPermission(path);
        if (!permission.isBlank()) {
            builder.header("X-CloudIslands-Admin-Permissions", permission);
        }
    }

    private String adminPermission(String path) {
        if (path.equals("/v1/jobs") || path.equals("/v1/jobs/claim") || path.equals("/v1/jobs/complete") || path.equals("/v1/jobs/fail") || path.equals("/v1/jobs/recover")) {
            return "JOB_MANAGE";
        }
        if (path.equals("/v1/audit") || path.equals("/v1/admin/audit") || path.equals("/v1/admin/audit/list") || path.equals("/v1/events") || path.equals("/metrics")) {
            return "AUDIT_READ";
        }
        if (path.equals("/v1/admin/cache/clear") || path.equals("/v1/admin/reload")) {
            return "CACHE_CLEAR";
        }
        if (path.startsWith("/v1/admin/migrations/")) {
            return "MIGRATION_MANAGE";
        }
        if (path.startsWith("/v1/admin/players/")) {
            return "PLAYER_MANAGE";
        }
        if (path.startsWith("/v1/admin/templates/")) {
            return "TEMPLATE_MANAGE";
        }
        if (path.startsWith("/v1/admin/routes/")) {
            return "ROUTE_MANAGE";
        }
        if (path.startsWith("/v1/admin/block-values")) {
            return "ECONOMY_MANAGE";
        }
        if (path.startsWith("/v1/admin/nodes/")) {
            if (path.endsWith("/drain") || path.endsWith("/sweep")) {
                return "NODE_DRAIN";
            }
            if (path.endsWith("/undrain")) {
                return "NODE_UNDRAIN";
            }
            if (path.endsWith("/kickall")) {
                return "NODE_KICK";
            }
            if (path.endsWith("/shutdown-safe")) {
                return "NODE_SHUTDOWN";
            }
            return "AUDIT_READ";
        }
        if (path.startsWith("/v1/admin/islands/")) {
            if (path.endsWith("/activate")) {
                return "ISLAND_ACTIVATE";
            }
            if (path.endsWith("/deactivate")) {
                return "ISLAND_DEACTIVATE";
            }
            if (path.endsWith("/migrate")) {
                return "ISLAND_MIGRATE";
            }
            if (path.endsWith("/snapshot")) {
                return "ISLAND_SNAPSHOT";
            }
            if (path.endsWith("/save")) {
                return "ISLAND_SNAPSHOT";
            }
            if (path.endsWith("/restore")) {
                return "ISLAND_RESTORE";
            }
            if (path.endsWith("/rollback")) {
                return "ISLAND_RESTORE";
            }
            if (path.endsWith("/quarantine")) {
                return "ISLAND_QUARANTINE";
            }
            if (path.endsWith("/delete")) {
                return "ISLAND_DELETE";
            }
            if (path.endsWith("/repair")) {
                return "ISLAND_REPAIR";
            }
            if (path.endsWith("/tp")) {
                return "ISLAND_TELEPORT";
            }
            return "AUDIT_READ";
        }
        return path.startsWith("/v1/admin") ? "AUDIT_READ" : "";
    }

    private static String text(String json, String field, String fallback) {
        String needle = "\"" + field + "\":\"";
        int start = json.indexOf(needle);
        if (start < 0) {
            return fallback;
        }
        int valueStart = start + needle.length();
        int end = jsonStringEnd(json, valueStart);
        return end < 0 ? fallback : unescape(json.substring(valueStart, end));
    }

    private static int jsonStringEnd(String json, int start) {
        boolean escaped = false;
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (c == '\\') {
                escaped = true;
                continue;
            }
            if (c == '"') {
                return i;
            }
        }
        return -1;
    }

    private static String unescape(String value) {
        StringBuilder builder = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c != '\\' || i + 1 >= value.length()) {
                builder.append(c);
                continue;
            }
            char next = value.charAt(++i);
            switch (next) {
                case '"' -> builder.append('"');
                case '\\' -> builder.append('\\');
                case '/' -> builder.append('/');
                case 'b' -> builder.append('\b');
                case 'f' -> builder.append('\f');
                case 'n' -> builder.append('\n');
                case 'r' -> builder.append('\r');
                case 't' -> builder.append('\t');
                case 'u' -> {
                    if (i + 4 < value.length()) {
                        try {
                            builder.append((char) Integer.parseInt(value.substring(i + 1, i + 5), 16));
                            i += 4;
                        } catch (NumberFormatException ignored) {
                            builder.append("\\u");
                        }
                    } else {
                        builder.append("\\u");
                    }
                }
                default -> builder.append(next);
            }
        }
        return builder.toString();
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\' -> builder.append("\\\\");
                case '"' -> builder.append("\\\"");
                case '\b' -> builder.append("\\b");
                case '\f' -> builder.append("\\f");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> {
                    if (c < 0x20) {
                        builder.append(String.format("\\u%04x", (int) c));
                    } else {
                        builder.append(c);
                    }
                }
            }
        }
        return builder.toString();
    }

    private static String countsPayload(Map<String, Long> counts) {
        StringBuilder builder = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, Long> entry : counts.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null || entry.getValue() <= 0L) {
                continue;
            }
            if (!first) {
                builder.append('|');
            }
            first = false;
            builder.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return builder.toString();
    }

    private static UUID uuid(String json, String field, UUID fallback) {
        try {
            return UUID.fromString(text(json, field, fallback.toString()));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private static final class RouteSessionJson {
        static PlayerRouteSession parse(String json) {
            return new PlayerRouteSession(
                uuid(json, "playerUuid", new UUID(0L, 0L)),
                uuid(json, "ticketId", new UUID(0L, 0L)),
                text(json, "targetNode", ""),
                text(json, "targetServerName", ""),
                text(json, "nonce", ""),
                Instant.parse(text(json, "expiresAt", Instant.now().toString()))
            );
        }
    }

    private static final class RouteTicketJson {
        private RouteTicketJson() {}

        static RouteTicket parseNested(String json, String field) {
            String needle = "\"" + field + "\":";
            int start = json.indexOf(needle);
            if (start < 0) {
                return null;
            }
            int objectStart = json.indexOf('{', start + needle.length());
            if (objectStart < 0) {
                return null;
            }
            int depth = 0;
            for (int i = objectStart; i < json.length(); i++) {
                char c = json.charAt(i);
                if (c == '{') {
                    depth++;
                } else if (c == '}') {
                    depth--;
                    if (depth == 0) {
                        return parse(json.substring(objectStart, i + 1));
                    }
                }
            }
            return null;
        }

        static RouteTicket parse(String json) {
            if (json == null || json.isBlank()) {
                return null;
            }
            UUID ticketId = uuid(json, "ticketId", UUID.randomUUID());
            UUID playerUuid = uuid(json, "playerUuid", new UUID(0L, 0L));
            UUID islandId = uuid(json, "islandId", new UUID(0L, 0L));
            RouteAction action = enumValue(RouteAction.class, text(json, "action", "HOME"), RouteAction.HOME);
            RouteTicketState state = enumValue(RouteTicketState.class, text(json, "state", "READY"), RouteTicketState.READY);
            String targetNode = text(json, "targetNode", "");
            String targetWorld = text(json, "targetWorld", "ci_shard_001");
            String nonce = text(json, "nonce", "");
            String serverName = text(json, "targetServerName", targetNode);
            java.util.LinkedHashMap<String, String> payload = new java.util.LinkedHashMap<>();
            payload.put("targetServerName", serverName);
            putIfPresent(payload, json, "targetType");
            putIfPresent(payload, json, "homeName");
            putIfPresent(payload, json, "warpName");
            putIfPresent(payload, json, "localX");
            putIfPresent(payload, json, "localY");
            putIfPresent(payload, json, "localZ");
            putIfPresent(payload, json, "yaw");
            putIfPresent(payload, json, "pitch");
            Instant expiresAt = Instant.parse(text(json, "expiresAt", Instant.now().plusSeconds(30).toString()));
            return new RouteTicket(ticketId, playerUuid, action, islandId, targetNode, targetWorld, state, expiresAt, nonce, Map.copyOf(payload));
        }

        private static void putIfPresent(Map<String, String> payload, String json, String field) {
            String value = text(json, field, null);
            if (value != null) {
                payload.put(field, value);
                return;
            }
            String scalar = scalar(json, field);
            if (scalar != null) {
                payload.put(field, scalar);
            }
        }

        private static String scalar(String json, String field) {
            String needle = "\"" + field + "\":";
            int start = json.indexOf(needle);
            if (start < 0) {
                return null;
            }
            int valueStart = start + needle.length();
            int end = valueStart;
            while (end < json.length() && "0123456789.-".indexOf(json.charAt(end)) >= 0) {
                end++;
            }
            return end == valueStart ? null : json.substring(valueStart, end);
        }

        private static <E extends Enum<E>> E enumValue(Class<E> type, String value, E fallback) {
            try {
                return Enum.valueOf(type, value);
            } catch (IllegalArgumentException ignored) {
                return fallback;
            }
        }
    }
}
