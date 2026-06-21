package kr.lunaf.cloudislands.coreservice.http.routes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import kr.lunaf.cloudislands.common.json.SimpleJson;
import kr.lunaf.cloudislands.coreservice.InMemoryNodeRegistry;
import kr.lunaf.cloudislands.coreservice.config.CoreServiceConfig;
import org.junit.jupiter.api.Test;

class CoreConfigRoutesProductionReadinessTest {
    @Test
    void configSurfaceExposesProductionGaDrillMatrix() {
        CoreConfigRoutes routes = new CoreConfigRoutes(CoreServiceConfig.fromEnvironment(), new InMemoryNodeRegistry(6));
        Map<?, ?> summary = SimpleJson.object(SimpleJson.parse(routes.configSummaryJson()));

        assertEquals(kr.lunaf.cloudislands.common.observability.ProductionGaDrillMatrix.CONTRACT, SimpleJson.text(summary.get("productionGaDrillContract")));
        assertEquals(kr.lunaf.cloudislands.common.observability.ProductionGaDrillMatrix.evidenceSummary(), SimpleJson.text(summary.get("productionGaDrillEvidence")));
        assertEquals(kr.lunaf.cloudislands.common.observability.ProductionGaDrillMatrix.failureInjectionSummary(), SimpleJson.text(summary.get("productionGaFailureInjectionScenarios")));
        assertEquals(kr.lunaf.cloudislands.common.observability.VersionCompatibilityPolicy.matrixSummary(), SimpleJson.text(summary.get("versionCompatibilityMatrix")));
        assertEquals(kr.lunaf.cloudislands.common.observability.VersionCompatibilityPolicy.rollingUpgradeOrderSummary(), SimpleJson.text(summary.get("versionRollingUpgradeOrder")));
        assertEquals(kr.lunaf.cloudislands.common.observability.VersionCompatibilityPolicy.SUPPORTED_PAPER_VERSION, SimpleJson.text(summary.get("versionSupportedPaper")));
        assertEquals(kr.lunaf.cloudislands.common.observability.VersionCompatibilityPolicy.SUPPORTED_VELOCITY_VERSION, SimpleJson.text(summary.get("versionSupportedVelocity")));
        assertTrue(summary.containsKey("versionProtocolChangePolicy"));
    }
}
