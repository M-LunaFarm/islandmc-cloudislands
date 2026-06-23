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
        String minecraftMatrix = Files.readString(root.resolve("gradle/minecraft-versions.toml"));
        String readme = Files.readString(root.resolve("README.md"));
        String workflow = Files.readString(root.resolve(".github/workflows/build.yml"));

        assertTrue(versionCatalog.contains("cloudislands = \"1.0.1\""));
        assertTrue(versionCatalog.contains("java-current = \"21\""));
        assertTrue(versionCatalog.contains("minecraft-baseline = \"1.21.11\""));
        assertTrue(versionCatalog.contains("velocity-api = \"3.5.0-SNAPSHOT\""));

        assertTrue(readme.contains("Version: `1.0.1`"));
        assertTrue(readme.contains("Current release: `v1.0.1`"));
        assertTrue(minecraftMatrix.contains("id = \"paper-1.21\""));
        assertTrue(minecraftMatrix.contains("id = \"paper-26.1\""));
        assertTrue(minecraftMatrix.contains("id = \"paper-26.2\""));
        assertTrue(readme.contains("<!-- minecraft-version-matrix:start -->"));
        assertTrue(readme.contains("| Paper `1.21.x` | `paper121Compile` | `paper121BootSmoke` | release-supported |"));
        assertTrue(readme.contains("| Paper `26.1.x` | `paper261Compile` | pending official Paper build | experimental compile-only |"));
        assertTrue(readme.contains("| Paper `26.2.x` | `paper262Compile` | pending official Paper build | experimental compile-only |"));
        assertTrue(readme.contains("Velocity `3.5.0-SNAPSHOT` remains the proxy compile baseline"));
        assertTrue(readme.contains("paper121Compile"));
        assertTrue(readme.contains("paper121BootSmoke"));
        assertTrue(readme.contains("compileAllMinecraftVersions"));
        assertTrue(readme.contains("bootSmokeAllStableMinecraftVersions"));
        assertTrue(readme.contains("verifyReadmeVersionTable"));

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
