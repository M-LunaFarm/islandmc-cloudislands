package kr.lunaf.cloudislands.coreclient;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.model.IslandFlag;
import kr.lunaf.cloudislands.common.json.SimpleJson;

public final class CoreGuiViews {
    private static final Set<String> INTERNAL_LOG_PAYLOAD_KEYS = Set.of(
        "activenode",
        "activeworld",
        "cellx",
        "cellz",
        "fromnode",
        "nodeid",
        "originx",
        "originz",
        "sourcenode",
        "targetnode",
        "targetservername",
        "worldname"
    );

    private CoreGuiViews() {
    }

    public static CompletableFuture<IslandInfoView> islandInfo(CoreApiClient client, UUID islandId) {
        return client.islandInfo(islandId).thenApply(CoreGuiViews::islandInfo);
    }

    public static CompletableFuture<BankView> islandBank(CoreApiClient client, UUID islandId) {
        return client.islandBank(islandId).thenApply(body -> {
            Map<?, ?> root = root(body);
            return new BankView(text(root, "balance"), text(root, "updatedAt"));
        });
    }

    public static CompletableFuture<String> islandBiome(CoreApiClient client, UUID islandId) {
        return client.islandBiome(islandId).thenApply(body -> text(root(body), "biomeKey"));
    }

    public static CompletableFuture<Map<IslandFlag, String>> islandFlags(CoreApiClient client, UUID islandId) {
        return client.listIslandFlags(islandId).thenApply(CoreGuiViews::flags);
    }

    public static CompletableFuture<List<TemplateView>> templates(CoreApiClient client) {
        return client.listTemplates().thenApply(CoreGuiViews::templates);
    }

    public static CompletableFuture<RankingData> rankings(CoreApiClient client, int limit) {
        CompletableFuture<String> level = client.topIslandsByLevel(limit);
        CompletableFuture<String> worth = client.topIslandsByWorth(limit);
        CompletableFuture<String> reviews = client.topIslandsByReviews(limit);
        return level.thenCombine(worth, (levelBody, worthBody) -> new RankingData(rankings(levelBody, "level"), rankings(worthBody, "worth"), List.of()))
            .thenCombine(reviews, (data, reviewBody) -> new RankingData(data.levels(), data.worths(), reviewRankings(reviewBody)));
    }

    public static CompletableFuture<List<MemberView>> islandMembers(CoreApiClient client, UUID islandId) {
        return client.listIslandMembers(islandId).thenApply(CoreGuiViews::members);
    }

    public static CompletableFuture<List<InviteView>> pendingInvites(CoreApiClient client, UUID playerUuid) {
        return client.listPendingInvites(playerUuid).thenApply(CoreGuiViews::invites);
    }

    public static CompletableFuture<List<PlayerIslandView>> playerIslands(CoreApiClient client, UUID playerUuid) {
        return client.listPlayerIslands(playerUuid).thenApply(CoreGuiViews::playerIslands);
    }

    public static CompletableFuture<List<PublicIslandView>> publicIslands(CoreApiClient client, int limit) {
        return client.listPublicIslands(limit).thenApply(CoreGuiViews::publicIslands);
    }

    public static CompletableFuture<List<BanView>> islandBans(CoreApiClient client, UUID islandId) {
        return client.listIslandBans(islandId).thenApply(CoreGuiViews::bans);
    }

    public static CompletableFuture<List<HomeView>> islandHomes(CoreApiClient client, UUID islandId) {
        return client.listIslandHomes(islandId).thenApply(CoreGuiViews::homes);
    }

    public static CompletableFuture<List<WarpView>> islandWarps(CoreApiClient client, UUID islandId) {
        return client.listIslandWarps(islandId).thenApply(CoreGuiViews::warps);
    }

    public static CompletableFuture<List<WarpView>> publicWarps(CoreApiClient client, int limit) {
        return client.listPublicWarps(limit).thenApply(CoreGuiViews::warps);
    }

    public static CompletableFuture<List<WarpView>> publicWarps(CoreApiClient client, int limit, String category, String query) {
        return client.listPublicWarps(limit, category, query).thenApply(CoreGuiViews::warps);
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

    public static CompletableFuture<List<UpgradeView>> islandUpgrades(CoreApiClient client, UUID islandId) {
        return client.listIslandUpgrades(islandId).thenApply(CoreGuiViews::upgrades);
    }

    public static CompletableFuture<List<MissionView>> islandMissions(CoreApiClient client, UUID islandId, String kind) {
        return client.listIslandMissions(islandId, kind).thenApply(CoreGuiViews::missions);
    }

    public static CompletableFuture<List<LimitView>> islandLimits(CoreApiClient client, UUID islandId) {
        return client.listIslandLimits(islandId).thenApply(CoreGuiViews::limits);
    }

    public static CompletableFuture<List<SnapshotView>> islandSnapshots(CoreApiClient client, UUID islandId, int limit) {
        return client.listIslandSnapshots(islandId, limit).thenApply(CoreGuiViews::snapshots);
    }

    public static CompletableFuture<List<LogEntryView>> islandLogs(CoreApiClient client, UUID islandId, int limit) {
        return client.listIslandLogs(islandId, limit).thenApply(CoreGuiViews::logs);
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
            text(root, "ownerUuid")
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
                    text(object, "presenceSource")
                ));
            }
        }
        return members;
    }

    private static List<InviteView> invites(String body) {
        List<InviteView> invites = new ArrayList<>();
        for (Map<?, ?> object : entries(body)) {
            String inviteId = text(object, "inviteId");
            if (!inviteId.isBlank()) {
                invites.add(new InviteView(inviteId, text(object, "islandId"), text(object, "inviterUuid"), text(object, "createdAt"), text(object, "expiresAt")));
            }
        }
        return invites;
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

    private static List<HomeView> homes(String body) {
        List<HomeView> homes = new ArrayList<>();
        for (Map<?, ?> object : entries(body)) {
            String name = text(object, "name");
            if (!name.isBlank()) {
                homes.add(new HomeView(name, doubleValue(object, "localX", "x"), doubleValue(object, "localY", "y"), doubleValue(object, "localZ", "z"), text(object, "createdAt")));
            }
        }
        return homes;
    }

    private static List<WarpView> warps(String body) {
        List<WarpView> warps = new ArrayList<>();
        for (Map<?, ?> object : entries(body)) {
            String name = text(object, "name");
            if (name.isBlank()) {
                name = text(object, "warpName");
            }
            if (!name.isBlank()) {
                warps.add(new WarpView(text(object, "islandId"), name, doubleValue(object, "localX", "x"), doubleValue(object, "localY", "y"), doubleValue(object, "localZ", "z"), bool(object, "publicAccess"), text(object, "category")));
            }
        }
        return warps;
    }

    private static Map<IslandFlag, String> flags(String body) {
        Map<IslandFlag, String> values = new EnumMap<>(IslandFlag.class);
        Map<?, ?> root = root(body);
        Map<?, ?> flags = object(root, "flags");
        if (flags.isEmpty()) {
            flags = object(root, "values");
        }
        for (Map.Entry<String, String> entry : objectFields(flags).entrySet()) {
            try {
                values.put(IslandFlag.valueOf(entry.getKey()), entry.getValue());
            } catch (IllegalArgumentException ignored) {
            }
        }
        return values;
    }

    private static List<PermissionRuleView> permissionRules(String body) {
        return permissionRulesView(body).rules();
    }

    private static PermissionRulesView permissionRulesView(String body) {
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
            String role = text(object, "role");
            if (!role.isBlank()) {
                roles.add(new RoleView(role, intValue(object, "weight"), text(object, "displayName")));
            }
        }
        return roles;
    }

    private static List<UpgradeView> upgrades(String body) {
        List<UpgradeView> upgrades = new ArrayList<>();
        for (Map<?, ?> object : entries(body)) {
            String key = text(object, "key");
            if (key.isBlank()) {
                key = text(object, "upgradeKey");
            }
            if (!key.isBlank()) {
                upgrades.add(new UpgradeView(key, text(object, "type"), intValue(object, "level")));
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

    private static List<LimitView> limits(String body) {
        List<LimitView> limits = new ArrayList<>();
        for (Map<?, ?> object : entries(body)) {
            String key = text(object, "key");
            if (key.isBlank()) {
                key = text(object, "limitKey");
            }
            if (!key.isBlank()) {
                limits.add(new LimitView(key, longValue(object, "value"), text(object, "updatedAt")));
            }
        }
        return limits;
    }

    private static List<SnapshotView> snapshots(String body) {
        List<SnapshotView> snapshots = new ArrayList<>();
        for (Map<?, ?> object : entries(body)) {
            long snapshotNo = longValue(object, "snapshotNo");
            if (snapshotNo > 0) {
                snapshots.add(new SnapshotView(snapshotNo, text(object, "reason"), longValue(object, "sizeBytes"), text(object, "createdAt")));
            }
        }
        return snapshots;
    }

    private static List<LogEntryView> logs(String body) {
        List<LogEntryView> entries = new ArrayList<>();
        for (Map<?, ?> object : entries(body)) {
            String action = text(object, "action");
            if (!action.isBlank()) {
                entries.add(new LogEntryView(text(object, "actorUuid"), action, logPayload(object(object, "payload")), text(object, "createdAt")));
            }
        }
        return entries;
    }

    private static Map<String, String> logPayload(Map<?, ?> payload) {
        Map<String, String> values = new LinkedHashMap<>();
        objectFields(payload).entrySet().stream()
            .filter(entry -> !INTERNAL_LOG_PAYLOAD_KEYS.contains(entry.getKey().toLowerCase(java.util.Locale.ROOT)))
            .forEach(entry -> values.put(entry.getKey(), entry.getValue()));
        return Map.copyOf(values);
    }

    private static Map<?, ?> root(String body) {
        return SimpleJson.object(SimpleJson.parse(body));
    }

    private static List<Map<?, ?>> entries(String body) {
        Object parsed = SimpleJson.parse(body);
        if (parsed instanceof List<?>) {
            return SimpleJson.list(parsed).stream()
                .map(SimpleJson::object)
                .filter(map -> !map.isEmpty())
                .toList();
        }
        Map<?, ?> root = SimpleJson.object(parsed);
        for (Object value : root.values()) {
            if (value instanceof List<?>) {
                return SimpleJson.list(value).stream()
                    .map(SimpleJson::object)
                    .filter(map -> !map.isEmpty())
                    .toList();
            }
        }
        return root.isEmpty() ? List.of() : List.of(root);
    }

    private static Map<?, ?> object(Map<?, ?> object, String key) {
        return SimpleJson.object(object.get(key));
    }

    private static Map<String, String> objectFields(Map<?, ?> object) {
        if (object == null || object.isEmpty()) {
            return Map.of();
        }
        Map<String, String> values = new LinkedHashMap<>();
        object.forEach((key, value) -> values.put(SimpleJson.text(key), SimpleJson.text(value)));
        return values;
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

    public record IslandInfoView(String name, String state, String islandId, long level, String worth, boolean publicAccess, boolean locked, long size, long border, String ownerUuid) {
    }

    public record BankView(String balance, String updatedAt) {
    }

    public record TemplateView(String id, String displayName, boolean enabled, String minNodeVersion) {
    }

    public record RankingData(List<RankingView> levels, List<RankingView> worths, List<RankingView> reviews) {
    }

    public record RankingView(int rank, String label, String islandId, long level, String worth) {
    }

    public record MemberView(String playerUuid, String role, String joinedAt, String playerName, String lastSeenAt, String presenceState, String presenceSource) {
    }

    public record InviteView(String inviteId, String islandId, String inviterUuid, String createdAt, String expiresAt) {
    }

    public record PlayerIslandView(String islandId, String name, String state, String role, long level, String worth) {
    }

    public record PublicIslandView(String islandId, String ownerUuid, String name, long level, String worth) {
    }

    public record BanView(String bannedUuid, String actorUuid, String reason, String createdAt, String expiresAt) {
    }

    public record HomeView(String name, double x, double y, double z, String createdAt) {
    }

    public record WarpView(String islandId, String name, double x, double y, double z, boolean publicAccess, String category) {
        public WarpView(String islandId, String name, double x, double y, double z, boolean publicAccess) {
            this(islandId, name, x, y, z, publicAccess, "default");
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

    public record UpgradeView(String key, String type, int level) {
    }

    public record MissionView(String key, String title, long progress, long goal, boolean completed, String reward) {
    }

    public record LimitView(String key, long value, String updatedAt) {
    }

    public record SnapshotView(long snapshotNo, String reason, long sizeBytes, String createdAt) {
    }

    public record LogEntryView(String actorUuid, String action, Map<String, String> payload, String createdAt) {
    }

    public record NodeSummaryView(String nodeId, String state, String pool, long players, long softPlayerCap, long hardPlayerCap, long activeIslands, long maxActiveIslands, long activationQueue, long maxActivationQueue, String mspt) {
    }
}
