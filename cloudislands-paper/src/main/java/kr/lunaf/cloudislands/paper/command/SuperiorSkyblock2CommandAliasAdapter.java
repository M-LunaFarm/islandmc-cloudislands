package kr.lunaf.cloudislands.paper.command;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

public final class SuperiorSkyblock2CommandAliasAdapter {
    private static final Map<String, Mapping> MAPPINGS = Map.ofEntries(
        Map.entry("top", new Mapping("top", "랭킹")),
        Map.entry("values", new Mapping("values", "values")),
        Map.entry("value", new Mapping("values", "values")),
        Map.entry("counts", new Mapping("block-counts", "블록상세")),
        Map.entry("recalc", new Mapping("levelcalc", "레벨계산")),
        Map.entry("missions", new Mapping("missions", "미션")),
        Map.entry("ratings", new Mapping("ratings", "후기")),
        Map.entry("setwarp", new Mapping("setwarp", "워프설정")),
        Map.entry("delwarp", new Mapping("warp-delete", "워프삭제")),
        Map.entry("teleport", new Mapping("home", "홈")),
        Map.entry("chest", new Mapping("chest", "창고")),
        Map.entry("team", new Mapping("members", "멤버")),
        Map.entry("panel", new Mapping("menu", "메뉴")),
        Map.entry("disband", new Mapping("delete", "삭제")),
        Map.entry("rankup", new Mapping("upgrade-buy", "업그레이드구매")),
        Map.entry("close", new Mapping("private", "비공개")),
        Map.entry("open", new Mapping("public", "공개")),
        Map.entry("uncoop", new Mapping("untrust", "신뢰해제")),
        Map.entry("permissions", new Mapping("permissions", "권한")),
        Map.entry("border", new Mapping("border", "경계"))
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

    static boolean knownAlias(String alias) {
        return MAPPINGS.containsKey(normalize(alias));
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

    private record Mapping(String subcommand, String displayCommand) {
    }

    record ResolvedAlias(String alias, String subcommand, String displayCommand, String[] args, boolean migrationMode) {
    }
}
