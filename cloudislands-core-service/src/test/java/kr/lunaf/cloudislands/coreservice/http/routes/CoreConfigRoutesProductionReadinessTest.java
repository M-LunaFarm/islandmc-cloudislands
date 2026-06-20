package kr.lunaf.cloudislands.coreservice.http.routes;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CoreConfigRoutesProductionReadinessTest {
    @Test
    void configSurfaceExposesProductionGaDrillMatrix() throws Exception {
        String source = Files.readString(repositoryRoot().resolve("cloudislands-core-service/src/main/java/kr/lunaf/cloudislands/coreservice/http/routes/CoreConfigRoutes.java"));

        assertTrue(source.contains("\\\"productionGaDrillContract\\\""));
        assertTrue(source.contains("\\\"productionGaDrillEvidence\\\""));
        assertTrue(source.contains("\\\"productionGaFailureInjectionScenarios\\\""));
        assertTrue(source.contains("\\\"versionCompatibilityMatrix\\\""));
        assertTrue(source.contains("\\\"versionRollingUpgradeOrder\\\""));
        assertTrue(source.contains("\\\"versionSupportedPaper\\\""));
        assertTrue(source.contains("\\\"versionSupportedVelocity\\\""));
        assertTrue(source.contains("\\\"versionProtocolChangePolicy\\\""));
        assertTrue(source.contains("ProductionGaDrillMatrix.CONTRACT"));
        assertTrue(source.contains("ProductionGaDrillMatrix.evidenceSummary()"));
        assertTrue(source.contains("ProductionGaDrillMatrix.failureInjectionSummary()"));
        assertTrue(source.contains("VersionCompatibilityPolicy.matrixSummary()"));
        assertTrue(source.contains("VersionCompatibilityPolicy.rollingUpgradeOrderSummary()"));
    }

    private static Path repositoryRoot() {
        Path path = Path.of("").toAbsolutePath();
        while (path != null && !Files.exists(path.resolve("settings.gradle.kts"))) {
            path = path.getParent();
        }
        if (path == null) {
            throw new IllegalStateException("Repository root not found");
        }
        return path;
    }
}
