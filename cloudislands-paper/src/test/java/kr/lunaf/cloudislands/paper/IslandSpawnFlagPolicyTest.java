package kr.lunaf.cloudislands.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import kr.lunaf.cloudislands.api.model.IslandFlag;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;

class IslandSpawnFlagPolicyTest {
    @Test
    void classifiesSpawnerTypesWithoutRequiringARealBukkitEntity() {
        assertEquals(IslandFlag.MONSTER_SPAWN, IslandSpawnFlagPolicy.categoryFlag(EntityType.ZOMBIE));
        assertEquals(IslandFlag.ANIMAL_SPAWN, IslandSpawnFlagPolicy.categoryFlag(EntityType.COW));
        assertEquals(IslandFlag.ANIMAL_SPAWN, IslandSpawnFlagPolicy.categoryFlag(EntityType.COD));
        assertEquals(IslandFlag.ANIMAL_SPAWN, IslandSpawnFlagPolicy.categoryFlag(EntityType.BAT));
        assertNull(IslandSpawnFlagPolicy.categoryFlag(EntityType.ARMOR_STAND));
        assertNull(IslandSpawnFlagPolicy.categoryFlag(null));
    }
}
