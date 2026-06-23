package kr.lunaf.cloudislands.paper.platform.compatibility;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class Paper261BuildWiringPolicyTest {
    @Test
    void gradleExposesPaperTwentySixOneCompileAndBootTasks() throws Exception {
        String build = Files.readString(Path.of("../build.gradle.kts"));

        assertTrue(build.contains("tasks.register(\"paper261Compile\")"));
        assertTrue(build.contains("Compiles the CloudIslands Paper plugin with the Paper 26.1 adapter included."));
        assertTrue(build.contains("tasks.register<Exec>(\"paper261BootSmoke\")"));
        assertTrue(build.contains("Boots a Paper 26.1 server when an official stable build is available."));
        assertTrue(build.contains("--version\", \"26.1\""));
        assertTrue(build.contains("paper-26.1"));
    }
}
