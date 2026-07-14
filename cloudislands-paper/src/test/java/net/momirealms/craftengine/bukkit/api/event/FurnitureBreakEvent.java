package net.momirealms.craftengine.bukkit.api.event;

import net.momirealms.craftengine.bukkit.entity.furniture.BukkitFurniture;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

public final class FurnitureBreakEvent extends PlayerEvent implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final BukkitFurniture furniture;
    private final Location location;
    private boolean cancelled;

    public FurnitureBreakEvent(Player player, BukkitFurniture furniture, Location location) {
        super(player);
        this.furniture = furniture;
        this.location = location;
    }

    public Player player() {
        return getPlayer();
    }

    public BukkitFurniture furniture() {
        return furniture;
    }

    public Location location() {
        return location;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }
}
