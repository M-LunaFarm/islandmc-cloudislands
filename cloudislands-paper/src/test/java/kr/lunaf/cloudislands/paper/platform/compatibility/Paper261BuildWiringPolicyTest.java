package kr.lunaf.cloudislands.paper.platform.compatibility;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class Paper261BuildWiringPolicyTest {
    @Test
    void gradleExposesPaperTwentySixOneCompileAndBootTasks() throws Exception {
        String build = Files.readString(Path.of("../gradle/version-matrix-gates.gradle.kts"));
        String matrix = Files.readString(Path.of("../gradle/minecraft-versions.toml"));

        assertTrue(build.contains("tasks.register<JavaCompile>(entry.compileTaskName)"));
        assertTrue(build.contains("resolutionStrategy.force(\"io.papermc.paper:paper-api:${entry.paperApiVersion}\")"));
        assertTrue(build.contains("javaCompiler.set(versionMatrixJavaToolchains.compilerFor"));
        assertTrue(build.contains("languageVersion.set(JavaLanguageVersion.of(entry.javaVersion))"));
        assertTrue(build.contains("options.release.set(paperTargetRelease)"));
        assertTrue(build.contains("tasks.register<Exec>(entry.bootSmokeTaskName)"));
        assertTrue(build.contains("--version\", entry.bootVersion"));
        assertTrue(build.contains("\"--java-command\", versionMatrixJavaToolchains.launcherFor"));
        assertTrue(matrix.contains("id = \"paper-26.1\""));
        assertTrue(matrix.contains("normalizedRange = \"26.1.x\""));
        assertTrue(matrix.contains("adapterClass = \"kr.lunaf.cloudislands.paper.platform.compatibility.Paper261Adapter\""));
        assertTrue(matrix.contains("paperApiVersion = \"26.1.2.build.74-stable\""));
        assertTrue(matrix.contains("javaVersion = 25"));
        assertTrue(matrix.contains("bootVersion = \"26.1.2\""));
        assertTrue(matrix.contains("bootSmokeEnabled = true"));
        assertTrue(matrix.contains("releaseSupported = true"));
    }
}
