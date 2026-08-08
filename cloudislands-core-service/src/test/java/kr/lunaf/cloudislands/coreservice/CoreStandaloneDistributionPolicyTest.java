package kr.lunaf.cloudislands.coreservice;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CoreStandaloneDistributionPolicyTest {
    @Test
    void coreShipsAsAnExecutableStandaloneReleaseAssetWithoutChangingVelocity() throws Exception {
        Path repository = Path.of("").toAbsolutePath().normalize().getParent();
        String coreBuild = Files.readString(Path.of("build.gradle.kts"));
        String distribution = Files.readString(repository.resolve("gradle/distribution.gradle.kts"));
        String release = Files.readString(repository.resolve(".github/workflows/release.yml"));
        String velocityBuild = Files.readString(repository.resolve("cloudislands-velocity/build.gradle.kts"));

        assertTrue(coreBuild.contains("val standaloneJar by tasks.registering(Jar::class)"));
        assertTrue(coreBuild.contains("archiveBaseName.set(\"CloudIslands-Core\")"));
        assertTrue(coreBuild.contains("\"Main-Class\" to \"kr.lunaf.cloudislands.coreservice.CloudIslandsCoreApplication\""));
        assertTrue(coreBuild.contains("val generateStandaloneServices by tasks.registering"));
        assertTrue(coreBuild.contains("META-INF/services/java.sql.Driver"));
        assertTrue(coreBuild.contains("val verifyStandaloneJar by tasks.registering"));
        assertTrue(distribution.contains("val standaloneJar = coreService.tasks.named<Jar>(\"standaloneJar\")"));
        assertTrue(release.contains("build/dist/services/core/CloudIslands-Core-*.jar"));
        assertFalse(velocityBuild.contains("project(\":cloudislands-core-service\")"));
        assertFalse(velocityBuild.contains("kr.lunaf.cloudislands.coreservice"));
    }
}
