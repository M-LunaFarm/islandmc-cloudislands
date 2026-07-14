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
        String wrapper = Files.readString(root.resolve("gradle/wrapper/gradle-wrapper.properties"));

        assertTrue(versionCatalog.contains("cloudislands = \"1.1.208\""));
        assertTrue(versionCatalog.contains("java-current = \"21\""));
        assertTrue(versionCatalog.contains("minecraft-baseline = \"1.21.11\""));
        assertTrue(versionCatalog.contains("velocity-api = \"3.5.0-SNAPSHOT\""));

        assertTrue(readme.contains("Version: `1.1.208`"));
        assertTrue(readme.contains("Current release: `v1.1.208`"));
        assertTrue(minecraftMatrix.contains("id = \"paper-1.21\""));
        assertTrue(minecraftMatrix.contains("id = \"paper-26.1\""));
        assertTrue(minecraftMatrix.contains("id = \"paper-26.2\""));
        assertTrue(readme.contains("<!-- minecraft-version-matrix:start -->"));
        assertTrue(readme.contains("| Paper `1.21.x` | `paper121Compile` | `paper121BootSmoke` | release-supported |"));
        assertTrue(readme.contains("| Paper `26.1.x` | `paper261Compile` | `paper261BootSmoke` | release-supported |"));
        assertTrue(readme.contains("| Paper `26.2.x` | `paper262Compile` | pending official Paper build | experimental compile-only |"));
        assertTrue(readme.contains("Velocity `3.5.0-SNAPSHOT` remains the proxy compile baseline"));
        assertTrue(readme.contains("paper121Compile"));
        assertTrue(readme.contains("paper121BootSmoke"));
        assertTrue(readme.contains("compileAllMinecraftVersions"));
        assertTrue(readme.contains("bootSmokeAllStableMinecraftVersions"));
        assertTrue(readme.contains("verifyReadmeVersionTable"));

        assertTrue(workflow.contains("platform: paper-1.21.11"));
        assertTrue(workflow.contains("platform: paper-26.1"));
        assertTrue(workflow.contains("java: \"25\""));
        assertTrue(workflow.contains("boot-task: paper261BootSmoke"));
        assertTrue(workflow.contains("java: \"21\""));
        assertTrue(workflow.contains("minecraft-baseline: \"1.21.11\""));
        assertTrue(workflow.contains("Set up Java 21 toolchain"));
        assertTrue(workflow.contains("Set up Java 25 build launcher"));
        assertTrue(workflow.contains("gradle/actions/setup-gradle@v6"));
        assertTrue(wrapper.contains("gradle-9.1.0-bin.zip"));
        assertTrue(wrapper.contains("distributionSha256Sum=a17ddd85a26b6a7f5ddb71ff8b05fc5104c0202c6e64782429790c933686c806"));
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
