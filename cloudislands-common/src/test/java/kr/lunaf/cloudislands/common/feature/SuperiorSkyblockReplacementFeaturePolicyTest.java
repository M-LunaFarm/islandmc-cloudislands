package kr.lunaf.cloudislands.common.feature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.List;
import org.junit.jupiter.api.Test;

class SuperiorSkyblockReplacementFeaturePolicyTest {
    @Test
    void listsTheFullSuperiorSkyblockReplacementScope() {
        assertEquals(
            Map.ofEntries(
                Map.entry("island-create", "섬 생성"),
                Map.entry("template-select", "템플릿 선택"),
                Map.entry("island-delete", "섬 삭제"),
                Map.entry("island-reset", "섬 리셋"),
                Map.entry("island-home", "섬 홈"),
                Map.entry("multiple-homes", "다중 홈"),
                Map.entry("island-visit", "섬 방문"),
                Map.entry("random-visit", "랜덤 방문"),
                Map.entry("public-private-access", "공개/비공개"),
                Map.entry("visitor-ban", "방문자 밴"),
                Map.entry("visitor-kick", "방문자 추방"),
                Map.entry("member-invite", "멤버 초대"),
                Map.entry("member-kick", "멤버 추방"),
                Map.entry("roles-permissions", "역할/권한"),
                Map.entry("custom-roles", "커스텀 역할"),
                Map.entry("island-flags", "섬 플래그"),
                Map.entry("island-warps", "섬 워프"),
                Map.entry("island-ranking", "섬 랭킹"),
                Map.entry("island-level", "섬 레벨"),
                Map.entry("island-worth", "섬 가치"),
                Map.entry("block-value-settings", "블록 가치 설정"),
                Map.entry("island-upgrades", "섬 업그레이드"),
                Map.entry("island-size-expand", "섬 크기 확장"),
                Map.entry("island-border", "섬 경계"),
                Map.entry("island-biome", "섬 바이옴"),
                Map.entry("island-bank", "섬 은행"),
                Map.entry("island-chat", "섬 채팅"),
                Map.entry("team-chat", "팀 채팅"),
                Map.entry("missions", "미션"),
                Map.entry("challenges", "챌린지"),
                Map.entry("generator-upgrades", "생성기 업그레이드"),
                Map.entry("spawner-hopper-limits", "스포너/호퍼 제한"),
                Map.entry("entity-limits", "엔티티 제한"),
                Map.entry("redstone-limits", "레드스톤 제한"),
                Map.entry("island-logs", "섬 로그"),
                Map.entry("admin-recovery", "관리자 복구"),
                Map.entry("snapshot-rollback", "스냅샷/롤백"),
                Map.entry("island-migration", "섬 마이그레이션"),
                Map.entry("server-drain", "서버 drain"),
                Map.entry("distributed-api", "분산 API"),
                Map.entry("web-api", "웹 API"),
                Map.entry("external-java-api", "외부 플러그인 Java API")
            ),
            SuperiorSkyblockReplacementFeaturePolicy.requiredFeatures()
        );
    }

    @Test
    void resolvesFeatureLabelsByStableKey() {
        assertTrue(SuperiorSkyblockReplacementFeaturePolicy.requiredFeature("island-create"));
        assertTrue(SuperiorSkyblockReplacementFeaturePolicy.requiredFeature("external-java-api"));
        assertEquals("섬 워프", SuperiorSkyblockReplacementFeaturePolicy.label("island-warps"));
        assertFalse(SuperiorSkyblockReplacementFeaturePolicy.requiredFeature("legacy-placeholder"));
        assertFalse(SuperiorSkyblockReplacementFeaturePolicy.requiredFeature(null));
        assertEquals("", SuperiorSkyblockReplacementFeaturePolicy.label(null));
    }

    @Test
    void pinsReplacementFeatureDomains() {
        assertEquals(
            SuperiorSkyblockReplacementFeaturePolicy.requiredFeatures().keySet(),
            SuperiorSkyblockReplacementFeaturePolicy.featureDomains().keySet()
        );
        assertEquals("lifecycle", SuperiorSkyblockReplacementFeaturePolicy.featureDomain("island-create"));
        assertEquals("routing", SuperiorSkyblockReplacementFeaturePolicy.featureDomain("island-home"));
        assertEquals("access-control", SuperiorSkyblockReplacementFeaturePolicy.featureDomain("visitor-ban"));
        assertEquals("membership", SuperiorSkyblockReplacementFeaturePolicy.featureDomain("roles-permissions"));
        assertEquals("protection", SuperiorSkyblockReplacementFeaturePolicy.featureDomain("island-flags"));
        assertEquals("ranking-value", SuperiorSkyblockReplacementFeaturePolicy.featureDomain("island-worth"));
        assertEquals("economy-upgrades", SuperiorSkyblockReplacementFeaturePolicy.featureDomain("island-bank"));
        assertEquals("world-management", SuperiorSkyblockReplacementFeaturePolicy.featureDomain("island-biome"));
        assertEquals("social", SuperiorSkyblockReplacementFeaturePolicy.featureDomain("team-chat"));
        assertEquals("progression", SuperiorSkyblockReplacementFeaturePolicy.featureDomain("missions"));
        assertEquals("limits-generation", SuperiorSkyblockReplacementFeaturePolicy.featureDomain("redstone-limits"));
        assertEquals("operations", SuperiorSkyblockReplacementFeaturePolicy.featureDomain("snapshot-rollback"));
        assertEquals("api-surface", SuperiorSkyblockReplacementFeaturePolicy.featureDomain("external-java-api"));
        assertTrue(SuperiorSkyblockReplacementFeaturePolicy.featureDomainCovered("web-api"));
        assertFalse(SuperiorSkyblockReplacementFeaturePolicy.featureDomainCovered("legacy-provider"));
        assertEquals("", SuperiorSkyblockReplacementFeaturePolicy.featureDomain(null));
    }

    @Test
    void pinsMigrationInputOnlyRuntimeProviderFence() {
        assertEquals(
            "superiorskyblock2-is-readonly-migration-input-never-runtime-authority",
            SuperiorSkyblockReplacementFeaturePolicy.MIGRATION_INPUT_ONLY_POLICY
        );
        assertEquals(
            "warn-and-ignore-no-service-lookup-no-event-hooks-no-data-writes",
            SuperiorSkyblockReplacementFeaturePolicy.FORBIDDEN_RUNTIME_PROVIDER_ACTION
        );
        assertEquals(
            "cloudislands-core-api-is-authoritative-for-every-superiorskyblock2-replacement-feature",
            SuperiorSkyblockReplacementFeaturePolicy.REPLACEMENT_AUTHORITY_POLICY
        );
        assertEquals(
            "replacement-features-use-cloudislands-services-never-superiorskyblock2-service-lookup-events-or-storage",
            SuperiorSkyblockReplacementFeaturePolicy.REPLACEMENT_RUNTIME_POLICY
        );
        assertEquals(
            List.of("SuperiorSkyblock2", "BentoBox", "ASkyBlock", "uSkyBlock", "IridiumSkyblock"),
            SuperiorSkyblockReplacementFeaturePolicy.forbiddenRuntimeProviders()
        );
        assertEquals(
            "SuperiorSkyblock2,BentoBox,ASkyBlock,uSkyBlock,IridiumSkyblock",
            SuperiorSkyblockReplacementFeaturePolicy.forbiddenRuntimeProviderSummary()
        );
        assertTrue(SuperiorSkyblockReplacementFeaturePolicy.forbiddenRuntimeProvider("SuperiorSkyblock2"));
        assertTrue(SuperiorSkyblockReplacementFeaturePolicy.forbiddenRuntimeProvider(" bentobox "));
        assertFalse(SuperiorSkyblockReplacementFeaturePolicy.forbiddenRuntimeProvider("CloudIslands"));
    }
}
