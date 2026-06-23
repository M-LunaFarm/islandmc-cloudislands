package kr.lunaf.cloudislands.paper.platform.compatibility;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PaperVersionPackagingPolicyTest {
    @Test
    void gradleVerifiesAdapterClassesInFinalPaperArtifact() throws Exception {
        String build = Files.readString(Path.of("../build.gradle.kts"));
        String matrix = Files.readString(Path.of("../gradle/minecraft-versions.toml"));

        assertTrue(build.contains("verifyAdapterPackaging"));
        assertTrue(build.contains("verifyVersionPackaging"));
        assertTrue(build.contains("ZipFile(paperJar.get().archiveFile.get().asFile)"));
        assertTrue(build.contains("minecraftVersionMatrix.entries.map { it.adapterJarEntry }"));
        assertTrue(build.contains("PaperRuntimeCompatibility.class"));
        assertTrue(build.contains("PaperRuntimeCompatibility\\$RuntimeSelection.class"));
        assertTrue(build.contains("PaperAdapterSelfTest.class"));
        assertTrue(build.contains("PaperVersionAdapterRegistry.class"));
        assertTrue(build.contains("AbstractPaper26Adapter.class"));
        assertTrue(build.contains("DefaultPaperVersionAdapter.class"));
        assertTrue(build.contains("RuntimeCapabilities.class"));
        assertTrue(build.contains("ServerVersion.class"));
        assertTrue(build.contains("VersionRange.class"));
        assertTrue(build.contains("Duplicate class/resource entries in final Paper artifact"));
        assertTrue(build.contains("tasks.named(\"check\")"));
        assertTrue(build.contains("dependsOn(verifyAdapterPackaging)"));
        assertTrue(build.contains("tasks.named(\"distBundle\")"));
        assertTrue(matrix.contains("adapterClass = \"kr.lunaf.cloudislands.paper.platform.compatibility.Paper121FamilyAdapter\""));
        assertTrue(matrix.contains("adapterClass = \"kr.lunaf.cloudislands.paper.platform.compatibility.Paper261Adapter\""));
        assertTrue(matrix.contains("adapterClass = \"kr.lunaf.cloudislands.paper.platform.compatibility.Paper262Adapter\""));
    }
}
