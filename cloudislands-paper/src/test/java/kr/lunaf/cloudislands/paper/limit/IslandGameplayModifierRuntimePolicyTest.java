package kr.lunaf.cloudislands.paper.limit;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class IslandGameplayModifierRuntimePolicyTest {
    @Test
    void gameplayModifierLimitsAreAppliedByPaperRuntime() throws Exception {
        Path root = Path.of(".");
        String cropGrowth = Files.readString(root.resolve("src/main/java/kr/lunaf/cloudislands/paper/generator/IslandCropGrowthListener.java"));
        String entityLimits = Files.readString(root.resolve("src/main/java/kr/lunaf/cloudislands/paper/limit/IslandEntityLimitListener.java"));
        String blockLimits = Files.readString(root.resolve("src/main/java/kr/lunaf/cloudislands/paper/limit/IslandLimitListener.java"));
        String protection = Files.readString(root.resolve("src/main/java/kr/lunaf/cloudislands/paper/IslandProtectionListener.java"));
        String effects = Files.readString(root.resolve("src/main/java/kr/lunaf/cloudislands/paper/limit/IslandEffectApplier.java"));
        String bootstrap = Files.readString(root.resolve("src/main/java/kr/lunaf/cloudislands/paper/PaperPluginBootstrap.java"));

        assertTrue(cropGrowth.contains("\"RATE:CROP_GROWTH\""), "Crop growth listener must consume the Core crop growth runtime key");
        assertTrue(cropGrowth.contains("event.setCancelled(true)"), "Crop growth rate 0 must fail closed by cancelling natural growth");
        assertTrue(entityLimits.contains("\"RATE:MOB_DROPS\""), "Entity listener must consume the Core mob drops runtime key");
        assertTrue(entityLimits.contains("drops.clear()"), "Mob drop rate 0 must fail closed by clearing drops");
        assertTrue(entityLimits.contains("\"RATE:SPAWNER_RATES\""), "Entity listener must consume the Core spawner rate runtime key");
        assertTrue(entityLimits.contains("CreatureSpawnEvent.SpawnReason.SPAWNER"), "Spawner rate must apply only to spawner-origin spawns");
        assertTrue(blockLimits.contains("limitIfReady"), "Restricted placement must fail closed until the Core limit snapshot is ready");
        assertTrue(blockLimits.contains("blockCountIfReady"), "Restricted placement must use authoritative cached block counts");
        assertTrue(blockLimits.contains("BlockMultiPlaceEvent"), "Multi-block placement must be counted atomically");
        assertTrue(!blockLimits.contains("getLoadedChunks"), "Placement events must never scan loaded chunks on the main thread");
        assertTrue(protection.contains("onBlockPlaceAccepted") && protection.contains("onBlockBreakAccepted"), "Block deltas must be separated from cancellable authorization handlers");
        assertTrue(protection.contains("priority = EventPriority.MONITOR, ignoreCancelled = true"), "Only finally accepted block events may mutate authoritative counts");
        for (String effectKey : new String[] {"EFFECT:SPEED", "EFFECT:HASTE", "EFFECT:JUMP_BOOST", "EFFECT:NIGHT_VISION", "EFFECT:REGENERATION"}) {
            assertTrue(effects.contains(effectKey), effectKey);
        }
        assertTrue(effects.contains("player.addPotionEffect"), "Island effects must be applied to players inside an island region");
        assertTrue(bootstrap.contains("new IslandCropGrowthListener(plugin.agent.protection(), cropGrowthLevels, limitCache)"), "Crop growth listener must receive the Core limit cache");
        assertTrue(bootstrap.contains("new IslandEffectApplier(plugin, plugin.agent.protection(), limitCache).start()"), "Effect applier must be started for island nodes");
    }
}
