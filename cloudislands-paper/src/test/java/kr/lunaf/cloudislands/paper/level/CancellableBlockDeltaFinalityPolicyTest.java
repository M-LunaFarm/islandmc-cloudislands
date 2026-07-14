package kr.lunaf.cloudislands.paper.level;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import com.destroystokyo.paper.event.block.BlockDestroyEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.junit.jupiter.api.Test;

class CancellableBlockDeltaFinalityPolicyTest {
    @Test
    void cancellableBlockMutationsPublishDeltasOnlyAfterFinalAcceptance() throws Exception {
        String listener = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/IslandProtectionListener.java"));
        List<String> protectionHandlers = List.of(
            "onBucketEmpty(PlayerBucketEmptyEvent event)",
            "onBucketFill(PlayerBucketFillEvent event)",
            "onFertilize(BlockFertilizeEvent event)",
            "onStructureGrow(StructureGrowEvent event)",
            "onEntityChangeBlock(EntityChangeBlockEvent event)",
            "onFluid(BlockFromToEvent event)",
            "onBurn(BlockBurnEvent event)",
            "onLeavesDecay(LeavesDecayEvent event)",
            "onFade(BlockFadeEvent event)"
        );
        List<String> acceptedHandlers = List.of(
            "onBucketEmptyAccepted(PlayerBucketEmptyEvent event)",
            "onBucketFillAccepted(PlayerBucketFillEvent event)",
            "onFertilizeAccepted(BlockFertilizeEvent event)",
            "onStructureGrowAccepted(StructureGrowEvent event)",
            "onEntityChangeBlockAccepted(EntityChangeBlockEvent event)",
            "onPhysicalBlockDestroyAccepted(BlockDestroyEvent event)",
            "onFluidAccepted(BlockFromToEvent event)",
            "onBurnAccepted(BlockBurnEvent event)",
            "onBlockFormAccepted(BlockFormEvent event)",
            "onLeavesDecayAccepted(LeavesDecayEvent event)",
            "onFadeAccepted(BlockFadeEvent event)"
        );

        protectionHandlers.forEach(signature -> {
            String body = methodBody(listener, signature);
            assertFalse(body.contains("blockDeltas."), signature);
            assertFalse(body.contains("reportBlockReplacement("), signature);
        });
        acceptedHandlers.forEach(signature -> {
            assertTrue(listener.contains(
                "@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)\n    public void " + signature
            ), signature);
            String body = methodBody(listener, signature);
            assertTrue(body.contains("blockDeltas.") || body.contains("reportBlockReplacement("), signature);
        });
        String blockGrow = methodBody(listener, "onBlockGrowCount(BlockGrowEvent event)");
        String blockForm = methodBody(listener, "onBlockFormAccepted(BlockFormEvent event)");
        assertTrue(blockGrow.contains("event instanceof BlockFormEvent"));
        assertTrue(blockForm.contains("event instanceof BlockSpreadEvent"));
        assertTrue(BlockGrowEvent.class.isAssignableFrom(BlockFormEvent.class));
        assertTrue(BlockFormEvent.class.isAssignableFrom(BlockSpreadEvent.class));

        String physicalDestroy = methodBody(listener, "onPhysicalBlockDestroyAccepted(BlockDestroyEvent event)");
        assertTrue(physicalDestroy.contains("event.getNewState().getMaterial()"));
        assertFalse(physicalDestroy.contains("setCancelled("));
    }

    @Test
    void generatorReplacementMetricsObserveTheFinalBlockFormResult() throws Exception {
        String generator = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/generator/IslandGeneratorListener.java"));

        assertTrue(generator.contains("pendingBlockForms.add(event)"));
        assertTrue(generator.contains("priority = EventPriority.MONITOR"));
        assertTrue(generator.contains("pendingBlockForms.remove(event) && !event.isCancelled()"));
        assertFalse(methodBody(generator, "onBlockForm(BlockFormEvent event)").contains("reportReplacement("));
    }

    private static String methodBody(String source, String signature) {
        int signatureIndex = source.indexOf(signature);
        if (signatureIndex < 0) {
            throw new AssertionError("Missing method: " + signature);
        }
        int bodyStart = source.indexOf('{', signatureIndex);
        int depth = 0;
        for (int index = bodyStart; index < source.length(); index++) {
            char character = source.charAt(index);
            if (character == '{') {
                depth++;
            } else if (character == '}' && --depth == 0) {
                return source.substring(bodyStart, index + 1);
            }
        }
        throw new AssertionError("Unclosed method: " + signature);
    }
}
