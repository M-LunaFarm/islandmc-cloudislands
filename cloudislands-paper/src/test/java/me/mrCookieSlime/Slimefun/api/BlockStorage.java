package me.mrCookieSlime.Slimefun.api;

import org.bukkit.Material;
import org.bukkit.block.Block;

public final class BlockStorage {
    private BlockStorage() {
    }

    public static String checkID(Block block) {
        return block != null && block.getType() == Material.NOTE_BLOCK ? "ELECTRIC_MOTOR" : null;
    }
}
