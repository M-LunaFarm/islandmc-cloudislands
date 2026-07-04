package kr.lunaf.cloudislands.paper.activation;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class IslandActivationJobHandlerPolicyTest {
    @Test
    void templateBundleCreateFailsClosedWhenRestoreOrPlacementIsUnavailable() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/activation/IslandActivationJobHandler.java"), StandardCharsets.UTF_8);

        assertTrue(source.contains("job.type() == IslandJobType.CREATE_ISLAND && !job.payload().getOrDefault(\"templateBundlePath\", \"\").isBlank()"), "bundled CREATE_ISLAND jobs must use the template bundle branch");
        assertTrue(source.contains("throw new IOException(\"template bundle restore is unavailable"), "bundled create must fail when no world restorer is wired");
        assertTrue(source.contains("throw new IOException(\"template bundle placement is unavailable"), "bundled create must fail when no cell transfer is wired");
        assertTrue(source.contains("worldRestorer.stageTemplateBundle"), "bundled create must stage the configured template bundle");
        assertTrue(source.contains("cellTransfer.place(placement)"), "staged template bundles must be placed into the shard world cell");
    }
}
