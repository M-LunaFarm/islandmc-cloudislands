package kr.lunaf.cloudislands.paper.limit;

import org.bukkit.event.entity.EntityRemoveEvent;

/** Distinguishes permanent removals missing a death/break event from temporary chunk unloads. */
public final class EntityRemovalAccountingPolicy {
    private EntityRemovalAccountingPolicy() {
    }

    public static boolean records(EntityRemoveEvent.Cause cause) {
        return cause == EntityRemoveEvent.Cause.DESPAWN
            || cause == EntityRemoveEvent.Cause.ENTER_BLOCK
            || cause == EntityRemoveEvent.Cause.OUT_OF_WORLD
            || cause == EntityRemoveEvent.Cause.TRANSFORMATION;
    }
}
