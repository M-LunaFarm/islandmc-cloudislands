package kr.lunaf.cloudislands.coreservice.mission;

import java.util.List;

public final class MissionCatalog {
    private static final List<MissionDefinition> DEFINITIONS = List.of(
        new MissionDefinition("first_blocks", "MISSION", "첫 블록 설치", 64L, "100 coins"),
        new MissionDefinition("starter_farm", "MISSION", "기본 농장 만들기", 128L, "wheat seeds"),
        new MissionDefinition("island_level_10", "MISSION", "섬 레벨 10 달성", 10L, "500 coins"),
        new MissionDefinition("daily_builder", "CHALLENGE", "오늘의 건축가", 256L, "challenge crate"),
        new MissionDefinition("daily_miner", "CHALLENGE", "오늘의 광부", 128L, "ore bundle")
    );

    private MissionCatalog() {}

    public static List<MissionDefinition> all() {
        return DEFINITIONS;
    }

    public static List<MissionDefinition> byKind(String kind) {
        String normalized = normalizeKind(kind);
        return DEFINITIONS.stream().filter(definition -> definition.kind().equals(normalized)).toList();
    }

    public static String normalizeKind(String kind) {
        if (kind == null || kind.isBlank()) {
            return "MISSION";
        }
        String normalized = kind.toUpperCase();
        return normalized.equals("CHALLENGE") ? "CHALLENGE" : "MISSION";
    }
}
