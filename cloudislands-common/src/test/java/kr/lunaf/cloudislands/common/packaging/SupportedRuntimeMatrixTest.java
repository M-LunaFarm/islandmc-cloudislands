package kr.lunaf.cloudislands.common.packaging;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SupportedRuntimeMatrixTest {
    @Test
    void readmeAndCiMatrixPublishTheCentralSupportedRuntimeBaseline() throws IOException {
        Path root = repositoryRoot();
        String versionCatalog = Files.readString(root.resolve("gradle/libs.versions.toml"));
        String readme = Files.readString(root.resolve("README.md"));
        String workflow = Files.readString(root.resolve(".github/workflows/build.yml"));

        assertTrue(versionCatalog.contains("cloudislands = \"1.0.1\""));
        assertTrue(versionCatalog.contains("java-current = \"21\""));
        assertTrue(versionCatalog.contains("minecraft-baseline = \"1.21.11\""));
        assertTrue(versionCatalog.contains("velocity-api = \"3.5.0-SNAPSHOT\""));

        assertTrue(readme.contains("Version: `1.0.1`"));
        assertTrue(readme.contains("Current release: `v1.0.1`"));
        assertTrue(readme.contains("| Paper `1.21.11` | compile baseline | boot smoke task exists | Core integration smoke is separate |"));
        assertTrue(readme.contains("| Paper `1.21.x` family | not yet matrix-verified | not yet family-verified | not yet version-parity verified |"));
        assertTrue(readme.contains("| Paper `26.1` | not defined | not verified | not verified |"));
        assertTrue(readme.contains("| Paper `26.2` | not defined | not verified | not verified |"));
        assertTrue(readme.contains("| Velocity `3.5.0-SNAPSHOT` | compile baseline | boot smoke task exists | routing integration is partial |"));
        assertTrue(readme.contains("It does not yet expose the multi-version matrix tasks required for release certification."));

        assertTrue(workflow.contains("platform: paper-1.21.11"));
        assertTrue(workflow.contains("java: \"21\""));
        assertTrue(workflow.contains("minecraft-baseline: \"1.21.11\""));
        assertTrue(workflow.contains("java-version: ${{ matrix.java }}"));
        assertTrue(workflow.contains("cloudislands-dist-${{ matrix.platform }}-java${{ matrix.java }}"));
    }

    private static Path repositoryRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            if (Files.exists(current.resolve("settings.gradle.kts"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Could not locate repository root");
    }
}
