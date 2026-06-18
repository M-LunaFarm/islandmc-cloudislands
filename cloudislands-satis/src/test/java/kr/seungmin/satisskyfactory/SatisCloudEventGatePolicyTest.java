package kr.seungmin.satisskyfactory;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SatisCloudEventGatePolicyTest {
    @Test
    void lifecycleEventsAreDroppedWhenLifecycleStateIsDisabled() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/seungmin/satisskyfactory/SatisSkyFactoryPlugin.java"));

        assertTrue(source.contains("private void runSatisLifecycle(UUID islandId, String operation, Runnable action)"));
        assertTrue(source.contains("if (islandId == null || database == null || !operationalFeatureEnabled(\"lifecycle\") || !lifecycleStateEnabled())"));
        assertTrue(source.contains("if (!isEnabled() || database == null || !operationalFeatureEnabled(\"lifecycle\") || !lifecycleStateEnabled())"));
    }

    @Test
    void featureSpecificLifecycleEventsRequireTheirFeatureGate() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/seungmin/satisskyfactory/SatisSkyFactoryPlugin.java"));

        assertTrue(source.contains("private void runSatisLifecycle(String feature, UUID islandId, String operation, Runnable action)"));
        assertTrue(source.contains("if (!operationalFeatureEnabled(feature))"));
        assertTrue(source.contains("runSatisLifecycle(\"members\", event.islandId()"));
        assertTrue(source.contains("runSatisLifecycle(\"permissions\", event.islandId()"));
        assertTrue(source.contains("runSatisLifecycle(\"chat\", event.islandId()"));
        assertTrue(source.contains("runSatisLifecycle(\"storage\", event.islandId()"));
        assertTrue(source.contains("runSatisLifecycle(\"biomes\", event.islandId()"));
        assertTrue(source.contains("runSatisLifecycle(\"warps\", event.islandId()"));
        assertTrue(source.contains("runSatisLifecycle(\"level-values\", event.islandId()"));
        assertTrue(source.contains("runSatisLifecycle(\"upgrades\", event.islandId()"));
    }

    @Test
    void routeEventsOnlyPublishDiagnosticStateWhenRouteEventGateIsEnabled() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/seungmin/satisskyfactory/SatisSkyFactoryPlugin.java"));

        assertTrue(source.contains("private boolean routeEventStateEnabled()"));
        assertTrue(source.contains("return cloudIslandsApi != null && operationalFeatureEnabled(\"addon-state\") && operationalFeatureEnabled(\"route-events\");"));
        assertTrue(source.contains("if (!routeEventStateEnabled()) {\n            recordRouteEventBlocked();\n            return;\n        }"));
        assertTrue(source.contains("last-route-policy\", \"diagnostic-state-only-no-routing-authority"));
        assertTrue(source.contains("last-route-player-visible-topology\", \"hidden"));
    }
}
