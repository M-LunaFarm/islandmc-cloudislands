package kr.lunaf.cloudislands.paper.level;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ExplosionDeltaFinalityPolicyTest {
    @Test
    void explosionDeltasUseTheFinalAcceptedBlockList() throws Exception {
        String listener = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/IslandProtectionListener.java"));

        assertTrue(listener.contains("priority = EventPriority.MONITOR, ignoreCancelled = true)\n    public void onEntityExplodeAccepted"));
        assertTrue(listener.contains("priority = EventPriority.MONITOR, ignoreCancelled = true)\n    public void onBlockExplodeAccepted"));
        assertTrue(listener.contains("SoftExplosionProtectionPolicy.isSoft(event.getExplosionResult())"));
        assertTrue(listener.contains("event.blockList().removeIf(block -> softExplosionDenied(source, block, windCharge))"));
    }
}
