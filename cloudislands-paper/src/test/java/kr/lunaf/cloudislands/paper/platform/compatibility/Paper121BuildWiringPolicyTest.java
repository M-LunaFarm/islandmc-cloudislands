package kr.lunaf.cloudislands.paper.platform.compatibility;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class Paper121BuildWiringPolicyTest {
    @Test
    void gradleExposesPaperOneTwentyOneCompileAndBootSmokeTasks() throws Exception {
        String build = Files.readString(Path.of("../build.gradle.kts"));
        String matrix = Files.readString(Path.of("../gradle/minecraft-versions.toml"));

        assertTrue(build.contains("val paperVersionCompileTasks = minecraftVersionMatrix.compileEntries.associateWith"));
        assertTrue(build.contains("tasks.register(entry.compileTaskName)"));
        assertTrue(build.contains("tasks.register<Exec>(entry.bootSmokeTaskName)"));
        assertTrue(build.contains("tasks.register(\"paperBootSmoke\")"));
        assertTrue(build.contains("minecraftVersionMatrix.latestStable.bootSmokeTaskName"));
        assertTrue(matrix.contains("id = \"paper-1.21\""));
        assertTrue(matrix.contains("bootSmokeEnabled = true"));
        assertTrue(matrix.contains("releaseSupported = true"));
    }
}
