package kr.lunaf.cloudislands.coreservice.http.routes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Locale;
import kr.lunaf.cloudislands.common.json.SimpleJson;
import kr.lunaf.cloudislands.coreservice.InMemoryNodeRegistry;
import kr.lunaf.cloudislands.coreservice.config.CoreServiceConfig;
import kr.lunaf.cloudislands.coreservice.security.CoreAuthMode;
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

    @Test
    void configSurfaceReportsEffectiveAuthMode() {
        CoreServiceConfig config = CoreServiceConfig.fromEnvironment();
        CoreAuthMode authMode = config.authMode();
        CoreConfigRoutes routes = new CoreConfigRoutes(config, new InMemoryNodeRegistry(6));
        Map<?, ?> summary = SimpleJson.object(SimpleJson.parse(routes.configSummaryJson()));

        assertEquals(authMode.name(), SimpleJson.text(summary.get("authMode")));
        assertEquals(authMode.name(), SimpleJson.text(summary.get("coreApiAuthMode")));
        assertEquals(authMode.name().toLowerCase(Locale.ROOT).replace('_', '-'), SimpleJson.text(summary.get("coreApiAuthPolicy")));
        assertEquals(authMode == CoreAuthMode.MTLS_REQUIRED, Boolean.TRUE.equals(summary.get("coreApiMtlsRequired")));
        assertEquals(authMode.acceptsMtls(), Boolean.TRUE.equals(summary.get("coreApiMtlsAccepted")));
        assertEquals(config.requireMtls(), Boolean.TRUE.equals(summary.get("coreApiRequireMtlsFlag")));
    }
}
