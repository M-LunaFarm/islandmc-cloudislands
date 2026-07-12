package kr.lunaf.cloudislands.paper.application.view;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.model.IslandFlag;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.coreclient.CoreGuiViews;
import kr.lunaf.cloudislands.coreclient.PermissionAssignmentView;

public final class PaperGuiViews {
    private PaperGuiViews() {
    }

    public static CompletableFuture<IslandInfoView> islandInfo(CoreApiClient client, UUID islandId) {
        return CoreGuiViews.islandInfo(client, islandId).thenApply(PaperGuiViews::islandInfo);
    }

    public static CompletableFuture<BankView> islandBank(CoreApiClient client, UUID islandId) {
        return CoreGuiViews.islandBank(client, islandId).thenApply(PaperGuiViews::bank);
    }

    public static CompletableFuture<CoreGuiViews.BiomeView> islandBiome(CoreApiClient client, UUID islandId) {
        return CoreGuiViews.islandBiome(client, islandId);
    }

    public static CompletableFuture<Map<IslandFlag, String>> islandFlags(CoreApiClient client, UUID islandId) {
        return CoreGuiViews.islandFlags(client, islandId);
    }

    public static CompletableFuture<List<TemplateView>> templates(CoreApiClient client) {
        return CoreGuiViews.templates(client).thenApply(views -> views.stream().map(PaperGuiViews::template).toList());
    }

    public static CompletableFuture<RankingData> rankings(CoreApiClient client, int limit) {
        return client.progression().rankings(limit).thenApply(PaperGuiViews::rankings);
    }

    public static CompletableFuture<List<MemberView>> islandMembers(CoreApiClient client, UUID islandId) {
        return CoreGuiViews.islandMembers(client, islandId).thenApply(views -> views.stream().map(PaperGuiViews::member).toList());
    }

    public static CompletableFuture<List<InviteView>> pendingInvites(CoreApiClient client, UUID playerUuid) {
        return CoreGuiViews.pendingInvites(client, playerUuid).thenApply(views -> views.stream().map(PaperGuiViews::invite).toList());
    }

    public static CompletableFuture<List<PlayerIslandView>> playerIslands(CoreApiClient client, UUID playerUuid) {
        return CoreGuiViews.playerIslands(client, playerUuid).thenCombine(
            client.playerProfiles().profile(playerUuid).exceptionally(error -> null),
            (views, profile) -> {
                String primaryIslandId = profile == null ? "" : profile.primaryIslandId();
                return views.stream().map(view -> playerIsland(view, primaryIslandId)).toList();
            }
        );
    }

    public static CompletableFuture<List<PublicIslandView>> publicIslands(CoreApiClient client, int limit) {
        return CoreGuiViews.publicIslands(client, limit).thenApply(views -> views.stream().map(PaperGuiViews::publicIsland).toList());
    }

    public static CompletableFuture<List<PublicIslandView>> publicIslands(CoreApiClient client, int offset, int limit) {
        return CoreGuiViews.publicIslands(client, offset, limit).thenApply(views -> views.stream().map(PaperGuiViews::publicIsland).toList());
    }

    public static CompletableFuture<List<BanView>> islandBans(CoreApiClient client, UUID islandId) {
        return CoreGuiViews.islandBans(client, islandId).thenApply(views -> views.stream().map(PaperGuiViews::ban).toList());
    }

    public static CompletableFuture<List<HomeView>> islandHomes(CoreApiClient client, UUID islandId) {
        return CoreGuiViews.islandHomes(client, islandId).thenApply(views -> views.stream().map(PaperGuiViews::home).toList());
    }

    public static CompletableFuture<List<WarpView>> islandWarps(CoreApiClient client, UUID islandId) {
        return CoreGuiViews.islandWarps(client, islandId).thenApply(views -> views.stream().map(PaperGuiViews::warp).toList());
    }

    public static CompletableFuture<List<WarpView>> publicWarps(CoreApiClient client, int limit) {
        return CoreGuiViews.publicWarps(client, limit).thenApply(views -> views.stream().map(PaperGuiViews::warp).toList());
    }

    public static CompletableFuture<List<WarpView>> publicWarps(CoreApiClient client, int limit, String category, String query) {
        return CoreGuiViews.publicWarps(client, limit, category, query).thenApply(views -> views.stream().map(PaperGuiViews::warp).toList());
    }

    public static CompletableFuture<List<WarpView>> publicWarps(CoreApiClient client, int offset, int limit, String category, String query) {
        return CoreGuiViews.publicWarps(client, offset, limit, category, query).thenApply(views -> views.stream().map(PaperGuiViews::warp).toList());
    }

    public static CompletableFuture<List<PermissionRuleView>> islandPermissions(CoreApiClient client, UUID islandId) {
        return islandPermissionRules(client, islandId).thenApply(PermissionRulesView::rules);
    }

    public static CompletableFuture<PermissionRulesView> islandPermissionRules(CoreApiClient client, UUID islandId) {
        return CoreGuiViews.islandPermissionRules(client, islandId)
            .thenApply(view -> new PermissionRulesView(view.version(), view.rules().stream().map(PaperGuiViews::permissionRule).toList()));
    }

    public static CompletableFuture<List<PermissionOverrideView>> islandPermissionOverrides(CoreApiClient client, UUID islandId) {
        return client.permissionQueries().permissions(islandId)
            .thenApply(views -> views.stream()
                .filter(view -> !view.playerUuid().isBlank())
                .map(PaperGuiViews::permissionOverride)
                .toList());
    }

    public static CompletableFuture<List<RoleView>> islandRoles(CoreApiClient client, UUID islandId) {
        return CoreGuiViews.islandRoles(client, islandId).thenApply(views -> views.stream().map(PaperGuiViews::role).toList());
    }

    public static CompletableFuture<List<UpgradeView>> islandUpgrades(CoreApiClient client, UUID islandId) {
        return CoreGuiViews.islandUpgrades(client, islandId).thenApply(views -> views.stream().map(PaperGuiViews::upgrade).toList());
    }

    public static CompletableFuture<List<MissionView>> islandMissions(CoreApiClient client, UUID islandId, String kind) {
        return CoreGuiViews.islandMissions(client, islandId, kind).thenApply(views -> views.stream().map(PaperGuiViews::mission).toList());
    }

    public static CompletableFuture<List<LimitView>> islandLimits(CoreApiClient client, UUID islandId) {
        return CoreGuiViews.islandLimits(client, islandId).thenApply(views -> views.stream().map(PaperGuiViews::limit).toList());
    }

    public static CompletableFuture<List<SnapshotView>> islandSnapshots(CoreApiClient client, UUID islandId, int limit) {
        return CoreGuiViews.islandSnapshots(client, islandId, limit).thenApply(views -> views.stream().map(PaperGuiViews::snapshot).toList());
    }

    public static CompletableFuture<List<LogEntryView>> islandLogs(CoreApiClient client, UUID islandId, int limit) {
        return CoreGuiViews.islandLogs(client, islandId, limit).thenApply(views -> views.stream().map(PaperGuiViews::logEntry).toList());
    }

    private static IslandInfoView islandInfo(CoreGuiViews.IslandInfoView view) {
        return new IslandInfoView(view.name(), view.state(), view.islandId(), view.level(), view.worth(), view.publicAccess(), view.locked(), view.size(), view.border(), view.ownerUuid());
    }

    private static BankView bank(CoreGuiViews.BankView view) {
        return new BankView(view.balance(), view.updatedAt());
    }

    private static TemplateView template(CoreGuiViews.TemplateView view) {
        return new TemplateView(view.id(), view.displayName(), view.description(), view.category(), view.enabled(), view.minNodeVersion(), view.requiredPermission(), view.iconMaterial(), view.iconCustomModelData(), view.previewImageKey(), view.bundleStoragePath(), view.bundleChecksum(), view.bundleSizeBytes(), view.schemaVersion(), view.defaultIslandSize(), view.spawnWorldOffsetX(), view.spawnWorldOffsetY(), view.spawnWorldOffsetZ(), view.spawnYaw(), view.spawnPitch(), view.homeName(), view.environmentPreset(), view.biomeKey(), view.borderColor(), view.bankInitialBalance(), view.creationCost(), view.sortOrder(), view.tags());
    }

    private static RankingData rankings(CoreGuiViews.RankingData view) {
        return new RankingData(view.levels().stream().map(PaperGuiViews::ranking).toList(), view.worths().stream().map(PaperGuiViews::ranking).toList(), view.reviews().stream().map(PaperGuiViews::ranking).toList());
    }

    private static RankingView ranking(CoreGuiViews.RankingView view) {
        return new RankingView(view.rank(), view.label(), view.islandId(), view.level(), view.worth());
    }

    private static MemberView member(CoreGuiViews.MemberView view) {
        return new MemberView(view.playerUuid(), view.role(), view.joinedAt(), view.playerName(), view.lastSeenAt(), view.presenceState(), view.presenceSource(), view.expiresAt());
    }

    private static InviteView invite(CoreGuiViews.InviteView view) {
        return new InviteView(view.inviteId(), view.islandId(), view.inviterUuid(), view.createdAt(), view.expiresAt());
    }

    private static PlayerIslandView playerIsland(CoreGuiViews.PlayerIslandView view, String primaryIslandId) {
        return new PlayerIslandView(view.islandId(), view.name(), view.state(), view.role(), view.level(), view.worth(), view.islandId().equals(primaryIslandId));
    }

    private static PublicIslandView publicIsland(CoreGuiViews.PublicIslandView view) {
        return new PublicIslandView(view.islandId(), view.ownerUuid(), view.name(), view.level(), view.worth());
    }

    private static BanView ban(CoreGuiViews.BanView view) {
        return new BanView(view.bannedUuid(), view.actorUuid(), view.reason(), view.createdAt(), view.expiresAt());
    }

    private static HomeView home(CoreGuiViews.HomeView view) {
        return new HomeView(view.name(), view.worldName(), view.x(), view.y(), view.z(), view.yaw(), view.pitch(), view.createdAt());
    }

    private static WarpView warp(CoreGuiViews.WarpView view) {
        return new WarpView(view.islandId(), view.name(), view.worldName(), view.x(), view.y(), view.z(), view.yaw(), view.pitch(), view.publicAccess(), view.category());
    }

    private static PermissionRuleView permissionRule(CoreGuiViews.PermissionRuleView view) {
        return new PermissionRuleView(view.role(), view.permission(), view.allowed(), view.version());
    }

    private static PermissionOverrideView permissionOverride(PermissionAssignmentView view) {
        return new PermissionOverrideView(view.playerUuid(), view.permission(), view.allowed());
    }

    private static RoleView role(CoreGuiViews.RoleView view) {
        return new RoleView(view.role(), view.weight(), view.displayName());
    }

    private static UpgradeView upgrade(CoreGuiViews.UpgradeView view) {
        return new UpgradeView(view.key(), view.type(), view.level(), view.maxLevel(), view.nextCost(), view.nextItemCosts());
    }

    private static MissionView mission(CoreGuiViews.MissionView view) {
        return new MissionView(view.key(), view.title(), view.progress(), view.goal(), view.completed(), view.reward(), view.category(), view.description(), view.triggerType(), view.targetKey(), view.rewardType(), view.repeatable(), view.dailyReset());
    }

    private static LimitView limit(CoreGuiViews.LimitView view) {
        return new LimitView(view.key(), view.value(), view.updatedAt());
    }

    private static SnapshotView snapshot(CoreGuiViews.SnapshotView view) {
        return new SnapshotView(view.snapshotNo(), view.reason(), view.sizeBytes(), view.createdAt(), view.checksum(), view.nodeId());
    }

    private static LogEntryView logEntry(CoreGuiViews.LogEntryView view) {
        return new LogEntryView(view.actorUuid(), view.action(), view.payload(), view.createdAt());
    }

    public static NodeSummaryView nodeSummary(CoreGuiViews.NodeSummaryView view) {
        return new NodeSummaryView(view.nodeId(), view.state(), view.pool(), view.players(), view.softPlayerCap(), view.hardPlayerCap(), view.activeIslands(), view.maxActiveIslands(), view.activationQueue(), view.maxActivationQueue(), view.mspt(), view.storageAvailable(), view.storagePrimaryDegraded(), view.storageSaveRetryQueueTotal(), view.secondsSinceHeartbeat(), view.stale(), view.routeCandidate(), view.allocationBlockReason());
    }

    public static NodeSummaryView emptyNodeSummary(String nodeId) {
        return new NodeSummaryView(nodeId == null ? "" : nodeId, "UNKNOWN", "island", 0L, 0L, 0L, 0L, 0L, 0L, 0L, "0", true, false, 0L, -1L, false, true, "");
    }

    public record IslandInfoView(String name, String state, String islandId, long level, String worth, boolean publicAccess, boolean locked, long size, long border, String ownerUuid) {
    }

    public record BankView(String balance, String updatedAt) {
    }

    public record TemplateView(String id, String displayName, String description, String category, boolean enabled, String minNodeVersion, String requiredPermission, String iconMaterial, int iconCustomModelData, String previewImageKey, String bundleStoragePath, String bundleChecksum, long bundleSizeBytes, int schemaVersion, int defaultIslandSize, double spawnWorldOffsetX, double spawnWorldOffsetY, double spawnWorldOffsetZ, float spawnYaw, float spawnPitch, String homeName, String environmentPreset, String biomeKey, String borderColor, String bankInitialBalance, String creationCost, int sortOrder, List<String> tags) {
        public TemplateView(String id, String displayName, boolean enabled, String minNodeVersion) {
            this(id, displayName, "", "default", enabled, minNodeVersion, "", "GRASS_BLOCK", 0, "", "", "", 0L, 3, 300, 0.5D, 100.0D, 0.5D, 180.0F, 0.0F, "default", "normal", "minecraft:plains", "BLUE", "0", "0", 0, List.of());
        }
    }

    public record RankingData(List<RankingView> levels, List<RankingView> worths, List<RankingView> reviews) {
    }

    public record RankingView(int rank, String label, String islandId, long level, String worth) {
    }

    public record MemberView(String playerUuid, String role, String joinedAt, String playerName, String lastSeenAt, String presenceState, String presenceSource, String expiresAt) {
    }

    public record InviteView(String inviteId, String islandId, String inviterUuid, String createdAt, String expiresAt) {
    }

    public record PlayerIslandView(String islandId, String name, String state, String role, long level, String worth, boolean primary) {
    }

    public record PublicIslandView(String islandId, String ownerUuid, String name, long level, String worth) {
    }

    public record BanView(String bannedUuid, String actorUuid, String reason, String createdAt, String expiresAt) {
    }

    public record HomeView(String name, String worldName, double x, double y, double z, float yaw, float pitch, String createdAt) {
        public HomeView(String name, double x, double y, double z, String createdAt) {
            this(name, "", x, y, z, 0.0F, 0.0F, createdAt);
        }

        public HomeView {
            name = name == null ? "" : name;
            worldName = worldName == null ? "" : worldName;
            createdAt = createdAt == null ? "" : createdAt;
        }
    }

    public record WarpView(String islandId, String name, String worldName, double x, double y, double z, float yaw, float pitch, boolean publicAccess, String category) {
        public WarpView(String islandId, String name, double x, double y, double z, boolean publicAccess) {
            this(islandId, name, x, y, z, publicAccess, "default");
        }

        public WarpView(String islandId, String name, double x, double y, double z, boolean publicAccess, String category) {
            this(islandId, name, "", x, y, z, 0.0F, 0.0F, publicAccess, category);
        }

        public WarpView {
            islandId = islandId == null ? "" : islandId;
            name = name == null ? "" : name;
            worldName = worldName == null ? "" : worldName;
            category = category == null ? "" : category;
        }
    }

    public record PermissionRulesView(String version, List<PermissionRuleView> rules) {
    }

    public record PermissionRuleView(String role, String permission, boolean allowed, String version) {
    }

    public record PermissionOverrideView(String playerUuid, String permission, boolean allowed) {
    }

    public record RoleView(String role, int weight, String displayName) {
    }

    public record UpgradeView(String key, String type, int level, int maxLevel, String nextCost, java.util.Map<String, String> nextItemCosts) {
        public UpgradeView(String key, String type, int level) {
            this(key, type, level, 0, "", java.util.Map.of());
        }

        public UpgradeView {
            nextCost = nextCost == null ? "" : nextCost;
            nextItemCosts = nextItemCosts == null ? java.util.Map.of() : java.util.Map.copyOf(nextItemCosts);
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

    public record SnapshotView(long snapshotNo, String reason, long sizeBytes, String createdAt, String checksum, String nodeId) {
        public SnapshotView {
            reason = reason == null ? "" : reason;
            createdAt = createdAt == null ? "" : createdAt;
            checksum = checksum == null ? "" : checksum;
            nodeId = nodeId == null ? "" : nodeId;
        }
    }

    public record LogEntryView(String actorUuid, String action, Map<String, String> payload, String createdAt) {
    }

    public record NodeSummaryView(
        String nodeId,
        String state,
        String pool,
        long players,
        long softPlayerCap,
        long hardPlayerCap,
        long activeIslands,
        long maxActiveIslands,
        long activationQueue,
        long maxActivationQueue,
        String mspt,
        boolean storageAvailable,
        boolean storagePrimaryDegraded,
        long storageSaveRetryQueueTotal,
        long secondsSinceHeartbeat,
        boolean stale,
        boolean routeCandidate,
        String allocationBlockReason
    ) {
        public NodeSummaryView(String nodeId, String state, String pool, long players, long softPlayerCap, long hardPlayerCap, long activeIslands, long maxActiveIslands, long activationQueue, long maxActivationQueue, String mspt) {
            this(nodeId, state, pool, players, softPlayerCap, hardPlayerCap, activeIslands, maxActiveIslands, activationQueue, maxActivationQueue, mspt, true, false, 0L, -1L, false, true, "");
        }

        public boolean shutdownSafe() {
            return activeIslands <= 0L && activationQueue <= 0L && !stale;
        }
    }
}
