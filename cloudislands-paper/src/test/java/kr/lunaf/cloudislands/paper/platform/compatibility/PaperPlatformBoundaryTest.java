package kr.lunaf.cloudislands.paper.platform.compatibility;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class PaperPlatformBoundaryTest {
    @Test
    void coreModulesDoNotImportMinecraftServerApis() throws Exception {
        Path root = repositoryRoot();
        try (Stream<Path> files = Stream.of(
                root.resolve("cloudislands-api/src/main/java"),
                root.resolve("cloudislands-common/src/main/java"),
                root.resolve("cloudislands-protocol/src/main/java"),
                root.resolve("cloudislands-core-service/src/main/java")
            ).flatMap(PaperPlatformBoundaryTest::javaFiles)) {
            String violations = files
                .filter(PaperPlatformBoundaryTest::importsMinecraftServerApi)
                .map(path -> root.relativize(path).toString())
                .sorted()
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");

            assertTrue(violations.isBlank(), violations);
        }
    }

    private static Path repositoryRoot() {
        Path current = Path.of(".").toAbsolutePath().normalize();
        if (Files.exists(current.resolve("cloudislands-api"))) {
            return current;
        }
        Path parent = current.getParent();
        if (parent != null && Files.exists(parent.resolve("cloudislands-api"))) {
            return parent;
        }
        return current;
    }

    @Test
    void paperCompatibilityBoundaryTypesExist() {
        PaperCapabilities capabilities = new DetectedPaperCapabilities();

        assertTrue(capabilities.supportsMinorApiVersion());
    }

    @Test
    void bukkitSchedulerAccessStaysInsidePlatformSchedulerAdapter() throws Exception {
        Path root = repositoryRoot();
        Path paperSource = root.resolve("cloudislands-paper/src/main/java");
        try (Stream<Path> files = javaFiles(paperSource)) {
            String violations = files
                .filter(path -> !root.relativize(path).toString().contains("/platform/scheduler/"))
                .filter(PaperPlatformBoundaryTest::directlyUsesBukkitScheduler)
                .map(path -> root.relativize(path).toString())
                .sorted()
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");

            assertTrue(violations.isBlank(), violations);
        }
    }

    @Test
    void playerAndWorldRuntimeAccessStaysInsidePlatformAdapters() throws Exception {
        Path root = repositoryRoot();
        Path paperSource = root.resolve("cloudislands-paper/src/main/java");
        try (Stream<Path> files = javaFiles(paperSource)) {
            String violations = files
                .filter(path -> {
                    String relative = root.relativize(path).toString();
                    return !relative.contains("/platform/player/")
                        && !relative.contains("/platform/world/");
                })
                .filter(PaperPlatformBoundaryTest::directlyUsesPlayerOrWorldRuntime)
                .map(path -> root.relativize(path).toString())
                .sorted()
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");

            assertTrue(violations.isBlank(), violations);
        }
    }

    @Test
    void bukkitEventBusAccessStaysInsidePlatformEventAdapter() throws Exception {
        Path root = repositoryRoot();
        Path paperSource = root.resolve("cloudislands-paper/src/main/java");
        try (Stream<Path> files = javaFiles(paperSource)) {
            String violations = files
                .filter(path -> !root.relativize(path).toString().contains("/platform/event/"))
                .filter(PaperPlatformBoundaryTest::directlyUsesBukkitEventBus)
                .map(path -> root.relativize(path).toString())
                .sorted()
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");

            assertTrue(violations.isBlank(), violations);
        }
    }

    @Test
    void runtimeModulesDoNotUseRegexJsonParsers() throws Exception {
        Path root = repositoryRoot();
        try (Stream<Path> files = Stream.of(
                root.resolve("cloudislands-common/src/main/java"),
                root.resolve("cloudislands-storage/src/main/java"),
                root.resolve("cloudislands-core-service/src/main/java"),
                root.resolve("cloudislands-paper/src/main/java"),
                root.resolve("cloudislands-velocity/src/main/java")
            ).flatMap(PaperPlatformBoundaryTest::javaFiles)) {
            String violations = files
                .filter(PaperPlatformBoundaryTest::directlyUsesRegex)
                .map(path -> root.relativize(path).toString())
                .sorted()
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");

            assertTrue(violations.isBlank(), violations);
        }
    }

    private static Stream<Path> javaFiles(Path root) {
        try {
            if (Files.notExists(root)) {
                return Stream.empty();
            }
            return Files.walk(root).filter(path -> path.toString().endsWith(".java"));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static boolean importsMinecraftServerApi(Path path) {
        try {
            String source = Files.readString(path);
            return source.contains("import org.bukkit.")
                || source.contains("import io.papermc.")
                || source.contains("import com.velocitypowered.")
                || source.contains("import net.minecraft.");
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static boolean directlyUsesBukkitScheduler(Path path) {
        try {
            String source = Files.readString(path);
            return source.contains(".getServer().getScheduler()")
                || source.contains("Bukkit.getScheduler()");
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static boolean directlyUsesBukkitEventBus(Path path) {
        try {
            String source = Files.readString(path);
            return source.contains(".getPluginManager().registerEvents(")
                || source.contains("Bukkit.getPluginManager().callEvent(")
                || source.contains(".getPluginManager().callEvent(");
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static boolean directlyUsesRegex(Path path) {
        try {
            String source = Files.readString(path);
            return source.contains("java.util.regex")
                || source.contains("Pattern.compile")
                || source.contains("Matcher ");
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static boolean directlyUsesPlayerOrWorldRuntime(Path path) {
        try {
            for (String line : Files.readAllLines(path)) {
                if (line.contains("Bukkit.getPlayer(")
                    || line.contains("Bukkit.getWorld(")
                    || line.contains(".getServer().getWorld(")
                    || line.contains("Bukkit.createWorld(")
                    || line.contains(".getServer().createWorld(")
                    || line.contains("Bukkit.unloadWorld(")
                    || line.contains(".getServer().unloadWorld(")) {
                    return true;
                }
                if (line.contains(".teleport(") && !line.contains("players.teleport(")) {
                    return true;
                }
            }
            return false;
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
