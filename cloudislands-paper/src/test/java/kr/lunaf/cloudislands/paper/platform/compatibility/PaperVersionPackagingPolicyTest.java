package kr.lunaf.cloudislands.paper.platform.compatibility;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PaperVersionPackagingPolicyTest {
    @Test
    void gradleVerifiesAdapterClassesInFinalPaperArtifact() throws Exception {
        String build = Files.readString(Path.of("../build.gradle.kts"));
        String versionMatrixGates = Files.readString(Path.of("../gradle/version-matrix-gates.gradle.kts"));
        String buildSurface = build + "\n" + versionMatrixGates;
        String matrix = Files.readString(Path.of("../gradle/minecraft-versions.toml"));

        assertTrue(buildSurface.contains("verifyAdapterPackaging"));
        assertTrue(buildSurface.contains("verifyVersionPackaging"));
        assertTrue(buildSurface.contains("ZipFile(paperJar.get().archiveFile.get().asFile)"));
        assertTrue(buildSurface.contains("minecraftVersionMatrix.entries.map { it.adapterJarEntry }"));
        assertTrue(buildSurface.contains("PaperRuntimeCompatibility.class"));
        assertTrue(buildSurface.contains("PaperRuntimeCompatibility\\$RuntimeSelection.class"));
        assertTrue(buildSurface.contains("PaperAdapterSelfTest.class"));
        assertTrue(buildSurface.contains("PaperVersionAdapterRegistry.class"));
        assertTrue(buildSurface.contains("AbstractPaper26Adapter.class"));
        assertTrue(buildSurface.contains("DefaultPaperVersionAdapter.class"));
        assertTrue(buildSurface.contains("RuntimeCapabilities.class"));
        assertTrue(buildSurface.contains("ServerVersion.class"));
        assertTrue(buildSurface.contains("VersionRange.class"));
        assertTrue(buildSurface.contains("Duplicate class/resource entries in final Paper artifact"));
        assertTrue(buildSurface.contains("tasks.named(\"check\")"));
        assertTrue(buildSurface.contains("dependsOn(tasks.named(\"verifyAdapterPackaging\"))"));
        assertTrue(buildSurface.contains("tasks.named(\"distBundle\")"));
        assertTrue(matrix.contains("adapterClass = \"kr.lunaf.cloudislands.paper.platform.compatibility.Paper121FamilyAdapter\""));
        assertTrue(matrix.contains("adapterClass = \"kr.lunaf.cloudislands.paper.platform.compatibility.Paper261Adapter\""));
        assertTrue(matrix.contains("adapterClass = \"kr.lunaf.cloudislands.paper.platform.compatibility.Paper262Adapter\""));
    }
}
