package kr.lunaf.cloudislands.coreservice.template;

import java.time.Instant;
import java.util.List;

/** Templates that work without an uploaded world bundle. Paper builds them directly. */
public final class BuiltInIslandTemplates {
    private BuiltInIslandTemplates() {
    }

    public static List<IslandTemplateSnapshot> playable() {
        return List.of(defaultTemplate(), starterTemplate());
    }

    public static IslandTemplateSnapshot defaultTemplate() {
        return template(
            "default",
            "기본 섬",
            "번들 파일 없이 바로 생성되는 기본 생존 섬입니다.",
            "GRASS_BLOCK",
            0
        );
    }

    public static IslandTemplateSnapshot starterTemplate() {
        return template(
            "starter",
            "초보자 섬",
            "흙과 잔디 플랫폼, 묘목, 용암·얼음과 기본 작물이 든 상자를 제공하는 내장 템플릿입니다.",
            "OAK_SAPLING",
            10
        );
    }

    private static IslandTemplateSnapshot template(String id, String name, String description, String icon, int sortOrder) {
        return new IslandTemplateSnapshot(
            id,
            name,
            description,
            "beginner",
            true,
            "",
            "",
            icon,
            0,
            "builtin:" + id,
            "",
            "",
            0L,
            3,
            300,
            0.5D,
            100.0D,
            0.5D,
            180.0F,
            0.0F,
            "default",
            "normal",
            "minecraft:plains",
            "BLUE",
            "0",
            "0",
            sortOrder,
            List.of("builtin", "starter", "bundle-free"),
            Instant.EPOCH,
            Instant.EPOCH
        );
    }
}
