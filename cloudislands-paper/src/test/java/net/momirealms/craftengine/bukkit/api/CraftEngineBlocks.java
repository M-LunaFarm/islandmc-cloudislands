package net.momirealms.craftengine.bukkit.api;

import org.bukkit.Material;
import org.bukkit.block.Block;

public final class CraftEngineBlocks {
    private CraftEngineBlocks() {
    }

    public static ImmutableBlockState getCustomBlockState(Block block) {
        if (block.getType() == Material.BARRIER) {
            throw new IllegalStateException("simulated unavailable world state");
        }
        if (block.getType() != Material.NOTE_BLOCK) {
            return null;
        }
        return new ImmutableBlockState(new Holder(new BlockDefinition(new Key("custom:machine"))));
    }

    public record ImmutableBlockState(Holder owner) {
    }

    public record Holder(BlockDefinition value) {
    }

    public record BlockDefinition(Key id) {
    }

    public record Key(String value) {
        public String asString() {
            return value;
        }
    }
}
