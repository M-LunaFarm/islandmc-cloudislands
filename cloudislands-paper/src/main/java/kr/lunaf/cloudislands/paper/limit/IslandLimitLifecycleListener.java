package kr.lunaf.cloudislands.paper.limit;

import kr.lunaf.cloudislands.paper.event.IslandDeactivateEvent;
import kr.lunaf.cloudislands.paper.event.IslandDeactivatedEvent;
import kr.lunaf.cloudislands.paper.event.IslandDeleteEvent;
import kr.lunaf.cloudislands.paper.event.IslandDeletedEvent;
import kr.lunaf.cloudislands.paper.event.IslandMigratedEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/** Clears island-scoped runtime state once an island leaves or changes ownership of this node. */
public final class IslandLimitLifecycleListener implements Listener {
    private final IslandRuntimeStateInvalidator invalidator;

    public IslandLimitLifecycleListener(IslandRuntimeStateInvalidator invalidator) {
        this.invalidator = invalidator;
    }

    @EventHandler
    public void onDeactivate(IslandDeactivateEvent event) {
        invalidator.invalidate(event.islandId());
    }

    @EventHandler
    public void onDeactivated(IslandDeactivatedEvent event) {
        invalidator.invalidate(event.islandId());
    }

    @EventHandler
    public void onMigrated(IslandMigratedEvent event) {
        invalidator.invalidate(event.islandId());
    }

    @EventHandler
    public void onDelete(IslandDeleteEvent event) {
        invalidator.invalidate(event.islandId());
    }

    @EventHandler
    public void onDeleted(IslandDeletedEvent event) {
        invalidator.invalidate(event.islandId());
    }
}
