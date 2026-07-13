package kr.lunaf.cloudislands.paper.limit;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vehicle;

public final class IslandEntityLimitKeys {
    public static final String COUNT_KEY = "cloudislands:limit/entity";

    private IslandEntityLimitKeys() {
    }

    public static boolean counts(Entity entity) {
        if (entity == null || entity instanceof Player) {
            return false;
        }
        return entity instanceof LivingEntity || entity instanceof Hanging || entity instanceof Vehicle;
    }
}
