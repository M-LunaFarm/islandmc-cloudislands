package kr.lunaf.cloudislands.paper;

import kr.lunaf.cloudislands.api.model.IslandFlag;
import org.bukkit.entity.Ambient;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Monster;
import org.bukkit.entity.WaterMob;

/** Keeps vanilla and vendor spawner paths on the same island spawn-flag classification. */
public final class IslandSpawnFlagPolicy {
    private IslandSpawnFlagPolicy() {
    }

    public static IslandFlag categoryFlag(EntityType entityType) {
        Class<? extends Entity> entityClass = entityType == null ? null : entityType.getEntityClass();
        if (entityClass == null) {
            return null;
        }
        if (Monster.class.isAssignableFrom(entityClass)) {
            return IslandFlag.MONSTER_SPAWN;
        }
        if (Animals.class.isAssignableFrom(entityClass)
            || WaterMob.class.isAssignableFrom(entityClass)
            || Ambient.class.isAssignableFrom(entityClass)) {
            return IslandFlag.ANIMAL_SPAWN;
        }
        return null;
    }
}
