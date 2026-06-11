package kr.lunaf.cloudislands.paper.generator;

import java.util.Locale;
import java.util.Random;
import kr.lunaf.cloudislands.paper.ProtectionController;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFormEvent;

public final class IslandGeneratorListener implements Listener {
    private final ProtectionController protection;
    private final GeneratorRegistry registry;
    private final GeneratorLevelCache levels;
    private final Random random = new Random();

    public IslandGeneratorListener(ProtectionController protection, GeneratorRegistry registry, GeneratorLevelCache levels) {
        this.protection = protection;
        this.registry = registry;
        this.levels = levels;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockForm(BlockFormEvent event) {
        Material formed = event.getNewState().getType();
        if (formed != Material.COBBLESTONE && formed != Material.STONE && formed != Material.BASALT) {
            return;
        }
        Block block = event.getBlock();
        protection.islandAt(block).ifPresent(islandId -> {
            int level = levels.level(islandId);
            String materialKey = registry.rule("default", level).select(random);
            Material material = material(materialKey);
            if (material != null && material.isBlock()) {
                event.getNewState().setType(material);
            }
        });
    }

    private Material material(String materialKey) {
        String key = materialKey.toUpperCase(Locale.ROOT).replace("MINECRAFT:", "");
        return Material.matchMaterial(key);
    }
}
