package kr.lunaf.cloudislands.paper.generator;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import kr.lunaf.cloudislands.api.generator.GeneratorRuleSnapshot;
import kr.lunaf.cloudislands.paper.ProtectionController;
import kr.lunaf.cloudislands.paper.level.BlockDeltaReporter;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;

public final class IslandGeneratorListener implements Listener {
    private final ProtectionController protection;
    private final GeneratorRegistry registry;
    private final GeneratorLevelCache levels;
    private final BlockDeltaReporter blockDeltas;
    private final Random random = new Random();
    private final AtomicLong formEvents = new AtomicLong();
    private final AtomicLong fluidCollisionEvents = new AtomicLong();
    private final AtomicLong formReplacements = new AtomicLong();
    private final AtomicLong fluidReplacements = new AtomicLong();
    private final AtomicLong islandMisses = new AtomicLong();
    private final AtomicLong materialResolveFailures = new AtomicLong();
    private final Set<BlockFormEvent> pendingBlockForms = Collections.newSetFromMap(new IdentityHashMap<>());

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
        formEvents.incrementAndGet();
        Block block = event.getBlock();
        UUID islandId = protection.islandAt(block).orElse(null);
        if (islandId == null) {
            islandMisses.incrementAndGet();
            return;
        }
        Material material = generatedMaterial(levels.selection(islandId), biomeKey(block));
        if (validGeneratedMaterial(material)) {
            event.getNewState().setType(material);
            pendingBlockForms.add(event);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockFormResult(BlockFormEvent event) {
        if (pendingBlockForms.remove(event) && !event.isCancelled()) {
            formReplacements.incrementAndGet();
        }
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
        fluidCollisionEvents.incrementAndGet();
        Material previous = target.getType();
        UUID islandId = protection.islandAt(target).orElse(null);
        if (islandId == null) {
            islandMisses.incrementAndGet();
            return;
        }
        Material material = generatedMaterial(levels.selection(islandId), biomeKey(target));
        if (validGeneratedMaterial(material)) {
            event.setCancelled(true);
            target.setType(material);
            fluidReplacements.incrementAndGet();
            reportReplacement(islandId, previous, material);
        }
    }

    private boolean validGeneratedMaterial(Material material) {
        if (material != null && material.isBlock()) {
            return true;
        }
        materialResolveFailures.incrementAndGet();
        return false;
    }

    private void reportReplacement(java.util.UUID islandId, Material previous, Material material) {
        if (previous != null && previous.isBlock() && !previous.isAir()) {
            blockDeltas.broken(islandId, previous);
        }
        blockDeltas.placed(islandId, material);
    }

    private Material generatedMaterial(GeneratorLevelCache.GeneratorSelection selection, String biomeKey) {
        String materialKey = selectCoreRule(selection, biomeKey);
        if (materialKey.isBlank()) {
            GeneratorLevelCache.GeneratorProfile profile = selection.profile();
            materialKey = registry.rule(profile.generatorKey(), profile.level()).select(random);
        }
        return material(materialKey);
    }

    private String selectCoreRule(GeneratorLevelCache.GeneratorSelection selection, String biomeKey) {
        List<GeneratorRuleSnapshot> eligible = eligibleCoreRules(selection, biomeKey);
        double total = eligible.stream().mapToDouble(GeneratorRuleSnapshot::chance).sum();
        if (total <= 0.0D) {
            return "";
        }
        double value = random.nextDouble() * total;
        double cursor = 0.0D;
        for (GeneratorRuleSnapshot rule : eligible) {
            cursor += rule.chance();
            if (value <= cursor) {
                return rule.materialKey();
            }
        }
        return eligible.get(eligible.size() - 1).materialKey();
    }

    static List<GeneratorRuleSnapshot> eligibleCoreRules(GeneratorLevelCache.GeneratorSelection selection, String biomeKey) {
        return selection.rules().stream()
            .filter(GeneratorRuleSnapshot::enabled)
            .filter(rule -> rule.minIslandLevel() <= selection.islandLevel())
            .filter(rule -> rule.minUpgradeLevel() <= selection.profile().level())
            .filter(rule -> GeneratorBiomeFilter.accepts(rule.biomeKey(), biomeKey))
            .toList();
    }

    private String biomeKey(Block block) {
        try {
            return block.getBiome().getKey().toString();
        } catch (RuntimeException ignored) {
            return "*";
        }
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

    public long formReplacements() {
        return formReplacements.get();
    }

    public long formEvents() {
        return formEvents.get();
    }

    public long fluidCollisionEvents() {
        return fluidCollisionEvents.get();
    }

    public long fluidReplacements() {
        return fluidReplacements.get();
    }

    public long islandMisses() {
        return islandMisses.get();
    }

    public long materialResolveFailures() {
        return materialResolveFailures.get();
    }

    public String eventPolicy() {
        return "BlockFormEvent=cobblestone-stone-basalt,BlockFromToEvent=water-lava-fluid-collision";
    }

    public int generatorKeyCount() {
        return registry.generatorKeyCount();
    }

    public int ruleLevelCount() {
        return registry.ruleLevelCount();
    }

    public long cacheTtlSeconds() {
        return levels.ttlSeconds();
    }
}
