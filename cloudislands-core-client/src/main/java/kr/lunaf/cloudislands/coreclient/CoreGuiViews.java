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
import kr.lunaf.cloudislands.common.json.SimpleJson;

public final class CoreGuiViews {
    private CoreGuiViews() {
    }

    public static CompletableFuture<IslandInfoView> islandInfo(CoreApiClient client, UUID islandId) {
        return client.islandInfo(islandId).thenApply(CoreGuiViews::islandInfo);
    }

    public static IslandInfoView islandInfoView(String body) {
        return islandInfo(body);
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
        return client.listTemplates().thenApply(CoreGuiViews::templates);
    }

    public static CompletableFuture<RankingData> rankings(CoreApiClient client, int limit) {
        return client.progression().rankings(limit);
    }

    public static CompletableFuture<List<MemberView>> islandMembers(CoreApiClient client, UUID islandId) {
        return client.listIslandMembers(islandId).thenApply(CoreGuiViews::members);
    }

    public static List<MemberView> memberViews(String body) {
        return members(body);
    }

    public static CompletableFuture<List<InviteView>> pendingInvites(CoreApiClient client, UUID playerUuid) {
        return client.members().pendingInvites(playerUuid);
    }

    public static List<InviteView> inviteViews(String body) {
        return invites(body);
    }

    public static InviteView inviteView(String body) {
        return invite(root(body));
    }

    public static CompletableFuture<List<PlayerIslandView>> playerIslands(CoreApiClient client, UUID playerUuid) {
        return client.listPlayerIslands(playerUuid).thenApply(CoreGuiViews::playerIslands);
    }

    public static CompletableFuture<List<PublicIslandView>> publicIslands(CoreApiClient client, int limit) {
        return client.listPublicIslands(limit).thenApply(CoreGuiViews::publicIslands);
    }

    public static CompletableFuture<List<BanView>> islandBans(CoreApiClient client, UUID islandId) {
        return client.members().bans(islandId);
    }

    public static List<BanView> banViews(String body) {
        return bans(body);
    }

    public static PlayerProfileView playerProfile(String body) {
        Map<?, ?> root = root(body);
        return new PlayerProfileView(text(root, "playerUuid"), text(root, "primaryIslandId"));
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
        return client.listIslandPermissions(islandId).thenApply(CoreGuiViews::permissionRulesView);
    }

    public static CompletableFuture<List<RoleView>> islandRoles(CoreApiClient client, UUID islandId) {
        return client.listIslandRoles(islandId).thenApply(CoreGuiViews::roles);
    }

    public static RoleView roleView(String body) {
        return role(root(body));
    }

    public static CompletableFuture<List<UpgradeView>> islandUpgrades(CoreApiClient client, UUID islandId) {
        return client.listIslandUpgrades(islandId).thenApply(CoreGuiViews::upgrades);
    }

    public static CompletableFuture<List<MissionView>> islandMissions(CoreApiClient client, UUID islandId, String kind) {
        return client.listIslandMissions(islandId, kind).thenApply(CoreGuiViews::missions);
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

    public static List<LogEntryView> logViews(String body) {
        return CoreCommunicationJson.records(body).stream()
            .map(CoreCommunicationJson::view)
            .toList();
    }

    public static NodeSummaryView nodeSummary(String nodeId, String body) {
        Map<?, ?> root = root(body);
        return new NodeSummaryView(
            nodeId,
            text(root, "state"),
            text(root, "pool"),
            longValue(root, "players"),
            longValue(root, "softPlayerCap"),
            longValue(root, "hardPlayerCap"),
            longValue(root, "activeIslands"),
            longValue(root, "maxActiveIslands"),
            longValue(root, "activationQueue"),
            longValue(root, "maxActivationQueue"),
            text(root, "mspt")
        );
    }

    private static IslandInfoView islandInfo(String body) {
        Map<?, ?> root = root(body);
        return new IslandInfoView(
            text(root, "name"),
            text(root, "state"),
            text(root, "islandId"),
            longValue(root, "level"),
            text(root, "worth"),
            bool(root, "publicAccess"),
            bool(root, "locked"),
            longValue(root, "size"),
            longValue(root, "border"),
            text(root, "ownerUuid"),
            text(root, "createdAt"),
            text(root, "updatedAt")
        );
    }

    private static List<TemplateView> templates(String body) {
        List<TemplateView> templates = new ArrayList<>();
        for (Map<?, ?> object : entries(body)) {
            String id = text(object, "id");
            if (!id.isBlank()) {
                templates.add(new TemplateView(id, text(object, "displayName"), bool(object, "enabled", true), text(object, "minNodeVersion")));
            }
        }
        return templates;
    }

    private static List<RankingView> rankings(String body, String label) {
        List<RankingView> rankings = new ArrayList<>();
        for (Map<?, ?> object : entries(body)) {
            String islandId = text(object, "islandId");
            if (!islandId.isBlank()) {
                rankings.add(new RankingView(rankings.size() + 1, label, islandId, longValue(object, "level"), text(object, "worth")));
            }
        }
        return rankings;
    }

    private static List<RankingView> reviewRankings(String body) {
        List<RankingView> rankings = new ArrayList<>();
        for (Map<?, ?> object : entries(body)) {
            String islandId = text(object, "islandId");
            if (!islandId.isBlank()) {
                rankings.add(new RankingView(rankings.size() + 1, "reviews", islandId, longValue(object, "reviewCount"), String.format(java.util.Locale.ROOT, "%.2f", doubleValue(object, "averageRating"))));
            }
        }
        return rankings;
    }

    private static List<MemberView> members(String body) {
        List<MemberView> members = new ArrayList<>();
        for (Map<?, ?> object : entries(body)) {
            String playerUuid = text(object, "playerUuid");
            if (!playerUuid.isBlank()) {
                members.add(new MemberView(
                    playerUuid,
                    text(object, "role"),
                    text(object, "joinedAt"),
                    text(object, "playerName"),
                    text(object, "lastSeenAt"),
                    text(object, "presenceState"),
                    text(object, "presenceSource"),
                    text(object, "expiresAt")
                ));
            }
        }
        return members;
    }

    private static List<InviteView> invites(String body) {
        List<InviteView> invites = new ArrayList<>();
        for (Map<?, ?> object : entries(body)) {
            InviteView invite = invite(object);
            if (!invite.inviteId().isBlank()) {
                invites.add(invite);
            }
        }
        return invites;
    }

    private static InviteView invite(Map<?, ?> object) {
        return new InviteView(
            text(object, "inviteId"),
            text(object, "islandId"),
            text(object, "inviterUuid"),
            text(object, "targetUuid"),
            text(object, "state"),
            text(object, "createdAt"),
            text(object, "expiresAt")
        );
    }

    private static List<PlayerIslandView> playerIslands(String body) {
        List<PlayerIslandView> islands = new ArrayList<>();
        for (Map<?, ?> object : entries(body)) {
            String islandId = text(object, "islandId");
            if (!islandId.isBlank()) {
                String name = text(object, "name");
                String role = text(object, "role");
                islands.add(new PlayerIslandView(islandId, name.isBlank() ? islandId : name, text(object, "state"), role.isBlank() ? "MEMBER" : role, longValue(object, "level"), text(object, "worth")));
            }
        }
        return islands;
    }

    private static List<PublicIslandView> publicIslands(String body) {
        List<PublicIslandView> islands = new ArrayList<>();
        for (Map<?, ?> object : entries(body)) {
            String islandId = text(object, "islandId");
            if (!islandId.isBlank()) {
                String name = text(object, "name");
                islands.add(new PublicIslandView(islandId, text(object, "ownerUuid"), name.isBlank() ? islandId : name, longValue(object, "level"), text(object, "worth")));
            }
        }
        return islands;
    }

    private static List<BanView> bans(String body) {
        List<BanView> bans = new ArrayList<>();
        for (Map<?, ?> object : entries(body)) {
            String bannedUuid = text(object, "bannedUuid");
            if (!bannedUuid.isBlank()) {
                bans.add(new BanView(bannedUuid, text(object, "actorUuid"), text(object, "reason"), text(object, "createdAt"), text(object, "expiresAt")));
            }
        }
        return bans;
    }

    private static List<PermissionRuleView> permissionRules(String body) {
        return permissionRulesView(body).rules();
    }

    public static PermissionRulesView permissionRulesView(String body) {
        Map<?, ?> root = root(body);
        String version = text(root, "version");
        List<PermissionRuleView> rules = new ArrayList<>();
        for (Map<?, ?> object : entries(body)) {
            String role = text(object, "role");
            String permission = text(object, "permission");
            if (!role.isBlank() && !permission.isBlank()) {
                rules.add(new PermissionRuleView(role, permission, bool(object, "allowed"), version));
            }
        }
        return new PermissionRulesView(version, List.copyOf(rules));
    }

    private static List<RoleView> roles(String body) {
        List<RoleView> roles = new ArrayList<>();
        for (Map<?, ?> object : entries(body)) {
            RoleView role = role(object);
            if (!role.role().isBlank()) {
                roles.add(role);
            }
        }
        return roles;
    }

    private static RoleView role(Map<?, ?> object) {
        String role = text(object, "role");
        if (role.isBlank()) {
            role = text(object, "roleKey");
        }
        return new RoleView(role, intValue(object, "weight"), text(object, "displayName"));
    }

    private static List<UpgradeView> upgrades(String body) {
        List<UpgradeView> upgrades = new ArrayList<>();
        for (Map<?, ?> object : entries(body)) {
            String key = text(object, "key");
            if (key.isBlank()) {
                key = text(object, "upgradeKey");
            }
            if (!key.isBlank()) {
                upgrades.add(new UpgradeView(key, text(object, "type"), intValue(object, "level"), text(object, "generatorKey")));
            }
        }
        return upgrades;
    }

    private static List<MissionView> missions(String body) {
        List<MissionView> missions = new ArrayList<>();
        for (Map<?, ?> object : entries(body)) {
            String key = text(object, "key");
            if (key.isBlank()) {
                key = text(object, "missionKey");
            }
            if (!key.isBlank()) {
                missions.add(new MissionView(key, text(object, "title"), longValue(object, "progress"), longValue(object, "goal"), bool(object, "completed"), text(object, "reward")));
            }
        }
        return missions;
    }

    private static List<LogEntryView> logs(List<IslandLogRecord> records) {
        return records.stream()
            .map(CoreCommunicationJson::view)
            .toList();
    }

    private static Map<?, ?> root(String body) {
        return CoreJson.object(body);
    }

    private static List<Map<?, ?>> entries(String body) {
        return CoreJson.entries(body);
    }

    private static String text(Map<?, ?> object, String key) {
        return SimpleJson.text(object.get(key));
    }

    private static long longValue(Map<?, ?> object, String key) {
        return SimpleJson.number(object.get(key));
    }

    private static int intValue(Map<?, ?> object, String key) {
        return (int) SimpleJson.number(object.get(key));
    }

    private static double doubleValue(Map<?, ?> object, String key) {
        Object value = object.get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(SimpleJson.text(value));
        } catch (NumberFormatException exception) {
            return 0.0D;
        }
    }

    private static double doubleValue(Map<?, ?> object, String key, String fallbackKey) {
        return object.containsKey(key) ? doubleValue(object, key) : doubleValue(object, fallbackKey);
    }

    private static boolean bool(Map<?, ?> object, String key) {
        return bool(object, key, false);
    }

    private static boolean bool(Map<?, ?> object, String key, boolean fallback) {
        Object value = object.get(key);
        return value instanceof Boolean bool ? bool : (value == null ? fallback : Boolean.parseBoolean(SimpleJson.text(value)));
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

    public record MissionView(String key, String title, long progress, long goal, boolean completed, String reward) {
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
