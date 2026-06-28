package kr.lunaf.cloudislands.coreclient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.model.IslandBankSnapshot;
import kr.lunaf.cloudislands.api.model.IslandFlag;
import kr.lunaf.cloudislands.api.model.IslandLogRecord;
import kr.lunaf.cloudislands.api.model.IslandSnapshotRecord;

public final class CoreGuiViews {
    private CoreGuiViews() {
    }

    public static CompletableFuture<IslandInfoView> islandInfo(CoreApiClient client, UUID islandId) {
        return client instanceof IslandQueryClient queries ? queries.getIsland(islandId) : client.islands().getIsland(islandId);
    }

    static IslandInfoView islandInfoView(String body) {
        return CoreIslandJson.info(body);
    }

    public static CompletableFuture<BankView> islandBank(CoreApiClient client, UUID islandId) {
        return client.bank().snapshot(islandId).thenApply(CoreGuiViews::bankView);
    }

    private static BankView bankView(IslandBankSnapshot snapshot) {
        return new BankView(
            snapshot == null ? "0" : snapshot.balance(),
            snapshot == null || snapshot.updatedAt() == null || snapshot.updatedAt().equals(java.time.Instant.EPOCH) ? "" : snapshot.updatedAt().toString()
        );
    }

    public static CompletableFuture<BiomeView> islandBiome(CoreApiClient client, UUID islandId) {
        return client.environment().biome(islandId).thenApply(CoreEnvironmentJson::biomeView);
    }

    public static CompletableFuture<Map<IslandFlag, String>> islandFlags(CoreApiClient client, UUID islandId) {
        return client.environment().flags(islandId).thenApply(kr.lunaf.cloudislands.api.model.IslandFlagsSnapshot::values);
    }

    public static CompletableFuture<List<TemplateView>> templates(CoreApiClient client) {
        return client.templates().list().thenApply(CoreTemplateJson::guiTemplates);
    }

    public static CompletableFuture<RankingData> rankings(CoreApiClient client, int limit) {
        return client.progression().rankings(limit);
    }

    public static CompletableFuture<List<MemberView>> islandMembers(CoreApiClient client, UUID islandId) {
        return client.islands().listMembers(islandId);
    }

    static List<MemberView> memberViews(String body) {
        return members(body);
    }

    public static CompletableFuture<List<InviteView>> pendingInvites(CoreApiClient client, UUID playerUuid) {
        return client.members().pendingInvites(playerUuid);
    }

    static List<InviteView> inviteViews(String body) {
        return CoreMemberJson.inviteViews(body);
    }

    static InviteView inviteView(String body) {
        return CoreMemberJson.inviteView(body);
    }

    public static CompletableFuture<List<PlayerIslandView>> playerIslands(CoreApiClient client, UUID playerUuid) {
        return client.navigation().playerIslands(playerUuid);
    }

    public static CompletableFuture<List<PublicIslandView>> publicIslands(CoreApiClient client, int limit) {
        return client.navigation().publicIslands(limit);
    }

    public static CompletableFuture<List<BanView>> islandBans(CoreApiClient client, UUID islandId) {
        return client.members().bans(islandId);
    }

    static List<BanView> banViews(String body) {
        return CoreMemberJson.banViews(body);
    }

    static PlayerProfileView playerProfile(String body) {
        return CorePlayerProfileJson.guiProfile(body);
    }

    public static PlayerProfileView playerProfile(kr.lunaf.cloudislands.coreclient.PlayerProfileView profile) {
        return CorePlayerProfileJson.guiProfile(profile);
    }

    public static CompletableFuture<List<HomeView>> islandHomes(CoreApiClient client, UUID islandId) {
        return client.homeWarps().homes(islandId);
    }

    public static CompletableFuture<List<WarpView>> islandWarps(CoreApiClient client, UUID islandId) {
        return client.homeWarps().warps(islandId);
    }

    public static CompletableFuture<List<WarpView>> publicWarps(CoreApiClient client, int limit) {
        return client.homeWarps().publicWarps(limit, "", "");
    }

    public static CompletableFuture<List<WarpView>> publicWarps(CoreApiClient client, int limit, String category, String query) {
        return client.homeWarps().publicWarps(limit, category, query);
    }

    public static CompletableFuture<List<PermissionRuleView>> islandPermissions(CoreApiClient client, UUID islandId) {
        return islandPermissionRules(client, islandId).thenApply(PermissionRulesView::rules);
    }

    public static CompletableFuture<PermissionRulesView> islandPermissionRules(CoreApiClient client, UUID islandId) {
        return client.permissionQueries().permissionRules(islandId);
    }

    public static CompletableFuture<List<RoleView>> islandRoles(CoreApiClient client, UUID islandId) {
        return client.permissionQueries().roles(islandId);
    }

    static RoleView roleView(String body) {
        return CorePermissionJson.roleView(body);
    }

    public static CompletableFuture<List<UpgradeView>> islandUpgrades(CoreApiClient client, UUID islandId) {
        return client.progression().upgrades(islandId);
    }

    public static CompletableFuture<List<MissionView>> islandMissions(CoreApiClient client, UUID islandId, String kind) {
        return client.progression().missions(islandId, kind);
    }

    public static CompletableFuture<List<LimitView>> islandLimits(CoreApiClient client, UUID islandId) {
        return client.environment().limits(islandId).thenApply(values -> values.stream()
            .map(CoreEnvironmentJson::limitView)
            .toList());
    }

    public static CompletableFuture<List<SnapshotView>> islandSnapshots(CoreApiClient client, UUID islandId, int limit) {
        return client.snapshots().records(islandId, limit).thenApply(CoreGuiViews::snapshots);
    }

    private static List<SnapshotView> snapshots(List<IslandSnapshotRecord> records) {
        return records.stream()
            .map(CoreSnapshotJson::view)
            .toList();
    }

    public static CompletableFuture<List<LogEntryView>> islandLogs(CoreApiClient client, UUID islandId, int limit) {
        return client.communication().records(islandId, limit).thenApply(CoreGuiViews::logs);
    }

    static List<LogEntryView> logViews(String body) {
        return CoreCommunicationJson.records(body).stream()
            .map(CoreCommunicationJson::view)
            .toList();
    }

    static NodeSummaryView nodeSummary(String nodeId, String body) {
        return JdkAdminNodeQueryClient.nodeSummary(nodeId, body);
    }

    private static List<MemberView> members(String body) {
        return CoreMemberJson.memberViews(body);
    }

    private static List<PlayerIslandView> playerIslands(String body) {
        return JdkNavigationQueryClient.playerIslandViews(body);
    }

    private static List<PublicIslandView> publicIslands(String body) {
        return JdkNavigationQueryClient.publicIslandViews(body);
    }

    static PermissionRulesView permissionRulesView(String body) {
        return CorePermissionJson.permissionRulesView(body);
    }

    private static List<LogEntryView> logs(List<IslandLogRecord> records) {
        return records.stream()
            .map(CoreCommunicationJson::view)
            .toList();
    }

    public record IslandInfoView(String name, String state, String islandId, long level, String worth, boolean publicAccess, boolean locked, long size, long border, String ownerUuid, String createdAt, String updatedAt) {
        public IslandInfoView(String name, String state, String islandId, long level, String worth, boolean publicAccess, boolean locked, long size, long border, String ownerUuid) {
            this(name, state, islandId, level, worth, publicAccess, locked, size, border, ownerUuid, "", "");
        }
    }

    public record BankView(String balance, String updatedAt) {
    }

    public record BiomeView(String key, String updatedBy, String updatedAt) {
        public BiomeView(String key) {
            this(key, "", "");
        }

        public BiomeView {
            key = key == null ? "" : key;
            updatedBy = updatedBy == null ? "" : updatedBy;
            updatedAt = updatedAt == null ? "" : updatedAt;
        }
    }

    public record TemplateView(String id, String displayName, boolean enabled, String minNodeVersion) {
    }

    public record RankingData(List<RankingView> levels, List<RankingView> worths, List<RankingView> reviews) {
    }

    public record RankingView(int rank, String label, String islandId, long level, String worth) {
    }

    public record MemberView(String playerUuid, String role, String joinedAt, String playerName, String lastSeenAt, String presenceState, String presenceSource, String expiresAt) {
        public MemberView(String playerUuid, String role, String joinedAt, String playerName, String lastSeenAt, String presenceState, String presenceSource) {
            this(playerUuid, role, joinedAt, playerName, lastSeenAt, presenceState, presenceSource, "");
        }
    }

    public record InviteView(String inviteId, String islandId, String inviterUuid, String targetUuid, String state, String createdAt, String expiresAt) {
        public InviteView(String inviteId, String islandId, String inviterUuid, String createdAt, String expiresAt) {
            this(inviteId, islandId, inviterUuid, "", "PENDING", createdAt, expiresAt);
        }

        public InviteView {
            inviteId = inviteId == null ? "" : inviteId;
            islandId = islandId == null ? "" : islandId;
            inviterUuid = inviterUuid == null ? "" : inviterUuid;
            targetUuid = targetUuid == null ? "" : targetUuid;
            state = state == null || state.isBlank() ? "PENDING" : state;
            createdAt = createdAt == null ? "" : createdAt;
            expiresAt = expiresAt == null ? "" : expiresAt;
        }
    }

    public record PlayerProfileView(String playerUuid, String primaryIslandId) {
    }

    public record PlayerIslandView(String islandId, String name, String state, String role, long level, String worth) {
    }

    public record PublicIslandView(String islandId, String ownerUuid, String name, long level, String worth) {
    }

    public record BanView(String bannedUuid, String actorUuid, String reason, String createdAt, String expiresAt) {
    }

    public record HomeView(String islandId, String name, double x, double y, double z, String createdBy, String createdAt) {
        public HomeView(String name, double x, double y, double z, String createdAt) {
            this("", name, x, y, z, "", createdAt);
        }

        public HomeView {
            islandId = islandId == null ? "" : islandId;
            name = name == null ? "" : name;
            createdBy = createdBy == null ? "" : createdBy;
            createdAt = createdAt == null ? "" : createdAt;
        }
    }

    public record WarpView(String islandId, String name, double x, double y, double z, boolean publicAccess, String createdBy, String createdAt, String category) {
        public WarpView(String islandId, String name, double x, double y, double z, boolean publicAccess) {
            this(islandId, name, x, y, z, publicAccess, "default");
        }

        public WarpView(String islandId, String name, double x, double y, double z, boolean publicAccess, String category) {
            this(islandId, name, x, y, z, publicAccess, "", "", category);
        }

        public WarpView {
            islandId = islandId == null ? "" : islandId;
            name = name == null ? "" : name;
            createdBy = createdBy == null ? "" : createdBy;
            createdAt = createdAt == null ? "" : createdAt;
            category = category == null ? "" : category;
        }
    }

    public record PermissionRulesView(String version, List<PermissionRuleView> rules) {
    }

    public record PermissionRuleView(String role, String permission, boolean allowed, String version) {
        public PermissionRuleView {
            version = version == null ? "" : version;
        }
    }

    public record RoleView(String role, int weight, String displayName) {
    }

    public record UpgradeView(String key, String type, int level, String generatorKey) {
        public UpgradeView(String key, String type, int level) {
            this(key, type, level, "");
        }

        public UpgradeView {
            key = key == null ? "" : key;
            type = type == null ? "" : type;
            generatorKey = generatorKey == null ? "" : generatorKey;
        }
    }

    public record MissionView(String key, String title, long progress, long goal, boolean completed, String reward, String category, String description, String triggerType, String targetKey, String rewardType, boolean repeatable, boolean dailyReset) {
        public MissionView(String key, String title, long progress, long goal, boolean completed, String reward) {
            this(key, title, progress, goal, completed, reward, "", "", "", "", "", false, false);
        }

        public MissionView {
            key = key == null ? "" : key;
            title = title == null ? "" : title;
            reward = reward == null ? "" : reward;
            category = category == null ? "" : category;
            description = description == null ? "" : description;
            triggerType = triggerType == null ? "" : triggerType;
            targetKey = targetKey == null ? "" : targetKey;
            rewardType = rewardType == null ? "" : rewardType;
            progress = Math.max(0L, progress);
            goal = Math.max(1L, goal);
        }
    }

    public record LimitView(String key, long value, String updatedAt) {
    }

    public record SnapshotView(long snapshotNo, String reason, long sizeBytes, String createdAt, String checksum, String storagePath) {
        public SnapshotView(long snapshotNo, String reason, long sizeBytes, String createdAt) {
            this(snapshotNo, reason, sizeBytes, createdAt, "", "");
        }

        public SnapshotView(long snapshotNo, String reason, long sizeBytes, String createdAt, String checksum) {
            this(snapshotNo, reason, sizeBytes, createdAt, checksum, "");
        }

        public SnapshotView {
            reason = reason == null ? "" : reason;
            createdAt = createdAt == null ? "" : createdAt;
            checksum = checksum == null ? "" : checksum;
            storagePath = storagePath == null ? "" : storagePath;
        }
    }

    public record LogEntryView(String actorUuid, String action, Map<String, String> payload, String createdAt) {
    }

    public record NodeSummaryView(String nodeId, String state, String pool, long players, long softPlayerCap, long hardPlayerCap, long activeIslands, long maxActiveIslands, long activationQueue, long maxActivationQueue, String mspt) {
    }
}
