package kr.lunaf.cloudislands.paper.platform.compatibility;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class Paper121BuildWiringPolicyTest {
    @Test
    void gradleExposesPaperOneTwentyOneCompileAndBootSmokeTasks() throws Exception {
        String build = Files.readString(Path.of("../build.gradle.kts"));

        assertTrue(build.contains("tasks.register(\"paper121Compile\")"));
        assertTrue(build.contains("Compiles the CloudIslands Paper plugin against the Paper 1.21 API baseline."));
        assertTrue(build.contains("tasks.register(\"paper121BootSmoke\")"));
        assertTrue(build.contains("Boots the Paper 1.21 baseline server and verifies the 1.21 family adapter loads."));
        assertTrue(build.contains("dependsOn(tasks.named(\"paperBootSmoke\"))"));
    }
}
