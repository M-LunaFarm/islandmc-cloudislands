package kr.lunaf.cloudislands.paper.event;

import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.api.model.PermissionResult;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class IslandPermissionCheckEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID islandId;
    private final UUID playerUuid;
    private final Player player;
    private final Block block;
    private final IslandPermission permission;
    private final PermissionResult result;

    public IslandPermissionCheckEvent(UUID islandId, UUID playerUuid, Player player, Block block, IslandPermission permission, PermissionResult result) {
        this.islandId = islandId;
        this.playerUuid = playerUuid;
        this.player = player;
        this.block = block;
        this.permission = permission;
        this.result = result;
    }

    public UUID islandId() {
        return islandId;
    }

    public UUID playerUuid() {
        return playerUuid;
    }

    public Player player() {
        return player;
    }

    public Block block() {
        return block;
    }

    public IslandPermission permission() {
        return permission;
    }

    public PermissionResult result() {
        return result;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
