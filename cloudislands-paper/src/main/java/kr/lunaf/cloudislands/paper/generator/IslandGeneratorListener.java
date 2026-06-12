package kr.lunaf.cloudislands.paper.generator;

import java.util.Locale;
import java.util.Random;
import kr.lunaf.cloudislands.paper.ProtectionController;
import kr.lunaf.cloudislands.paper.level.BlockDeltaReporter;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;

public final class IslandGeneratorListener implements Listener {
    private final ProtectionController protection;
    private final GeneratorRegistry registry;
    private final GeneratorLevelCache levels;
    private final BlockDeltaReporter blockDeltas;
    private final Random random = new Random();

    public IslandGeneratorListener(ProtectionController protection, GeneratorRegistry registry, GeneratorLevelCache levels, BlockDeltaReporter blockDeltas) {
        this.protection = protection;
        this.registry = registry;
        this.levels = levels;
        this.blockDeltas = blockDeltas;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockForm(BlockFormEvent event) {
        Material formed = event.getNewState().getType();
        if (formed != Material.COBBLESTONE && formed != Material.STONE && formed != Material.BASALT) {
            return;
        }
        Block block = event.getBlock();
        protection.islandAt(block).ifPresent(islandId -> {
            Material material = generatedMaterial(levels.level(islandId));
            if (material != null && material.isBlock()) {
                event.getNewState().setType(material);
                reportReplacement(islandId, block, material);
            }
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onFluidFlow(BlockFromToEvent event) {
        Material source = event.getBlock().getType();
        if (source != Material.WATER && source != Material.LAVA) {
            return;
        }
        Block target = event.getToBlock();
        if (!touchesOppositeFluid(target, source)) {
            return;
        }
        protection.islandAt(target).ifPresent(islandId -> {
            Material material = generatedMaterial(levels.level(islandId));
            if (material != null && material.isBlock()) {
                event.setCancelled(true);
                target.setType(material);
                reportReplacement(islandId, target, material);
            }
        });
    }

    private void reportReplacement(java.util.UUID islandId, Block block, Material material) {
        if (!block.getType().isAir()) {
            blockDeltas.broken(islandId, block);
        }
        blockDeltas.placed(islandId, material);
    }

    private Material generatedMaterial(int level) {
        return material(registry.rule("default", level).select(random));
    }

    private boolean touchesOppositeFluid(Block block, Material source) {
        Material opposite = source == Material.WATER ? Material.LAVA : Material.WATER;
        return block.getRelative(BlockFace.NORTH).getType() == opposite
            || block.getRelative(BlockFace.SOUTH).getType() == opposite
            || block.getRelative(BlockFace.EAST).getType() == opposite
            || block.getRelative(BlockFace.WEST).getType() == opposite
            || block.getRelative(BlockFace.UP).getType() == opposite
            || block.getRelative(BlockFace.DOWN).getType() == opposite;
    }

    private Material material(String materialKey) {
        String key = materialKey.toUpperCase(Locale.ROOT).replace("MINECRAFT:", "");
        return Material.matchMaterial(key);
    }
}
