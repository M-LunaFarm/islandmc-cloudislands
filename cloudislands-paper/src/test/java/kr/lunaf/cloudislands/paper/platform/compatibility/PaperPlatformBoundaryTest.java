package kr.lunaf.cloudislands.paper.platform.compatibility;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
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

    @Test
    void guiClassesDoNotInvokePlayerCommands() throws Exception {
        Path root = repositoryRoot();
        Path guiSource = root.resolve("cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/gui");
        try (Stream<Path> files = javaFiles(guiSource)) {
            String violations = files
                .filter(path -> containsAny(path, "performCommand(", "dispatchCommand("))
                .map(path -> root.relativize(path).toString())
                .sorted()
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");

            assertTrue(violations.isBlank(), violations);
        }
    }

    @Test
    void guiClassesDoNotIdentifyMenusByTitleString() throws Exception {
        Path root = repositoryRoot();
        Path guiSource = root.resolve("cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/gui");
        try (Stream<Path> files = javaFiles(guiSource)) {
            String violations = files
                .filter(path -> contains(path, "getView().getTitle()"))
                .map(path -> root.relativize(path).toString())
                .sorted()
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");

            assertTrue(violations.isBlank(), violations);
        }
    }

    @Test
    void guiClassesDoNotParseControlDataFromLore() throws Exception {
        Path root = repositoryRoot();
        Path guiSource = root.resolve("cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/gui");
        try (Stream<Path> files = javaFiles(guiSource)) {
            String violations = files
                .filter(path -> containsAny(path,
                    "loreValue(",
                    "templateId=",
                    "플래그=",
                    "role=",
                    "permission=",
                    "플레이어=",
                    "대상=",
                    "초대 ID=",
                    "섬 ID=",
                    "homeName=",
                    "warpPublic=",
                    "biomeKey=",
                    "limitValue=",
                    "missionKey=",
                    "업그레이드=",
                    "번호="))
                .map(path -> root.relativize(path).toString())
                .sorted()
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");

            assertTrue(violations.isBlank(), violations);
        }
    }

    @Test
    void guiClassesDoNotParseCoreJsonResponses() throws Exception {
        Path root = repositoryRoot();
        Path guiSource = root.resolve("cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/gui");
        try (Stream<Path> files = javaFiles(guiSource)) {
            String violations = files
                .filter(path -> containsAny(path,
                    "thenAccept(body",
                    "String body",
                    "(String body",
                    "body.indexOf(",
                    "body.substring(",
                    "jsonStringEnd(",
                    "objectEnd(",
                    "rawScalar("))
                .map(path -> root.relativize(path).toString())
                .sorted()
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");

            assertTrue(violations.isBlank(), violations);
        }
    }

    @Test
    void guiClassesDoNotExposeDestructiveConfirmActionsDirectly() throws Exception {
        Path root = repositoryRoot();
        Path guiSource = root.resolve("cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/gui");
        try (Stream<Path> files = javaFiles(guiSource)) {
            String violations = files
                .filter(path -> !path.getFileName().toString().equals("IslandConfirmationMenu.java")
                    && !path.getFileName().toString().equals("IslandDangerMenu.java"))
                .filter(path -> containsAny(path,
                    "\"island.warp.delete.confirm\"",
                    "\"island.member.remove.confirm\"",
                    "\"island.ban.pardon.confirm\"",
                    "\"island.snapshot.restore.confirm\"",
                    "\"island.danger.reset.confirm\"",
                    "\"island.danger.delete.confirm\""))
                .map(path -> root.relativize(path).toString())
                .sorted()
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");

            assertTrue(violations.isBlank(), violations);
        }
    }

    @Test
    void velocityDefaultConfigDoesNotExposeDatabaseOwnership() throws Exception {
        Path root = repositoryRoot();
        Path config = root.resolve("cloudislands-velocity/src/main/resources/config.yaml");
        String source = Files.readString(config);

        assertTrue(!source.contains("database:"), "Velocity default config must not expose database ownership settings");
        assertTrue(!source.contains("POSTGRESQL"), "Velocity default config must not mention Core database backends");
        assertTrue(!source.contains("MYSQL"), "Velocity default config must not mention Core database backends");
        assertTrue(!source.contains("MARIADB"), "Velocity default config must not mention Core database backends");
    }

    @Test
    void splitConfigV2DefaultsArePackagedForEachRuntime() throws Exception {
        Path root = repositoryRoot();
        for (String required : java.util.List.of(
            "cloudislands-paper/src/main/resources/config-v2/config.yml",
            "cloudislands-paper/src/main/resources/config-v2/runtime.yml",
            "cloudislands-paper/src/main/resources/config-v2/integrations.yml",
            "cloudislands-paper/src/main/resources/config-v2/security.yml",
            "cloudislands-paper/src/main/resources/config-v2/features.yml",
            "cloudislands-paper/src/main/resources/config-v2/gameplay.yml",
            "cloudislands-paper/src/main/resources/config-v2/ui/menus/main.yml",
            "cloudislands-paper/src/main/resources/config-v2/ui/menus/permissions.yml",
            "cloudislands-core-service/src/main/resources/config-v2/application.yml",
            "cloudislands-core-service/src/main/resources/config-v2/database.yml",
            "cloudislands-core-service/src/main/resources/config-v2/security.yml",
            "cloudislands-velocity/src/main/resources/config-v2/config.yml",
            "cloudislands-velocity/src/main/resources/config-v2/core-api.yml",
            "cloudislands-velocity/src/main/resources/config-v2/routing.yml",
            "cloudislands-velocity/src/main/resources/config-v2/security.yml"
        )) {
            assertTrue(Files.exists(root.resolve(required)), required);
        }
    }

    @Test
    void splitConfigV2UsesCanonicalFeatureAndRuntimeOwnership() throws Exception {
        Path root = repositoryRoot();
        String paperFeatures = Files.readString(root.resolve("cloudislands-paper/src/main/resources/config-v2/features.yml"));
        String coreDatabase = Files.readString(root.resolve("cloudislands-core-service/src/main/resources/config-v2/database.yml"));
        String velocityConfig = Files.readString(root.resolve("cloudislands-velocity/src/main/resources/config-v2/config.yml"))
            + "\n" + Files.readString(root.resolve("cloudislands-velocity/src/main/resources/config-v2/core-api.yml"))
            + "\n" + Files.readString(root.resolve("cloudislands-velocity/src/main/resources/config-v2/routing.yml"));

        assertTrue(!paperFeatures.contains("addons:"), "Paper config v2 must not expose legacy addon feature roots");
        assertTrue(!paperFeatures.contains("cloudislands-satis"), "Paper config v2 must keep Satis features at the canonical satis root");
        assertTrue(countLines(paperFeatures, "satis:") == 1, "Paper config v2 must define exactly one canonical satis root");

        assertTrue(coreDatabase.contains("type: POSTGRESQL"), "Core config v2 database.yml must declare one selected backend type");
        assertTrue(!containsAnyLine(coreDatabase, "postgresql:", "mysql:", "mariadb:", "setup:"), "Core config v2 database.yml must not expose multiple typed backend blocks or setup state");

        assertTrue(!containsAnyText(velocityConfig, "database:", "storage:", "POSTGRESQL", "MYSQL", "MARIADB"), "Velocity config v2 must not expose Core database or island storage ownership");
    }

    @Test
    void splitConfigV2DoesNotContainPlaintextSecrets() throws Exception {
        Path root = repositoryRoot();
        try (Stream<Path> files = Stream.of(
                root.resolve("cloudislands-paper/src/main/resources/config-v2"),
                root.resolve("cloudislands-core-service/src/main/resources/config-v2"),
                root.resolve("cloudislands-velocity/src/main/resources/config-v2")
            ).flatMap(PaperPlatformBoundaryTest::yamlFiles)) {
            String violations = files
                .flatMap(path -> plaintextSecretViolations(root, path).stream())
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

    private static Stream<Path> yamlFiles(Path root) {
        try {
            if (Files.notExists(root)) {
                return Stream.empty();
            }
            return Files.walk(root).filter(path -> {
                String value = path.toString();
                return value.endsWith(".yml") || value.endsWith(".yaml");
            });
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

    private static boolean contains(Path path, String needle) {
        try {
            return Files.readString(path).contains(needle);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static boolean containsAny(Path path, String... needles) {
        try {
            String source = Files.readString(path);
            for (String needle : needles) {
                if (source.contains(needle)) {
                    return true;
                }
            }
            return false;
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static boolean containsAnyText(String source, String... needles) {
        for (String needle : needles) {
            if (source.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsAnyLine(String source, String... lines) {
        return source.lines().map(String::trim).anyMatch(value -> {
            for (String line : lines) {
                if (value.equals(line)) {
                    return true;
                }
            }
            return false;
        });
    }

    private static long countLines(String source, String line) {
        return source.lines().filter(value -> value.equals(line)).count();
    }

    private static List<String> plaintextSecretViolations(Path root, Path path) {
        try {
            return Files.readAllLines(path).stream()
                .map(String::trim)
                .filter(line -> !line.isBlank() && !line.startsWith("#"))
                .filter(PaperPlatformBoundaryTest::looksLikeSecretLine)
                .filter(line -> !usesSecretReference(line))
                .map(line -> root.relativize(path) + ": " + line)
                .toList();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static boolean looksLikeSecretLine(String line) {
        int separator = line.indexOf(':');
        if (separator <= 0 || separator == line.length() - 1) {
            return false;
        }
        String key = line.substring(0, separator).trim().toLowerCase(java.util.Locale.ROOT);
        return key.equals("password")
            || key.endsWith("-password")
            || key.endsWith("_password")
            || key.endsWith("token")
            || key.endsWith("-token")
            || key.endsWith("_token")
            || key.equals("secret")
            || key.endsWith("-secret")
            || key.endsWith("_secret")
            || key.equals("access-key")
            || key.equals("secret-key")
            || key.equals("bearer-token")
            || key.equals("auth-token")
            || key.equals("admin-token");
    }

    private static boolean usesSecretReference(String line) {
        String value = line.substring(line.indexOf(':') + 1).trim();
        return value.equals("\"\"")
            || value.equals("''")
            || value.isBlank()
            || value.startsWith("\"${")
            || value.startsWith("${")
            || value.startsWith("\"<")
            || value.startsWith("<");
    }
}
