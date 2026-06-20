package kr.lunaf.cloudislands.paper.gui;

import java.util.UUID;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

public final class CloudIslandsMenuHolder implements InventoryHolder {
    private final UUID sessionId;
    private final String menuId;
    private Inventory inventory;

    public CloudIslandsMenuHolder(String menuId) {
        this(menuId, UUID.randomUUID());
    }

    public CloudIslandsMenuHolder(String menuId, UUID sessionId) {
        this.menuId = menuId == null ? "" : menuId;
        this.sessionId = sessionId == null ? UUID.randomUUID() : sessionId;
    }

    public UUID sessionId() {
        return sessionId;
    }

    public String menuId() {
        return menuId;
    }

    void attach(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
