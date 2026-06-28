package kr.lunaf.cloudislands.paper.mission;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import kr.lunaf.cloudislands.coreclient.CoreGuiViews;

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

    public static List<Trigger> merge(List<Trigger> fallback, List<Trigger> definitions) {
        Map<String, Trigger> merged = new LinkedHashMap<>();
        for (Trigger trigger : fallback == null ? List.<Trigger>of() : fallback) {
            merged.put(trigger.kind() + ':' + trigger.missionKey(), trigger);
        }
        for (Trigger trigger : definitions == null ? List.<Trigger>of() : definitions) {
            merged.putIfAbsent(trigger.kind() + ':' + trigger.missionKey(), trigger);
        }
        return List.copyOf(merged.values());
    }

    public static List<Trigger> matchingDefinitions(String kind, List<CoreGuiViews.MissionView> missions, String triggerType, String targetKey, long amount) {
        String safeKind = kind == null || kind.isBlank() ? "MISSION" : kind.trim().toUpperCase(Locale.ROOT);
        String safeTrigger = triggerType == null ? "" : triggerType.trim().toUpperCase(Locale.ROOT);
        String safeTarget = normalizeTarget(targetKey);
        long safeAmount = Math.max(1L, amount);
        if (safeTrigger.isBlank()) {
            return List.of();
        }
        List<Trigger> triggers = new ArrayList<>();
        for (CoreGuiViews.MissionView mission : missions == null ? List.<CoreGuiViews.MissionView>of() : missions) {
            if (mission.key().isBlank() || !safeTrigger.equals(normalizeTrigger(mission.triggerType()))) {
                continue;
            }
            if (mission.completed() && !mission.repeatable()) {
                continue;
            }
            if (targetMatches(mission.targetKey(), safeTarget)) {
                triggers.add(new Trigger(mission.key(), safeKind, safeAmount));
            }
        }
        return List.copyOf(triggers);
    }

    public static String normalizeKey(String key) {
        if (key == null || key.isBlank()) {
            return "";
        }
        return key.trim().toLowerCase(Locale.ROOT).replace("minecraft:", "");
    }

    private static String normalizeTrigger(String triggerType) {
        return triggerType == null ? "" : triggerType.trim().toUpperCase(Locale.ROOT);
    }

    private static String normalizeTarget(String targetKey) {
        return targetKey == null ? "" : targetKey.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean targetMatches(String definitionTarget, String eventTarget) {
        String safeDefinitionTarget = normalizeTarget(definitionTarget);
        if (safeDefinitionTarget.isBlank() || safeDefinitionTarget.equals("*")) {
            return true;
        }
        if (safeDefinitionTarget.equals(eventTarget)) {
            return true;
        }
        return normalizeKey(safeDefinitionTarget).equals(normalizeKey(eventTarget));
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
