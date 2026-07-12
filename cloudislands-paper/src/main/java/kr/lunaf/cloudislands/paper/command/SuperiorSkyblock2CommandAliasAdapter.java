package kr.lunaf.cloudislands.paper.command;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

public final class SuperiorSkyblock2CommandAliasAdapter {
    private static final Map<String, AdminAliasGuidance> ADMIN_GUIDANCE = Map.ofEntries(
        admin("add", "island member add <island> <player> [role]", false),
        admin("addbanklimit", "island addbanklimit <island> <delta>", false),
        admin("addblocklimit", "island addblocklimit <island> <materialKey> <delta>", false),
        admin("addcooplimit", "island addcooplimit <island> <delta>", false),
        admin("addentitylimit", "island addentitylimit <island> <delta>", false),
        admin("addgenerator", "island addgenerator <island> <levels> [generatorKey]", false),
        admin("addsize", "island addsize <island> <delta>", false),
        admin("addteamlimit", "island addteamlimit <island> <delta>", false),
        admin("addwarpslimit", "island addwarpslimit <island> <delta>", false),
        admin("delwarp", "island delwarp <island> <warp>", true),
        admin("demote", "island member demote <island> <player>", false),
        admin("deposit", "island bank deposit <island> <amount>", false),
        admin("disband", "island delete <island> --confirm", true),
        admin("fly", "fly player <player> <true|false>", false),
        admin("ignore", "island ignore <island>", false),
        admin("join", "island join <island> [role]", false),
        admin("kick", "island member kick <island> <player>", true),
        admin("mission", "island mission complete <island> <player> <missionKey> [kind]", false),
        admin("msg", "message player <player> <message>", false),
        admin("name", "island rename <island> <name>", false),
        admin("openmenu", "openmenu <player> <menuId>", false),
        admin("promote", "island member promote <island> <player>", false),
        admin("purge", "island delete <island> --confirm", true),
        admin("reload", "reload", false),
        admin("removeblocklimit", "island removeblocklimit <island> <materialKey>", true),
        admin("removeentitylimit", "island removeentitylimit <island>", true),
        admin("removeratings", "island removeratings <island> <reviewer>", true),
        admin("schematic", "template verify-bundle <id>", false),
        admin("cmdall", "help command list", true),
        admin("msgall", "help command list", true),
        admin("titleall", "help command list", true),
        admin("debug", "doctor", false),
        admin("modules", "integrations", false),
        admin("resetworld", "island reset <island> --confirm", true),
        admin("setlimit", "island setblockamount <island> <materialKey> <amount>", false),
        admin("setbanklimit", "island setbanklimit <island> <limit>", false),
        admin("setbiome", "island setbiome <island> <biomeKey>", false),
        admin("setcooplimit", "island setcooplimit <island> <limit>", false),
        admin("setentitylimit", "island setentitylimit <island> <limit>", false),
        admin("setrate", "island setrate <island> <reviewer> <rating> [comment]", false),
        admin("setrolelimit", "island setrolelimit <island> <role> <limit>", false),
        admin("setsettings", "island setsettings <island> <flag> <value>", false),
        admin("setsize", "island setsize <island> <size>", false),
        admin("setspawn", "setspawn", false),
        admin("setspawnerrates", "setspawnerrates <island> <percent>", false),
        admin("setteamlimit", "island setteamlimit <island> <limit>", false),
        admin("setwarpslimit", "island setwarpslimit <island> <limit>", false),
        admin("setgenerator", "island setgenerator <island> <generatorKey>", false),
        admin("setpermission", "island setpermission <island> <permission> <true|false>", false),
        admin("resetpermissions", "island resetpermissions <island>", true),
        admin("resetsettings", "island resetsettings <island>", true),
        admin("show", "island info <island>", false),
        admin("spy", "spy [true|false|toggle]", false),
        admin("stats", "metrics", false),
        admin("teleport", "island tp <island>", false),
        admin("title", "title player <player> <title> [subtitle]", false),
        admin("unignore", "island unignore <island>", false),
        admin("withdraw", "island bank withdraw <island> <amount>", false),
        admin("bypass", "help command list", false)
    );
    private static final Map<String, Mapping> MAPPINGS = Map.ofEntries(
        Map.entry("top", new Mapping("top", "랭킹")),
        Map.entry("values", new Mapping("values", "values")),
        Map.entry("value", new Mapping("value", "가치")),
        Map.entry("counts", new Mapping("block-counts", "블록상세")),
        Map.entry("recalc", new Mapping("levelcalc", "레벨계산")),
        Map.entry("missions", new Mapping("missions", "미션")),
        Map.entry("ratings", new Mapping("ratings", "후기")),
        Map.entry("setwarp", new Mapping("setwarp", "워프설정")),
        Map.entry("delwarp", new Mapping("warp-delete", "워프삭제")),
        Map.entry("delhome", new Mapping("home-delete", "홈삭제")),
        Map.entry("deletehome", new Mapping("home-delete", "홈삭제")),
        Map.entry("teleport", new Mapping("home", "홈")),
        Map.entry("chest", new Mapping("chest", "창고")),
        Map.entry("team", new Mapping("member-list-target", "멤버목록")),
        Map.entry("coops", new Mapping("members", "멤버")),
        Map.entry("panel", new Mapping("menu", "메뉴")),
        Map.entry("disband", new Mapping("delete", "삭제")),
        Map.entry("rankup", new Mapping("upgrade-buy", "업그레이드구매")),
        Map.entry("close", new Mapping("private", "비공개")),
        Map.entry("open", new Mapping("public", "공개")),
        Map.entry("uncoop", new Mapping("untrust", "신뢰해제")),
        Map.entry("permissions", new Mapping("permissions", "권한")),
        Map.entry("border", new Mapping("border", "경계")),
        Map.entry("join", new Mapping("accept", "초대수락")),
        Map.entry("balance", new Mapping("bank-balance-target", "은행잔액")),
        Map.entry("bal", new Mapping("bank-balance-target", "은행잔액")),
        Map.entry("money", new Mapping("bank-balance-target", "은행잔액")),
        Map.entry("setbiome", new Mapping("biome", "바이옴")),
        Map.entry("vault", new Mapping("chest", "창고")),
        Map.entry("add", new Mapping("invite", "초대")),
        Map.entry("remove", new Mapping("kick", "추방")),
        Map.entry("lang", new Mapping("language", "언어")),
        Map.entry("manager", new Mapping("menu", "메뉴")),
        Map.entry("cp", new Mapping("menu", "메뉴")),
        Map.entry("setperm", new Mapping("permissions", "권한")),
        Map.entry("settp", new Mapping("sethome", "셋홈")),
        Map.entry("setgo", new Mapping("sethome", "셋홈")),
        Map.entry("show", new Mapping("info-target", "정보")),
        Map.entry("showteam", new Mapping("member-list-target", "멤버목록")),
        Map.entry("online", new Mapping("member-list-target", "멤버목록")),
        Map.entry("tc", new Mapping("teamchat", "팀채팅")),
        Map.entry("tp", new Mapping("home", "홈")),
        Map.entry("go", new Mapping("home", "홈")),
        Map.entry("leader", new Mapping("transfer", "양도")),
        Map.entry("leadership", new Mapping("transfer", "양도")),
        Map.entry("expel", new Mapping("kickvisitor", "방문자추방")),
        Map.entry("warp", new Mapping("legacy-warp", "워프"))
    );
    private static final ConcurrentHashMap<String, LongAdder> USAGE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, LongAdder> ADMIN_USAGE = new ConcurrentHashMap<>();

    private final boolean enabled;
    private final boolean migrationMode;

    public SuperiorSkyblock2CommandAliasAdapter(boolean enabled, boolean migrationMode) {
        this.enabled = enabled;
        this.migrationMode = migrationMode;
    }

    static SuperiorSkyblock2CommandAliasAdapter disabled() {
        return new SuperiorSkyblock2CommandAliasAdapter(false, false);
    }

    static java.util.Set<String> playerAliases() {
        return MAPPINGS.keySet();
    }

    Optional<ResolvedAlias> translate(String[] args) {
        if (!enabled || args == null || args.length == 0) {
            return Optional.empty();
        }
        String alias = normalize(args[0]);
        Mapping mapping = MAPPINGS.get(alias);
        if (mapping == null) {
            return Optional.empty();
        }
        String[] translated = args.clone();
        translated[0] = mapping.subcommand();
        USAGE.computeIfAbsent(alias, _ignored -> new LongAdder()).increment();
        return Optional.of(new ResolvedAlias(alias, mapping.subcommand(), mapping.displayCommand(), translated, migrationMode));
    }

    Optional<AdminAliasGuidance> adminGuidance(String alias) {
        if (!enabled) {
            return Optional.empty();
        }
        String normalized = normalize(alias);
        if (MAPPINGS.containsKey(normalized)) {
            return Optional.empty();
        }
        AdminAliasGuidance guidance = ADMIN_GUIDANCE.get(normalized);
        if (guidance != null) {
            ADMIN_USAGE.computeIfAbsent(normalized, _ignored -> new LongAdder()).increment();
        }
        return Optional.ofNullable(guidance);
    }

    Optional<AdminAliasGuidance> adminGuidance(String[] args) {
        if (!enabled || args == null || args.length == 0) {
            return Optional.empty();
        }
        if (normalize(args[0]).equals("admin")) {
            if (args.length < 2) {
                ADMIN_USAGE.computeIfAbsent("admin", _ignored -> new LongAdder()).increment();
                return Optional.of(new AdminAliasGuidance("admin", "help command list", false));
            }
            String alias = normalize(args[1]);
            AdminAliasGuidance guidance = ADMIN_GUIDANCE.get(alias);
            if (guidance == null) {
                ADMIN_USAGE.computeIfAbsent("admin.unknown", _ignored -> new LongAdder()).increment();
                return Optional.of(new AdminAliasGuidance("admin " + alias, "help command list", false));
            }
            ADMIN_USAGE.computeIfAbsent(alias, _ignored -> new LongAdder()).increment();
            return Optional.of(new AdminAliasGuidance("admin " + guidance.alias(), guidance.ciadminCommand(), guidance.dangerous()));
        }
        return adminGuidance(args[0]);
    }

    static boolean knownAlias(String alias) {
        return MAPPINGS.containsKey(normalize(alias));
    }

    static boolean knownAdminAlias(String alias) {
        return ADMIN_GUIDANCE.containsKey(normalize(alias));
    }

    public static Map<String, Long> usageSnapshot() {
        Map<String, Long> snapshot = new LinkedHashMap<>();
        for (String alias : MAPPINGS.keySet().stream().sorted().toList()) {
            long value = USAGE.getOrDefault(alias, new LongAdder()).sum();
            if (value > 0L) {
                snapshot.put(alias, value);
            }
        }
        return Collections.unmodifiableMap(snapshot);
    }

    public static Map<String, Long> adminUsageSnapshot() {
        Map<String, Long> snapshot = new LinkedHashMap<>();
        for (String alias : ADMIN_USAGE.keySet().stream().sorted().toList()) {
            long value = ADMIN_USAGE.getOrDefault(alias, new LongAdder()).sum();
            if (value > 0L) {
                snapshot.put(alias, value);
            }
        }
        return Collections.unmodifiableMap(snapshot);
    }

    public static String metricsLine() {
        Map<String, Long> snapshot = usageSnapshot();
        String playerMetrics = snapshot.isEmpty() ? "legacySs2Aliases=0" : usageMetrics("legacySs2Aliases", snapshot);
        Map<String, Long> adminSnapshot = adminUsageSnapshot();
        return adminSnapshot.isEmpty() ? playerMetrics : playerMetrics + ";" + usageMetrics("legacySs2AdminAliases", adminSnapshot);
    }

    private static String usageMetrics(String metric, Map<String, Long> snapshot) {
        String aliases = snapshot.entrySet().stream()
            .map(entry -> entry.getKey() + ":" + entry.getValue())
            .reduce((left, right) -> left + "," + right)
            .orElse("");
        long total = snapshot.values().stream().mapToLong(Long::longValue).sum();
        return metric + "=" + total + "[" + aliases + "]";
    }

    static void resetUsageForTests() {
        USAGE.clear();
        ADMIN_USAGE.clear();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static Map.Entry<String, AdminAliasGuidance> admin(String alias, String ciadminCommand, boolean dangerous) {
        return Map.entry(alias, new AdminAliasGuidance(alias, ciadminCommand, dangerous));
    }

    private record Mapping(String subcommand, String displayCommand) {
    }

    record ResolvedAlias(String alias, String subcommand, String displayCommand, String[] args, boolean migrationMode) {
    }

    record AdminAliasGuidance(String alias, String ciadminCommand, boolean dangerous) {
    }
}
