package kr.lunaf.cloudislands.paper.config;

import java.util.List;
import kr.lunaf.cloudislands.common.config.ConfigSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaperRuntimeConfigReloadResultTest {
    @Test
    void messageChangesAreLiveSafeAndDoNotRequireAFalseRuntimeRestart() {
        PaperRuntimeConfig current = config("smoke-node", "before");
        PaperRuntimeConfig candidate = config("smoke-node", "after");

        PaperRuntimeConfigReloadResult result = PaperRuntimeConfigReloadResult.analyze(current, candidate);

        assertEquals(List.of("messages"), result.liveChanges());
        assertTrue(result.restartRequiredChanges().isEmpty());
        assertTrue(result.appliedResult().applied());
    }

    @Test
    void nodeChangesAreRejectedAsRestartRequiredWithoutClaimingTheyWereApplied() {
        PaperRuntimeConfig current = config("smoke-node", "same");
        PaperRuntimeConfig candidate = config("replacement-node", "same");

        PaperRuntimeConfigReloadResult result = PaperRuntimeConfigReloadResult.analyze(current, candidate);

        assertFalse(result.applied());
        assertEquals(List.of("node"), result.restartRequiredChanges());
    }

    private static PaperRuntimeConfig config(String nodeId, String marker) {
        return PaperRuntimeConfigLoader.loadV2(List.of(
            new ConfigSource("paper/config-v2/config.yml", 10, "language: ko_kr\n"),
            new ConfigSource("paper/config-v2/runtime.yml", 20, "node:\n  id: " + nodeId + "\n"),
            new ConfigSource("paper/config-v2/ui/messages/ko_kr.yml", 30, "reload-test-marker: " + marker + "\n")
        ), value -> value == null ? "" : value.trim());
    }
}
