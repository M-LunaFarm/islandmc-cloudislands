package kr.lunaf.cloudislands.paper.platform.compatibility;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Stream;
import kr.lunaf.cloudislands.common.config.ConfigV2Validator;
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
    void paperRuntimeDoesNotReenterCommandsThroughPlayerCommandStrings() throws Exception {
        Path root = repositoryRoot();
        Path paperSource = root.resolve("cloudislands-paper/src/main/java");
        try (Stream<Path> files = javaFiles(paperSource)) {
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

        Path guiViewSource = root.resolve("cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/application/view/PaperGuiViews.java");
        String viewSource = Files.readString(guiViewSource);
        String viewViolations = Stream.of(
                "SimpleJson",
                "body.indexOf(",
                "body.substring(",
                "jsonStringEnd(",
                "objectEnd(",
                "rawScalar(")
            .filter(viewSource::contains)
            .map(token -> root.relativize(guiViewSource) + ": manual JSON parser token " + token)
            .reduce((left, right) -> left + "\n" + right)
            .orElse("");

        assertTrue(viewViolations.isBlank(), viewViolations);
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
                .map(path -> configValidationViolation(root, path))
                .filter(value -> !value.isBlank())
                .sorted()
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");

            assertTrue(violations.isBlank(), violations);
        }
    }

    @Test
    void configV2MenuLayoutsDoNotCollideAndUseRegisteredActions() throws Exception {
        Path root = repositoryRoot();
        Set<String> registeredActions = registeredGuiActions(root.resolve("cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/command/IslandCommandBackend.java"));
        try (Stream<Path> files = yamlFiles(root.resolve("cloudislands-paper/src/main/resources/config-v2/ui/menus"))) {
            String violations = files
                .flatMap(path -> menuConfigViolations(root, path, registeredActions).stream())
                .sorted()
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");

            assertTrue(violations.isBlank(), violations);
        }
    }

    @Test
    void guiJavaMenusDoNotReuseSlotsOrOverflowInventories() throws Exception {
        Path root = repositoryRoot();
        try (Stream<Path> files = javaFiles(root.resolve("cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/gui"))) {
            String violations = files
                .flatMap(path -> javaMenuSlotViolations(root, path).stream())
                .sorted()
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");

            assertTrue(violations.isBlank(), violations);
        }
    }

    @Test
    void guiJavaActionIdsAreRegistered() throws Exception {
        Path root = repositoryRoot();
        Set<String> registeredActions = registeredGuiActions(root.resolve("cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/command/IslandCommandBackend.java"));
        try (Stream<Path> files = javaFiles(root.resolve("cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/gui"))) {
            String violations = files
                .flatMap(path -> javaGuiActionViolations(root, path, registeredActions).stream())
                .sorted()
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");

            assertTrue(violations.isBlank(), violations);
        }
    }

    @Test
    void permissionMenuCoversFullApiPermissionEnum() throws Exception {
        Path root = repositoryRoot();
        String menu = Files.readString(root.resolve("cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/gui/IslandPermissionMenu.java"));
        String backend = Files.readString(root.resolve("cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/command/IslandCommandBackend.java"));

        assertTrue(menu.contains("IslandPermission.values()"), "Permission GUI must render from the API permission enum");
        assertTrue(menu.contains("PERMISSIONS_PER_PAGE"), "Permission GUI must paginate the full permission matrix");
        assertTrue(!menu.contains("List.of(\"BUILD\", \"BREAK\", \"INTERACT\""), "Permission GUI must not hard-code the legacy 8-permission subset");
        assertTrue(backend.contains("case \"island.permissions.page\""), "Permission GUI page action must be registered");
    }

    @Test
    void memberMenuPaginatesBeyondFirstInventoryPage() throws Exception {
        Path root = repositoryRoot();
        String menu = Files.readString(root.resolve("cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/gui/IslandMemberMenu.java"));
        String backend = Files.readString(root.resolve("cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/command/IslandCommandBackend.java"));

        assertTrue(menu.contains("MEMBERS_PER_PAGE"), "Member GUI must declare a page size");
        assertTrue(menu.contains(".skip((long) safePage * MEMBERS_PER_PAGE)"), "Member GUI must page through members instead of truncating to the first 45");
        assertTrue(menu.contains("\"island.members.page\""), "Member GUI must expose page navigation actions");
        assertTrue(backend.contains("case \"island.members.page\""), "Member GUI page action must be registered");
    }

    @Test
    void publicWarpsExposeCategoryAndSearchFilters() throws Exception {
        Path root = repositoryRoot();
        String model = Files.readString(root.resolve("cloudislands-api/src/main/java/kr/lunaf/cloudislands/api/model/IslandWarpSnapshot.java"));
        String routes = Files.readString(root.resolve("cloudislands-core-service/src/main/java/kr/lunaf/cloudislands/coreservice/http/routes/IslandWarpRoutes.java"));
        String client = Files.readString(root.resolve("cloudislands-core-client/src/main/java/kr/lunaf/cloudislands/coreclient/CoreApiClient.java"));
        String backend = Files.readString(root.resolve("cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/command/IslandCommandBackend.java"));

        assertTrue(model.contains("String category"), "Warp API model must include a category");
        assertTrue(routes.contains("queryText(exchange, \"category\""), "Core public warp route must accept category filters");
        assertTrue(routes.contains("queryText(exchange, \"query\""), "Core public warp route must accept search filters");
        assertTrue(client.contains("listPublicWarps(int limit, String category, String query)"), "Core client must expose filtered public warp lookup");
        assertTrue(backend.contains("listPublicWarps(player, args[1]"), "Paper public warp command must expose category/search arguments");
    }

    @Test
    void asyncGuiLoadFailuresUseInventoryStateScreens() throws Exception {
        Path root = repositoryRoot();
        Path guiSource = root.resolve("cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/gui");
        try (Stream<Path> files = javaFiles(guiSource)) {
            String violations = files
                .flatMap(path -> asyncGuiStateViolations(root, path).stream())
                .sorted()
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");

            assertTrue(violations.isBlank(), violations);
        }

        String registrar = Files.readString(root.resolve("cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/gui/IslandGuiMenuRegistrar.java"));
        assertTrue(registrar.contains("GuiStateMenus.listener()"), "GUI state menu listener must be registered");
        String states = Files.readString(root.resolve("cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/gui/GuiState.java"));
        assertTrue(states.contains("LOADING"), "GUI states must include Loading");
        assertTrue(states.contains("READY"), "GUI states must include Ready");
        assertTrue(states.contains("EMPTY"), "GUI states must include Empty");
        assertTrue(states.contains("ERROR"), "GUI states must include Error");
    }

    @Test
    void paperCoreMutationCallsCarryRequestMetadata() throws Exception {
        Path root = repositoryRoot();
        List<Path> files = List.of(
            root.resolve("cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/command/IslandCommandBackend.java"),
            root.resolve("cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/api/PaperCloudIslandsApi.java")
        );
        String violations = files.stream()
            .flatMap(path -> mutationMetadataViolations(root, path).stream())
            .sorted()
            .reduce((left, right) -> left + "\n" + right)
            .orElse("");

        assertTrue(violations.isBlank(), violations);

        String commandBackend = Files.readString(files.get(0));
        assertTrue(commandBackend.contains("mutateIdempotent(\"island.delete\""), "Island delete must use an idempotency key");
        assertTrue(commandBackend.contains("DangerousGuiActionPolicy.confirmed"), "Dangerous GUI mutations must verify a confirmation token");
        assertTrue(commandBackend.contains("mutateIdempotent(\"island.bank.withdraw\""), "Bank withdraw must use an idempotency key");
        assertTrue(commandBackend.contains("CoreMutationMetadata.request"), "Paper mutations must carry request IDs and audit actions");
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

    private static List<String> mutationMetadataViolations(Path root, Path path) {
        try {
            List<String> violations = new ArrayList<>();
            List<String> lines = Files.readAllLines(path);
            for (int index = 0; index < lines.size(); index++) {
                String line = lines.get(index);
                if (line.contains(".thenCompose(_body -> client.")) {
                    continue;
                }
                if (containsAnyText(line, "coreApiClient.", "client.", "coreClient.")
                    && containsAnyText(line, mutationMethodNeedles())
                    && !containsAnyText(line, "mutate(", "mutateIdempotent(")) {
                    violations.add(root.relativize(path) + ":" + (index + 1) + " " + line.trim());
                }
            }
            return violations;
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String[] mutationMethodNeedles() {
        return new String[] {
            ".accept",
            ".activate",
            ".adminDelete",
            ".adminIslandTeleport",
            ".ban",
            ".cancel",
            ".claim",
            ".clear",
            ".complete",
            ".consume",
            ".create",
            ".deactivate",
            ".decline",
            ".delete",
            ".deposit",
            ".disable",
            ".drain",
            ".enable",
            ".fail",
            ".kick",
            ".migrate",
            ".pardon",
            ".progress",
            ".publish",
            ".purchase",
            ".put",
            ".quarantine",
            ".record",
            ".recover",
            ".reload",
            ".remove",
            ".repair",
            ".replace",
            ".request",
            ".reset",
            ".restore",
            ".retry",
            ".rollback",
            ".save",
            ".send",
            ".set",
            ".shutdown",
            ".sweep",
            ".tableBulk",
            ".tableKeyValueBulkSave",
            ".transfer",
            ".undrain",
            ".upsert",
            ".withdraw"
        };
    }

    private static String configValidationViolation(Path root, Path path) {
        try {
            var result = ConfigV2Validator.validateYaml(root.relativize(path).toString(), Files.readString(path));
            return result.valid() ? "" : root.relativize(path) + ": " + result.summary();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static List<String> menuConfigViolations(Path root, Path path, Set<String> registeredActions) {
        try {
            List<String> violations = new ArrayList<>();
            List<String> lines = Files.readAllLines(path);
            Integer rows = yamlInt(lines, "rows");
            if (rows == null || rows < 1 || rows > 6) {
                violations.add(root.relativize(path) + ": rows must be 1..6");
            }

            List<String> layout = yamlStringList(lines, "layout");
            if (!layout.isEmpty()) {
                if (rows != null && layout.size() != rows) {
                    violations.add(root.relativize(path) + ": layout row count must match rows");
                }
                Set<String> symbols = new LinkedHashSet<>();
                for (int row = 0; row < layout.size(); row++) {
                    String value = layout.get(row);
                    if (value.length() != 9) {
                        violations.add(root.relativize(path) + ": layout row " + (row + 1) + " must be 9 columns");
                    }
                    for (int column = 0; column < value.length(); column++) {
                        char symbol = value.charAt(column);
                        if (symbol != '.') {
                            symbols.add(String.valueOf(symbol));
                        }
                    }
                }
                Set<String> itemKeys = yamlChildKeys(lines, "items");
                Set<String> missing = new LinkedHashSet<>(symbols);
                missing.removeAll(itemKeys);
                if (!missing.isEmpty()) {
                    violations.add(root.relativize(path) + ": layout symbols without items " + missing);
                }
            }

            for (String material : yamlValues(lines, "material")) {
                if (!materialExists(material)) {
                    violations.add(root.relativize(path) + ": unknown material " + material);
                }
            }
            for (String action : yamlActionValues(lines)) {
                if (!registeredActions.contains(action)) {
                    violations.add(root.relativize(path) + ": unregistered action-id " + action);
                }
            }
            return violations;
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static Set<String> registeredGuiActions(Path backend) {
        try {
            Set<String> actions = new HashSet<>();
            for (String line : Files.readAllLines(backend)) {
                if (!line.contains("case \"")) {
                    continue;
                }
                int offset = 0;
                while (offset < line.length()) {
                    int start = line.indexOf('"', offset);
                    if (start < 0) {
                        break;
                    }
                    int end = line.indexOf('"', start + 1);
                    if (end < 0) {
                        break;
                    }
                    String value = line.substring(start + 1, end);
                    if (isGuiActionId(value)) {
                        actions.add(value);
                    }
                    offset = end + 1;
                }
            }
            return actions;
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static List<String> javaMenuSlotViolations(Path root, Path path) {
        try {
            List<String> violations = new ArrayList<>();
            List<String> lines = Files.readAllLines(path);
            MenuBlock block = null;
            int slotStart = -1;
            int pendingLimit = -1;
            int pendingIndexLimit = -1;
            for (int index = 0; index < lines.size(); index++) {
                String line = lines.get(index);
                int lineNumber = index + 1;
                Integer inventorySize = parseInventorySize(line);
                if (inventorySize != null) {
                    if (block != null) {
                        violations.addAll(block.violations(root, path));
                    }
                    block = new MenuBlock(lineNumber, inventorySize);
                    slotStart = -1;
                    pendingLimit = -1;
                    pendingIndexLimit = -1;
                    continue;
                }
                if (block == null) {
                    continue;
                }
                Integer fixedSlot = parseFixedSlot(line);
                if (fixedSlot != null) {
                    block.addFixedSlot(fixedSlot, lineNumber, !line.contains("empty"));
                }
                Integer assignment = parseSlotAssignment(line);
                if (assignment != null) {
                    slotStart = assignment;
                }
                Integer limit = parseLimit(line);
                if (limit != null) {
                    pendingLimit = limit;
                }
                Integer indexLimit = parseIndexLimit(line);
                if (indexLimit != null) {
                    pendingIndexLimit = indexLimit;
                }
                if (line.contains("inventory.setItem(slot++")) {
                    if (slotStart < 0 || pendingLimit < 0) {
                        violations.add(root.relativize(path) + ":" + lineNumber + ": slot++ menu list must declare a finite limit");
                    } else {
                        block.addDynamicRange(slotStart, slotStart + pendingLimit - 1, lineNumber);
                    }
                    pendingLimit = -1;
                }
                if (line.contains("inventory.setItem(index")) {
                    if (pendingIndexLimit < 0) {
                        violations.add(root.relativize(path) + ":" + lineNumber + ": index menu list must declare a finite limit");
                    } else {
                        int offset = line.contains("index + 9") ? 9 : 0;
                        block.addDynamicRange(offset, offset + pendingIndexLimit - 1, lineNumber);
                    }
                    pendingIndexLimit = -1;
                }
                if (line.contains("player.openInventory(inventory);")) {
                    violations.addAll(block.violations(root, path));
                    block = null;
                    slotStart = -1;
                    pendingLimit = -1;
                    pendingIndexLimit = -1;
                }
            }
            if (block != null) {
                violations.addAll(block.violations(root, path));
            }
            return violations;
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static List<String> javaGuiActionViolations(Path root, Path path, Set<String> registeredActions) {
        try {
            List<String> violations = new ArrayList<>();
            List<String> lines = Files.readAllLines(path);
            for (int index = 0; index < lines.size(); index++) {
                String line = lines.get(index);
                if (!line.contains("GuiItems.action(")
                    && !line.contains("GuiActionRegistry.execute(")
                    && !line.contains("actions.execute(")) {
                    continue;
                }
                for (String action : quotedGuiActionIds(line)) {
                    if (!registeredActions.contains(action)) {
                        violations.add(root.relativize(path) + ":" + (index + 1) + ": unregistered action-id " + action);
                    }
                }
            }
            return violations;
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static List<String> quotedGuiActionIds(String line) {
        List<String> values = new ArrayList<>();
        int offset = 0;
        while (offset < line.length()) {
            int start = line.indexOf('"', offset);
            if (start < 0) {
                break;
            }
            int end = line.indexOf('"', start + 1);
            if (end < 0) {
                break;
            }
            String value = line.substring(start + 1, end);
            if (isGuiActionId(value)) {
                values.add(value);
            }
            offset = end + 1;
        }
        return values;
    }

    private static List<String> asyncGuiStateViolations(Path root, Path path) {
        try {
            String source = Files.readString(path);
            if (!source.contains(".exceptionally(") || !source.contains("load-failed")) {
                return List.of();
            }
            List<String> violations = new ArrayList<>();
            String relative = root.relativize(path).toString();
            boolean chatOnlyLoadFailure = source.lines()
                .anyMatch(line -> line.contains("load-failed") && line.contains("player.sendMessage("));
            if (chatOnlyLoadFailure) {
                violations.add(relative + ": async load failure must not fall back to chat-only errors");
            }
            if (!source.contains("GuiStateMenus.openLoading(")) {
                violations.add(relative + ": async menu must open a Loading state before Core response");
            }
            if (!source.contains("GuiStateMenus.openError(")) {
                violations.add(relative + ": async load failure must open an Error state with Retry/Back actions");
            }
            return violations;
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static Integer yamlInt(List<String> lines, String key) {
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith(key + ":")) {
                return Integer.parseInt(cleanYamlValue(trimmed.substring(trimmed.indexOf(':') + 1)));
            }
        }
        return null;
    }

    private static List<String> yamlStringList(List<String> lines, String key) {
        List<String> values = new ArrayList<>();
        boolean inBlock = false;
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.equals(key + ":")) {
                inBlock = true;
                continue;
            }
            if (inBlock && !line.startsWith(" ")) {
                break;
            }
            if (inBlock && trimmed.startsWith("-")) {
                values.add(cleanYamlValue(trimmed.substring(1)));
            }
        }
        return values;
    }

    private static Set<String> yamlChildKeys(List<String> lines, String key) {
        Set<String> values = new LinkedHashSet<>();
        boolean inBlock = false;
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.equals(key + ":")) {
                inBlock = true;
                continue;
            }
            if (inBlock && !line.startsWith(" ")) {
                break;
            }
            if (inBlock && line.startsWith("  ") && !line.startsWith("    ") && trimmed.endsWith(":")) {
                values.add(trimmed.substring(0, trimmed.length() - 1));
            }
        }
        return values;
    }

    private static List<String> yamlValues(List<String> lines, String key) {
        List<String> values = new ArrayList<>();
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith(key + ":")) {
                values.add(cleanYamlValue(trimmed.substring(trimmed.indexOf(':') + 1)));
            }
        }
        return values;
    }

    private static List<String> yamlActionValues(List<String> lines) {
        List<String> values = new ArrayList<>();
        boolean inActionBlock = false;
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.equals("actions:") || trimmed.equals("footer-actions:")) {
                inActionBlock = true;
                continue;
            }
            if (inActionBlock && !line.startsWith(" ")) {
                inActionBlock = false;
            }
            if (trimmed.startsWith("action:")) {
                values.add(cleanYamlValue(trimmed.substring(trimmed.indexOf(':') + 1)));
                continue;
            }
            if (inActionBlock && line.startsWith("  ") && trimmed.contains(":")) {
                values.add(cleanYamlValue(trimmed.substring(trimmed.indexOf(':') + 1)));
            }
        }
        return values.stream().filter(PaperPlatformBoundaryTest::isGuiActionId).toList();
    }

    private static String cleanYamlValue(String value) {
        String cleaned = value.trim();
        if ((cleaned.startsWith("\"") && cleaned.endsWith("\"")) || (cleaned.startsWith("'") && cleaned.endsWith("'"))) {
            return cleaned.substring(1, cleaned.length() - 1);
        }
        return cleaned;
    }

    private static boolean isGuiActionId(String value) {
        return value.startsWith("island.") || value.startsWith("admin.") || value.startsWith("gui.");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static boolean materialExists(String material) {
        try {
            Class<?> materialClass = Class.forName("org.bukkit.Material");
            Enum.valueOf((Class) materialClass.asSubclass(Enum.class), material);
            return true;
        } catch (ClassNotFoundException exception) {
            return knownMenuMaterials().contains(material);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static Set<String> knownMenuMaterials() {
        return Set.of(
            "BEACON",
            "CHEST",
            "COMPARATOR",
            "COMPASS",
            "EMERALD",
            "ENDER_PEARL",
            "GOLD_BLOCK",
            "GRASS_BLOCK",
            "HOPPER",
            "LAVA_BUCKET",
            "MAP",
            "NAME_TAG",
            "PLAYER_HEAD",
            "WRITABLE_BOOK"
        );
    }

    private static Integer parseInventorySize(String line) {
        int start = line.indexOf("GuiInventories.create(");
        if (start < 0) {
            return null;
        }
        int firstComma = line.indexOf(',', start);
        if (firstComma < 0) {
            return null;
        }
        int secondComma = line.indexOf(',', firstComma + 1);
        if (secondComma < 0) {
            return null;
        }
        return parseInteger(line.substring(firstComma + 1, secondComma).trim());
    }

    private static Integer parseFixedSlot(String line) {
        int start = line.indexOf("inventory.setItem(");
        if (start < 0) {
            return null;
        }
        int offset = start + "inventory.setItem(".length();
        int end = offset;
        while (end < line.length() && Character.isDigit(line.charAt(end))) {
            end++;
        }
        if (end == offset) {
            return null;
        }
        return parseInteger(line.substring(offset, end));
    }

    private static Integer parseSlotAssignment(String line) {
        String trimmed = line.trim();
        if (trimmed.startsWith("int slot = ")) {
            return parseInteger(trimmed.substring("int slot = ".length(), trimmed.indexOf(';')).trim());
        }
        if (trimmed.startsWith("slot = ")) {
            return parseInteger(trimmed.substring("slot = ".length(), trimmed.indexOf(';')).trim());
        }
        return null;
    }

    private static Integer parseLimit(String line) {
        int start = line.indexOf(".limit(");
        if (start < 0) {
            return null;
        }
        int end = line.indexOf(')', start);
        if (end < 0) {
            return null;
        }
        return parseInteger(line.substring(start + ".limit(".length(), end).trim());
    }

    private static Integer parseIndexLimit(String line) {
        int offset = 0;
        while (offset < line.length()) {
            int start = line.indexOf("index < ", offset);
            if (start < 0) {
                return null;
            }
            int valueStart = start + "index < ".length();
            int end = valueStart;
            while (end < line.length() && Character.isDigit(line.charAt(end))) {
                end++;
            }
            if (end > valueStart) {
                return parseInteger(line.substring(valueStart, end));
            }
            offset = valueStart;
        }
        return null;
    }

    private static Integer parseInteger(String value) {
        if ("MEMBERS_PER_PAGE".equals(value)) {
            return 45;
        }
        if ("PERMISSIONS_PER_PAGE".equals(value)) {
            return 8;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private record SlotUse(int slot, int line, boolean blocksDynamicArea) {
    }

    private record SlotRange(int start, int end, int line) {
    }

    private static final class MenuBlock {
        private final int line;
        private final int size;
        private final List<SlotUse> fixedSlots = new ArrayList<>();
        private final List<SlotRange> dynamicRanges = new ArrayList<>();

        private MenuBlock(int line, int size) {
            this.line = line;
            this.size = size;
        }

        private void addFixedSlot(int slot, int line, boolean blocksDynamicArea) {
            fixedSlots.add(new SlotUse(slot, line, blocksDynamicArea));
        }

        private void addDynamicRange(int start, int end, int line) {
            dynamicRanges.add(new SlotRange(start, end, line));
        }

        private List<String> violations(Path root, Path path) {
            List<String> violations = new ArrayList<>();
            Set<Integer> seen = new HashSet<>();
            for (SlotUse use : fixedSlots) {
                if (use.slot() < 0 || use.slot() >= size) {
                    violations.add(root.relativize(path) + ":" + use.line() + ": fixed slot " + use.slot() + " outside inventory size " + size);
                }
                if (!seen.add(use.slot())) {
                    violations.add(root.relativize(path) + ":" + use.line() + ": duplicate fixed slot " + use.slot());
                }
            }
            for (SlotRange range : dynamicRanges) {
                if (range.start() < 0 || range.end() >= size) {
                    violations.add(root.relativize(path) + ":" + range.line() + ": dynamic slots " + range.start() + ".." + range.end() + " outside inventory size " + size);
                }
                for (SlotUse use : fixedSlots) {
                    if (use.blocksDynamicArea() && use.slot() >= range.start() && use.slot() <= range.end()) {
                        violations.add(root.relativize(path) + ":" + range.line() + ": dynamic slots " + range.start() + ".." + range.end() + " overlap fixed slot " + use.slot());
                    }
                }
            }
            if (size % 9 != 0 || size < 9 || size > 54) {
                violations.add(root.relativize(path) + ":" + line + ": inventory size must be a 1..6 row chest size");
            }
            return violations;
        }
    }
}
