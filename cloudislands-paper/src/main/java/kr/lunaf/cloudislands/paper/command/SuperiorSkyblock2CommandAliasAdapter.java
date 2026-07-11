package kr.lunaf.cloudislands.paper.command;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

public final class SuperiorSkyblock2CommandAliasAdapter {
    private static final Map<String, AdminAliasGuidance> ADMIN_GUIDANCE = Map.ofEntries(
        admin("purge", "island delete <island> --confirm", true),
        admin("schematic", "template verify-bundle <id>", false),
        admin("cmdall", "help command list", true),
        admin("msgall", "help command list", true),
        admin("titleall", "help command list", true),
        admin("debug", "doctor", false),
        admin("modules", "integrations", false),
        admin("resetworld", "island reset <island> --confirm", true),
        admin("setlimit", "island setblockamount <island> <materialKey> <amount>", false),
        admin("setsize", "island setsize <island> <size>", false),
        admin("setteamlimit", "island setteamlimit <island> <limit>", false),
        admin("setwarpslimit", "island setwarpslimit <island> <limit>", false),
        admin("setgenerator", "island setgenerator <island> <generatorKey>", false),
        admin("setpermission", "island setpermission <island> <permission> <true|false>", false),
        admin("resetpermissions", "island resetpermissions <island>", true),
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
        return Optional.ofNullable(ADMIN_GUIDANCE.get(normalize(alias)));
    }

    Optional<AdminAliasGuidance> adminGuidance(String[] args) {
        if (!enabled || args == null || args.length == 0) {
            return Optional.empty();
        }
        if (normalize(args[0]).equals("admin")) {
            if (args.length < 2) {
                return Optional.empty();
            }
            AdminAliasGuidance guidance = ADMIN_GUIDANCE.get(normalize(args[1]));
            if (guidance == null) {
                return Optional.empty();
            }
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

    public static String metricsLine() {
        Map<String, Long> snapshot = usageSnapshot();
        if (snapshot.isEmpty()) {
            return "legacySs2Aliases=0";
        }
        String aliases = snapshot.entrySet().stream()
            .map(entry -> entry.getKey() + ":" + entry.getValue())
            .reduce((left, right) -> left + "," + right)
            .orElse("");
        long total = snapshot.values().stream().mapToLong(Long::longValue).sum();
        return "legacySs2Aliases=" + total + "[" + aliases + "]";
    }

    static void resetUsageForTests() {
        USAGE.clear();
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
