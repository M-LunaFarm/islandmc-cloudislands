package kr.lunaf.cloudislands.paper.mission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
