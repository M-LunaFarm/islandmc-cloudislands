package kr.lunaf.cloudislands.velocity.config;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class VelocityConfigSurfaceTest {
    @Test
    void velocityConfigSurfaceDoesNotExposeDatabaseSettings() throws Exception {
        Path root = projectRoot();
        String resources = readTree(root.resolve("cloudislands-velocity/src/main/resources"));
        String statusReporter = Files.readString(root.resolve("cloudislands-velocity/src/main/java/kr/lunaf/cloudislands/velocity/health/VelocityStatusReporter.java"));
        String buildScript = Files.readString(root.resolve("cloudislands-velocity/build.gradle.kts"));
        String configLoader = Files.readString(root.resolve("cloudislands-velocity/src/main/java/kr/lunaf/cloudislands/velocity/config/VelocityConfigLoader.java"));
        String surface = resources + "\n" + statusReporter + "\n" + buildScript;

        assertFalse(surface.matches("(?is).*\\b(database|jdbc|postgresql|mysql|mariadb)\\b.*"),
            "Velocity config, health, and manifest surface must not expose database settings");
        assertFalse((surface + "\n" + configLoader).matches("(?is).*setup[.-]core-api.*"),
            "Velocity must expose one canonical core-api config path without setup aliases");
    }

    private static String readTree(Path root) throws Exception {
        StringBuilder builder = new StringBuilder();
        try (var paths = Files.walk(root)) {
            for (Path path : paths.filter(Files::isRegularFile).sorted().toList()) {
                builder.append(Files.readString(path)).append('\n');
            }
        }
        return builder.toString();
    }

    private static Path projectRoot() {
        Path cwd = Path.of("").toAbsolutePath();
        return Files.exists(cwd.resolve("settings.gradle.kts")) ? cwd : cwd.getParent();
    }
}
