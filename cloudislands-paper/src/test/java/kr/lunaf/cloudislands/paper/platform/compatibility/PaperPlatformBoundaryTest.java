package kr.lunaf.cloudislands.paper.platform.compatibility;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Stream;
import kr.lunaf.cloudislands.common.config.ConfigV2Validator;
import kr.lunaf.cloudislands.paper.gui.GuiActionSchema;
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
    void guiClassesDoNotStoreCommandStringsAsControlData() throws Exception {
        Path root = repositoryRoot();
        Path guiSource = root.resolve("cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/gui");
        try (Stream<Path> files = javaFiles(guiSource)) {
            String violations = files
                .filter(path -> containsAny(path,
                    "Map.of(\"command\"",
                    "admin.node.command",
                    "commandItem(",
                    "GuiItems.action(" + System.lineSeparator() + "command"))
                .map(path -> root.relativize(path).toString())
                .sorted()
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");

            assertTrue(violations.isBlank(), violations);
        }
    }

    @Test
    void adminNodeGuiActionsCallCoreUsecases() throws Exception {
        Path root = repositoryRoot();
        String router = Files.readString(root.resolve("cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/command/IslandCommandRouter.java"));
        String adminHandler = Files.readString(root.resolve("cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/command/IslandAdminNodeCommandHandler.java"));
        String adminUseCase = Files.readString(root.resolve("cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/application/IslandAdminNodeUseCase.java"));
        String homeWarpHandler = Files.readString(root.resolve("cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/command/IslandHomeWarpCommandHandler.java"));
        String tokens = Files.readString(root.resolve("cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/gui/ConfirmationTokenPolicy.java"));

        assertTrue(router.contains("adminCommands.handleGuiAction(player, action"), "Admin node GUI actions must route through the admin handler");
        assertTrue(adminHandler.contains("action instanceof GuiAction.AdminNodeAction"), "Admin node GUI actions must use typed actions");
        assertTrue(adminHandler.contains("case LIST ->"), "Admin node list GUI action must call the Core usecase path");
        assertTrue(adminHandler.contains("adminNodeUseCase.listNodesSummary()"), "Admin node list GUI action must call the typed Core usecase path");
        assertTrue(adminUseCase.contains("AdminNodeQueryClient adminNodeQueries"), "Admin node reads must stay behind a typed query client");
        assertTrue(adminUseCase.contains("adminNodeQueries.listNodesSummary()"), "Admin node list usecase must read through the typed query client");
        assertTrue(!adminUseCase.contains("public CompletableFuture<String> listNodes("), "Admin node list usecase must expose typed summaries instead of raw JSON");
        assertTrue(adminHandler.contains("case INFO ->"), "Admin node info GUI action must refresh from Core");
        assertTrue(adminHandler.contains("adminNodeUseCase.nodeInfoView(nodeId)"), "Admin node info GUI action must refresh through the typed Core usecase");
        assertTrue(adminUseCase.contains("adminNodeQueries.nodeInfo("), "Admin node info usecase must read through the typed query client");
        assertTrue(!adminUseCase.contains("coreApiClient.listNodes("), "Admin node usecase must not parse raw node list bodies");
        assertTrue(!adminUseCase.contains("coreApiClient.nodeInfo("), "Admin node usecase must not parse raw node info bodies");
        assertTrue(!adminUseCase.contains("coreApiClient.nodeIslands("), "Admin node usecase must not parse raw node island bodies");
        assertTrue(!adminUseCase.contains("public CompletableFuture<String> nodeInfo("), "Admin node info usecase must expose typed views instead of raw JSON");
        assertTrue(adminHandler.contains("case DRAIN ->"), "Admin node drain GUI action must call Core");
        assertTrue(adminHandler.contains("adminNodeUseCase.drainAction(nodeId"), "Admin node drain GUI action must call the typed Core usecase");
        assertTrue(adminUseCase.contains("adminNodeCommands.drainNode("), "Admin node drain usecase must call typed Core command client");
        assertTrue(adminHandler.contains("case UNDRAIN ->"), "Admin node undrain GUI action must call Core");
        assertTrue(adminHandler.contains("adminNodeUseCase.undrainAction(nodeId"), "Admin node undrain GUI action must call the typed Core usecase");
        assertTrue(adminUseCase.contains("adminNodeCommands.undrainNode("), "Admin node undrain usecase must call typed Core command client");
        assertTrue(adminHandler.contains("case SWEEP ->"), "Admin node sweep GUI action must call Core");
        assertTrue(adminHandler.contains("adminNodeUseCase.sweepAction(nodeId"), "Admin node sweep GUI action must call the typed Core usecase");
        assertTrue(adminUseCase.contains("adminNodeCommands.sweepNode("), "Admin node sweep usecase must call typed Core command client");
        assertTrue(!adminUseCase.contains("public CompletableFuture<String> nodeIslands("), "Admin node islands usecase must expose typed summaries instead of raw JSON");
        assertTrue(adminUseCase.contains("adminNodeCommands.kickAllNode("), "Admin node kickall confirmation must call typed Core command client");
        assertTrue(adminUseCase.contains("adminNodeCommands.shutdownNodeSafely("), "Admin node shutdown confirmation must call typed Core command client");
        assertTrue(adminHandler.contains("confirmationAccepted(player, action, click)"), "Admin node danger actions must verify a typed confirmation token");
        assertTrue(tokens.contains("\"admin.node.kickall.confirm\""), "Admin node kickall must require a confirmation token");
        assertTrue(tokens.contains("\"admin.node.shutdown-safe.confirm\""), "Admin node shutdown-safe must require a confirmation token");
        assertTrue(!adminHandler.contains("case \"admin.node.list\",\n                \"admin.node.info\""), "Admin node GUI actions must not fall through to direct command guidance");
    }

    @Test
    void memberRemovalFlowsThroughApplicationUsecase() throws Exception {
        Path root = repositoryRoot();
        String backend = Files.readString(root.resolve("cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/command/IslandCommandBackend.java"));
        String membership = Files.readString(root.resolve("cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/command/IslandMembershipCommandHandler.java"));
        String usecase = Files.readString(root.resolve("cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/application/MemberManagementUseCase.java"));
        String memberPresentation = backend + "\n" + membership;

        assertTrue(memberPresentation.contains("MemberManagementUseCase"), "Member presentation handlers must own the member management usecase");
        assertTrue(memberPresentation.contains("memberManagement.removeMemberAction("), "Command and GUI member removal must call the typed application usecase");
        assertTrue(memberPresentation.contains("memberManagement.listMemberViews("), "Member list reads must call the typed application usecase");
        assertTrue(memberPresentation.contains("memberManagement.playerUuidByName("), "Player profile lookups in member flows must call the typed application usecase");
        assertTrue(memberPresentation.contains("memberManagement.resolveInviteByPlayerNameOrIslandName("), "Invite target resolution must live behind the application usecase");
        assertTrue(memberPresentation.contains("memberManagement.createInviteView("), "Member invite creation must call the typed application usecase");
        assertTrue(memberPresentation.contains("memberManagement.acceptInviteAction("), "Invite accept must call the typed application usecase");
        assertTrue(memberPresentation.contains("memberManagement.declineInviteAction("), "Invite decline must call the typed application usecase");
        assertTrue(memberPresentation.contains("memberManagement.listBanViews("), "Ban list reads must call the typed application usecase");
        assertTrue(memberPresentation.contains("memberManagement.setRoleAction("), "Command and GUI member role changes must call the typed application usecase");
        assertTrue(memberPresentation.contains("memberManagement.trustTemporarilyAction("), "Temporary trust changes must call the typed application usecase");
        assertTrue(memberPresentation.contains("memberManagement.transferOwnershipAction("), "Ownership transfers must call the typed application usecase");
        assertTrue(memberPresentation.contains("memberManagement.kickVisitorAction("), "Visitor kicks must call the typed application usecase");
        assertTrue(!memberPresentation.contains("coreApiClient.removeIslandMemberResult("), "Presentation code must not call member removal Core API directly");
        assertTrue(!memberPresentation.contains("coreApiClient.listIslandMembers("), "Presentation code must not call member list Core API directly");
        assertTrue(!memberPresentation.contains("coreApiClient.playerInfoByName("), "Presentation code must not call player lookup Core API directly");
        assertTrue(!memberPresentation.contains("coreApiClient.islandInfoByName("), "Presentation code must not call island-name lookup Core API directly");
        assertTrue(!memberPresentation.contains("coreApiClient.createIslandInvite("), "Presentation code must not call invite creation Core API directly");
        assertTrue(!memberPresentation.contains("coreApiClient.acceptIslandInviteResult("), "Presentation code must not call invite accept Core API directly");
        assertTrue(!memberPresentation.contains("coreApiClient.declineIslandInviteResult("), "Presentation code must not call invite decline Core API directly");
        assertTrue(!memberPresentation.contains("coreApiClient.listIslandBans("), "Presentation code must not call ban list Core API directly");
        assertTrue(!memberPresentation.contains("coreApiClient.setIslandMemberResult("), "Presentation code must not call member role Core API directly");
        assertTrue(!memberPresentation.contains("coreApiClient.trustIslandMemberTemporary("), "Presentation code must not call temporary trust Core API directly");
        assertTrue(!memberPresentation.contains("coreApiClient.transferIslandOwnershipResult("), "Presentation code must not call ownership transfer Core API directly");
        assertTrue(!memberPresentation.contains("coreApiClient.banIslandVisitorResult("), "Presentation code must not call visitor ban Core API directly");
        assertTrue(!memberPresentation.contains("coreApiClient.pardonIslandVisitorResult("), "Presentation code must not call visitor pardon Core API directly");
        assertTrue(!memberPresentation.contains("coreApiClient.kickIslandVisitorResult("), "Presentation code must not call visitor kick Core API directly");
        assertTrue(!memberPresentation.contains("jsonStringEnd("), "Presentation code must not parse Core JSON string fields manually");
        assertTrue(!memberPresentation.contains("unescape("), "Presentation code must not carry JSON string unescape helpers");
        assertTrue(!memberPresentation.contains("Double.parseDouble(json.substring"), "Presentation code must not parse Core JSON numbers manually");
        assertTrue(membership.contains("private void removeIslandMember(Player player, String target)"), "GUI confirmation and command routing must share the same member removal boundary");
        assertTrue(usecase.contains("memberCommands.removeMember("), "The application usecase must call typed member command client for removal");
        assertTrue(usecase.contains("islandQueries.listMembers("), "The application usecase must read members through the typed island query client");
        assertTrue(!usecase.contains("public CompletableFuture<String> listMembers("), "Member list usecase must expose typed views instead of raw JSON");
        assertTrue(usecase.contains("MemberQueryClient memberQueries"), "The application usecase must keep member reads behind a typed query client");
        assertTrue(usecase.contains("memberQueries.playerProfileByName("), "The application usecase must read player profiles through the typed member query client");
        assertTrue(usecase.contains("islandQueries.findIslandByName("), "The application usecase must read island names through the typed island query client");
        assertTrue(usecase.contains("findPendingInviteId("), "The application usecase must own pending invite matching");
        assertTrue(usecase.contains("memberCommands.createInvite("), "The application usecase must call typed member command client for invite creation");
        assertTrue(usecase.contains("memberCommands.acceptInvite("), "The application usecase must call typed member command client for invite accept");
        assertTrue(usecase.contains("memberCommands.declineInvite("), "The application usecase must call typed member command client for invite decline");
        assertTrue(!usecase.contains("public CompletableFuture<String> listPendingInvites("), "Pending invite usecase must expose typed views instead of raw JSON");
        assertTrue(usecase.contains("memberQueries.pendingInvites("), "The application usecase must read pending invites through the typed member query client");
        assertTrue(usecase.contains("memberQueries.bans("), "The application usecase must read bans through the typed member query client");
        assertTrue(!usecase.contains("coreApiClient.playerInfoByName("), "The application usecase must not parse raw player profile bodies");
        assertTrue(!usecase.contains("coreApiClient.listPendingInvites("), "The application usecase must not parse raw pending invite bodies");
        assertTrue(!usecase.contains("coreApiClient.listIslandBans("), "The application usecase must not parse raw ban bodies");
        assertTrue(!usecase.contains("public CompletableFuture<String> listBans("), "Ban list usecase must expose typed views instead of raw JSON");
        assertTrue(usecase.contains("memberCommands.setRole("), "The application usecase must call typed member command client for role changes");
        assertTrue(usecase.contains("memberCommands.trustTemporarily("), "The application usecase must call typed member command client for temporary trust");
        assertTrue(usecase.contains("memberCommands.transferOwnership("), "The application usecase must call typed member command client for ownership transfer");
        assertTrue(usecase.contains("memberCommands.banVisitor("), "The application usecase must call typed member command client for visitor ban");
        assertTrue(usecase.contains("memberCommands.pardonVisitor("), "The application usecase must call typed member command client for visitor pardon");
        assertTrue(usecase.contains("memberCommands.kickVisitor("), "The application usecase must call typed member command client for visitor kick");
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
    void guiInventoryCreationAlwaysUsesMenuHolderFactory() throws Exception {
        Path root = repositoryRoot();
        Path guiSource = root.resolve("cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/gui");
        try (Stream<Path> files = javaFiles(guiSource)) {
            String violations = files
                .filter(path -> !path.getFileName().toString().equals("GuiInventories.java"))
                .filter(path -> containsAny(path, "Bukkit.createInventory(", ".createInventory("))
                .map(path -> root.relativize(path).toString())
                .sorted()
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");

            assertTrue(violations.isBlank(), violations);
        }

        String factory = Files.readString(guiSource.resolve("GuiInventories.java"));
        assertTrue(factory.contains("new CloudIslandsMenuHolder(menuId)"), "GUI inventory factory must create a menu holder with the action/menu id");
        assertTrue(factory.contains("Bukkit.createInventory(holder"), "GUI inventory factory must attach the holder at creation time");
        assertTrue(factory.contains("holder.attach(inventory)"), "GUI inventory factory must keep holder.getInventory() wired to the created inventory");
        assertTrue(factory.contains("inventory.getHolder()"), "GUI menu lookup must read holder identity rather than title text");
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
                    && !path.getFileName().toString().equals("IslandDangerMenu.java")
                    && !path.getFileName().toString().equals("ConfirmationTokenPolicy.java")
                    && !path.getFileName().toString().equals("GuiActionSchema.java"))
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
            "cloudislands-paper/src/main/resources/config-v2/ui/menus/bank.yml",
            "cloudislands-paper/src/main/resources/config-v2/ui/menus/warps.yml",
            "cloudislands-paper/src/main/resources/config-v2/ui/menus/snapshots.yml",
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
        String paperIntegrations = Files.readString(root.resolve("cloudislands-paper/src/main/resources/config-v2/integrations.yml"));
        String coreDatabase = Files.readString(root.resolve("cloudislands-core-service/src/main/resources/config-v2/database.yml"));
        String velocityConfig = Files.readString(root.resolve("cloudislands-velocity/src/main/resources/config-v2/config.yml"))
            + "\n" + Files.readString(root.resolve("cloudislands-velocity/src/main/resources/config-v2/core-api.yml"))
            + "\n" + Files.readString(root.resolve("cloudislands-velocity/src/main/resources/config-v2/routing.yml"));

        assertTrue(!paperFeatures.contains("addons:"), "Paper config v2 must not expose legacy addon feature roots");
        assertTrue(!paperFeatures.contains("cloudislands-satis"), "Paper config v2 must keep Satis features at the canonical satis root");
        assertTrue(countLines(paperFeatures, "satis:") == 1, "Paper config v2 must define exactly one canonical satis root");

        assertTrue(coreDatabase.contains("type: POSTGRESQL"), "Core config v2 database.yml must declare one selected backend type");
        assertTrue(!containsAnyLine(coreDatabase, "postgresql:", "mysql:", "mariadb:", "setup:"), "Core config v2 database.yml must not expose multiple typed backend blocks or setup state");

        assertTrue(paperIntegrations.contains("hooks:"), "Paper config v2 integrations must expose first-class hook settings");
        assertTrue(paperIntegrations.contains("distributed-policy: paper-hooks-must-tag-island-uuid-runtime-fencing-token-node-id-and-node-ownership-before-core-state-changes"), "Paper integration config must publish the distributed hook policy");
        assertTrue(paperIntegrations.contains("runtime-fencing-token"), "Paper integration config must require runtime fencing claims for authority-changing hooks");
        assertTrue(paperIntegrations.contains("CoreProtect:"), "Paper integration config must include CoreProtect hook settings");
        assertTrue(paperIntegrations.contains("FastAsyncWorldEdit:"), "Paper integration config must include FAWE hook settings");
        assertTrue(paperIntegrations.contains("runtime-authority-required: true"), "Paper integration config must distinguish Core-state-changing hooks");

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
    void deploymentYamlDoesNotContainInlineSecrets() throws Exception {
        Path root = repositoryRoot();
        try (Stream<Path> files = yamlFiles(root.resolve("deploy"))) {
            String violations = files
                .flatMap(path -> deploymentSecretViolations(root, path).stream())
                .sorted()
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");

            assertTrue(violations.isBlank(), violations);
        }
    }

    @Test
    void configV2MenuLayoutsDoNotCollideAndUseRegisteredActions() throws Exception {
        Path root = repositoryRoot();
        Set<String> registeredActions = GuiActionSchema.registeredActionIds();
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
        Set<String> registeredActions = GuiActionSchema.registeredActionIds();
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
        String membershipHandler = Files.readString(root.resolve("cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/command/IslandMembershipCommandHandler.java"));

        assertTrue(menu.contains("IslandPermission.values()"), "Permission GUI must render from the API permission enum");
        assertTrue(menu.contains("PaperGuiViews.islandRoles"), "Permission GUI must render roles from the Core role catalog");
        assertTrue(menu.contains("ROLES_PER_PAGE"), "Permission GUI must paginate dynamic role catalogs");
        assertTrue(menu.contains("rolePage"), "Permission GUI must preserve role pagination state");
        assertTrue(menu.contains("PERMISSIONS_PER_PAGE"), "Permission GUI must paginate the full permission matrix");
        assertTrue(!menu.contains("List.of(\"BUILD\", \"BREAK\", \"INTERACT\""), "Permission GUI must not hard-code the legacy 8-permission subset");
        assertTrue(!menu.contains("private static final List<String> ROLES"), "Permission GUI must not hard-code a fixed role matrix");
        assertTrue(membershipHandler.contains("action instanceof GuiAction.PermissionPage"), "Permission GUI page action must be registered as a typed GUI action");
        assertTrue(membershipHandler.contains("permissionPage.rolePage()"), "Permission GUI page action must route role page state");
    }

    @Test
    void memberMenuPaginatesBeyondFirstInventoryPage() throws Exception {
        Path root = repositoryRoot();
        String menu = Files.readString(root.resolve("cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/gui/IslandMemberMenu.java"));
        String membershipHandler = Files.readString(root.resolve("cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/command/IslandMembershipCommandHandler.java"));

        assertTrue(menu.contains("MEMBERS_PER_PAGE"), "Member GUI must declare a page size");
        assertTrue(menu.contains(".skip((long) safePage * MEMBERS_PER_PAGE)"), "Member GUI must page through members instead of truncating to the first 45");
        assertTrue(menu.contains("\"island.members.page\""), "Member GUI must expose page navigation actions");
        assertTrue(membershipHandler.contains("action instanceof GuiAction.MemberPage"), "Member GUI page action must be registered as a typed GUI action");
    }

    @Test
    void publicWarpsExposeCategoryAndSearchFilters() throws Exception {
        Path root = repositoryRoot();
        String model = Files.readString(root.resolve("cloudislands-api/src/main/java/kr/lunaf/cloudislands/api/model/IslandWarpSnapshot.java"));
        String routes = Files.readString(root.resolve("cloudislands-core-service/src/main/java/kr/lunaf/cloudislands/coreservice/http/routes/IslandWarpRoutes.java"));
        String client = Files.readString(root.resolve("cloudislands-core-client/src/main/java/kr/lunaf/cloudislands/coreclient/CoreApiClient.java"));
        String router = Files.readString(root.resolve("cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/command/IslandCommandRouter.java"));
        String homeWarpHandler = Files.readString(root.resolve("cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/command/IslandHomeWarpCommandHandler.java"));

        assertTrue(model.contains("String category"), "Warp API model must include a category");
        assertTrue(routes.contains("queryText(exchange, \"category\""), "Core public warp route must accept category filters");
        assertTrue(routes.contains("queryText(exchange, \"query\""), "Core public warp route must accept search filters");
        assertTrue(client.contains("listPublicWarps(int limit, String category, String query)"), "Core client must expose filtered public warp lookup");
        assertTrue(router.contains("homeWarpCommands.handleCommand(player, subcommand, args)"), "Paper public warp command must be routed to the home/warp handler");
        assertTrue(homeWarpHandler.contains("listPublicWarps(player, args[1]"), "Paper public warp command must expose category/search arguments");
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
        assertTrue(registrar.contains("GuiStateMenus.listener(registry)"), "GUI state menu listener must be registered with the injected action registry");
        String states = Files.readString(root.resolve("cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/gui/GuiState.java"));
        assertTrue(states.contains("LOADING"), "GUI states must include Loading");
        assertTrue(states.contains("READY"), "GUI states must include Ready");
        assertTrue(states.contains("EMPTY"), "GUI states must include Empty");
        assertTrue(states.contains("ERROR"), "GUI states must include Error");
        assertTrue(states.contains("RETRY"), "GUI states must include Retry");
        assertTrue(states.contains("SAVING"), "GUI states must include Saving");
        assertTrue(states.contains("SUCCESS"), "GUI states must include Success");
        assertTrue(states.contains("CONFLICT"), "GUI states must include Conflict");
        String stateMenus = Files.readString(root.resolve("cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/gui/GuiStateMenus.java"));
        assertTrue(stateMenus.contains("openSaving("), "GUI state menus must expose a Saving screen");
        assertTrue(stateMenus.contains("openSuccess("), "GUI state menus must expose a Success screen");
        assertTrue(stateMenus.contains("openConflict("), "GUI state menus must expose a Conflict screen");
    }

    @Test
    void asyncGuiResponsesAreBoundToCurrentSessionRevision() throws Exception {
        Path root = repositoryRoot();
        Path guiSource = root.resolve("cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/gui");
        try (Stream<Path> files = javaFiles(guiSource)) {
            String violations = files
                .flatMap(path -> asyncGuiSessionViolations(root, path).stream())
                .sorted()
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");

            assertTrue(violations.isBlank(), violations);
        }

        String session = Files.readString(root.resolve("cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/gui/GuiSession.java"));
        assertTrue(session.contains("UUID sessionId"), "GUI sessions must carry a session id");
        assertTrue(session.contains("UUID playerId"), "GUI sessions must carry a player id");
        assertTrue(session.contains("String menuId"), "GUI sessions must carry a menu id");
        assertTrue(session.contains("long revision"), "GUI sessions must carry a revision");
        String sessions = Files.readString(root.resolve("cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/gui/GuiSessions.java"));
        assertTrue(sessions.contains("isCurrent("), "GUI session registry must expose current-session checks");
        assertTrue(sessions.contains("runIfCurrent("), "GUI session registry must guard main-thread rendering");
        String states = Files.readString(root.resolve("cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/gui/GuiStateMenus.java"));
        assertTrue(states.contains("InventoryCloseEvent"), "GUI sessions must be invalidated when menus close");
        assertTrue(states.contains("GuiSessions.invalidate"), "GUI state listener must invalidate stale sessions");
    }

    @Test
    void permissionGuiStagesChangesBeforeSaving() throws Exception {
        Path root = repositoryRoot();
        String backend = Files.readString(root.resolve("cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/command/IslandCommandBackend.java"));
        String permissionHandler = Files.readString(root.resolve("cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/command/IslandPermissionCommandHandler.java"));
        String membershipHandler = Files.readString(root.resolve("cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/command/IslandMembershipCommandHandler.java"));
        String menu = Files.readString(root.resolve("cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/gui/IslandPermissionMenu.java"));

        assertTrue(membershipHandler.contains("action instanceof GuiAction.ChangePermission"), "Permission cells must stage edits through typed GUI actions");
        assertTrue(membershipHandler.contains("runtime.stageIslandPermission"), "Permission cells must stage edits instead of immediately writing Core state");
        assertTrue(membershipHandler.contains("case PERMISSIONS_SAVE"), "Permission save must be the only GUI save action");
        assertTrue(membershipHandler.contains("runtime.saveStagedIslandPermissions"), "Permission save must be the only GUI save action");
        assertTrue(backend.contains("IslandPermissionCommandHandler"), "Permission commands must be extracted from the backend");
        assertTrue(permissionHandler.contains("stagedPermissionChanges"), "Permission edits must have a dirty session store");
        assertTrue(permissionHandler.contains("GuiStateMenus.openSaving"), "Permission save must show a Saving state");
        assertTrue(permissionHandler.contains("GuiStateMenus.openSuccess"), "Permission save must show a Success state");
        assertTrue(permissionHandler.contains("GuiStateMenus.openConflict"), "Permission save failures must show Conflict/Error recovery state");
        assertTrue(menu.contains("\"island.permissions.save\""), "Permission menu must expose an explicit save button");
        assertTrue(menu.contains("\"island.permissions.reset\""), "Permission menu must expose a reset/cancel button");
        assertTrue(!membershipHandler.contains("case \"island.permissions.set\""), "Permission cell clicks must not use raw string fallback routing");
    }

    @Test
    void roleMenuUsesRegisteredActionsInsteadOfCommandHints() throws Exception {
        Path root = repositoryRoot();
        String permissionHandler = Files.readString(root.resolve("cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/command/IslandPermissionCommandHandler.java"));
        String membershipHandler = Files.readString(root.resolve("cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/command/IslandMembershipCommandHandler.java"));
        String menu = Files.readString(root.resolve("cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/gui/IslandRoleMenu.java"));
        String menuConfig = Files.readString(root.resolve("cloudislands-paper/src/main/resources/config-v2/ui/menus/roles.yml"));
        String translations = Files.readString(root.resolve("cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/message/TranslationManager.java"));

        assertTrue(menu.contains("\"island.role.weight.adjust\""), "Role GUI must expose a direct role edit action");
        assertTrue(menu.contains("GuiItems.action(material(role.role())"), "Role rows must use PDC action metadata");
        assertTrue(menu.contains("GuiMenuRenderer.render(MENU"), "Role menu controls must render from config-v2 metadata");
        assertTrue(menuConfig.contains("material: PAPER"), "Role list control must be declarative config");
        assertTrue(menuConfig.contains("material: COMPARATOR"), "Role permission control must be declarative config");
        assertTrue(membershipHandler.contains("action instanceof GuiAction.RoleWeightAdjust"), "Role edit action must be registered as a typed GUI action");
        assertTrue(membershipHandler.contains("runtime.adjustIslandRoleWeight"), "Role edit action must route to the role weight adjuster");
        assertTrue(!membershipHandler.contains("IslandRole."), "Membership command presentation must use role keys instead of enum identities");
        assertTrue(!membershipHandler.contains("setIslandMemberRole(Player player, String target, IslandRole"), "Membership runtime boundary must not expose enum role identity");
        assertTrue(permissionHandler.contains("upsertIslandRole(player, roleKey, updatedWeight, displayName)"), "Role edit action must call the Core role mutation with dynamic role keys");
        assertTrue(permissionHandler.contains("resetIslandRole(player, roleKey)"), "Role edit action must support reset through the Core role mutation");
        assertTrue(!menu.contains("역할편집"), "Role GUI must not print command syntax as its edit path");
        assertTrue(!translations.contains("role-menu-edit-prefix"), "Role translations must not keep command-hint edit text");
        assertTrue(!translations.contains("role-menu-list-command"), "Role controls must describe actions rather than command aliases");
    }

    @Test
    void roleTabCompletionUsesCachedRoleCatalog() throws Exception {
        Path root = repositoryRoot();
        String completer = Files.readString(root.resolve("cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/command/IslandCommandTabCompleter.java"));
        String controller = Files.readString(root.resolve("cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/command/IslandCommandController.java"));
        String protection = Files.readString(root.resolve("cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/ProtectionController.java"));
        String cache = Files.readString(root.resolve("cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/cache/LocalIslandPermissionCache.java"));
        String sync = Files.readString(root.resolve("cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/cache/PermissionCacheSyncService.java"));

        assertTrue(!completer.contains("IslandRole.values()"), "role tab completion must not be fixed to the enum role list");
        assertTrue(controller.contains("new IslandCommandTabCompleter(plugin, protection)"), "tab completion must receive the protection/cache boundary");
        assertTrue(completer.contains("protection.roleCatalog"), "tab completion must read the current island role catalog");
        assertTrue(protection.contains("roleCatalog(UUID islandId"), "ProtectionController must expose the cached role catalog");
        assertTrue(cache.contains("putRoleDefinition"), "permission cache must store Core role definitions");
        assertTrue(cache.contains("roleCatalog(UUID islandId"), "permission cache must expose role catalog suggestions");
        assertTrue(sync.contains("client.listIslandRoles"), "permission cache sync must hydrate role catalog from Core roles");
    }

    @Test
    void paperPresentationUsesCoreProfileLocaleCache() throws Exception {
        Path root = repositoryRoot();
        String bootstrap = Files.readString(root.resolve("cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/PaperPluginBootstrap.java"));
        String profileListener = Files.readString(root.resolve("cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/session/PaperPlayerProfileListener.java"));
        String commandBackend = Files.readString(root.resolve("cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/command/IslandCommandBackend.java"));
        String branding = Files.readString(root.resolve("cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/session/PaperBrandingListener.java"));
        String scoreboard = Files.readString(root.resolve("cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/session/PaperScoreboardListener.java"));
        String chat = Files.readString(root.resolve("cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/session/PaperChatListener.java"));

        assertTrue(bootstrap.contains("plugin.playerLocales = new PlayerLocaleCache()"), "Paper runtime must create a player locale cache");
        assertTrue(bootstrap.contains("new PaperPlayerProfileListener(client, plugin.playerLocales)"), "Core profile touch must feed the locale cache");
        assertTrue(profileListener.contains("coreApiClient.playerProfileCommands()"), "Paper profile listener must use typed Core player profile commands");
        assertTrue(profileListener.contains("profile.locale()"), "Paper profile listener must use the typed Core profile locale returned by touch");
        assertTrue(commandBackend.contains("messages.forLocale(locales == null ? player.getLocale() : locales.locale(player))"), "Command and GUI messages must prefer the Core profile locale cache");
        assertTrue(branding.contains("locales == null ? player.getLocale() : locales.locale(player)"), "Tab and join messages must prefer the Core profile locale cache");
        assertTrue(scoreboard.contains("locales == null ? player.getLocale() : locales.locale(player)"), "Scoreboard messages must prefer the Core profile locale cache");
        assertTrue(chat.contains("locales == null ? player.getLocale() : locales.locale(player)"), "Chat renderer must prefer the Core profile locale cache");
    }

    @Test
    void paperCoreMutationCallsCarryRequestMetadata() throws Exception {
        Path root = repositoryRoot();
        List<Path> commandSources = commandActionSources(root);
        List<Path> actionSources = new ArrayList<>(commandSources);
        actionSources.add(root.resolve("cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/application/BankUseCase.java"));
        actionSources.add(root.resolve("cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/application/IslandCreationUseCase.java"));
        actionSources.add(root.resolve("cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/application/SnapshotUseCase.java"));
        List<Path> files = new ArrayList<>(actionSources);
        files.add(root.resolve("cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/api/PaperCloudIslandsApi.java"));
        String violations = files.stream()
            .flatMap(path -> mutationMetadataViolations(root, path).stream())
            .sorted()
            .reduce((left, right) -> left + "\n" + right)
            .orElse("");

        assertTrue(violations.isBlank(), violations);

        String commandBackend = Files.readString(root.resolve("cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/command/IslandCommandBackend.java"));
        String commandActions = actionSources.stream()
            .map(path -> {
                try {
                    return Files.readString(path);
                } catch (Exception exception) {
                    throw new IllegalStateException(exception);
                }
            })
            .reduce((left, right) -> left + "\n" + right)
            .orElse("");
        assertTrue(commandActions.contains("mutateIdempotent(\"island.delete\""), "Island delete must use an idempotency key");
        assertTrue(commandActions.contains("DangerousGuiActionPolicy.confirmed"), "Dangerous GUI mutations must verify a confirmation token");
        assertTrue(commandBackend.contains("ConfirmationTokenPolicy.withToken"), "General confirmation menus must attach confirmation tokens");
        assertTrue(commandActions.contains("confirmationAccepted(player, action, click)"), "Dangerous GUI actions must verify typed confirmation tokens");
        assertTrue(commandActions.contains("mutateIdempotent(\"island.bank.withdraw\""), "Bank withdraw must use an idempotency key");
        assertTrue(commandActions.contains("CoreMutationMetadata.request"), "Paper mutations must carry request IDs and audit actions");
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

    private static List<String> deploymentSecretViolations(Path root, Path path) {
        try {
            List<String> violations = new ArrayList<>();
            List<String> lines = Files.readAllLines(path);
            String relative = root.relativize(path).toString();
            for (int index = 0; index < lines.size(); index++) {
                String line = lines.get(index);
                if (line.matches(".*(ghp_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,}|sk-[A-Za-z0-9]{20,}|AKIA[0-9A-Z]{16}).*")) {
                    violations.add(relative + ":" + (index + 1) + ": contains inline credential-looking token");
                }
                String trimmed = line.trim();
                if (relative.endsWith("docker-compose.yml") && composeInlineSecretEnv(trimmed)) {
                    violations.add(relative + ":" + (index + 1) + ": compose secret env must use *_FILE and /run/secrets");
                }
            }
            if (relative.endsWith("templates/workloads.yaml")) {
                violations.addAll(helmInlineSecretEnvViolations(relative, lines));
            }
            return violations;
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static boolean composeInlineSecretEnv(String trimmed) {
        int separator = trimmed.indexOf(':');
        if (separator <= 0) {
            return false;
        }
        String key = trimmed.substring(0, separator).trim();
        String value = cleanYamlValue(trimmed.substring(separator + 1));
        return sensitiveEnvName(key) && !key.endsWith("_FILE") && !value.isBlank();
    }

    private static List<String> helmInlineSecretEnvViolations(String relative, List<String> lines) {
        List<String> violations = new ArrayList<>();
        for (int index = 0; index < lines.size(); index++) {
            String trimmed = lines.get(index).trim();
            if (!trimmed.startsWith("- name:")) {
                continue;
            }
            String envName = cleanYamlValue(trimmed.substring(trimmed.indexOf(':') + 1));
            if (!sensitiveEnvName(envName)) {
                continue;
            }
            String block = envBlock(lines, index);
            if (!block.contains("valueFrom:") || !block.contains("secretKeyRef:")) {
                violations.add(relative + ":" + (index + 1) + ": " + envName + " must use valueFrom.secretKeyRef");
            }
            if (block.lines().anyMatch(line -> line.trim().startsWith("value:"))) {
                violations.add(relative + ":" + (index + 1) + ": " + envName + " must not use inline value");
            }
        }
        return violations;
    }

    private static String envBlock(List<String> lines, int start) {
        StringBuilder block = new StringBuilder();
        for (int index = start; index < lines.size(); index++) {
            if (index > start && lines.get(index).trim().startsWith("- name:")) {
                break;
            }
            block.append(lines.get(index)).append('\n');
        }
        return block.toString();
    }

    private static boolean sensitiveEnvName(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(java.util.Locale.ROOT);
        return normalized.contains("PASSWORD")
            || normalized.contains("TOKEN")
            || normalized.contains("SECRET")
            || normalized.contains("ACCESS_KEY")
            || normalized.contains("SECRET_KEY")
            || normalized.equals("MINIO_ROOT_USER");
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

    private static List<Path> commandActionSources(Path root) {
        try (Stream<Path> files = javaFiles(root.resolve("cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/command"))) {
            return files.sorted().toList();
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

    private static List<String> asyncGuiSessionViolations(Path root, Path path) {
        try {
            String source = Files.readString(path);
            if (!source.contains(".thenAccept(") || !source.contains("openSync(plugin, player")) {
                return List.of();
            }
            List<String> violations = new ArrayList<>();
            String relative = root.relativize(path).toString();
            if (!source.contains("GuiSession session = GuiSessions.begin(player,")) {
                violations.add(relative + ": async menu must create a current GUI session while opening Loading");
            }
            if (!source.contains("GuiStateMenus.openLoading(plugin, player, session")) {
                violations.add(relative + ": async menu must bind Loading state to the GUI session");
            }
            if (!source.contains("openSync(plugin, player, session")) {
                violations.add(relative + ": async success path must pass the GUI session into openSync");
            }
            if (!source.contains("private static void openSync(Plugin plugin, Player player, GuiSession session")) {
                violations.add(relative + ": async renderer must receive the GUI session");
            }
            if (!source.contains("GuiSessions.runIfCurrent(plugin, player, session")) {
                violations.add(relative + ": async renderer must discard stale session responses");
            }
            if (source.contains(".exceptionally(")
                && source.contains("load-failed")
                && !source.contains("GuiStateMenus.openError(plugin, player, session")) {
                violations.add(relative + ": async error path must discard stale session errors");
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
        Map<String, String> actionAliases = yamlActionDefinitions(lines);
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
                String action = cleanYamlValue(trimmed.substring(trimmed.indexOf(':') + 1));
                values.add(actionAliases.getOrDefault(action, action));
                continue;
            }
            if (inActionBlock && line.startsWith("  ") && trimmed.contains(":")) {
                values.add(cleanYamlValue(trimmed.substring(trimmed.indexOf(':') + 1)));
            }
        }
        return values.stream().filter(PaperPlatformBoundaryTest::isGuiActionId).toList();
    }

    private static Map<String, String> yamlActionDefinitions(List<String> lines) {
        Map<String, String> values = new LinkedHashMap<>();
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
            if (inActionBlock && line.startsWith("  ") && trimmed.contains(":")) {
                int separator = trimmed.indexOf(':');
                values.put(trimmed.substring(0, separator).trim(), cleanYamlValue(trimmed.substring(separator + 1)));
            }
        }
        return values;
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
            "ANVIL",
            "ARROW",
            "BARRIER",
            "BELL",
            "BEACON",
            "BOOK",
            "CHEST",
            "CLOCK",
            "COMPARATOR",
            "COMPASS",
            "COMMAND_BLOCK",
            "EMERALD",
            "EMERALD_BLOCK",
            "ENDER_PEARL",
            "ENDER_EYE",
            "EXPERIENCE_BOTTLE",
            "FILLED_MAP",
            "GOLD_BLOCK",
            "GRAY_DYE",
            "GRASS_BLOCK",
            "HOPPER",
            "IRON_DOOR",
            "LAVA_BUCKET",
            "LEVER",
            "LIME_DYE",
            "MAP",
            "MINECART",
            "NAME_TAG",
            "NETHER_STAR",
            "OAK_DOOR",
            "OAK_SAPLING",
            "PAPER",
            "PLAYER_HEAD",
            "RED_BED",
            "REDSTONE",
            "REDSTONE_BLOCK",
            "REDSTONE_TORCH",
            "TNT",
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
