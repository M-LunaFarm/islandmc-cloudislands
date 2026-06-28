package kr.lunaf.cloudislands.coreservice.mission;

import java.util.List;

public final class MissionCatalog {
    private static final List<MissionDefinition> DEFINITIONS = List.of(
        new MissionDefinition("first_blocks", "MISSION", "building", "첫 블록 설치", "섬에 첫 건축 블록을 설치합니다.", "BLOCK_PLACE", "minecraft:oak_planks", 64L, "BANK_DEPOSIT", "100 coins", false, false),
        new MissionDefinition("starter_farm", "MISSION", "farming", "기본 농장 만들기", "완전히 자란 작물을 수확합니다.", "FARM_HARVEST", "minecraft:wheat", 128L, "ITEM", "wheat seeds", false, false),
        new MissionDefinition("island_level_10", "MISSION", "growth", "섬 레벨 10 달성", "섬 레벨을 10까지 올립니다.", "ISLAND_LEVEL", "level", 10L, "BANK_DEPOSIT", "500 coins", false, false),
        new MissionDefinition("bank_balance", "MISSION", "economy", "섬 은행 키우기", "섬 은행 잔액 목표를 달성합니다.", "BANK_BALANCE", "balance", 1000L, "BANK_DEPOSIT", "100 coins", false, false),
        new MissionDefinition("generator_collect", "MISSION", "generator", "생성기 수집", "섬 생성기에서 블록을 수집합니다.", "GENERATOR_COLLECT", "*", 128L, "GENERATOR_TIER", "default 2", false, false),
        new MissionDefinition("daily_builder", "CHALLENGE", "daily", "오늘의 건축가", "오늘 블록을 설치해 챌린지를 진행합니다.", "BLOCK_PLACE", "*", 256L, "COMMAND", "challenge crate", true, true),
        new MissionDefinition("daily_miner", "CHALLENGE", "daily", "오늘의 광부", "오늘 블록을 캐서 챌린지를 진행합니다.", "BLOCK_BREAK", "*", 128L, "ITEM", "ore bundle", true, true)
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
