package net.momirealms.craftengine.bukkit.api;

import net.momirealms.craftengine.bukkit.entity.furniture.BukkitFurniture;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;

public final class CraftEngineFurniture {
    private CraftEngineFurniture() {
    }

    public static BukkitFurniture getLoadedFurnitureByMetaEntity(Entity entity) {
        if (entity.getType() == EntityType.INTERACTION) {
            throw new IllegalStateException("simulated stale furniture payload");
        }
        return entity.getType() == EntityType.ITEM_DISPLAY ? new BukkitFurniture("custom:chair") : null;
    }
}
