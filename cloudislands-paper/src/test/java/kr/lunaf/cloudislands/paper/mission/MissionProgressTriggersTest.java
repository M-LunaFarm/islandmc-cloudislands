package kr.lunaf.cloudislands.paper.mission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import kr.lunaf.cloudislands.coreclient.CoreGuiViews;
import org.junit.jupiter.api.Test;

class MissionProgressTriggersTest {
    @Test
    void blockEventsMapToDefaultAndConventionMissionKeys() {
        assertEquals("daily_miner", MissionProgressTriggers.blockBreak("minecraft:stone").getFirst().missionKey());
        assertTrue(MissionProgressTriggers.blockBreak("minecraft:stone").stream().anyMatch(trigger -> trigger.missionKey().equals("block_break:stone")));

        assertTrue(MissionProgressTriggers.blockPlace("minecraft:oak_planks").stream().anyMatch(trigger -> trigger.missionKey().equals("first_blocks")));
        assertTrue(MissionProgressTriggers.blockPlace("minecraft:oak_planks").stream().anyMatch(trigger -> trigger.missionKey().equals("daily_builder")));
        assertTrue(MissionProgressTriggers.blockPlace("minecraft:oak_planks").stream().anyMatch(trigger -> trigger.missionKey().equals("block_place:oak_planks")));
    }

    @Test
    void gameplayEventsCoverFarmFishingMobAndCraftingProgress() {
        assertTrue(MissionProgressTriggers.farmHarvest("minecraft:wheat").stream().anyMatch(trigger -> trigger.missionKey().equals("starter_farm")));
        assertTrue(MissionProgressTriggers.fishingCatch().stream().anyMatch(trigger -> trigger.missionKey().equals("catch_fish") && trigger.kind().equals("CHALLENGE")));
        assertTrue(MissionProgressTriggers.mobKill("minecraft:zombie").stream().anyMatch(trigger -> trigger.missionKey().equals("mob_kill:zombie")));
        assertTrue(MissionProgressTriggers.crafting("minecraft:crafting_table", 4L).stream().anyMatch(trigger -> trigger.missionKey().equals("crafting:crafting_table") && trigger.amount() == 4L));
    }

    @Test
    void economyAndGeneratorEventsHaveMissionTriggerKeys() {
        assertTrue(MissionProgressTriggers.bankBalance(1000L).stream().anyMatch(trigger -> trigger.missionKey().equals("bank_balance") && trigger.amount() == 1000L));
        assertTrue(MissionProgressTriggers.generatorCollect("minecraft:diamond_ore", 3L).stream().anyMatch(trigger -> trigger.missionKey().equals("generator_collect") && trigger.amount() == 3L));
        assertTrue(MissionProgressTriggers.generatorCollect("minecraft:diamond_ore", 3L).stream().anyMatch(trigger -> trigger.missionKey().equals("generator_collect:diamond_ore") && trigger.amount() == 3L));
        assertTrue(MissionProgressTriggers.generatorCollectible("minecraft:diamond_ore"));
        assertTrue(MissionProgressTriggers.generatorCollectible("basalt"));
        assertFalse(MissionProgressTriggers.generatorCollectible("minecraft:dirt"));
    }

    @Test
    void definitionMetadataMatchesTriggerTargetAndRepeatableState() {
        List<CoreGuiViews.MissionView> views = List.of(
            new CoreGuiViews.MissionView("custom_oak", "Oak", 1L, 5L, false, "10", "building", "", "BLOCK_PLACE", "minecraft:oak_planks", "BANK_DEPOSIT", false, false),
            new CoreGuiViews.MissionView("wildcard_daily", "Daily", 5L, 5L, true, "crate", "daily", "", "BLOCK_PLACE", "*", "COMMAND", true, true),
            new CoreGuiViews.MissionView("done_once", "Done", 5L, 5L, true, "10", "building", "", "BLOCK_PLACE", "minecraft:oak_planks", "BANK_DEPOSIT", false, false),
            new CoreGuiViews.MissionView("wrong_event", "Break", 0L, 5L, false, "10", "mining", "", "BLOCK_BREAK", "minecraft:oak_planks", "BANK_DEPOSIT", false, false),
            new CoreGuiViews.MissionView("bank_goal", "Bank", 0L, 1000L, false, "50", "economy", "", "BANK_BALANCE", "balance", "BANK_DEPOSIT", false, false),
            new CoreGuiViews.MissionView("generator_goal", "Generator", 0L, 10L, false, "tier", "generator", "", "GENERATOR_COLLECT", "minecraft:diamond_ore", "GENERATOR_TIER", false, false)
        );

        List<MissionProgressTriggers.Trigger> triggers = MissionProgressTriggers.matchingDefinitions("MISSION", views, "BLOCK_PLACE", "minecraft:oak_planks", 2L);
        List<MissionProgressTriggers.Trigger> bankTriggers = MissionProgressTriggers.matchingDefinitions("MISSION", views, "BANK_BALANCE", "balance", 1000L);
        List<MissionProgressTriggers.Trigger> generatorTriggers = MissionProgressTriggers.matchingDefinitions("MISSION", views, "GENERATOR_COLLECT", "minecraft:diamond_ore", 3L);

        assertTrue(triggers.stream().anyMatch(trigger -> trigger.missionKey().equals("custom_oak") && trigger.amount() == 2L));
        assertTrue(triggers.stream().anyMatch(trigger -> trigger.missionKey().equals("wildcard_daily") && trigger.amount() == 2L));
        assertFalse(triggers.stream().anyMatch(trigger -> trigger.missionKey().equals("done_once")));
        assertFalse(triggers.stream().anyMatch(trigger -> trigger.missionKey().equals("wrong_event")));
        assertTrue(bankTriggers.stream().anyMatch(trigger -> trigger.missionKey().equals("bank_goal") && trigger.amount() == 1000L));
        assertTrue(generatorTriggers.stream().anyMatch(trigger -> trigger.missionKey().equals("generator_goal") && trigger.amount() == 3L));
    }

    @Test
    void fallbackTriggersWinWhenDefinitionKeyDuplicatesDefault() {
        List<MissionProgressTriggers.Trigger> merged = MissionProgressTriggers.merge(
            List.of(new MissionProgressTriggers.Trigger("first_blocks", "MISSION", 1L)),
            List.of(new MissionProgressTriggers.Trigger("first_blocks", "MISSION", 4L), new MissionProgressTriggers.Trigger("custom", "MISSION", 4L))
        );

        assertEquals(2, merged.size());
        assertTrue(merged.stream().anyMatch(trigger -> trigger.missionKey().equals("first_blocks") && trigger.amount() == 1L));
        assertTrue(merged.stream().anyMatch(trigger -> trigger.missionKey().equals("custom") && trigger.amount() == 4L));
    }
}
