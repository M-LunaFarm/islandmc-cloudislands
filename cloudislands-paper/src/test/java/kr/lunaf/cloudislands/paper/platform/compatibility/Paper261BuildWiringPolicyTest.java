package kr.lunaf.cloudislands.paper.platform.compatibility;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class Paper261BuildWiringPolicyTest {
    @Test
    void gradleExposesPaperTwentySixOneCompileAndBootTasks() throws Exception {
        String build = Files.readString(Path.of("../build.gradle.kts"));
        String matrix = Files.readString(Path.of("../gradle/minecraft-versions.toml"));

        assertTrue(build.contains("tasks.register(entry.compileTaskName)"));
        assertTrue(build.contains("tasks.register<Exec>(entry.bootSmokeTaskName)"));
        assertTrue(build.contains("--version\", entry.bootVersion"));
        assertTrue(matrix.contains("id = \"paper-26.1\""));
        assertTrue(matrix.contains("normalizedRange = \"26.1.x\""));
        assertTrue(matrix.contains("adapterClass = \"kr.lunaf.cloudislands.paper.platform.compatibility.Paper261Adapter\""));
        assertTrue(matrix.contains("bootSmokeEnabled = false"));
        assertTrue(matrix.contains("experimental = true"));
    }
}
