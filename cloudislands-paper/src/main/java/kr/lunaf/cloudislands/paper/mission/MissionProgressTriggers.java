package kr.lunaf.cloudislands.paper.mission;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class MissionProgressTriggers {
    private MissionProgressTriggers() {}

    public static List<Trigger> blockBreak(String materialKey) {
        List<Trigger> triggers = new ArrayList<>();
        triggers.add(new Trigger("daily_miner", "CHALLENGE", 1L));
        addConventionTriggers(triggers, "block_break", materialKey);
        return List.copyOf(triggers);
    }

    public static List<Trigger> blockPlace(String materialKey) {
        List<Trigger> triggers = new ArrayList<>();
        triggers.add(new Trigger("first_blocks", "MISSION", 1L));
        triggers.add(new Trigger("daily_builder", "CHALLENGE", 1L));
        addConventionTriggers(triggers, "block_place", materialKey);
        return List.copyOf(triggers);
    }

    public static List<Trigger> farmHarvest(String materialKey) {
        List<Trigger> triggers = new ArrayList<>();
        triggers.add(new Trigger("starter_farm", "MISSION", 1L));
        addConventionTriggers(triggers, "farm_harvest", materialKey);
        return List.copyOf(triggers);
    }

    public static List<Trigger> mobKill(String entityKey) {
        List<Trigger> triggers = new ArrayList<>();
        addConventionTriggers(triggers, "mob_kill", entityKey);
        return List.copyOf(triggers);
    }

    public static List<Trigger> fishingCatch() {
        return List.of(
            new Trigger("catch_fish", "CHALLENGE", 1L),
            new Trigger("fishing:catch", "MISSION", 1L)
        );
    }

    public static List<Trigger> crafting(String materialKey, long amount) {
        long safeAmount = Math.max(1L, amount);
        List<Trigger> triggers = new ArrayList<>();
        triggers.add(new Trigger("crafting", "MISSION", safeAmount));
        String normalized = normalizeKey(materialKey);
        if (!normalized.isBlank()) {
            triggers.add(new Trigger("crafting:" + normalized, "MISSION", safeAmount));
        }
        return List.copyOf(triggers);
    }

    public static String normalizeKey(String key) {
        if (key == null || key.isBlank()) {
            return "";
        }
        return key.trim().toLowerCase(Locale.ROOT).replace("minecraft:", "");
    }

    private static void addConventionTriggers(List<Trigger> triggers, String prefix, String key) {
        triggers.add(new Trigger(prefix, "MISSION", 1L));
        String normalized = normalizeKey(key);
        if (!normalized.isBlank()) {
            triggers.add(new Trigger(prefix + ":" + normalized, "MISSION", 1L));
        }
    }

    public record Trigger(String missionKey, String kind, long amount) {
        public Trigger {
            missionKey = missionKey == null ? "" : missionKey.trim().toLowerCase(Locale.ROOT);
            kind = kind == null || kind.isBlank() ? "MISSION" : kind.trim().toUpperCase(Locale.ROOT);
            amount = Math.max(1L, amount);
        }
    }
}
