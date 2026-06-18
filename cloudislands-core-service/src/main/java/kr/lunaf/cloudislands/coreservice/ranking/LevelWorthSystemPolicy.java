package kr.lunaf.cloudislands.coreservice.ranking;

import java.util.List;

public final class LevelWorthSystemPolicy {
    public static final String UPDATE_PIPELINE =
            "event-delta>dirty-flag>batch-recalc>ranking-snapshot>redis-ranking-cache";
    public static final String FULL_SCAN_PIPELINE =
            "periodic-island-chunk-scan>replace-block-counts>correct-drift>mark-dirty";
    public static final String LEVEL_FORMULA_TYPE = "EXPRESSION";
    public static final String LEVEL_FORMULA_EXPRESSION = "floor(total_level_points / 1000)";
    public static final String WORTH_FORMULA_TYPE = "SUM_BLOCK_VALUES";
    public static final String SNAPSHOT_TABLE =
            "island_rank_snapshots(island_id,level,worth,member_count,updated_at)";

    private static final List<String> DELTA_EVENTS = List.of(
            "BlockPlaceEvent:block_counts+1",
            "BlockBreakEvent:block_counts-1",
            "EntityPlaceEvent:entity_counts+1",
            "EntityRemove:entity_counts-1"
    );

    private static final List<String> REQUIRED_BLOCK_VALUES = List.of(
            "minecraft:diamond_block:worth=1000:level=10:limit=5000",
            "minecraft:emerald_block:worth=800:level=8:limit=5000",
            "minecraft:spawner:worth=5000:level=50:limit=200"
    );

    private LevelWorthSystemPolicy() {
    }

    public static List<String> deltaEvents() {
        return DELTA_EVENTS;
    }

    public static List<String> requiredBlockValues() {
        return REQUIRED_BLOCK_VALUES;
    }

    public static boolean deltaEvent(String event) {
        return DELTA_EVENTS.stream().anyMatch(value -> value.startsWith(event + ":"));
    }
}
