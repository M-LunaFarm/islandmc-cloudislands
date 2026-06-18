package kr.lunaf.cloudislands.common.feature;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SuperiorSkyblockReplacementFeaturePolicy {
    private static final Map<String, String> REQUIRED_FEATURES = buildRequiredFeatures();
    private static final List<String> MIGRATION_TARGETS = List.of(
        "island-id",
        "owner-uuid",
        "members",
        "roles",
        "permissions",
        "island-location",
        "island-size",
        "home-locations",
        "warps",
        "bans",
        "level",
        "worth",
        "upgrades",
        "flags",
        "block-value-settings"
    );
    private static final List<String> MIGRATION_STEPS = List.of(
        "read-only-scan",
        "create-cloudislands-migration-manifest",
        "dry-run-validation",
        "print-conflicts",
        "admin-approval",
        "database-import",
        "world-cell-extraction",
        "island-bundle-create",
        "checksum-verify",
        "cloudislands-activate-test"
    );
    private static final List<String> MIGRATION_COMMANDS = List.of(
        "/ciadmin migrate-superiorskyblock2 scan",
        "/ciadmin migrate-superiorskyblock2 dryrun",
        "/ciadmin migrate-superiorskyblock2 import",
        "/ciadmin migrate-superiorskyblock2 verify",
        "/ciadmin migrate-superiorskyblock2 rollback"
    );
    private static final List<String> FORBIDDEN_RUNTIME_PROVIDERS = List.of(
        "SuperiorSkyblock2",
        "BentoBox",
        "ASkyBlock",
        "uSkyBlock",
        "IridiumSkyblock"
    );
    public static final String MIGRATION_INPUT_ONLY_POLICY = "superiorskyblock2-is-readonly-migration-input-never-runtime-authority";
    public static final String FORBIDDEN_RUNTIME_PROVIDER_ACTION = "warn-and-ignore-no-service-lookup-no-event-hooks-no-data-writes";

    private SuperiorSkyblockReplacementFeaturePolicy() {
    }

    public static Map<String, String> requiredFeatures() {
        return REQUIRED_FEATURES;
    }

    public static boolean requiredFeature(String key) {
        return key != null && REQUIRED_FEATURES.containsKey(key);
    }

    public static String label(String key) {
        return key == null ? "" : REQUIRED_FEATURES.getOrDefault(key, "");
    }

    public static int requiredFeatureCount() {
        return REQUIRED_FEATURES.size();
    }

    public static String requiredFeatureKeys() {
        return String.join(",", REQUIRED_FEATURES.keySet());
    }

    public static List<String> migrationTargets() {
        return MIGRATION_TARGETS;
    }

    public static String migrationTargetSummary() {
        return String.join(",", MIGRATION_TARGETS);
    }

    public static List<String> migrationSteps() {
        return MIGRATION_STEPS;
    }

    public static String migrationStepSummary() {
        return String.join(">", MIGRATION_STEPS);
    }

    public static List<String> migrationCommands() {
        return MIGRATION_COMMANDS;
    }

    public static String migrationCommandSummary() {
        return String.join(",", MIGRATION_COMMANDS);
    }

    public static List<String> forbiddenRuntimeProviders() {
        return FORBIDDEN_RUNTIME_PROVIDERS;
    }

    public static String forbiddenRuntimeProviderSummary() {
        return String.join(",", FORBIDDEN_RUNTIME_PROVIDERS);
    }

    public static boolean forbiddenRuntimeProvider(String provider) {
        if (provider == null || provider.isBlank()) {
            return false;
        }
        String normalized = provider.trim().toLowerCase(java.util.Locale.ROOT);
        return FORBIDDEN_RUNTIME_PROVIDERS.stream()
            .map(value -> value.toLowerCase(java.util.Locale.ROOT))
            .anyMatch(normalized::equals);
    }

    private static Map<String, String> buildRequiredFeatures() {
        LinkedHashMap<String, String> features = new LinkedHashMap<>();
        features.put("island-create", "섬 생성");
        features.put("template-select", "템플릿 선택");
        features.put("island-delete", "섬 삭제");
        features.put("island-reset", "섬 리셋");
        features.put("island-home", "섬 홈");
        features.put("multiple-homes", "다중 홈");
        features.put("island-visit", "섬 방문");
        features.put("random-visit", "랜덤 방문");
        features.put("public-private-access", "공개/비공개");
        features.put("visitor-ban", "방문자 밴");
        features.put("visitor-kick", "방문자 추방");
        features.put("member-invite", "멤버 초대");
        features.put("member-kick", "멤버 추방");
        features.put("roles-permissions", "역할/권한");
        features.put("custom-roles", "커스텀 역할");
        features.put("island-flags", "섬 플래그");
        features.put("island-warps", "섬 워프");
        features.put("island-ranking", "섬 랭킹");
        features.put("island-level", "섬 레벨");
        features.put("island-worth", "섬 가치");
        features.put("block-value-settings", "블록 가치 설정");
        features.put("island-upgrades", "섬 업그레이드");
        features.put("island-size-expand", "섬 크기 확장");
        features.put("island-border", "섬 경계");
        features.put("island-biome", "섬 바이옴");
        features.put("island-bank", "섬 은행");
        features.put("island-chat", "섬 채팅");
        features.put("team-chat", "팀 채팅");
        features.put("missions", "미션");
        features.put("challenges", "챌린지");
        features.put("generator-upgrades", "생성기 업그레이드");
        features.put("spawner-hopper-limits", "스포너/호퍼 제한");
        features.put("entity-limits", "엔티티 제한");
        features.put("redstone-limits", "레드스톤 제한");
        features.put("island-logs", "섬 로그");
        features.put("admin-recovery", "관리자 복구");
        features.put("snapshot-rollback", "스냅샷/롤백");
        features.put("island-migration", "섬 마이그레이션");
        features.put("server-drain", "서버 drain");
        features.put("distributed-api", "분산 API");
        features.put("web-api", "웹 API");
        features.put("external-java-api", "외부 플러그인 Java API");
        return Collections.unmodifiableMap(features);
    }
}
