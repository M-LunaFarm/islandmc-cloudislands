package kr.lunaf.cloudislands.paper.application.view;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.model.IslandFlag;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;

public final class PaperGuiViews {
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

    private PaperGuiViews() {
    }

    public static CompletableFuture<IslandInfoView> islandInfo(CoreApiClient client, UUID islandId) {
        return client.islandInfo(islandId).thenApply(PaperGuiViews::islandInfo);
    }

    public static CompletableFuture<BankView> islandBank(CoreApiClient client, UUID islandId) {
        return client.islandBank(islandId).thenApply(body -> new BankView(text(body, "balance"), text(body, "updatedAt")));
    }

    public static CompletableFuture<String> islandBiome(CoreApiClient client, UUID islandId) {
        return client.islandBiome(islandId).thenApply(body -> text(body, "biomeKey"));
    }

    public static CompletableFuture<Map<IslandFlag, String>> islandFlags(CoreApiClient client, UUID islandId) {
        return client.listIslandFlags(islandId).thenApply(PaperGuiViews::flags);
    }

    public static CompletableFuture<List<TemplateView>> templates(CoreApiClient client) {
        return client.listTemplates().thenApply(PaperGuiViews::templates);
    }

    public static CompletableFuture<RankingData> rankings(CoreApiClient client, int limit) {
        CompletableFuture<String> level = client.topIslandsByLevel(limit);
        CompletableFuture<String> worth = client.topIslandsByWorth(limit);
        return level.thenCombine(worth, (levelBody, worthBody) -> new RankingData(rankings(levelBody, "level"), rankings(worthBody, "worth")));
    }

    public static CompletableFuture<List<MemberView>> islandMembers(CoreApiClient client, UUID islandId) {
        return client.listIslandMembers(islandId).thenApply(PaperGuiViews::members);
    }

    public static CompletableFuture<List<InviteView>> pendingInvites(CoreApiClient client, UUID playerUuid) {
        return client.listPendingInvites(playerUuid).thenApply(PaperGuiViews::invites);
    }

    public static CompletableFuture<List<PlayerIslandView>> playerIslands(CoreApiClient client, UUID playerUuid) {
        return client.listPlayerIslands(playerUuid).thenApply(PaperGuiViews::playerIslands);
    }

    public static CompletableFuture<List<PublicIslandView>> publicIslands(CoreApiClient client, int limit) {
        return client.listPublicIslands(limit).thenApply(PaperGuiViews::publicIslands);
    }

    public static CompletableFuture<List<BanView>> islandBans(CoreApiClient client, UUID islandId) {
        return client.listIslandBans(islandId).thenApply(PaperGuiViews::bans);
    }

    public static CompletableFuture<List<HomeView>> islandHomes(CoreApiClient client, UUID islandId) {
        return client.listIslandHomes(islandId).thenApply(PaperGuiViews::homes);
    }

    public static CompletableFuture<List<WarpView>> islandWarps(CoreApiClient client, UUID islandId) {
        return client.listIslandWarps(islandId).thenApply(PaperGuiViews::warps);
    }

    public static CompletableFuture<List<WarpView>> publicWarps(CoreApiClient client, int limit) {
        return client.listPublicWarps(limit).thenApply(PaperGuiViews::warps);
    }

    public static CompletableFuture<List<PermissionRuleView>> islandPermissions(CoreApiClient client, UUID islandId) {
        return client.listIslandPermissions(islandId).thenApply(PaperGuiViews::permissionRules);
    }

    public static CompletableFuture<List<RoleView>> islandRoles(CoreApiClient client, UUID islandId) {
        return client.listIslandRoles(islandId).thenApply(PaperGuiViews::roles);
    }

    public static CompletableFuture<List<UpgradeView>> islandUpgrades(CoreApiClient client, UUID islandId) {
        return client.listIslandUpgrades(islandId).thenApply(PaperGuiViews::upgrades);
    }

    public static CompletableFuture<List<MissionView>> islandMissions(CoreApiClient client, UUID islandId, String kind) {
        return client.listIslandMissions(islandId, kind).thenApply(PaperGuiViews::missions);
    }

    public static CompletableFuture<List<LimitView>> islandLimits(CoreApiClient client, UUID islandId) {
        return client.listIslandLimits(islandId).thenApply(PaperGuiViews::limits);
    }

    public static CompletableFuture<List<SnapshotView>> islandSnapshots(CoreApiClient client, UUID islandId, int limit) {
        return client.listIslandSnapshots(islandId, limit).thenApply(PaperGuiViews::snapshots);
    }

    public static CompletableFuture<List<LogEntryView>> islandLogs(CoreApiClient client, UUID islandId, int limit) {
        return client.listIslandLogs(islandId, limit).thenApply(PaperGuiViews::logs);
    }

    public static NodeSummaryView nodeSummary(String nodeId, String body) {
        return new NodeSummaryView(
            nodeId,
            text(body, "state"),
            text(body, "pool"),
            longValue(body, "players"),
            longValue(body, "softPlayerCap"),
            longValue(body, "hardPlayerCap"),
            longValue(body, "activeIslands"),
            longValue(body, "maxActiveIslands"),
            longValue(body, "activationQueue"),
            longValue(body, "maxActivationQueue"),
            rawScalar(body, "mspt")
        );
    }

    private static IslandInfoView islandInfo(String body) {
        return new IslandInfoView(
            text(body, "name"),
            text(body, "state"),
            text(body, "islandId"),
            longValue(body, "level"),
            text(body, "worth"),
            bool(body, "publicAccess"),
            bool(body, "locked"),
            longValue(body, "size"),
            longValue(body, "border"),
            text(body, "ownerUuid")
        );
    }

    private static List<TemplateView> templates(String body) {
        List<TemplateView> templates = new ArrayList<>();
        for (String object : objects(body)) {
            String id = text(object, "id");
            if (!id.isBlank()) {
                templates.add(new TemplateView(id, text(object, "displayName"), bool(object, "enabled", true), text(object, "minNodeVersion")));
            }
        }
        return templates;
    }

    private static List<RankingView> rankings(String body, String label) {
        List<RankingView> rankings = new ArrayList<>();
        for (String object : objects(body)) {
            String islandId = text(object, "islandId");
            if (!islandId.isBlank()) {
                rankings.add(new RankingView(rankings.size() + 1, label, islandId, longValue(object, "level"), text(object, "worth")));
            }
        }
        return rankings;
    }

    private static List<MemberView> members(String body) {
        List<MemberView> members = new ArrayList<>();
        for (String object : objects(body)) {
            String playerUuid = text(object, "playerUuid");
            if (!playerUuid.isBlank()) {
                members.add(new MemberView(playerUuid, text(object, "role"), text(object, "joinedAt")));
            }
        }
        return members;
    }

    private static List<InviteView> invites(String body) {
        List<InviteView> invites = new ArrayList<>();
        for (String object : objects(body)) {
            String inviteId = text(object, "inviteId");
            if (!inviteId.isBlank()) {
                invites.add(new InviteView(inviteId, text(object, "islandId"), text(object, "inviterUuid"), text(object, "createdAt"), text(object, "expiresAt")));
            }
        }
        return invites;
    }

    private static List<PlayerIslandView> playerIslands(String body) {
        List<PlayerIslandView> islands = new ArrayList<>();
        for (String object : objects(body)) {
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
        for (String object : objects(body)) {
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
        for (String object : objects(body)) {
            String bannedUuid = text(object, "bannedUuid");
            if (!bannedUuid.isBlank()) {
                bans.add(new BanView(bannedUuid, text(object, "actorUuid"), text(object, "reason"), text(object, "createdAt"), text(object, "expiresAt")));
            }
        }
        return bans;
    }

    private static List<HomeView> homes(String body) {
        List<HomeView> homes = new ArrayList<>();
        for (String object : objects(body)) {
            String name = text(object, "name");
            if (!name.isBlank()) {
                homes.add(new HomeView(name, doubleValue(object, "localX", "x"), doubleValue(object, "localY", "y"), doubleValue(object, "localZ", "z"), text(object, "createdAt")));
            }
        }
        return homes;
    }

    private static List<WarpView> warps(String body) {
        List<WarpView> warps = new ArrayList<>();
        for (String object : objects(body)) {
            String name = text(object, "name");
            if (name.isBlank()) {
                name = text(object, "warpName");
            }
            if (!name.isBlank()) {
                warps.add(new WarpView(text(object, "islandId"), name, doubleValue(object, "localX", "x"), doubleValue(object, "localY", "y"), doubleValue(object, "localZ", "z"), bool(object, "publicAccess")));
            }
        }
        return warps;
    }

    private static Map<IslandFlag, String> flags(String body) {
        Map<IslandFlag, String> values = new EnumMap<>(IslandFlag.class);
        for (Map.Entry<String, String> entry : objectFields(objectValue(body, "flags")).entrySet()) {
            try {
                values.put(IslandFlag.valueOf(entry.getKey()), entry.getValue());
            } catch (IllegalArgumentException ignored) {
            }
        }
        return values;
    }

    private static List<PermissionRuleView> permissionRules(String body) {
        List<PermissionRuleView> rules = new ArrayList<>();
        for (String object : objects(body)) {
            String role = text(object, "role");
            String permission = text(object, "permission");
            if (!role.isBlank() && !permission.isBlank()) {
                rules.add(new PermissionRuleView(role, permission, bool(object, "allowed")));
            }
        }
        return rules;
    }

    private static List<RoleView> roles(String body) {
        List<RoleView> roles = new ArrayList<>();
        for (String object : objects(body)) {
            String role = text(object, "role");
            if (!role.isBlank()) {
                roles.add(new RoleView(role, intValue(object, "weight"), text(object, "displayName")));
            }
        }
        return roles;
    }

    private static List<UpgradeView> upgrades(String body) {
        List<UpgradeView> upgrades = new ArrayList<>();
        for (String object : objects(body)) {
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
        for (String object : objects(body)) {
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
        for (String object : objects(body)) {
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
        for (String object : objects(body)) {
            long snapshotNo = longValue(object, "snapshotNo");
            if (snapshotNo > 0) {
                snapshots.add(new SnapshotView(snapshotNo, text(object, "reason"), longValue(object, "sizeBytes"), text(object, "createdAt")));
            }
        }
        return snapshots;
    }

    private static List<LogEntryView> logs(String body) {
        List<LogEntryView> entries = new ArrayList<>();
        for (String object : objects(body)) {
            String action = text(object, "action");
            if (!action.isBlank()) {
                entries.add(new LogEntryView(text(object, "actorUuid"), action, logPayload(objectValue(object, "payload")), text(object, "createdAt")));
            }
        }
        return entries;
    }

    private static Map<String, String> logPayload(String payload) {
        Map<String, String> values = new LinkedHashMap<>();
        objectFields(payload).entrySet().stream()
            .filter(entry -> !INTERNAL_LOG_PAYLOAD_KEYS.contains(entry.getKey().toLowerCase(java.util.Locale.ROOT)))
            .forEach(entry -> values.put(entry.getKey(), entry.getValue()));
        return Map.copyOf(values);
    }

    private static List<String> objects(String body) {
        List<String> objects = new ArrayList<>();
        int index = 0;
        while (body != null && index < body.length()) {
            int start = body.indexOf('{', index);
            if (start < 0) {
                break;
            }
            int end = objectEnd(body, start);
            if (end < 0) {
                break;
            }
            objects.add(body.substring(start, end + 1));
            index = start + 1;
        }
        return objects;
    }

    private static String objectValue(String body, String key) {
        String needle = "\"" + key + "\":{";
        int start = body == null ? -1 : body.indexOf(needle);
        if (start < 0) {
            return "";
        }
        start += needle.length() - 1;
        int end = objectEnd(body, start);
        return end < start ? "" : body.substring(start, end + 1);
    }

    private static Map<String, String> objectFields(String object) {
        if (object == null || object.isBlank()) {
            return Map.of();
        }
        Map<String, String> values = new LinkedHashMap<>();
        int index = 0;
        while (index < object.length()) {
            int keyStart = object.indexOf('"', index);
            if (keyStart < 0) {
                break;
            }
            int keyEnd = jsonStringEnd(object, keyStart + 1);
            if (keyEnd < 0) {
                break;
            }
            int colon = object.indexOf(':', keyEnd + 1);
            if (colon < 0) {
                break;
            }
            String key = unescape(object.substring(keyStart + 1, keyEnd));
            int valueStart = colon + 1;
            while (valueStart < object.length() && Character.isWhitespace(object.charAt(valueStart))) {
                valueStart++;
            }
            if (valueStart < object.length() && object.charAt(valueStart) == '"') {
                int valueEnd = jsonStringEnd(object, valueStart + 1);
                if (valueEnd < 0) {
                    break;
                }
                values.put(key, unescape(object.substring(valueStart + 1, valueEnd)));
                index = valueEnd + 1;
            } else {
                int valueEnd = valueStart;
                while (valueEnd < object.length() && ",}".indexOf(object.charAt(valueEnd)) < 0) {
                    valueEnd++;
                }
                values.put(key, object.substring(valueStart, valueEnd).trim());
                index = valueEnd + 1;
            }
        }
        return values;
    }

    private static int objectEnd(String body, int start) {
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int index = start; index < body.length(); index++) {
            char ch = body.charAt(index);
            if (inString) {
                if (ch == '"' && !escaped) {
                    inString = false;
                }
                escaped = ch == '\\' && !escaped;
                if (ch != '\\') {
                    escaped = false;
                }
                continue;
            }
            if (ch == '"') {
                inString = true;
            } else if (ch == '{') {
                depth++;
            } else if (ch == '}') {
                depth--;
                if (depth == 0) {
                    return index;
                }
            }
        }
        return -1;
    }

    private static String text(String body, String key) {
        String needle = "\"" + key + "\":\"";
        int start = body == null ? -1 : body.indexOf(needle);
        if (start < 0) {
            return "";
        }
        start += needle.length();
        int end = jsonStringEnd(body, start);
        return end < start ? "" : unescape(body.substring(start, end));
    }

    private static long longValue(String body, String key) {
        String raw = rawScalar(body, key);
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException exception) {
            return 0L;
        }
    }

    private static int intValue(String body, String key) {
        String raw = rawScalar(body, key);
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private static double doubleValue(String body, String key) {
        String raw = rawScalar(body, key);
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException exception) {
            return 0.0D;
        }
    }

    private static double doubleValue(String body, String key, String fallbackKey) {
        String raw = rawScalar(body, key);
        if (raw.isBlank()) {
            raw = rawScalar(body, fallbackKey);
        }
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException exception) {
            return 0.0D;
        }
    }

    private static boolean bool(String body, String key) {
        return bool(body, key, false);
    }

    private static boolean bool(String body, String key, boolean fallback) {
        String raw = rawScalar(body, key);
        return raw.equals("true") || (!raw.equals("false") && fallback);
    }

    private static String rawScalar(String body, String key) {
        String needle = "\"" + key + "\":";
        int start = body == null ? -1 : body.indexOf(needle);
        if (start < 0) {
            return "";
        }
        start += needle.length();
        int end = start;
        while (end < body.length() && ",}".indexOf(body.charAt(end)) < 0) {
            end++;
        }
        return body.substring(start, end).trim();
    }

    private static int jsonStringEnd(String body, int start) {
        boolean escaped = false;
        for (int index = start; index < body.length(); index++) {
            char ch = body.charAt(index);
            if (ch == '"' && !escaped) {
                return index;
            }
            escaped = ch == '\\' && !escaped;
            if (ch != '\\') {
                escaped = false;
            }
        }
        return -1;
    }

    private static String unescape(String value) {
        return value.replace("\\\"", "\"").replace("\\\\", "\\");
    }

    public record IslandInfoView(String name, String state, String islandId, long level, String worth, boolean publicAccess, boolean locked, long size, long border, String ownerUuid) {
    }

    public record BankView(String balance, String updatedAt) {
    }

    public record TemplateView(String id, String displayName, boolean enabled, String minNodeVersion) {
    }

    public record RankingData(List<RankingView> levels, List<RankingView> worths) {
    }

    public record RankingView(int rank, String label, String islandId, long level, String worth) {
    }

    public record MemberView(String playerUuid, String role, String joinedAt) {
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

    public record WarpView(String islandId, String name, double x, double y, double z, boolean publicAccess) {
    }

    public record PermissionRuleView(String role, String permission, boolean allowed) {
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
