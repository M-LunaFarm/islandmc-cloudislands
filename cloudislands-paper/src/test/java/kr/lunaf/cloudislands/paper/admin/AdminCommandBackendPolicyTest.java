package kr.lunaf.cloudislands.paper.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class AdminCommandBackendPolicyTest {
    @Test
    void adminTeleportRetainsTheInitiatingPlayerSession() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/admin/AdminCommandBackend.java"));

        assertTrue(source.contains("PlayerConnectionSession playerSession = PlayerConnectionSession.capture(player)"));
        assertTrue(source.contains("thenAccept(ticket -> routeTicket(playerSession, ticket"));
        assertTrue(source.contains("_player -> routeTicketCurrent(playerSession, ticket, failureMessage, attempt)"),
            "every polling transition must reject a replaced connection before another Core request");
        assertTrue(source.contains("routeTicket(playerSession, status.get(), failureMessage, attempt + 1)"));
        assertTrue(source.contains("runIfCurrent(playerSession, _player -> coreApiClient.routingCommands().publishRouteSession(ticket)"),
            "route-session publication must be fenced before Core receives it");
        assertTrue(source.contains("connectWithTicket(playerSession, ticket"));
        assertTrue(source.contains("if (playerSession.isCurrent(activePlayer))"));
        assertTrue(source.contains("clearFailedRoute(ticket, \"PLAYER_SESSION_REPLACED\")"),
            "a published admin route must be cleared if the initiating connection is replaced");
        assertFalse(source.contains("private void routeTicket(Player player, RouteTicket ticket"));
    }

    @Test
    void adminNodeMenuRejectsReplacedConnectionsAndOlderRequests() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/admin/AdminCommandBackend.java"));

        assertTrue(source.contains("GuiSession menuSession = GuiSessions.begin(player, \"admin.node\")"),
            "the menu request must reserve its revision before starting the Core lookup");
        assertTrue(source.contains("GuiStateMenus.openLoading(agent.plugin(), player, menuSession"),
            "the reserved session must own the visible loading state");
        assertTrue(source.contains("thenAccept(summary -> openNodeMenuIfCurrent(player, menuSession, menuMessages, summary))"));
        assertTrue(source.contains("openNodeMenuIfCurrent(player, menuSession, menuMessages, null)"),
            "the fallback response must share the same stale-response fence");
        assertTrue(source.contains("GuiSessions.runIfCurrent(agent.plugin(), player, menuSession"),
            "the final menu must require both the exact Player instance and newest GUI revision");
        assertFalse(source.contains("PaperSchedulers.run(agent.plugin(), () -> AdminNodeMenu.open(player"),
            "raw async callbacks must not reopen an admin menu for a stale Player instance");
    }

    @Test
    void sharedAsyncAdminRunnerRetainsTheInitiatingPlayerSession() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/admin/AdminCommandBackend.java"));

        assertTrue(source.contains("PlayerConnectionSession playerSession = sender instanceof Player player"));
        assertTrue(source.contains("? PlayerConnectionSession.capture(player)"),
            "every shared async admin operation must capture the Player before its future completes");
        assertTrue(source.contains("deliverAsyncAdminMessage(sender, playerSession, action + adminText(\"admin-command-action-complete\""));
        assertTrue(source.contains("deliverAsyncAdminMessage(sender, playerSession, action + adminText(\"admin-command-action-failed\""));
        assertTrue(source.contains("message(playerSession, text)"),
            "Player feedback must reuse the exact-connection scheduler fence");
        assertTrue(source.contains("message(sender, text)"),
            "console and non-Player senders must retain normal async feedback");
    }

    @Test
    void playerNodeManagementOpensLiveSelectorWhileConsoleKeepsTextOutput() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/admin/AdminCommandBackend.java"));
        String menu = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/gui/AdminNodeListMenu.java"));

        assertTrue(source.contains("AdminNodeListMenu.open(agent.plugin(), coreApiClient, player, messagesFor(player))"),
            "player node management must open the live node selector");
        assertTrue(source.contains("run(sender, \"Node list\", coreApiClient.adminNodes().listNodesSummary()"),
            "console node list output must remain available");
        assertTrue(source.contains("case \"admin.node\" -> AdminNodeListMenu.open(agent.plugin(), coreApiClient, target"),
            "admin openmenu must expose the live node selector");
        assertTrue(menu.contains("client.adminNodes().nodes()"), "node selector must load typed Core node snapshots");
    }

    @Test
    void pluginPermissionNodesAreBackedByCommandOrRuntimeChecks() throws Exception {
        String backend = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/admin/AdminCommandBackend.java"));
        String boundaryListener = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/IslandBoundaryListener.java"));
        String mainMenu = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/gui/IslandMainMenu.java"));
        String islandCommandPermissions = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandCommandPermission.java"));
        String velocityActionSupport = Files.readString(Path.of("../cloudislands-velocity/src/main/java/kr/lunaf/cloudislands/velocity/VelocityActionSupport.java"));
        String plugin = Files.readString(Path.of("src/main/resources/plugin.yml"));
        String runtimeSources = backend + "\n" + boundaryListener + "\n" + mainMenu + "\n" + velocityActionSupport;

        Set<String> declaredPermissions = declaredPermissionNodes(plugin);
        Set<String> backedPermissions = new TreeSet<>();
        backedPermissions.addAll(commandPermissionNodes(plugin));
        backedPermissions.addAll(explicitHasPermissionNodes(runtimeSources));
        backedPermissions.addAll(explicitPermissionStringNodes(runtimeSources));
        backedPermissions.addAll(explicitPermissionStringNodes(velocityActionSupport));
        backedPermissions.addAll(mappedAdminPermissionNodes(backend));
        backedPermissions.addAll(mappedIslandPermissionNodes(islandCommandPermissions));

        assertTrue(backend.contains("if (!hasAdminAccess(sender, args))"), "Admin commands must pass through the runtime permission gate");
        assertTrue(backend.contains("sender.hasPermission(permission)"), "Admin sub-permissions must be checked before routing");
        assertEquals(declaredPermissions, backedPermissions, "Every plugin.yml permission node must be backed by a command descriptor or runtime permission check");
    }

    @Test
    void diagnosticsExportIsAFirstClassAdminCommand() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/admin/AdminCommandBackend.java"));
        String configHandler = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/admin/AdminConfigCommandHandler.java"));
        String catalog = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/admin/AdminCommandCatalog.java"));
        String plugin = Files.readString(Path.of("src/main/resources/plugin.yml"));
        String adminSurface = source + "\n" + catalog;
        String configSurface = source + "\n" + configHandler;

        assertTrue(adminSurface.contains("\"diagnostics\""), "Diagnostics root command must be registered");
        assertTrue(adminSurface.contains("ciadmin diagnostics export"), "Diagnostics export must be listed in help");
        assertTrue(source.contains("handleDiagnostics"), "Diagnostics command must have a handler");
        assertTrue(source.contains("cloudislands.admin.\" + root"), "Diagnostics must be covered by admin permission mapping");
        assertTrue(source.contains("redactDiagnostic"), "Diagnostics export must redact secrets");
        assertTrue(source.contains("coreApiClient.adminStorage().status()"), "Diagnostics export must include typed storage health");
        assertTrue(source.contains("coreApiClient.adminMetrics().summary()"), "Diagnostics export must include typed metrics");
        assertTrue(source.contains("coreApiClient.adminCoreConfig().config()"), "Diagnostics export must include typed core config");
        assertTrue(source.contains("coreApiClient.adminNodes().listNodesSummary()"), "Diagnostics export must include typed node context");
        assertTrue(source.contains("heartbeatLagDiagnosticBody(AdminNodeSummaryView"), "Heartbeat diagnostics must render from typed node context");
        assertTrue(!source.contains("coreApiClient.listNodes().thenApply(Object::toString)"), "Diagnostics export must not parse raw node list bodies");
        assertTrue(source.contains("coreApiClient.adminRoutes().debug(new UUID(0L, 0L))"), "Diagnostics export must include typed route ticket debug state");
        assertTrue(source.contains("coreApiClient.jobs().list().thenApply(this::jobListMessage)"), "Diagnostics export must include typed job context");
        assertTrue(source.contains("diagnosticSection(\"route-debug\""), "Diagnostics bundle must have a route debug section");
        assertTrue(source.contains("diagnosticSection(\"heartbeat-lag\""), "Diagnostics bundle must have a heartbeat lag section");
        assertTrue(source.contains("heartbeatLagDiagnosticBody"), "Diagnostics export must summarize heartbeat lag from node state");
        assertTrue(source.contains("nodes.staleNodeCount()"), "Heartbeat diagnostics must expose typed stale node count");
        assertTrue(source.contains("nodes.heartbeatTimeoutSeconds()"), "Heartbeat diagnostics must expose typed heartbeat timeout");
        assertTrue(source.contains("coreApiClient.adminAudit().list(25)"), "Diagnostics export must include bounded typed audit context");
        assertTrue(source.contains("configHandler.validationDiagnosticSectionAsync()"), "Diagnostics export must include async local config validation");
        assertTrue(source.contains("configHandler.effectiveConfigDiagnosticSectionAsync()"), "Diagnostics export must include async redacted effective config");
        assertTrue(source.contains("PaperSchedulers.supplyAsync(agent.plugin(), () -> writeDiagnostics"), "Diagnostics filesystem writes must execute away from the Paper thread");
        assertTrue(configHandler.contains("## config-validation"), "Diagnostics bundle must have a config validation section");
        assertTrue(configHandler.contains("## effective-config-redacted"), "Diagnostics bundle must have a redacted effective config section");
        assertTrue(source.contains("pluginVersion="), "Diagnostics bundle must include runtime version context");
        assertTrue(configHandler.contains("validateConfigV2Bundle()"), "Diagnostics config validation must use the same validator as config reload");
        assertTrue(configHandler.contains("effectiveConfigV2Yaml(true)"), "Diagnostics effective config must be redacted");
        assertTrue(configSurface.contains("AdminConfigCommandHandler"), "Config admin operations must be split from the main backend");
        assertTrue(plugin.contains("cloudislands.admin.diagnostics"), "Diagnostics command must have a plugin permission");
    }

    @Test
    void supportBundleCreateIsAFirstClassAdminCommand() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/admin/AdminCommandBackend.java"));
        String catalog = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/admin/AdminCommandCatalog.java"));
        String plugin = Files.readString(Path.of("src/main/resources/plugin.yml"));
        String coreClient = Files.readString(Path.of("../cloudislands-core-client/src/main/java/kr/lunaf/cloudislands/coreclient/CoreApiClient.java"));
        String jdkClient = Files.readString(Path.of("../cloudislands-core-client/src/main/java/kr/lunaf/cloudislands/coreclient/JdkAdminSupportBundleClient.java"));
        String adminSurface = source + "\n" + catalog;

        assertTrue(adminSurface.contains("\"support-bundle\""), "Support bundle root command must be registered");
        assertTrue(adminSurface.contains("ciadmin support-bundle create"), "Support bundle create must be listed in help");
        assertTrue(source.contains("handleSupportBundle"), "Support bundle command must have a handler");
        assertTrue(source.contains("coreApiClient.adminSupportBundle().create()"), "Support bundle command must use the typed Core support bundle client");
        assertTrue(source.contains("writeSupportBundle"), "Support bundle command must write a local support bundle file");
        assertTrue(source.contains("cloudislands-support-bundle-") && source.contains(".zip"), "Support bundle must be packaged as a zip bundle");
        assertTrue(source.contains("core-support-bundle.json"), "Support bundle zip must include the redacted Core bundle");
        assertTrue(source.contains("paper-runtime.txt"), "Support bundle zip must include local Paper runtime context");
        assertTrue(source.contains("redactDiagnostic(coreBundleJson"), "Support bundle output must pass through redaction");
        assertTrue(coreClient.contains("AdminSupportBundleClient adminSupportBundle()"), "Core client must expose a typed support bundle client");
        assertTrue(jdkClient.contains("postResultBody(\"/v1/admin/support-bundle\", \"{}\")"), "Support bundle client must call the Core support-bundle endpoint");
        assertTrue(plugin.contains("cloudislands.admin.support-bundle"), "Support bundle command must have a plugin permission");
    }

    @Test
    void supportBundleRedactionRemovesSecretsAndTokens() throws Exception {
        String redacted = AdminDiagnosticRedactor.redact("{\"token\":\"ghp_example123456789\",\"password\":\"plain\",\"authorization\":\"Bearer secret\"}");

        assertTrue(redacted.contains("token=***"), "Token field must be redacted");
        assertTrue(redacted.contains("password=***"), "Password field must be redacted");
        assertTrue(redacted.contains("authorization=***"), "Authorization field must be redacted");
        assertTrue(!redacted.contains("ghp_example123456789"), "GitHub token material must not remain");
        assertTrue(!redacted.contains("plain"), "Password value must not remain");
        assertTrue(!redacted.contains("Bearer secret"), "Authorization value must not remain");
    }

    @Test
    void adminPlayerDisbandQuotaCommandsAreBackedByTypedCoreProfileMutations() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/admin/AdminCommandBackend.java"));
        String catalog = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/admin/AdminCommandCatalog.java"));
        String profileClient = Files.readString(Path.of("../cloudislands-core-client/src/main/java/kr/lunaf/cloudislands/coreclient/PlayerProfileCommandClient.java"));
        String jdkProfileClient = Files.readString(Path.of("../cloudislands-core-client/src/main/java/kr/lunaf/cloudislands/coreclient/JdkPlayerProfileCommandClient.java"));
        String coreRoutes = Files.readString(Path.of("../cloudislands-core-service/src/main/java/kr/lunaf/cloudislands/coreservice/http/routes/PlayerProfileRoutes.java"));
        String profileModel = Files.readString(Path.of("../cloudislands-api/src/main/java/kr/lunaf/cloudislands/api/model/PlayerIslandProfile.java"));
        String parity = Files.readString(Path.of("../gradle/report-gates.gradle.kts"));
        String adminSurface = source + "\n" + catalog;

        assertTrue(adminSurface.contains("\"setdisbands\""), "Admin player command completion must expose setdisbands");
        assertTrue(adminSurface.contains("\"givedisbands\""), "Admin player command completion must expose givedisbands");
        assertTrue(adminSurface.contains("ciadmin player setdisbands <player> <value>"), "setdisbands must be listed in help");
        assertTrue(adminSurface.contains("ciadmin player givedisbands <player> <delta>"), "givedisbands must be listed in help");
        assertTrue(adminSurface.contains("ciadmin player setisland <player> <island>"), "setisland help must accept an island UUID or name");
        assertTrue(source.contains("coreApiClient.playerProfileCommands().setDisbandsRemaining(playerUuid, requestedDisbands)"), "setdisbands must call the typed Core profile mutation with prevalidated input");
        assertTrue(source.contains("coreApiClient.playerProfileCommands().addDisbandsRemaining(playerUuid, requestedDisbands)"), "givedisbands must call the typed Core profile mutation with prevalidated input");
        assertTrue(source.contains("setPlayerPrimaryIsland(sender, playerUuid, requestedIslandTarget)"), "setisland must resolve its island target before mutating the player profile");
        assertTrue(source.contains("resolveIslandUuid(sender, islandTarget)"), "setisland must share UUID and exact island-name resolution with other admin commands");
        assertTrue(source.contains("setPrimaryIsland(playerUuid, islandId)"), "setisland must pass the resolved immutable island UUID to Core");
        int playerHandler = source.indexOf("private boolean handlePlayer(CommandSender sender, String[] args)");
        int playerResolution = source.indexOf("resolvePlayerUuid(sender, args[2]).thenAccept", playerHandler);
        int playerResolutionEnd = source.indexOf("}).exceptionally(error ->", playerResolution);
        String playerResolutionCallback = source.substring(playerResolution, playerResolutionEnd);
        assertTrue(source.indexOf("String requestedIslandTarget", playerHandler) < playerResolution, "player command arguments must be captured before asynchronous profile resolution");
        assertFalse(playerResolutionCallback.contains("sender.sendMessage"), "profile resolution callbacks must not use Bukkit CommandSender directly");
        assertFalse(playerResolutionCallback.contains("uuid(sender"), "profile resolution callbacks must not parse and report UUIDs off-thread");
        assertFalse(playerResolutionCallback.contains("sendCommandUsage"), "profile resolution callbacks must not render usage off-thread");
        assertTrue(source.contains("profile.disbandsRemaining()"), "Admin player output must render the remaining disband quota");
        assertTrue(profileClient.contains("setDisbandsRemaining(UUID playerUuid, int value)"), "Typed profile client must expose absolute disband quota mutation");
        assertTrue(profileClient.contains("addDisbandsRemaining(UUID playerUuid, int delta)"), "Typed profile client must expose additive disband quota mutation");
        assertTrue(jdkProfileClient.contains("postResultBody(\"/v1/admin/players/setdisbands\""), "JDK profile client must call the setdisbands route");
        assertTrue(jdkProfileClient.contains("postResultBody(\"/v1/admin/players/adddisbands\""), "JDK profile client must call the adddisbands route");
        assertTrue(coreRoutes.contains("PLAYER_SET_DISBANDS"), "Core route must audit absolute disband quota changes");
        assertTrue(coreRoutes.contains("PLAYER_ADD_DISBANDS"), "Core route must audit additive disband quota changes");
        assertTrue(profileModel.contains("int disbandsRemaining"), "Player profile model must persist disband quota state");
        assertTrue(parity.contains("superior.admin.givedisbands\", \"cloudislands.admin.player\", \"SUPPORTED_VERIFIED\""), "givedisbands parity must be supported");
        assertTrue(parity.contains("superior.admin.setdisbands\", \"cloudislands.admin.player\", \"SUPPORTED_VERIFIED\""), "setdisbands parity must be supported");
    }

    @Test
    void adminBonusCompatibilityCommandsUseCoreLimitStateAndUpgradeRecalculation() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/admin/AdminCommandBackend.java"));
        String catalog = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/admin/AdminCommandCatalog.java"));
        String progressionClient = Files.readString(Path.of("../cloudislands-core-client/src/main/java/kr/lunaf/cloudislands/coreclient/ProgressionCommandClient.java"));
        String jdkProgressionClient = Files.readString(Path.of("../cloudislands-core-client/src/main/java/kr/lunaf/cloudislands/coreclient/JdkProgressionCommandClient.java"));
        String coreRoutes = Files.readString(Path.of("../cloudislands-core-service/src/main/java/kr/lunaf/cloudislands/coreservice/http/routes/IslandUpgradeRoutes.java"));
        String parity = Files.readString(Path.of("../gradle/report-gates.gradle.kts"));
        String adminSurface = source + "\n" + catalog;

        assertTrue(adminSurface.contains("\"bonus\""), "Bonus inspection root command must be cataloged");
        assertTrue(adminSurface.contains("\"addbonus\""), "Bonus additive root command must be cataloged");
        assertTrue(adminSurface.contains("\"syncbonus\""), "Bonus sync root command must be cataloged");
        assertTrue(adminSurface.contains("ciadmin bonus <island>"), "Bonus command must be listed in help");
        assertTrue(adminSurface.contains("ciadmin addbonus <island> <bonusKey> <delta>"), "Addbonus command must be listed in help");
        assertTrue(adminSurface.contains("ciadmin syncbonus <island>"), "Syncbonus command must be listed in help");
        assertTrue(source.contains("BONUS_LIMIT_PREFIX = \"BONUS:\""), "Bonus compatibility state must use explicit Core limit key namespace");
        assertTrue(source.contains("coreApiClient.environment().limitViews(islandId).thenApply(this::bonusListMessage)"), "Bonus inspection must read typed Core limit state");
        assertTrue(source.contains("coreApiClient.environmentCommands().adminAddLimit(islandId, bonusKey, bonusDelta)"), "Addbonus must mutate typed Core limit state using prevalidated immutable input");
        assertTrue(source.indexOf("if (args[0].equalsIgnoreCase(\"addbonus\") && args.length < 4)") < source.indexOf("resolveIslandUuid(sender, args[1]).thenAccept"), "Addbonus usage validation must happen before asynchronous island resolution");
        assertTrue(source.contains("coreApiClient.progressionCommands().adminRecalculateUpgrades(islandId)"), "Syncbonus must recalculate upgrade effects through typed Core");
        assertTrue(source.contains("return \"cloudislands.admin.upgrade-rules\";"), "Bonus compatibility roots must use upgrade-rules admin permission");
        assertTrue(progressionClient.contains("adminRecalculateUpgrades(UUID islandId)"), "Progression command client must expose admin recalculation");
        assertTrue(jdkProgressionClient.contains("postResultBody(\"/v1/admin/islands/upgrades/recalculate\""), "JDK progression client must call admin recalculation route");
        assertTrue(coreRoutes.contains("/v1/admin/islands/upgrades/recalculate"), "Core upgrade routes must register admin recalculation");
        assertTrue(coreRoutes.contains("ISLAND_UPGRADE_ADMIN_RECALCULATE"), "Core admin recalculation must be audited");
        assertTrue(parity.contains("superior.admin.addbonus\", \"cloudislands.admin.upgrade-rules\", \"SUPPORTED_VERIFIED\""), "addbonus parity must be supported");
        assertTrue(parity.contains("superior.admin.bonus\", \"cloudislands.admin.upgrade-rules\", \"SUPPORTED_VERIFIED\""), "bonus parity must be supported");
        assertTrue(parity.contains("superior.admin.syncbonus\", \"cloudislands.admin.upgrade-rules\", \"SUPPORTED_VERIFIED\""), "syncbonus parity must be supported");
    }

    @Test
    void adminBankDepositAndWithdrawAreFirstClassIslandCommands() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/admin/AdminCommandBackend.java"));
        String catalog = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/admin/AdminCommandCatalog.java"));
        String lifecycleClient = Files.readString(Path.of("../cloudislands-core-client/src/main/java/kr/lunaf/cloudislands/coreclient/IslandLifecycleCommandClient.java"));
        String jdkLifecycleClient = Files.readString(Path.of("../cloudislands-core-client/src/main/java/kr/lunaf/cloudislands/coreclient/JdkIslandLifecycleCommandClient.java"));
        String coreRoutes = Files.readString(Path.of("../cloudislands-core-service/src/main/java/kr/lunaf/cloudislands/coreservice/http/routes/AdminIslandLifecycleRoutes.java"));
        String parity = Files.readString(Path.of("../gradle/report-gates.gradle.kts"));
        String adminSurface = source + "\n" + catalog;

        assertTrue(adminSurface.contains("\"bank\""), "Island bank must be cataloged for admin completion");
        assertTrue(adminSurface.contains("ciadmin island bank deposit <island> <amount>"), "Admin bank deposit must be listed in help");
        assertTrue(adminSurface.contains("ciadmin island bank withdraw <island> <amount>"), "Admin bank withdraw must be listed in help");
        assertTrue(source.contains("handleIslandBank"), "Admin bank commands must have a dedicated handler before island id resolution");
        assertTrue(source.contains("coreApiClient.lifecycle().adminBankDeposit(islandId, args[4])"), "Admin bank deposit must use the typed lifecycle client");
        assertTrue(source.contains("coreApiClient.lifecycle().adminBankWithdraw(islandId, args[4])"), "Admin bank withdraw must use the typed lifecycle client");
        assertTrue(lifecycleClient.contains("adminBankDeposit(UUID islandId, String amount)"), "Lifecycle client must expose admin bank deposit");
        assertTrue(lifecycleClient.contains("adminBankWithdraw(UUID islandId, String amount)"), "Lifecycle client must expose admin bank withdraw");
        assertTrue(jdkLifecycleClient.contains("postResultBody(\"/v1/admin/islands/bank/deposit\""), "JDK lifecycle client must call admin deposit endpoint");
        assertTrue(jdkLifecycleClient.contains("postResultBody(\"/v1/admin/islands/bank/withdraw\""), "JDK lifecycle client must call admin withdraw endpoint");
        assertTrue(coreRoutes.contains("ISLAND_BANK_ADMIN_DEPOSIT"), "Core admin deposit route must audit operator mutation");
        assertTrue(coreRoutes.contains("ISLAND_BANK_ADMIN_WITHDRAW"), "Core admin withdraw route must audit operator mutation");
        assertTrue(coreRoutes.contains("ADMIN_DEPOSIT") && coreRoutes.contains("ADMIN_WITHDRAW"), "Core admin bank routes must emit admin bank event operations");
        assertTrue(parity.contains("\"superior.admin.deposit\", \"cloudislands.admin.island\", \"SUPPORTED_VERIFIED\""), "Feature parity matrix must mark admin deposit verified");
        assertTrue(parity.contains("\"superior.admin.withdraw\", \"cloudislands.admin.island\", \"SUPPORTED_VERIFIED\""), "Feature parity matrix must mark admin withdraw verified");
    }

    @Test
    void adminMemberMutationsAreFirstClassIslandCommands() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/admin/AdminCommandBackend.java"));
        String catalog = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/admin/AdminCommandCatalog.java"));
        String memberClient = Files.readString(Path.of("../cloudislands-core-client/src/main/java/kr/lunaf/cloudislands/coreclient/MemberCommandClient.java"));
        String jdkMemberClient = Files.readString(Path.of("../cloudislands-core-client/src/main/java/kr/lunaf/cloudislands/coreclient/JdkMemberCommandClient.java"));
        String coreRoutes = Files.readString(Path.of("../cloudislands-core-service/src/main/java/kr/lunaf/cloudislands/coreservice/http/routes/IslandMemberRoutes.java"));
        String parity = Files.readString(Path.of("../gradle/report-gates.gradle.kts"));
        String adminSurface = source + "\n" + catalog;

        for (String command : List.of(
            "ciadmin island member add <island> <player> [role]",
            "ciadmin island member kick <island> <player>",
            "ciadmin island member promote <island> <player>",
            "ciadmin island member demote <island> <player>",
            "ciadmin island member setleader <island> <player>",
            "ciadmin island join <island> [role]"
        )) {
            assertTrue(adminSurface.contains(command), command);
        }
        assertTrue(adminSurface.contains("\"member\""), "Island member must be cataloged for admin completion");
        assertTrue(adminSurface.contains("\"join\""), "Island join must be cataloged for admin completion");
        assertTrue(source.contains("handleIslandMember"), "Admin member commands must have a dedicated handler before island id resolution");
        assertTrue(source.contains("handleIslandJoin"), "Admin self-join command must have a dedicated handler after island id resolution");
        assertTrue(source.contains("coreApiClient.memberCommands().adminAddMember(islandId, playerUuid"), "Admin member add must use the typed member client");
        assertTrue(source.contains("coreApiClient.memberCommands().adminAddMember(islandId, player.getUniqueId(), roleKey)"), "Admin join must add the command sender through the typed member client");
        assertTrue(source.contains("coreApiClient.memberCommands().adminKickMember(islandId, playerUuid)"), "Admin member kick must use the typed member client");
        assertTrue(source.contains("coreApiClient.memberCommands().adminPromoteMember(islandId, playerUuid)"), "Admin member promote must use the typed member client");
        assertTrue(source.contains("coreApiClient.memberCommands().adminDemoteMember(islandId, playerUuid)"), "Admin member demote must use the typed member client");
        assertTrue(source.contains("coreApiClient.memberCommands().adminSetLeader(islandId, playerUuid)"), "Admin member setleader must use the typed member client");
        assertTrue(memberClient.contains("adminAddMember(UUID islandId, UUID targetUuid, String roleKey)"), "Member client must expose admin add");
        assertTrue(memberClient.contains("adminKickMember(UUID islandId, UUID targetUuid)"), "Member client must expose admin kick");
        assertTrue(memberClient.contains("adminPromoteMember(UUID islandId, UUID targetUuid)"), "Member client must expose admin promote");
        assertTrue(memberClient.contains("adminDemoteMember(UUID islandId, UUID targetUuid)"), "Member client must expose admin demote");
        assertTrue(memberClient.contains("adminSetLeader(UUID islandId, UUID targetUuid)"), "Member client must expose admin setleader");
        assertTrue(jdkMemberClient.contains("postResultBody(\"/v1/admin/islands/members/add\""), "JDK member client must call admin add endpoint");
        assertTrue(jdkMemberClient.contains("postResultBody(\"/v1/admin/islands/members/setleader\""), "JDK member client must call admin setleader endpoint");
        assertTrue(coreRoutes.contains("ISLAND_MEMBER_ADMIN_ADD"), "Core admin add route must audit operator mutation");
        assertTrue(coreRoutes.contains("ISLAND_MEMBER_ADMIN_KICK"), "Core admin kick route must audit operator mutation");
        assertTrue(coreRoutes.contains("ISLAND_MEMBER_ADMIN_PROMOTE"), "Core admin promote route must audit operator mutation");
        assertTrue(coreRoutes.contains("ISLAND_MEMBER_ADMIN_DEMOTE"), "Core admin demote route must audit operator mutation");
        assertTrue(coreRoutes.contains("ISLAND_MEMBER_ADMIN_SETLEADER"), "Core admin setleader route must audit operator mutation");
        for (String permission : List.of("superior.admin.add", "superior.admin.join", "superior.admin.kick", "superior.admin.promote", "superior.admin.demote", "superior.admin.setleader")) {
            assertTrue(parity.contains("\"" + permission + "\", \"cloudislands.admin.island\", \"SUPPORTED_VERIFIED\""), "Feature parity matrix must mark " + permission + " verified");
        }
    }

    @Test
    void superiorSkyblockPermissionBacklogOnlyContainsIncompleteParityEntries() throws Exception {
        String gates = Files.readString(Path.of("../gradle/report-gates.gradle.kts"));

        assertTrue(gates.contains(".filter { it.status !in setOf(\"SUPPORTED_VERIFIED\", \"COVERED_BY\") }"), "Permission backlog must not list shipped or covered parity entries");
        assertTrue(gates.contains("incompleteHighPriority"), "P0/P1 permission parity must fail fast when not supported");
        assertTrue(gates.contains("it.priority in setOf(\"P0\", \"P1\") && it.status !in setOf(\"SUPPORTED_VERIFIED\", \"COVERED_BY\")"), "P0/P1 incomplete permission parity must be rejected");
        assertTrue(!gates.contains("SuperiorSkyblock2 permission backlog must include P0/P1/P2 groups"), "Backlog verification must not force completed P0/P1 entries to remain in generated backlog");
        assertTrue(gates.contains("SuperiorSkyblock2 permission backlog priority drift"), "Backlog priority validation must compare against actual incomplete entries");
    }

    @Test
    void adminRenameAndBiomeAreFirstClassIslandCommands() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/admin/AdminCommandBackend.java"));
        String catalog = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/admin/AdminCommandCatalog.java"));
        String settingsClient = Files.readString(Path.of("../cloudislands-core-client/src/main/java/kr/lunaf/cloudislands/coreclient/IslandSettingsCommandClient.java"));
        String jdkSettingsClient = Files.readString(Path.of("../cloudislands-core-client/src/main/java/kr/lunaf/cloudislands/coreclient/JdkIslandSettingsCommandClient.java"));
        String environmentClient = Files.readString(Path.of("../cloudislands-core-client/src/main/java/kr/lunaf/cloudislands/coreclient/IslandEnvironmentCommandClient.java"));
        String jdkEnvironmentClient = Files.readString(Path.of("../cloudislands-core-client/src/main/java/kr/lunaf/cloudislands/coreclient/JdkIslandEnvironmentCommandClient.java"));
        String coreRoutes = Files.readString(Path.of("../cloudislands-core-service/src/main/java/kr/lunaf/cloudislands/coreservice/http/routes/IslandSettingsRoutes.java"));
        String parity = Files.readString(Path.of("../gradle/report-gates.gradle.kts"));
        String adminSurface = source + "\n" + catalog;

        assertTrue(adminSurface.contains("ciadmin island rename <island> <name>"), "Admin rename must be listed in help");
        assertTrue(adminSurface.contains("ciadmin island setbiome <island> <biomeKey>"), "Admin biome mutation must be listed in help");
        assertTrue(catalog.contains("\"rename\"") && catalog.contains("\"setbiome\""), "Admin rename and biome commands must be cataloged for completion");
        assertTrue(source.contains("coreApiClient.settingsCommands().adminSetName(islandId, joined(args, 3))"), "Admin rename must use the typed settings client");
        assertTrue(source.contains("coreApiClient.environmentCommands().adminSetBiome(islandId, args[3])"), "Admin biome mutation must use the typed environment client");
        assertTrue(settingsClient.contains("adminSetName(UUID islandId, String name)"), "Settings client must expose admin rename");
        assertTrue(jdkSettingsClient.contains("postResultBody(\"/v1/admin/islands/name\""), "JDK settings client must call admin rename endpoint");
        assertTrue(environmentClient.contains("adminSetBiome(UUID islandId, String biomeKey)"), "Environment client must expose admin biome mutation");
        assertTrue(jdkEnvironmentClient.contains("postResultBody(\"/v1/admin/islands/biome/set\""), "JDK environment client must call admin biome endpoint");
        assertTrue(coreRoutes.contains("/v1/admin/islands/name"), "Core settings routes must register admin rename");
        assertTrue(coreRoutes.contains("/v1/admin/islands/biome/set"), "Core settings routes must register admin biome mutation");
        assertTrue(coreRoutes.contains("ISLAND_ADMIN_RENAME"), "Core admin rename route must audit operator mutation");
        assertTrue(coreRoutes.contains("ISLAND_BIOME_ADMIN_SET"), "Core admin biome route must audit operator mutation");
        assertTrue(parity.contains("\"superior.admin.name\", \"cloudislands.admin.island\", \"SUPPORTED_VERIFIED\""), "Feature parity matrix must mark admin rename verified");
        assertTrue(parity.contains("\"superior.admin.setbiome\", \"cloudislands.admin.island\", \"SUPPORTED_VERIFIED\""), "Feature parity matrix must mark admin biome verified");
    }

    @Test
    void adminSettingsMutationsAreFirstClassIslandCommands() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/admin/AdminCommandBackend.java"));
        String catalog = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/admin/AdminCommandCatalog.java"));
        String settingsClient = Files.readString(Path.of("../cloudislands-core-client/src/main/java/kr/lunaf/cloudislands/coreclient/IslandSettingsCommandClient.java"));
        String jdkSettingsClient = Files.readString(Path.of("../cloudislands-core-client/src/main/java/kr/lunaf/cloudislands/coreclient/JdkIslandSettingsCommandClient.java"));
        String coreRoutes = Files.readString(Path.of("../cloudislands-core-service/src/main/java/kr/lunaf/cloudislands/coreservice/http/routes/IslandSettingsRoutes.java"));
        String metadataRepository = Files.readString(Path.of("../cloudislands-core-service/src/main/java/kr/lunaf/cloudislands/coreservice/repository/IslandMetadataRepository.java"));
        String parity = Files.readString(Path.of("../gradle/report-gates.gradle.kts"));
        String adminSurface = source + "\n" + catalog;

        assertTrue(adminSurface.contains("ciadmin island setsettings <island> <flag> <value>"), "Admin setsettings must be listed in help");
        assertTrue(adminSurface.contains("ciadmin island resetsettings <island>"), "Admin resetsettings must be listed in help");
        assertTrue(catalog.contains("\"setsettings\""), "Admin setsettings must be cataloged for completion");
        assertTrue(catalog.contains("\"resetsettings\""), "Admin resetsettings must be cataloged for completion");
        assertTrue(source.contains("coreApiClient.settingsCommands().adminSetFlag(islandId, flag, args[4])"), "Admin setsettings must use typed settings client");
        assertTrue(source.contains("coreApiClient.settingsCommands().adminResetFlags(islandId)"), "Admin resetsettings must use typed settings client");
        assertTrue(settingsClient.contains("adminSetFlag(UUID islandId, IslandFlag flag, String value)"), "Settings client must expose admin flag mutation");
        assertTrue(settingsClient.contains("adminResetFlags(UUID islandId)"), "Settings client must expose admin flag reset");
        assertTrue(jdkSettingsClient.contains("postResultBody(\"/v1/admin/islands/flags/set\""), "JDK settings client must call admin flag set endpoint");
        assertTrue(jdkSettingsClient.contains("postResultBody(\"/v1/admin/islands/flags/reset\""), "JDK settings client must call admin flag reset endpoint");
        assertTrue(coreRoutes.contains("/v1/admin/islands/flags/set"), "Core settings routes must register admin flag set");
        assertTrue(coreRoutes.contains("/v1/admin/islands/flags/reset"), "Core settings routes must register admin flag reset");
        assertTrue(coreRoutes.contains("ISLAND_FLAG_ADMIN_SET"), "Core admin flag set route must audit operator mutation");
        assertTrue(coreRoutes.contains("ISLAND_FLAGS_ADMIN_RESET"), "Core admin flag reset route must audit operator mutation");
        assertTrue(metadataRepository.contains("resetFlags(UUID islandId)"), "Metadata repository must support real flag reset");
        assertTrue(parity.contains("\"superior.admin.setsettings\", \"cloudislands.admin.island\", \"SUPPORTED_VERIFIED\""), "Feature parity matrix must mark superior.admin.setsettings verified");
        assertTrue(parity.contains("\"superior.admin.resetsettings\", \"cloudislands.admin.island\", \"SUPPORTED_VERIFIED\""), "Feature parity matrix must mark superior.admin.resetsettings verified");
    }

    @Test
    void adminWarpDeletionIsAFirstClassIslandCommand() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/admin/AdminCommandBackend.java"));
        String catalog = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/admin/AdminCommandCatalog.java"));
        String homeWarpClient = Files.readString(Path.of("../cloudislands-core-client/src/main/java/kr/lunaf/cloudislands/coreclient/HomeWarpCommandClient.java"));
        String jdkHomeWarpClient = Files.readString(Path.of("../cloudislands-core-client/src/main/java/kr/lunaf/cloudislands/coreclient/JdkHomeWarpCommandClient.java"));
        String coreRoutes = Files.readString(Path.of("../cloudislands-core-service/src/main/java/kr/lunaf/cloudislands/coreservice/http/routes/IslandWarpRoutes.java"));
        String parity = Files.readString(Path.of("../gradle/report-gates.gradle.kts"));
        String adminSurface = source + "\n" + catalog;

        assertTrue(adminSurface.contains("ciadmin island delwarp <island> <warp>"), "Admin warp deletion must be listed in help");
        assertTrue(catalog.contains("\"delwarp\""), "Admin warp deletion must be cataloged for completion");
        assertTrue(source.contains("coreApiClient.homeWarpCommands().adminDeleteWarp(islandId, args[3])"), "Admin warp deletion must use the typed home/warp client");
        assertTrue(homeWarpClient.contains("adminDeleteWarp(UUID islandId, String name)"), "Home/warp client must expose admin delete");
        assertTrue(adminSurface.contains("ciadmin island delhome <island> <home>"), "Admin home deletion must be listed in help");
        assertTrue(catalog.contains("\"delhome\""), "Admin home deletion must be cataloged for completion");
        assertTrue(source.contains("coreApiClient.homeWarpCommands().adminDeleteHome(islandId, args[3])"), "Admin home deletion must use the typed home/warp client");
        assertTrue(homeWarpClient.contains("adminDeleteHome(UUID islandId, String name)"), "Home/warp client must expose admin home delete");
        assertTrue(jdkHomeWarpClient.contains("postResultBody(\"/v1/admin/islands/homes/delete\""), "JDK home/warp client must call admin home delete endpoint");
        assertTrue(coreRoutes.contains("/v1/admin/islands/homes/delete"), "Core home routes must register admin delete");
        assertTrue(coreRoutes.contains("ISLAND_HOME_ADMIN_DELETE"), "Core admin home delete route must audit operator mutation");
        assertTrue(jdkHomeWarpClient.contains("postResultBody(\"/v1/admin/islands/warps/delete\""), "JDK home/warp client must call admin delete endpoint");
        assertTrue(coreRoutes.contains("/v1/admin/islands/warps/delete"), "Core warp routes must register admin delete");
        assertTrue(coreRoutes.contains("ISLAND_WARP_ADMIN_DELETE"), "Core admin delete route must audit operator mutation");
        assertTrue(parity.contains("\"superior.admin.delwarp\", \"cloudislands.admin.island\", \"SUPPORTED_VERIFIED\""), "Feature parity matrix must mark admin delwarp verified");
    }

    @Test
    void adminGeneratorMutationsAreFirstClassIslandCommands() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/admin/AdminCommandBackend.java"));
        String catalog = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/admin/AdminCommandCatalog.java"));
        String generatorClient = Files.readString(Path.of("../cloudislands-core-client/src/main/java/kr/lunaf/cloudislands/coreclient/GeneratorCommandClient.java"));
        String jdkGeneratorClient = Files.readString(Path.of("../cloudislands-core-client/src/main/java/kr/lunaf/cloudislands/coreclient/JdkGeneratorCommandClient.java"));
        String coreRoutes = Files.readString(Path.of("../cloudislands-core-service/src/main/java/kr/lunaf/cloudislands/coreservice/http/routes/AdminGeneratorRoutes.java"));
        String parity = Files.readString(Path.of("../gradle/report-gates.gradle.kts"));
        String adminSurface = source + "\n" + catalog;

        assertTrue(adminSurface.contains("ciadmin island setgenerator <island> <generatorKey> <level>"), "Admin generator set must be listed in help");
        assertTrue(adminSurface.contains("ciadmin island addgenerator <island> <levels> [generatorKey]"), "Admin generator add must be listed in help");
        assertTrue(adminSurface.contains("ciadmin island cleargenerator <island>"), "Admin generator clear must be listed in help");
        assertTrue(catalog.contains("\"setgenerator\"") && catalog.contains("\"addgenerator\"") && catalog.contains("\"cleargenerator\""), "Admin generator commands must be cataloged for completion");
        assertTrue(source.contains("coreApiClient.generatorCommands().adminSetGenerator(islandId, args[3], (int) number(args[4], 1L))"), "Admin generator set must use the typed generator client");
        assertTrue(source.contains("coreApiClient.generatorCommands().adminAddGeneratorLevels(islandId, generatorKey, (int) number(args[3], 1L))"), "Admin generator add must use the typed generator client");
        assertTrue(source.contains("coreApiClient.generatorCommands().adminClearGenerator(islandId)"), "Admin generator clear must use the typed generator client");
        assertTrue(generatorClient.contains("adminSetGenerator(UUID islandId, String generatorKey, int level)"), "Generator client must expose admin set");
        assertTrue(generatorClient.contains("adminAddGeneratorLevels(UUID islandId, String generatorKey, int levels)"), "Generator client must expose admin add");
        assertTrue(generatorClient.contains("adminClearGenerator(UUID islandId)"), "Generator client must expose admin clear");
        assertTrue(jdkGeneratorClient.contains("postResultBody(\"/v1/admin/islands/generator/set\""), "JDK generator client must call admin set endpoint");
        assertTrue(jdkGeneratorClient.contains("postResultBody(\"/v1/admin/islands/generator/add\""), "JDK generator client must call admin add endpoint");
        assertTrue(jdkGeneratorClient.contains("postResultBody(\"/v1/admin/islands/generator/clear\""), "JDK generator client must call admin clear endpoint");
        assertTrue(coreRoutes.contains("/v1/admin/islands/generator/set"), "Core admin generator routes must register set");
        assertTrue(coreRoutes.contains("/v1/admin/islands/generator/add"), "Core admin generator routes must register add");
        assertTrue(coreRoutes.contains("/v1/admin/islands/generator/clear"), "Core admin generator routes must register clear");
        assertTrue(coreRoutes.contains("ISLAND_GENERATOR_ADMIN_SET"), "Core admin generator set route must audit operator mutation");
        assertTrue(coreRoutes.contains("ISLAND_GENERATOR_ADMIN_ADD"), "Core admin generator add route must audit operator mutation");
        assertTrue(coreRoutes.contains("ISLAND_GENERATOR_ADMIN_CLEAR"), "Core admin generator clear route must audit operator mutation");
        assertTrue(parity.contains("\"superior.admin.setgenerator\", \"cloudislands.admin.island\", \"SUPPORTED_VERIFIED\""), "Feature parity matrix must mark admin setgenerator verified");
        assertTrue(parity.contains("\"superior.admin.addgenerator\", \"cloudislands.admin.island\", \"SUPPORTED_VERIFIED\""), "Feature parity matrix must mark admin addgenerator verified");
        assertTrue(parity.contains("\"superior.admin.cleargenerator\", \"cloudislands.admin.island\", \"SUPPORTED_VERIFIED\""), "Feature parity matrix must mark admin cleargenerator verified");
    }

    @Test
    void adminLimitMutationsAreFirstClassIslandCommands() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/admin/AdminCommandBackend.java"));
        String catalog = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/admin/AdminCommandCatalog.java"));
        String environmentClient = Files.readString(Path.of("../cloudislands-core-client/src/main/java/kr/lunaf/cloudislands/coreclient/IslandEnvironmentCommandClient.java"));
        String jdkEnvironmentClient = Files.readString(Path.of("../cloudislands-core-client/src/main/java/kr/lunaf/cloudislands/coreclient/JdkIslandEnvironmentCommandClient.java"));
        String coreRoutes = Files.readString(Path.of("../cloudislands-core-service/src/main/java/kr/lunaf/cloudislands/coreservice/http/routes/ProgressionRoutes.java"));
        String parity = Files.readString(Path.of("../gradle/report-gates.gradle.kts"));
        String adminSurface = source + "\n" + catalog;

        for (String command : List.of("setbanklimit", "addbanklimit", "setentitylimit", "addentitylimit", "setteamlimit", "addteamlimit", "setcooplimit", "addcooplimit", "setrolelimit", "setchestrow", "setwarpslimit", "addwarpslimit", "setsize", "addsize")) {
            assertTrue(adminSurface.contains("ciadmin island " + command + " <island>"), "Admin limit command must be listed in help: " + command);
            assertTrue(catalog.contains("\"" + command + "\""), "Admin limit command must be cataloged for completion: " + command);
        }
        assertTrue(source.contains("coreApiClient.environmentCommands().adminSetLimit(islandId, adminLimitKey, value)"), "Admin absolute limit mutations must use the typed environment client");
        assertTrue(source.contains("coreApiClient.environmentCommands().adminAddLimit(islandId, adminLimitKey, value)"), "Admin additive limit mutations must use the typed environment client");
        assertTrue(source.contains("GameplayParityPolicy.roleLimitKey(roleKey)"), "Role limit command must use the shared role-limit key");
        assertTrue(source.contains("coreApiClient.environmentCommands().adminSetLimit(islandId, limitKey, number(args[4], 0L))"), "Admin role limits must use the typed environment client");
        assertTrue(source.contains("GameplayParityPolicy.WAREHOUSE_ROWS_LIMIT_KEY"), "Chest row command must use the shared warehouse rows limit key");
        assertTrue(source.contains("coreApiClient.environmentCommands().adminSetLimit(islandId, GameplayParityPolicy.WAREHOUSE_ROWS_LIMIT_KEY, rows)"), "Admin chest rows must use the typed environment client");
        assertTrue(source.contains("case \"setbanklimit\", \"addbanklimit\" -> \"BANK\""), "Bank limit commands must map to the BANK limit key");
        assertTrue(source.contains("case \"setteamlimit\", \"addteamlimit\" -> \"MEMBERS\""), "Team limit commands must map to the enforced MEMBERS limit key");
        assertTrue(source.contains("case \"setcooplimit\", \"addcooplimit\" -> GameplayParityPolicy.roleLimitKey(\"TRUSTED\")"), "Co-op limit commands must map to the enforced TRUSTED role limit key");
        assertTrue(source.contains("case \"setwarpslimit\", \"addwarpslimit\" -> \"WARPS\""), "Warp limit commands must map to the enforced WARPS limit key");
        assertTrue(source.contains("case \"setsize\", \"addsize\" -> \"SIZE\""), "Size commands must map to the SIZE limit key");
        assertTrue(environmentClient.contains("adminSetLimit(UUID islandId, String limitKey, long value)"), "Environment client must expose admin set limit");
        assertTrue(environmentClient.contains("adminAddLimit(UUID islandId, String limitKey, long delta)"), "Environment client must expose admin add limit");
        assertTrue(jdkEnvironmentClient.contains("postResultBody(\"/v1/admin/islands/limits/set\""), "JDK environment client must call admin set limit endpoint");
        assertTrue(jdkEnvironmentClient.contains("postResultBody(\"/v1/admin/islands/limits/add\""), "JDK environment client must call admin add limit endpoint");
        assertTrue(coreRoutes.contains("/v1/admin/islands/limits/set"), "Core progression routes must register admin limit set");
        assertTrue(coreRoutes.contains("/v1/admin/islands/limits/add"), "Core progression routes must register admin limit add");
        assertTrue(coreRoutes.contains("ISLAND_LIMIT_ADMIN_SET"), "Core admin limit set route must audit operator mutation");
        assertTrue(coreRoutes.contains("ISLAND_LIMIT_ADMIN_ADD"), "Core admin limit add route must audit operator mutation");
        for (String permission : List.of("superior.admin.setbanklimit", "superior.admin.addbanklimit", "superior.admin.setentitylimit", "superior.admin.addentitylimit", "superior.admin.setteamlimit", "superior.admin.addteamlimit", "superior.admin.setcooplimit", "superior.admin.addcooplimit", "superior.admin.setrolelimit", "superior.admin.setchestrow", "superior.admin.setwarpslimit", "superior.admin.addwarpslimit", "superior.admin.setsize", "superior.admin.addsize")) {
            assertTrue(parity.contains("\"" + permission + "\", \"cloudislands.admin.island\", \"SUPPORTED_VERIFIED\""), "Feature parity matrix must mark " + permission + " verified");
        }
    }

    @Test
    void adminBlockAndEntityLimitRemovalCommandsAreFirstClassIslandCommands() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/admin/AdminCommandBackend.java"));
        String catalog = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/admin/AdminCommandCatalog.java"));
        String parity = Files.readString(Path.of("../gradle/report-gates.gradle.kts"));
        String adminSurface = source + "\n" + catalog;

        for (String command : List.of("setblocklimit", "addblocklimit", "removeblocklimit", "removeentitylimit")) {
            assertTrue(adminSurface.contains("ciadmin island " + command + " <island>"), "Admin block/entity limit command must be listed in help: " + command);
            assertTrue(catalog.contains("\"" + command + "\""), "Admin block/entity limit command must be cataloged for completion: " + command);
        }
        assertTrue(source.contains("GameplayParityPolicy.blockAmountLimitKey(args[3])"), "Block limit commands must write the shared BLOCK_AMOUNT limit key");
        assertTrue(source.contains("coreApiClient.environmentCommands().adminAddLimit(islandId, limitKey, number(args[4], 0L))"), "Admin additive block limits must use typed environment client");
        assertTrue(source.contains("coreApiClient.environmentCommands().adminSetLimit(islandId, limitKey, Long.MAX_VALUE)"), "Admin block limit removal must set an unbounded limit");
        assertTrue(source.contains("args.length > 3 ? GameplayParityPolicy.entityTypeLimitKey(args[3]) : \"ENTITY\""), "Admin entity limit removal must preserve global syntax and support exact entity types");
        assertTrue(source.contains("coreApiClient.environmentCommands().adminSetLimit(islandId, limitKey, Long.MAX_VALUE)"), "Admin entity limit removal must set the selected limit to unbounded");
        for (String permission : List.of("superior.admin.setblocklimit", "superior.admin.addblocklimit", "superior.admin.removeblocklimit", "superior.admin.removeentitylimit")) {
            assertTrue(parity.contains("\"" + permission + "\", \"cloudislands.admin.island\", \"SUPPORTED_VERIFIED\""), "Feature parity matrix must mark " + permission + " verified");
        }
    }

    @Test
    void adminRankingIgnoreIsFirstClassIslandCommand() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/admin/AdminCommandBackend.java"));
        String catalog = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/admin/AdminCommandCatalog.java"));
        String progressionClient = Files.readString(Path.of("../cloudislands-core-client/src/main/java/kr/lunaf/cloudislands/coreclient/ProgressionCommandClient.java"));
        String jdkProgressionClient = Files.readString(Path.of("../cloudislands-core-client/src/main/java/kr/lunaf/cloudislands/coreclient/JdkProgressionCommandClient.java"));
        String coreRoutes = Files.readString(Path.of("../cloudislands-core-service/src/main/java/kr/lunaf/cloudislands/coreservice/http/routes/ProgressionRoutes.java"));
        String parity = Files.readString(Path.of("../gradle/report-gates.gradle.kts"));
        String adminSurface = source + "\n" + catalog;

        for (String command : List.of("ignore", "unignore")) {
            assertTrue(adminSurface.contains("ciadmin island " + command + " <island>"), "Admin ranking command must be listed in help: " + command);
            assertTrue(catalog.contains("\"" + command + "\""), "Admin ranking command must be cataloged for completion: " + command);
        }
        assertTrue(source.contains("coreApiClient.progressionCommands().setRankingIgnored(islandId, ignored)"), "Admin ranking ignore must use typed progression client");
        assertTrue(progressionClient.contains("setRankingIgnored(UUID islandId, boolean ignored)"), "Progression client must expose ranking ignore mutation");
        assertTrue(jdkProgressionClient.contains("postResultBody(\"/v1/admin/rankings/ignore\""), "JDK progression client must call ranking ignore endpoint");
        assertTrue(coreRoutes.contains("/v1/admin/rankings/ignore"), "Core progression routes must register ranking ignore");
        assertTrue(coreRoutes.contains("rankingRepository.setIgnored(islandId, ignored)"), "Core route must mutate ranking ignore state");
        assertTrue(coreRoutes.contains("ISLAND_RANKING_IGNORE"), "Core route must audit ranking ignore");
        assertTrue(coreRoutes.contains("ISLAND_RANKING_UNIGNORE"), "Core route must audit ranking unignore");
        assertTrue(parity.contains("\"superior.admin.ignore\", \"cloudislands.admin.island\", \"SUPPORTED_VERIFIED\""), "Feature parity matrix must mark superior.admin.ignore verified");
        assertTrue(parity.contains("\"superior.admin.unignore\", \"cloudislands.admin.island\", \"SUPPORTED_VERIFIED\""), "Feature parity matrix must mark superior.admin.unignore verified");
    }

    @Test
    void adminMissionMutationsAreFirstClassIslandCommands() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/admin/AdminCommandBackend.java"));
        String catalog = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/admin/AdminCommandCatalog.java"));
        String progressionClient = Files.readString(Path.of("../cloudislands-core-client/src/main/java/kr/lunaf/cloudislands/coreclient/ProgressionCommandClient.java"));
        String jdkProgressionClient = Files.readString(Path.of("../cloudislands-core-client/src/main/java/kr/lunaf/cloudislands/coreclient/JdkProgressionCommandClient.java"));
        String coreRoutes = Files.readString(Path.of("../cloudislands-core-service/src/main/java/kr/lunaf/cloudislands/coreservice/http/routes/ProgressionRoutes.java"));
        String parity = Files.readString(Path.of("../gradle/report-gates.gradle.kts"));
        String adminSurface = source + "\n" + catalog;

        assertTrue(adminSurface.contains("ciadmin island mission complete <island>"), "Admin mission complete must be listed in help");
        assertTrue(adminSurface.contains("ciadmin island mission progress <island>"), "Admin mission progress must be listed in help");
        assertTrue(catalog.contains("\"mission\""), "Admin mission command must be cataloged for completion");
        assertTrue(source.contains("coreApiClient.progressionCommands().adminCompleteMission(islandId, actorUuid, missionKey, kind)"), "Admin mission complete must use typed progression client");
        assertTrue(source.contains("coreApiClient.progressionCommands().adminProgressMission(islandId, actorUuid, missionKey, kind, number(args[6], 1L))"), "Admin mission progress must use typed progression client");
        assertTrue(progressionClient.contains("adminCompleteMission(UUID islandId, UUID actorUuid, String missionKey, String kind)"), "Progression client must expose admin mission complete");
        assertTrue(progressionClient.contains("adminProgressMission(UUID islandId, UUID actorUuid, String missionKey, String kind, long amount)"), "Progression client must expose admin mission progress");
        assertTrue(jdkProgressionClient.contains("postResultBody(\"/v1/admin/islands/missions/complete\""), "JDK progression client must call admin mission complete endpoint");
        assertTrue(jdkProgressionClient.contains("postResultBody(\"/v1/admin/islands/missions/progress\""), "JDK progression client must call admin mission progress endpoint");
        assertTrue(coreRoutes.contains("/v1/admin/islands/missions/complete"), "Core progression routes must register admin mission complete");
        assertTrue(coreRoutes.contains("/v1/admin/islands/missions/progress"), "Core progression routes must register admin mission progress");
        assertTrue(coreRoutes.contains("ISLAND_MISSION_ADMIN_COMPLETE"), "Core admin mission route must audit operator mutation");
        assertTrue(coreRoutes.contains("actorType\", \"ADMIN\""), "Core admin mission progress event must identify admin mutation");
        assertTrue(parity.contains("\"superior.admin.mission\", \"cloudislands.admin.island\", \"SUPPORTED_VERIFIED\""), "Feature parity matrix must mark superior.admin.mission verified");
    }

    @Test
    void adminRankupMutationIsFirstClassIslandCommand() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/admin/AdminCommandBackend.java"));
        String catalog = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/admin/AdminCommandCatalog.java"));
        String progressionClient = Files.readString(Path.of("../cloudislands-core-client/src/main/java/kr/lunaf/cloudislands/coreclient/ProgressionCommandClient.java"));
        String jdkProgressionClient = Files.readString(Path.of("../cloudislands-core-client/src/main/java/kr/lunaf/cloudislands/coreclient/JdkProgressionCommandClient.java"));
        String coreRoutes = Files.readString(Path.of("../cloudislands-core-service/src/main/java/kr/lunaf/cloudislands/coreservice/http/routes/IslandUpgradeRoutes.java"));
        String parity = Files.readString(Path.of("../gradle/report-gates.gradle.kts"));
        String adminSurface = source + "\n" + catalog;

        assertTrue(adminSurface.contains("ciadmin island rankup <island> <upgradeKey>"), "Admin rankup must be listed in help");
        assertTrue(catalog.contains("\"rankup\""), "Admin rankup command must be cataloged for completion");
        assertTrue(source.contains("coreApiClient.progressionCommands().adminPurchaseUpgrade(islandId, args[3])"), "Admin rankup must use the typed progression command client");
        assertTrue(source.contains("upgradePurchaseMessage(\"Island rankup\", result)"), "Admin rankup must render typed purchase results");
        assertTrue(progressionClient.contains("adminPurchaseUpgrade(UUID islandId, String upgradeKey)"), "Progression client must expose admin rankup");
        assertTrue(jdkProgressionClient.contains("postResultBody(\"/v1/admin/islands/upgrades/purchase\""), "JDK progression client must call admin rankup endpoint");
        assertTrue(coreRoutes.contains("/v1/admin/islands/upgrades/purchase"), "Core upgrade routes must register admin rankup");
        assertTrue(coreRoutes.contains("ISLAND_UPGRADE_ADMIN_PURCHASE"), "Core admin rankup route must audit operator mutation");
        assertTrue(coreRoutes.contains("actorType\", actorType"), "Core admin rankup event must identify admin mutation");
        assertTrue(parity.contains("\"superior.admin.rankup\", \"cloudislands.admin.island\", \"SUPPORTED_VERIFIED\""), "Feature parity matrix must mark superior.admin.rankup verified");
    }

    @Test
    void adminPermissionMutationsAreFirstClassIslandCommands() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/admin/AdminCommandBackend.java"));
        String catalog = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/admin/AdminCommandCatalog.java"));
        String permissionClient = Files.readString(Path.of("../cloudislands-core-client/src/main/java/kr/lunaf/cloudislands/coreclient/PermissionCommandClient.java"));
        String jdkPermissionClient = Files.readString(Path.of("../cloudislands-core-client/src/main/java/kr/lunaf/cloudislands/coreclient/JdkPermissionCommandClient.java"));
        String coreRoutes = Files.readString(Path.of("../cloudislands-core-service/src/main/java/kr/lunaf/cloudislands/coreservice/http/routes/PermissionRoleRoutes.java"));
        String permissionRepository = Files.readString(Path.of("../cloudislands-core-service/src/main/java/kr/lunaf/cloudislands/coreservice/permission/IslandPermissionRuleRepository.java"));
        String parity = Files.readString(Path.of("../gradle/report-gates.gradle.kts"));
        String adminSurface = source + "\n" + catalog;

        assertTrue(adminSurface.contains("ciadmin island setpermission <island>"), "Admin setpermission must be listed in help");
        assertTrue(adminSurface.contains("ciadmin island resetpermissions <island>"), "Admin resetpermissions must be listed in help");
        assertTrue(catalog.contains("\"setpermission\""), "Admin setpermission must be cataloged for completion");
        assertTrue(catalog.contains("\"resetpermissions\""), "Admin resetpermissions must be cataloged for completion");
        assertTrue(source.contains("coreApiClient.permissions().adminSetPermission(islandId, args[3], permission, allowed)"), "Admin setpermission must use typed permission client");
        assertTrue(source.contains("coreApiClient.permissions().adminResetPermissions(islandId, args[3])"), "Admin resetpermissions must use typed permission client");
        assertTrue(permissionClient.contains("adminSetPermission(UUID islandId, String roleKey, IslandPermission permission, boolean allowed)"), "Permission client must expose admin setpermission");
        assertTrue(permissionClient.contains("adminResetPermissions(UUID islandId, String roleKey)"), "Permission client must expose admin resetpermissions");
        assertTrue(jdkPermissionClient.contains("\"/v1/admin/islands/permissions/set\""), "JDK permission client must call admin setpermission endpoint");
        assertTrue(jdkPermissionClient.contains("\"/v1/admin/islands/permissions/reset\""), "JDK permission client must call admin resetpermissions endpoint");
        assertTrue(coreRoutes.contains("/v1/admin/islands/permissions/set"), "Core permission routes must register admin setpermission");
        assertTrue(coreRoutes.contains("/v1/admin/islands/permissions/reset"), "Core permission routes must register admin resetpermissions");
        assertTrue(coreRoutes.contains("ISLAND_PERMISSION_ADMIN_SET"), "Core route must audit admin permission set");
        assertTrue(coreRoutes.contains("ISLAND_PERMISSION_ADMIN_RESET"), "Core route must audit admin permission reset");
        assertTrue(permissionRepository.contains("resetRoleKey(UUID islandId, String roleKey)"), "Permission repository must support real role permission reset");
        assertTrue(parity.contains("\"superior.admin.setpermission\", \"cloudislands.admin.island\", \"SUPPORTED_VERIFIED\""), "Feature parity matrix must mark superior.admin.setpermission verified");
        assertTrue(parity.contains("\"superior.admin.resetpermissions\", \"cloudislands.admin.island\", \"SUPPORTED_VERIFIED\""), "Feature parity matrix must mark superior.admin.resetpermissions verified");
    }

    @Test
    void adminReviewModerationCommandsAreFirstClassIslandCommands() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/admin/AdminCommandBackend.java"));
        String catalog = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/admin/AdminCommandCatalog.java"));
        String navigationClient = Files.readString(Path.of("../cloudislands-core-client/src/main/java/kr/lunaf/cloudislands/coreclient/NavigationCommandClient.java"));
        String jdkNavigationClient = Files.readString(Path.of("../cloudislands-core-client/src/main/java/kr/lunaf/cloudislands/coreclient/JdkNavigationCommandClient.java"));
        String reviewRoutes = Files.readString(Path.of("../cloudislands-core-service/src/main/java/kr/lunaf/cloudislands/coreservice/http/routes/IslandReviewRoutes.java"));
        String parity = Files.readString(Path.of("../gradle/report-gates.gradle.kts"));
        String adminSurface = source + "\n" + catalog;

        assertTrue(adminSurface.contains("ciadmin island setrate <island>"), "Admin setrate must be listed in help");
        assertTrue(adminSurface.contains("ciadmin island removeratings <island>"), "Admin removeratings must be listed in help");
        assertTrue(adminSurface.contains("ciadmin island reviews [limit]"), "Review moderation queue must be listed in help");
        assertTrue(adminSurface.contains("ciadmin island moderate-review <island>"), "Review moderation action must be listed in help");
        assertTrue(catalog.contains("\"setrate\""), "Admin setrate must be cataloged for completion");
        assertTrue(catalog.contains("\"removeratings\""), "Admin removeratings must be cataloged for completion");
        assertTrue(catalog.contains("\"reviews\""), "Review moderation queue must be cataloged for completion");
        assertTrue(catalog.contains("\"moderate-review\""), "Review moderation action must be cataloged for completion");
        assertTrue(source.contains("coreApiClient.navigationCommands().setReview(islandId, reviewerUuid, rating, comment)"), "Admin setrate must use typed navigation review client");
        assertTrue(source.contains("coreApiClient.navigationCommands().deleteReview(islandId, reviewerUuid)"), "Admin removeratings must use typed navigation review client");
        assertTrue(source.contains("reviewModerationQueue(limit)"), "Admin review queue must use typed navigation review client");
        assertTrue(source.contains("moderateReview(islandId, reviewerUuid, moderatorUuid, moderationState, note)"), "Admin review moderation must use typed navigation review client");
        assertTrue(source.contains("AdminReviewModerationMenu.open(agent.plugin(), coreApiClient, player, messagesFor(player), limit)"), "Player operators must receive a clickable review moderation queue");
        assertTrue(source.contains("case \"admin.reviews\" -> AdminReviewModerationMenu.open"), "Admin openmenu must expose review moderation");
        assertTrue(navigationClient.contains("setReview(UUID islandId, UUID reviewerUuid, int rating, String comment)"), "Navigation client must expose review set mutation");
        assertTrue(navigationClient.contains("deleteReview(UUID islandId, UUID reviewerUuid)"), "Navigation client must expose review deletion mutation");
        assertTrue(navigationClient.contains("reviewModerationQueue(int limit)"), "Navigation client must expose the moderation queue");
        assertTrue(navigationClient.contains("moderateReview(UUID islandId, UUID reviewerUuid, UUID moderatorUuid"), "Navigation client must expose review moderation");
        assertTrue(jdkNavigationClient.contains("\"/v1/islands/reviews/set\""), "JDK navigation client must call review set endpoint");
        assertTrue(jdkNavigationClient.contains("\"/v1/islands/reviews/delete\""), "JDK navigation client must call review delete endpoint");
        assertTrue(jdkNavigationClient.contains("\"/v1/admin/reviews/moderation\""), "JDK navigation client must call review moderation queue endpoint");
        assertTrue(jdkNavigationClient.contains("\"/v1/admin/reviews/moderate\""), "JDK navigation client must call review moderation action endpoint");
        assertTrue(reviewRoutes.contains("/v1/islands/reviews/set"), "Core review routes must register review set");
        assertTrue(reviewRoutes.contains("/v1/islands/reviews/delete"), "Core review routes must register review delete");
        assertTrue(reviewRoutes.contains("ISLAND_REVIEW_SET"), "Core review set route must audit review mutation");
        assertTrue(reviewRoutes.contains("ISLAND_REVIEW_DELETE"), "Core review delete route must audit review deletion");
        assertTrue(reviewRoutes.contains("ISLAND_REVIEW_MODERATE"), "Core review moderation route must audit operator actions");
        assertTrue(parity.contains("\"superior.admin.setrate\", \"cloudislands.admin.island\", \"SUPPORTED_VERIFIED\""), "Feature parity matrix must mark superior.admin.setrate verified");
        assertTrue(parity.contains("\"superior.admin.removeratings\", \"cloudislands.admin.island\", \"SUPPORTED_VERIFIED\""), "Feature parity matrix must mark superior.admin.removeratings verified");
    }

    @Test
    void playerJobManagementOpensARealQueueWhileConsoleKeepsTextOutput() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/admin/AdminCommandBackend.java"));
        String menu = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/gui/AdminJobMenu.java"));
        String handler = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandAdminNodeCommandHandler.java"));

        assertTrue(source.contains("AdminJobMenu.open(agent.plugin(), coreApiClient, player, messagesFor(player))"), "Player operators must receive a clickable job queue");
        assertTrue(source.contains("run(sender, \"Jobs list\", coreApiClient.jobs().list()"), "Console job output must remain available");
        assertTrue(source.contains("case \"admin.jobs\" -> AdminJobMenu.open(agent.plugin(), coreApiClient, target"), "Admin openmenu must expose the live job queue");
        assertTrue(menu.contains("client.jobs().list()"), "Job menu must load typed Core job data");
        assertTrue(handler.contains("coreApiClient.jobCommands().retry(jobId)"), "Job retry clicks must call the typed Core client");
        assertTrue(handler.contains("coreApiClient.jobCommands().cancel(jobId)"), "Job cancellation clicks must call the typed Core client");
    }

    @Test
    void playerRouteManagementOpensLiveTicketsWithConfirmedCleanup() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/admin/AdminCommandBackend.java"));
        String menu = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/gui/AdminRouteMenu.java"));
        String handler = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandAdminNodeCommandHandler.java"));

        assertTrue(source.contains("AdminRouteMenu.open(agent.plugin(), coreApiClient, player, messagesFor(player))"), "Player operators must receive a live route queue");
        assertTrue(source.contains("case \"admin.route\" -> AdminRouteMenu.open(agent.plugin(), coreApiClient, target"), "Admin openmenu must expose live route tickets");
        assertTrue(menu.contains("client.adminRoutes().debug(new UUID(0L, 0L))"), "Route menu must load typed Core diagnostics");
        assertFalse(menu.contains("routeSession.nonce()"), "Route menu must not render route nonces");
        assertTrue(handler.contains("coreApiClient.adminRoutes().clear(playerUuid, ticketId, \"ADMIN_GUI\")"), "Confirmed route cleanup must call the typed Core client");
        assertTrue(handler.contains("AdminRouteMenu.clearConfirmationMaterial()"), "Route cleanup confirmation material must come from config-v2");
    }

    @Test
    void doctorIsAFirstClassAdminHealthCommand() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/admin/AdminCommandBackend.java"));
        String catalog = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/admin/AdminCommandCatalog.java"));
        String plugin = Files.readString(Path.of("src/main/resources/plugin.yml"));
        String adminSurface = source + "\n" + catalog;

        assertTrue(adminSurface.contains("\"doctor\""), "Doctor root command must be registered");
        assertTrue(adminSurface.contains("ciadmin doctor"), "Doctor command must be listed in help");
        assertTrue(adminSurface.contains("ciadmin doctor --json"), "Doctor JSON export must be listed in help");
        assertTrue(adminSurface.contains("ciadmin doctor --markdown"), "Doctor Markdown export must be listed in help");
        assertTrue(source.contains("handleDoctor"), "Doctor command must have a handler");
        assertTrue(source.contains("coreApiClient.adminCoreConfig().config()"), "Doctor must include Core config/API reachability");
        assertTrue(source.contains("setupReadinessDiagnosticBody"), "Doctor must include setup readiness checks");
        assertTrue(source.contains("\"setup-readiness\""), "Doctor must render setup readiness as a first-class section");
        assertTrue(source.contains("coreApiReachable=true"), "Setup doctor must explicitly prove Core API reachability");
        assertTrue(source.contains("redisReachable=policy:"), "Setup doctor must explicitly cover Redis readiness");
        assertTrue(source.contains("databaseDurable="), "Setup doctor must explicitly cover SQL/durable database readiness");
        assertTrue(source.contains("storageShared="), "Setup doctor must explicitly cover storage readiness");
        assertTrue(source.contains("velocityBackendNames=duplicateCount:"), "Setup doctor must explicitly cover Velocity backend name uniqueness");
        assertTrue(source.contains("nodeIdentity=defaultRiskCount:"), "Setup doctor must explicitly cover node identity uniqueness");
        assertTrue(source.contains("forwardingSecretCheck=security.forwarding-secret+velocity-modern-forwarding-required"), "Setup doctor must explicitly cover forwarding secret readiness without exposing the secret");
        assertTrue(source.contains("routeTicketSmoke=ttl:"), "Setup doctor must explicitly cover route ticket smoke readiness");
        assertTrue(source.contains("templateChecksum="), "Setup doctor must explicitly cover template checksum readiness");
        assertTrue(source.contains("migrationReadiness=enabled:"), "Setup doctor must explicitly cover migration readiness");
        assertTrue(source.contains("snapshotPolicyDiagnosticBody"), "Doctor must include snapshot policy status");
        assertTrue(source.contains("snapshotLatest="), "Doctor snapshot policy must expose latest snapshot retention");
        assertTrue(source.contains("coreApiClient.adminMetrics().summary()"), "Doctor must include typed metrics");
        assertTrue(source.contains("coreApiClient.adminStorage().status()"), "Doctor must include typed storage health");
        assertTrue(source.contains("coreApiClient.adminNodes().listNodesSummary()"), "Doctor must include typed node and heartbeat context");
        assertTrue(source.contains("coreApiClient.jobs().list()"), "Doctor must include typed job queue context");
        assertTrue(source.contains("coreApiClient.adminRoutes().debug(new UUID(0L, 0L))"), "Doctor must include typed route ticket context");
        assertTrue(source.contains("coreApiClient.adminAudit().list(5)"), "Doctor must include recent typed audit context");
        assertTrue(source.contains("coreApiClient.templates().list().thenApply(this::templateDoctorDiagnosticBody)"), "Doctor must include template bundle validation context");
        assertTrue(source.contains("doctorSeverity(String body)"), "Doctor output must classify sections with CRITICAL/WARN/INFO");
        assertTrue(source.contains("\"CRITICAL\"") && source.contains("\"WARN\"") && source.contains("\"INFO\""), "Doctor severity labels must be operator-visible");
        assertTrue(source.contains("doctorRecommendation(String label, String severity, String body)"), "Doctor must recommend operator remediation commands");
        assertTrue(source.contains("recommendedCommand"), "Doctor JSON/Markdown export must include remediation commands");
        assertTrue(source.contains("hasOption(args, \"--json\")"), "Doctor must support JSON export");
        assertTrue(source.contains("hasOption(args, \"--markdown\")"), "Doctor must support Markdown export");
        assertTrue(source.contains("doctorJson(DoctorReport report)"), "Doctor must render a structured JSON export");
        assertTrue(source.contains("doctorMarkdown(DoctorReport report)"), "Doctor must render a Markdown export");
        assertTrue(source.contains("/ciadmin support-bundle create"), "Doctor must recommend support bundle creation for unknown failures");
        assertTrue(source.contains("configDoctorChecks"), "Doctor must render the P8 config-doctor risk checklist from Core config");
        assertTrue(source.contains("WARN_TEMPLATE_CATALOG_EMPTY"), "Doctor must warn when no templates are visible");
        assertTrue(source.contains("WARN_BUNDLE_MISSING"), "Doctor template diagnostics must warn when enabled templates have no bundle");
        assertTrue(source.contains("integrationStatusMessage()"), "Doctor must include integration state");
        assertTrue(source.contains("superiorSkyblock2MigrationDiagnosticBody()"), "Doctor must include local SS2 cutover compatibility state");
        assertTrue(source.contains("migrationEnabled && !legacyAliasesEnabled"), "Doctor must identify migration cutovers that forgot legacy command aliases");
        assertTrue(source.contains("enable migration.legacy-aliases.superiorskyblock2.enabled during cutover"), "Doctor must provide the exact legacy alias remediation key");
        assertTrue(source.contains("label.equals(\"ss2-migration\")"), "Doctor must provide a focused remediation command for SS2 cutover warnings");
        assertTrue(plugin.contains("cloudislands.admin.doctor"), "Doctor command must have a plugin permission");
    }

    @Test
    void setupWizardIsAFirstClassAdminCommand() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/admin/AdminCommandBackend.java"));
        String catalog = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/admin/AdminCommandCatalog.java"));
        String plugin = Files.readString(Path.of("src/main/resources/plugin.yml"));
        String adminSurface = source + "\n" + catalog;

        for (String command : List.of(
            "ciadmin setup start",
            "ciadmin setup core",
            "ciadmin setup redis",
            "ciadmin setup database",
            "ciadmin setup storage",
            "ciadmin setup velocity",
            "ciadmin setup paper-node",
            "ciadmin setup verify",
            "ciadmin setup explain node",
            "ciadmin setup explain velocity",
            "ciadmin setup explain storage",
            "ciadmin setup explain security",
            "ciadmin setup export-redacted"
        )) {
            assertTrue(adminSurface.contains(command), command);
        }
        assertTrue(catalog.contains("\"setup\""), "Setup root command must be registered");
        assertTrue(catalog.contains("\"wizard\""), "Setup wizard alias must be cataloged");
        assertTrue(catalog.contains("\"explain\""), "Setup explain command must be cataloged");
        assertTrue(catalog.contains("\"export-redacted\""), "Setup redacted export must be cataloged");
        assertTrue(catalog.contains("SETUP_COMMANDS"), "Setup subcommands must be cataloged for tab completion");
        assertTrue(source.contains("handleSetup"), "Setup command must have a handler");
        assertTrue(source.contains("return handleDoctor(sender, new String[] {\"doctor\"})"), "Setup verify must delegate to doctor checks");
        assertTrue(source.contains("setupExplainMessage"), "Setup explain must have a focused explanation handler");
        assertTrue(source.contains("configHandler.effectiveConfigDiagnosticSectionAsync()"), "Setup export-redacted must reuse async redacted effective config output");
        assertTrue(source.contains("args.length == 2 && args[0].equalsIgnoreCase(\"setup\")"), "Setup tab completion must use setup subcommands");
        assertTrue(source.contains("args.length == 3 && args[0].equalsIgnoreCase(\"setup\") && args[1].equalsIgnoreCase(\"explain\")"), "Setup explain tab completion must suggest setup topics");
        assertTrue(source.contains("\"setup\"") && source.contains("cloudislands.admin.\" + root"), "Setup must be a first-class admin permission root");
        assertTrue(plugin.contains("cloudislands.admin.setup"), "Setup command must have a plugin permission");
    }

    @Test
    void dashboardIsAFirstClassAdminOverviewCommand() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/admin/AdminCommandBackend.java"));
        String catalog = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/admin/AdminCommandCatalog.java"));
        String plugin = Files.readString(Path.of("src/main/resources/plugin.yml"));
        String adminSurface = source + "\n" + catalog;

        assertTrue(adminSurface.contains("\"dashboard\""), "Dashboard root command must be registered");
        assertTrue(adminSurface.contains("ciadmin dashboard"), "Dashboard command must be listed in help");
        assertTrue(source.contains("handleDashboard"), "Dashboard command must have a handler");
        assertTrue(source.contains("AdminDashboardMenu.open(player, messagesFor(player))"), "Player dashboard command must open the operations hub");
        assertTrue(source.contains("dashboardMessage(List<CharSequence> parts)"), "Dashboard must render a focused overview message");
        assertTrue(source.contains("coreApiClient.adminMetrics().summary()"), "Dashboard must include typed metrics");
        assertTrue(source.contains("coreApiClient.adminNodes().listNodesSummary()"), "Dashboard must include typed node state");
        assertTrue(source.contains("coreApiClient.jobs().list()"), "Dashboard must include typed job queue state");
        assertTrue(source.contains("coreApiClient.adminRoutes().debug(new UUID(0L, 0L))"), "Dashboard must include typed route state");
        assertTrue(source.contains("coreApiClient.adminStorage().status()"), "Dashboard must include typed storage health");
        assertTrue(source.contains("integrationStatusMessage()"), "Dashboard must include integration state");
        assertTrue(source.contains("case \"admin.dashboard\" -> AdminDashboardMenu.open(target, targetMessages)"), "Admin openmenu must expose the dashboard hub");
        assertTrue(plugin.contains("cloudislands.admin.dashboard"), "Dashboard command must have a plugin permission");
    }

    @Test
    void configOperationsAreFirstClassAdminCommands() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/admin/AdminCommandBackend.java"));
        String configHandler = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/admin/AdminConfigCommandHandler.java"));
        String catalog = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/admin/AdminCommandCatalog.java"));
        String plugin = Files.readString(Path.of("src/main/resources/plugin.yml"));
        String adminSurface = source + "\n" + catalog;
        String configSurface = source + "\n" + configHandler;

        assertTrue(source.contains("CONFIG_COMMANDS"), "Config subcommands must be registered for completion");
        assertTrue(adminSurface.contains("ciadmin config validate"), "Config validate must be listed in help");
        assertTrue(adminSurface.contains("ciadmin config diff"), "Config diff must be listed in help");
        assertTrue(adminSurface.contains("ciadmin config reload"), "Config reload must be listed in help");
        assertTrue(adminSurface.contains("ciadmin config effective"), "Config effective must be listed in help");
        assertTrue(adminSurface.contains("ciadmin config sources"), "Config sources must be listed in help");
        assertTrue(source.contains("configHandler.handle(sender, args)"), "Config command must route to a dedicated operation handler");
        assertTrue(configHandler.contains("ConfigV2Validator.validateYaml"), "Config validate must run schema and secret validation");
        assertTrue(configHandler.contains("ConfigV2Validator.redactYaml"), "Effective config output must redact secrets");
        assertTrue(configHandler.contains("validation.valid()"), "Config reload must keep the current config when validation fails");
        assertTrue(configHandler.contains("PaperBootstrapStatus.sanitize"), "Config reload failures must preserve the runtime and redact credential-bearing diagnostics");
        assertTrue(configHandler.contains("restartRequiredChanges"), "Config reload must report restart-required sections instead of claiming stale listeners were refreshed");
        assertTrue(configSurface.contains("reloadRuntimeConfig()"), "Config reload must refresh the active Config v2 runtime snapshot after validation passes");
        assertTrue(configHandler.contains("plugin::loadRuntimeConfigSnapshot"), "Admin config reload must load the candidate snapshot through the Paper runtime boundary");
        assertTrue(configHandler.contains("plugin.applyRuntimeConfigSnapshot(candidate)"), "Admin config reload must apply a preloaded snapshot on the Paper scheduler");
        assertTrue(configHandler.contains("PaperSchedulers.supplyAsync"), "Config filesystem operations must execute away from the Paper command thread");
        assertTrue(configHandler.contains("PaperSchedulers.supply(plugin"), "Config runtime mutation must return to the Paper global scheduler");
        assertFalse(configHandler.contains("sender.sendMessage"), "Async config continuations must not retain or message a command sender directly");
        assertTrue(configHandler.contains("reloadCoreAfterLocalApply"), "Core maintenance reload must run only after the local Paper snapshot result is known");
        assertTrue(configHandler.contains(".handle((maintenance, error) -> (CharSequence) (error == null"), "Core maintenance transport failure must not be misreported as a rejected local Paper reload");
        assertTrue(configHandler.contains(": localResult"), "A locally applied Paper config result must remain visible when Core maintenance is unavailable");
        assertFalse(source.contains("exceptionally(configHandler::reloadFailureMessage)"), "Addon refresh transport failure must not be misreported as a rejected local Paper reload");
        assertTrue(configHandler.contains("ConfigDiff.between"), "Config diff must report changed and restart-required paths");
        assertTrue(configHandler.contains("currentConfigYaml"), "Config diff must compare against the current runtime config when available");
        assertTrue(plugin.contains("cloudislands.admin.config"), "Config command must have a plugin permission");
    }

    @Test
    void integrationsCommandCoversMajorHookPlugins() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/admin/AdminCommandBackend.java"));
        String catalog = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/admin/AdminCommandCatalog.java"));
        String plugin = Files.readString(Path.of("src/main/resources/plugin.yml"));
        String policy = Files.readString(Path.of("../cloudislands-common/src/main/java/kr/lunaf/cloudislands/common/integration/CloudIntegrationPolicy.java"));
        String adminSurface = source + "\n" + catalog;

        assertTrue(adminSurface.contains("\"integrations\""), "Integrations root command must be registered");
        assertTrue(adminSurface.contains("ciadmin integrations"), "Integrations command must be listed in help");
        assertTrue(adminSurface.contains("ciadmin integrations report"), "Integrations report command must be listed in help");
        assertTrue(source.contains("integrationStatusMessage"), "Integrations command must have a status handler");
        assertTrue(source.contains("integrationRegistry().statusLine()"), "Integrations command must use the runtime integration registry");
        assertTrue(source.contains("certificationReport()"), "Integrations command must run the runtime certification report");
        assertTrue(source.contains("cloudislands-integrations-"), "Integrations command must generate report artifacts");
        assertTrue(source.contains(".json"), "Integrations command must generate JSON report artifacts");
        assertTrue(source.contains(".md"), "Integrations command must generate Markdown report artifacts");
        assertTrue(source.contains("failedIntegrationRemediation"), "Integrations command must expose OPERATION_FAILED remediation guidance");
        assertTrue(source.contains("integrationsDiagnosticSection"), "Diagnostics export must include integration policy state");
        assertTrue(source.contains("CloudIntegrationPolicy.knownPlugins()"), "Integrations command must use the shared integration policy");
        assertTrue(policy.contains("LuckPerms"), "LuckPerms must be covered by integration status");
        assertTrue(policy.contains("CoreProtect"), "CoreProtect must be covered by integration status");
        assertTrue(policy.contains("FastAsyncWorldEdit"), "FAWE must be covered by integration status");
        assertTrue(policy.contains("DISTRIBUTED_HOOK_POLICY"), "Integrations must publish the distributed hook policy");
        assertTrue(policy.contains("requiredRuntimeClaims"), "Integration policy must expose required runtime claims");
        assertTrue(policy.contains("validateHookContext"), "Integration policy must validate hook authority context");
        assertTrue(plugin.contains("cloudislands.admin.integrations"), "Integrations command must have a plugin permission");
        assertTrue(plugin.contains("LuckPerms"), "LuckPerms must be declared as a soft dependency");
        assertTrue(plugin.contains("CoreProtect"), "CoreProtect must be declared as a soft dependency");
        assertTrue(plugin.contains("FastAsyncWorldEdit"), "FAWE must be declared as a soft dependency");
        assertTrue(plugin.contains("ItemsAdder"), "ItemsAdder must be declared as a soft dependency");
        assertTrue(plugin.contains("Oraxen"), "Oraxen must be declared as a soft dependency");
        assertTrue(plugin.contains("Nexo"), "Nexo must be declared as a soft dependency");
        assertTrue(plugin.contains("CraftEngine"), "CraftEngine must be declared as a soft dependency");
        assertTrue(plugin.contains("RoseStacker"), "RoseStacker must be declared as a soft dependency");
        assertTrue(plugin.contains("AdvancedSpawners"), "AdvancedSpawners must be declared as a soft dependency");
        assertTrue(plugin.contains("Plan"), "Plan must be declared as a soft dependency");
        assertTrue(plugin.contains("SuperVanish"), "Vanish hooks must be declared as soft dependencies");
        assertTrue(plugin.contains("PremiumVanish"), "Vanish hooks must be declared as soft dependencies");
        assertTrue(plugin.contains("SlimeWorldManager"), "SlimeWorldManager hooks must be declared as soft dependencies");
        Set<String> softDependencies = Arrays.stream(plugin.substring(plugin.indexOf("softdepend: [") + "softdepend: [".length(), plugin.indexOf("]", plugin.indexOf("softdepend: ["))).split(","))
            .map(String::trim)
            .collect(Collectors.toSet());
        assertTrue(softDependencies.containsAll(kr.lunaf.cloudislands.common.integration.CloudIntegrationPolicy.knownPlugins()), "plugin.yml soft dependencies must cover the shared hook policy");
    }

    @Test
    void islandVisitorStatsAreExposedForOperators() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/admin/AdminCommandBackend.java"));
        String catalog = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/admin/AdminCommandCatalog.java"));
        String adminSurface = source + "\n" + catalog;

        assertTrue(adminSurface.contains("ciadmin island visitor-stats <island>"), "Visitor stats command must be listed in help");
        assertTrue(source.contains("coreApiClient.visitorStats().stats"), "Visitor stats command must use the typed Core visitor stats API");
    }

    @Test
    void islandBulkRestoreIsAFirstClassOperatorTool() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/admin/AdminCommandBackend.java"));
        String catalog = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/admin/AdminCommandCatalog.java"));
        String adminSurface = source + "\n" + catalog;

        assertTrue(adminSurface.contains("\"bulk-restore\""), "Bulk restore must be registered as an island admin subcommand");
        assertTrue(adminSurface.contains("ciadmin island bulk-restore <snapshot") && adminSurface.contains("bulk-restore <snapshot> <island...> --confirm"), "Bulk restore must be listed in admin help with confirmation");
        assertTrue(source.contains("handleBulkRestore"), "Bulk restore must have a dedicated handler before island uuid resolution");
        assertTrue(source.contains("coreApiClient.lifecycle().restoreIslandSnapshot(islandId, snapshotNo)"), "Bulk restore must use the typed lifecycle restore API");
        assertTrue(source.contains("bulkRestoreMessage"), "Bulk restore must summarize accepted and rejected restores for operators");
    }

    @Test
    void adminStorageCommandUsesTypedCoreClient() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/admin/AdminCommandBackend.java"));
        String catalog = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/admin/AdminCommandCatalog.java"));
        String adminSurface = source + "\n" + catalog;

        assertTrue(adminSurface.contains("ciadmin storage verify <island>"), "Storage verify command must be listed for operators");
        assertTrue(source.contains("coreApiClient.adminStorage().status"), "Storage command must use the typed Core storage status API");
        assertTrue(source.contains("storageStatusMessage(AdminStorageStatusView"), "Storage command must render a typed storage view");
        assertTrue(source.contains("handleStorage"), "Storage command must route through a dedicated handler");
        assertTrue(source.contains("storageVerifyMessage(UUID"), "Storage verify must render a typed island storage check");
        assertTrue(source.contains("coreApiClient.adminIslands().runtime"), "Storage verify must include typed island runtime state");
        assertTrue(source.contains("coreApiClient.snapshots().listSnapshots"), "Storage verify must include typed snapshot metadata");
    }

    @Test
    void playerStorageCommandOpensLiveDashboardWhileConsoleKeepsTextOutput() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/admin/AdminCommandBackend.java"));
        String menu = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/gui/AdminStorageMenu.java"));

        assertTrue(source.contains("AdminStorageMenu.open(agent.plugin(), coreApiClient, player, messagesFor(player))"),
            "player storage command must open the live dashboard");
        assertTrue(source.contains("run(sender, \"Storage status\", coreApiClient.adminStorage().status()"),
            "console storage status must remain available");
        assertTrue(source.contains("case \"admin.storage\" -> AdminStorageMenu.open(agent.plugin(), coreApiClient, target"),
            "admin openmenu must expose the live storage dashboard");
        assertTrue(menu.contains("client.adminStorage().status()"), "storage dashboard must load the typed Core status view");
        assertTrue(menu.contains("GuiSessions.runIfCurrent(plugin, player, session"),
            "storage status responses must retain the initiating player connection");
    }

    @Test
    void playerEventsCommandOpensLiveStreamWhileConsoleKeepsTextOutput() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/admin/AdminCommandBackend.java"));
        String menu = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/gui/AdminEventMenu.java"));
        String mainMenu = Files.readString(Path.of("src/main/resources/config-v2/ui/menus/main.yml"));

        assertTrue(source.contains("AdminEventMenu.open(agent.plugin(), coreApiClient, player, messagesFor(player))"),
            "player events command must open the live event stream");
        assertTrue(source.contains("run(sender, \"Events list\", coreApiClient.adminEvents().list(100)"),
            "console event output must remain available");
        assertTrue(source.contains("case \"admin.events\" -> AdminEventMenu.open(agent.plugin(), coreApiClient, target"),
            "admin openmenu must expose the live event stream");
        assertTrue(menu.contains("client.adminEvents().list(EVENT_LIMIT)"), "event menu must load typed Core events");
        assertTrue(menu.contains("Comparator.comparingLong(AdminEventView::seq).reversed()"), "event menu must show newest events first");
        assertTrue(mainMenu.contains("rightAction: admin.events.open"), "main admin button must expose the event stream on right click");
        String dashboardMenu = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/gui/AdminDashboardMenu.java"));
        assertTrue(dashboardMenu.contains("player.hasPermission(\"cloudislands.admin.events\")") || dashboardMenu.contains("ACTION_PERMISSIONS.values().stream().anyMatch(player::hasPermission)"),
            "events-only operators must be able to use the main admin button");
        assertTrue(mainMenu.contains("action: admin.dashboard.open"), "main admin button must open the operations dashboard");
    }

    @Test
    void playerAuditCommandOpensLiveLogsWhileConsoleKeepsTextOutput() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/admin/AdminCommandBackend.java"));
        String menu = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/gui/AdminAuditMenu.java"));
        String eventDefinition = Files.readString(Path.of("src/main/resources/config-v2/ui/menus/admin-events.yml"));
        String auditDefinition = Files.readString(Path.of("src/main/resources/config-v2/ui/menus/admin-audit.yml"));

        assertTrue(source.contains("AdminAuditMenu.open(agent.plugin(), coreApiClient, player, messagesFor(player))"),
            "player audit command must open live audit logs");
        assertTrue(source.contains("run(sender, \"Audit logs\", coreApiClient.adminAudit().list(100)"),
            "console audit output must remain available");
        assertTrue(source.contains("case \"admin.audit\" -> AdminAuditMenu.open(agent.plugin(), coreApiClient, target"),
            "admin openmenu must expose live audit logs");
        assertTrue(menu.contains("client.adminAudit().list(AUDIT_LIMIT)"), "audit menu must load typed Core audit entries");
        assertTrue(eventDefinition.contains("audit: admin.audit.open"), "event menu must link to audit logs");
        assertTrue(auditDefinition.contains("events: admin.events.open"), "audit menu must link back to events");
    }

    @Test
    void playerMetricsCommandOpensLiveDashboardWhileConsoleKeepsTextOutput() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/admin/AdminCommandBackend.java"));
        String menu = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/gui/AdminMetricsMenu.java"));
        String eventDefinition = Files.readString(Path.of("src/main/resources/config-v2/ui/menus/admin-events.yml"));
        String auditDefinition = Files.readString(Path.of("src/main/resources/config-v2/ui/menus/admin-audit.yml"));

        assertTrue(source.contains("AdminMetricsMenu.open(agent.plugin(), coreApiClient, player, messagesFor(player))"),
            "player metrics command must open the live dashboard");
        assertTrue(source.contains("run(sender, \"Core metrics\", coreApiClient.adminMetrics().summary()"),
            "console metrics output must remain available");
        assertTrue(source.contains("case \"admin.metrics\" -> AdminMetricsMenu.open(agent.plugin(), coreApiClient, target"),
            "admin openmenu must expose live Core metrics");
        assertTrue(menu.contains("client.adminMetrics().summary()"), "metrics menu must load the typed Core metrics summary");
        assertTrue(eventDefinition.contains("metrics: admin.metrics.open"), "event menu must link to Core metrics");
        assertTrue(auditDefinition.contains("metrics: admin.metrics.open"), "audit menu must link to Core metrics");
    }

    @Test
    void adminIslandInfoAndRuntimeUseTypedCoreClient() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/admin/AdminCommandBackend.java"));
        String catalog = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/admin/AdminCommandCatalog.java"));
        String plugin = Files.readString(Path.of("src/main/resources/plugin.yml"));
        String adminSurface = source + "\n" + catalog + "\n" + plugin;

        assertTrue(adminSurface.contains("ciadmin island where <player|island>"), "Island where command must document player and island targets");
        assertTrue(adminSurface.contains("ciadmin island inspect <player|island>"), "Island inspect command must document unified player and island targets");
        assertTrue(adminSurface.contains("ciadmin island inspect <player|island> --json"), "Island inspect JSON export must be listed for operators");
        assertTrue(catalog.contains("\"inspect\""), "Island inspect must be cataloged for tab completion");
        assertTrue(source.contains("return \"cloudislands.admin.island.inspect\""), "Island inspect must use its explicit admin permission node");
        assertTrue(source.contains("adminPermissionFallbacks") && source.contains("cloudislands.admin.island"), "Island inspect must keep root island-admin permission compatibility");
        assertTrue(plugin.contains("cloudislands.admin.island.inspect:"), "Island inspect explicit permission must be declared in plugin.yml");
        assertTrue(adminSurface.contains("ciadmin island recover <island>"), "Island recover command must be listed for operators");
        assertTrue(source.contains("coreApiClient.adminIslands().info"), "Island info command must use the typed Core admin island API");
        assertTrue(source.contains("coreApiClient.adminIslands().runtime"), "Island runtime command must use the typed Core admin island API");
        assertTrue(source.contains("args[1].equalsIgnoreCase(\"recover\") || args[1].equalsIgnoreCase(\"repair\")"), "Island recover must be an explicit repair alias");
        assertTrue(source.contains("coreApiClient.lifecycle().repairIsland"), "Island recover must use the typed lifecycle repair API");
        assertTrue(source.contains("run(sender, \"Island inspect\", islandInspectMessage(args[2], hasOption(args, \"--json\")))"), "Island inspect must be a first-class handler with JSON support");
        assertTrue(source.contains("coreApiClient.bank().islandBank(islandId)"), "Island inspect must include bank state");
        assertTrue(source.contains("coreApiClient.snapshots().listSnapshots(islandId, 5)"), "Island inspect must include latest snapshot state");
        assertTrue(source.contains("coreApiClient.visitorStats().stats(islandId, 10)"), "Island inspect must include visitor state");
        assertTrue(source.contains("coreApiClient.jobs().list()"), "Island inspect must include pending job state");
        assertTrue(source.contains("coreApiClient.adminAudit().list(10)"), "Island inspect must include audit state");
        assertTrue(source.contains("coreApiClient.adminRoutes().debug(new UUID(0L, 0L))"), "Island inspect must include route state");
        assertTrue(source.contains("coreApiClient.adminStorage().status()"), "Island inspect must include storage state");
        assertTrue(source.contains("islandInspectJson(IslandInspectReport report)"), "Island inspect must expose JSON output");
        assertTrue(source.contains("islandInspectCommand=/ciadmin island inspect <player|island> --json"), "Support bundle runtime manifest must advertise the inspect JSON command");
        assertTrue(source.contains("islandWhereMessage"), "Island where must route through a player-aware resolver");
        assertTrue(source.contains("coreApiClient.playerProfiles().profile"), "Island where must resolve player primary islands through the typed player profile API");
        assertTrue(source.contains("profile.primaryIslandId()"), "Island where must use the player's primary island as the runtime target");
        assertTrue(source.contains("runtimeInfoMessage(AdminIslandRuntimeView"), "Island runtime command must render a typed runtime view");
    }

    @Test
    void destructiveIslandAdminCommandsRequireExplicitConfirmation() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/admin/AdminCommandBackend.java"));
        String catalog = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/admin/AdminCommandCatalog.java"));
        String adminSurface = source + "\n" + catalog;

        assertTrue(adminSurface.contains("ciadmin island restore <island> <snapshot> --confirm"), "Restore help must advertise explicit confirmation");
        assertTrue(adminSurface.contains("ciadmin island rollback <island> <snapshot> --confirm"), "Rollback help must advertise explicit confirmation");
        assertTrue(adminSurface.contains("ciadmin island bulk-restore <snapshot> <island...> --confirm"), "Bulk restore help must advertise explicit confirmation");
        assertTrue(adminSurface.contains("ciadmin island delete <island> --confirm"), "Delete help must advertise explicit confirmation");
        assertTrue(source.contains("if (!confirmed(args))"), "Destructive island admin commands must check confirmation before mutation");
        assertTrue(source.contains("private boolean confirmed(String[] args)"), "Confirmation parsing must be centralized");
        assertTrue(source.contains("args.length - 1"), "Bulk restore must treat the final argument as the confirmation marker");
        assertTrue(source.contains("for (int index = 3; index < args.length - 1; index++)"), "Bulk restore must not treat --confirm as an island target");
    }

    @Test
    void adminBlockValueSearchSupportsProgressionTuningUx() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/admin/AdminCommandBackend.java"));
        String catalog = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/admin/AdminCommandCatalog.java"));
        String adminSurface = source + "\n" + catalog;

        assertTrue(adminSurface.contains("ciadmin block-values search <query> [limit]"), "Block value search must be listed for operators");
        assertTrue(catalog.contains("List.of(\"list\", \"search\", \"set\", \"reload\")"), "Block value tab completion must include search and reload");
        assertTrue(source.contains("args[1].equalsIgnoreCase(\"search\")"), "Block value search must route explicitly");
        assertTrue(source.contains("args[1].equalsIgnoreCase(\"reload\")"), "Block value reload must route explicitly");
        assertTrue(source.contains("blockValueSearchMessage(String query, List<BlockValueView> values, int limit)"), "Block value search must render a focused result");
        assertTrue(source.contains("coreApiClient.blockValues().list().thenApply(values -> blockValueSearchMessage"), "Block value search must reuse the typed Core block value query");
        assertTrue(source.contains("coreApiClient.adminMaintenance().reload().thenApply(result -> maintenanceMessage(\"Block values reload\", result))"), "Block value reload must use the typed Core maintenance reload boundary");
    }

    @Test
    void gameplayParityAdminModifiersUseCoreVisibleLimitKeys() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/admin/AdminCommandBackend.java"));
        String catalog = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/admin/AdminCommandCatalog.java"));
        String plugin = Files.readString(Path.of("src/main/resources/plugin.yml"));
        String adminSurface = source + "\n" + catalog;

        for (String command : List.of(
            "ciadmin setblockamount <island> <materialKey> <amount>",
            "ciadmin seteffect <island> <effectKey> <amplifier>",
            "ciadmin setcropgrowth <island> <percent>",
            "ciadmin setmobdrops <island> <percent>",
            "ciadmin setspawnerrates <island> <percent>"
        )) {
            assertTrue(adminSurface.contains(command), command);
        }
        assertTrue(source.contains("handleGameplayModifier"), "Gameplay parity commands must route through a dedicated handler");
        assertTrue(source.contains("coreApiClient.environmentCommands().setLimit"), "Gameplay parity commands must write Core-visible runtime modifiers");
        assertTrue(source.contains("GameplayParityPolicy.blockAmountLimitKey(args[2])"), "setblockamount must store the shared namespaced block amount key");
        assertTrue(source.contains("\"EFFECT:\" + normalizeGameplayKey"), "seteffect must store a namespaced effect key");
        assertTrue(source.contains("\"RATE:CROP_GROWTH\""), "setcropgrowth must write the crop growth rate key");
        assertTrue(source.contains("\"RATE:MOB_DROPS\""), "setmobdrops must write the mob drop rate key");
        assertTrue(source.contains("\"RATE:SPAWNER_RATES\""), "setspawnerrates must write the spawner rate key");
        for (String permission : List.of(
            "cloudislands.admin.setblockamount",
            "cloudislands.admin.seteffect",
            "cloudislands.admin.setcropgrowth",
            "cloudislands.admin.setmobdrops",
            "cloudislands.admin.setspawnerrates"
        )) {
            assertTrue(plugin.contains(permission), permission);
        }
    }

    @Test
    void adminSetSpawnIsAFirstClassPaperRuntimeCommand() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/admin/AdminCommandBackend.java"));
        String spawnGateway = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/platform/world/AdminWorldSpawnGateway.java"));
        String catalog = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/admin/AdminCommandCatalog.java"));
        String plugin = Files.readString(Path.of("src/main/resources/plugin.yml"));
        String parity = Files.readString(Path.of("../gradle/report-gates.gradle.kts"));
        String adminSurface = source + "\n" + catalog;

        assertTrue(adminSurface.contains("ciadmin setspawn"), "Current-location setspawn help must be listed");
        assertTrue(adminSurface.contains("ciadmin setspawn <world> <x> <y> <z> [yaw]"), "Coordinate setspawn help must be listed");
        assertTrue(source.contains("handleSetSpawn"), "setspawn must route through a focused Paper runtime handler");
        assertTrue(source.contains("AdminWorldSpawnGateway"), "setspawn must route Paper world runtime access through the platform adapter");
        assertTrue(spawnGateway.contains("world.setSpawnLocation(location)"), "setspawn must use the Paper World spawn mutation API");
        assertTrue(source.contains("auditAdminSetSpawn"), "setspawn must emit an admin audit log line");
        assertTrue(source.contains("sender instanceof Player player") && spawnGateway.contains("player.getLocation()"), "Player operators must be able to set spawn from their current location");
        assertTrue(spawnGateway.contains("plugin.getServer().getWorld(worldName)"), "Coordinate setspawn must resolve a named Bukkit world inside the platform adapter");
        assertTrue(source.contains("worldNames()"), "setspawn tab completion must suggest loaded worlds");
        assertTrue(plugin.contains("cloudislands.admin.setspawn"), "setspawn must have a plugin permission");
        assertTrue(parity.contains("\"superior.admin.setspawn\", \"cloudislands.admin.setspawn\", \"SUPPORTED_VERIFIED\", \"P2\""), "Feature parity matrix must mark superior.admin.setspawn verified P2");
    }

    @Test
    void adminOpenMenuIsAFirstClassPaperRuntimeCommand() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/admin/AdminCommandBackend.java"));
        String catalog = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/admin/AdminCommandCatalog.java"));
        String plugin = Files.readString(Path.of("src/main/resources/plugin.yml"));
        String parity = Files.readString(Path.of("../gradle/report-gates.gradle.kts"));
        String adminSurface = source + "\n" + catalog;

        assertTrue(adminSurface.contains("ciadmin openmenu <player> <menuId>"), "Openmenu help must be listed for operators");
        assertTrue(source.contains("handleOpenMenu"), "openmenu must route through a focused Paper runtime handler");
        assertTrue(source.contains("ADMIN_OPEN_MENU_IDS"), "openmenu must use a fixed supported-menu allowlist");
        assertTrue(source.contains("IslandMainMenu.open(target") && source.contains("AdminNodeListMenu.open(agent.plugin(), coreApiClient, target"), "openmenu must open supported player and admin menus");
        assertTrue(source.contains("getPlayerExact(args[1])"), "openmenu must target an online Paper player by exact name");
        assertTrue(source.contains("auditAdminOpenMenu"), "openmenu must emit an admin audit log line");
        assertTrue(plugin.contains("cloudislands.admin.openmenu"), "openmenu must have a plugin permission");
        assertTrue(parity.contains("\"superior.admin.openmenu\", \"cloudislands.admin.openmenu\", \"SUPPORTED_VERIFIED\", \"P2\""), "Feature parity matrix must mark superior.admin.openmenu verified P2");
    }

    @Test
    void adminSpyIsAFirstClassModerationRuntimeCommand() throws Exception {
        String backend = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/admin/AdminCommandBackend.java"));
        String catalog = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/admin/AdminCommandCatalog.java"));
        String plugin = Files.readString(Path.of("src/main/resources/plugin.yml"));
        String parity = Files.readString(Path.of("../gradle/report-gates.gradle.kts"));
        String pluginMain = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/CloudIslandsPaperPlugin.java"));
        String bootstrap = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/PaperPluginBootstrap.java"));
        String chatListener = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/session/PaperChatListener.java"));
        String eventPoller = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/cache/PermissionEventPoller.java"));
        String registry = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/AdminChatSpyRegistry.java"));
        String adminSurface = backend + "\n" + catalog;

        assertTrue(catalog.contains("\"fly\", \"spy\", \"openmenu\""), "Spy must be a root admin command beside runtime moderation commands");
        assertTrue(adminSurface.contains("ciadmin spy [true|false|toggle]"), "Self spy toggle help must be listed");
        assertTrue(adminSurface.contains("ciadmin spy <player> [true|false|toggle]"), "Targeted spy toggle help must be listed");
        assertTrue(backend.contains("handleAdminSpyCommand"), "Spy must route through a focused Paper runtime handler");
        assertTrue(backend.contains("adminChatSpies()"), "Spy command must mutate the shared Paper spy registry");
        assertTrue(backend.contains("auditAdminSpy"), "Spy command must emit an admin audit log line");
        assertTrue(backend.contains("getPlayerExact(args[1])"), "Targeted spy command must resolve online Paper players by exact name");
        assertTrue(pluginMain.contains("AdminChatSpyRegistry adminChatSpies") && pluginMain.contains("adminChatSpies.clearAll()"), "Spy registry must be owned by the plugin and cleared on shutdown");
        assertTrue(registry.contains("ConcurrentHashMap.newKeySet()") && registry.contains("enabled(Player player)"), "Spy registry must be thread-safe and player-addressable");
        assertTrue(bootstrap.contains("plugin.adminChatSpies = new AdminChatSpyRegistry()"), "Bootstrap must create the spy registry");
        assertTrue(bootstrap.contains("plugin.playerLocales, plugin.adminChatSpies, plugin.teamChatModes)"), "Global chat listener must receive the spy and team-chat mode registries");
        assertTrue(chatListener.contains("@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)") && chatListener.contains("scheduleAdminSpyLine(event)"), "Global chat spy must observe only final accepted chat events");
        assertTrue(chatListener.contains("player.hasPermission(\"cloudislands.admin.spy\")"), "Global chat must deliver spy lines only to authorized enabled operators");
        assertTrue(chatListener.contains("PaperSchedulers.run(plugin, () -> viewers.forEach") && chatListener.contains("ChatPlayerIdentityPolicy.isCurrent(identity.expectedPlayer(), player)"), "Global chat spy permission checks and delivery must run on Paper's scheduler and reject stale Player instances");
        assertTrue(eventPoller.contains("sendAdminSpyChat(normalizedChannel, actorName, chatMessage)"), "Core-backed island/team chat broadcasts must also be visible to spy operators");
        assertTrue(eventPoller.contains("adminSpyMessageLine") && eventPoller.contains("messages.plain(\"admin-chat-spy-format\""), "Spy chat delivery must use localizable formatting");
        assertTrue(plugin.contains("cloudislands.admin.spy"), "Spy command must have a plugin permission");
        assertTrue(parity.contains("\"superior.admin.spy\", \"cloudislands.admin.spy\", \"SUPPORTED_VERIFIED\", \"P2\""), "Feature parity matrix must mark superior.admin.spy verified P2");
    }

    @Test
    void invalidAdminPermissionInputsCannotFallbackToBuildOrDeny() throws Exception {
        String backend = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/admin/AdminCommandBackend.java"));

        assertTrue(backend.contains("IslandPermission permission = islandPermission(args[4])"));
        assertTrue(backend.contains("Boolean allowed = strictBooleanArgument(args[5])"));
        assertTrue(backend.contains("if (permission == null || allowed == null)"), "invalid permission mutations must stop before Core");
        assertFalse(backend.contains("return IslandPermission.BUILD;"), "an unknown admin permission must never silently mutate BUILD");
        assertFalse(backend.contains("return IslandFlag.VISITOR_INTERACT;"), "an unknown admin flag must never silently mutate VISITOR_INTERACT");
        assertTrue(backend.contains("if (flag == null)"), "invalid island flags must stop before Core mutation");
        assertTrue(backend.contains("admin-command-permission-input-invalid"), "operators must receive an actionable validation error");
    }

    @Test
    void adminUpgradeRulesUseTypedCoreClient() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/admin/AdminCommandBackend.java"));

        assertTrue(source.contains("coreApiClient.progression().upgradeRules"), "Upgrade rules command must use the typed Core progression API");
        assertTrue(source.contains("upgradeRulesMessage(List<UpgradeRuleView>"), "Upgrade rules command must render typed upgrade rules");
    }

    @Test
    void adminTemplateCommandsCoverImportPreviewAndValidationUx() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/admin/AdminCommandBackend.java"));
        String catalog = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/admin/AdminCommandCatalog.java"));
        String adminSurface = source + "\n" + catalog;

        assertTrue(adminSurface.contains("ciadmin template import <name>"), "Template import command must be listed for operators");
        assertTrue(adminSurface.contains("ciadmin template preview <id>"), "Template preview command must be listed for operators");
        assertTrue(adminSurface.contains("ciadmin template validate <id>"), "Template validate command must be listed for operators");
        assertTrue(adminSurface.contains("ciadmin template seticon <name> <material>"), "Template icon command must be listed for operators");
        assertTrue(adminSurface.contains("ciadmin template setcost <name> <amount>"), "Template cost command must be listed for operators");
        assertTrue(adminSurface.contains("ciadmin template setpermission <name> <permission>"), "Template permission command must be listed for operators");
        assertTrue(source.contains("coreApiClient.templateCommands().upsert(templateId, displayName, false, \"\")"), "Template import must register a disabled template through the typed command client");
        assertTrue(source.contains("coreApiClient.templates().list().thenApply(templates -> templatePreviewMessage(args[2], templates))"), "Template preview must use the typed template query client");
        assertTrue(source.contains("coreApiClient.templates().list().thenApply(templates -> templateValidateMessage(args[2], templates))"), "Template validate must use the typed template query client");
        assertTrue(source.contains("args[1].equalsIgnoreCase(\"seticon\")"), "Template icon command must route explicitly");
        assertTrue(source.contains("args[1].equalsIgnoreCase(\"setcost\")"), "Template cost command must route explicitly");
        assertTrue(source.contains("args[1].equalsIgnoreCase(\"setpermission\")"), "Template permission command must route explicitly");
        assertTrue(source.contains("coreApiClient.templates().get(args[2]).thenCompose(template ->"), "Template mutation commands must fetch and preserve the existing typed template");
        assertTrue(source.contains("templateWithCatalogFields(TemplateView template"), "Template mutation commands must preserve non-catalog template fields");
        assertTrue(source.contains("templateValidationStatus(TemplateView template)"), "Template validation must expose operator-facing validation status");
        assertTrue(source.contains("\"BLOCKED_MIGRATION_INPUT_ONLY\""), "Template validation must guard the SuperiorSkyblock2 migration-only template");
        assertTrue(source.contains("\"not-certified\""), "Template preview/validate must disclose missing bundle checksum certification");
    }

    @Test
    void playerTemplateCommandOpensLiveManagerWhileConsoleKeepsTextOutput() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/admin/AdminCommandBackend.java"));
        String menu = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/gui/AdminTemplateMenu.java"));
        String dashboard = Files.readString(Path.of("src/main/resources/config-v2/ui/menus/admin-dashboard.yml"));

        assertTrue(source.contains("AdminTemplateMenu.open(agent.plugin(), coreApiClient, player, messagesFor(player))"),
            "player template list command must open the live manager");
        assertTrue(source.contains("run(sender, \"Template list\", coreApiClient.templates().list().thenApply(this::templateListMessage))"),
            "console template list output must remain available");
        assertTrue(source.contains("case \"admin.templates\" -> AdminTemplateMenu.open(agent.plugin(), coreApiClient, target"),
            "admin openmenu must expose the live template manager");
        assertTrue(menu.contains("client.templates().list()"), "template manager must load the typed Core catalog");
        assertTrue(menu.contains("GuiSessions.runIfCurrent(plugin, player, session"),
            "template catalog responses must retain the initiating player connection");
        assertTrue(dashboard.contains("templates: admin.templates.open"),
            "operations dashboard must link to template management");
    }

    @Test
    void adminMaintenanceCommandsUseTypedCoreClient() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/admin/AdminCommandBackend.java"));

        assertTrue(source.contains("coreApiClient.adminMaintenance().clearCache"), "Cache clear command must use the typed Core maintenance API");
        assertTrue(source.contains("coreApiClient.adminMaintenance().reload"), "Reload commands must use the typed Core maintenance API");
        assertTrue(source.contains("maintenanceMessage(String label, AdminMaintenanceResultView"), "Maintenance commands must render typed maintenance results");
    }

    @Test
    void adminAddonStateSummaryUsesTypedCoreClient() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/admin/AdminCommandBackend.java"));

        assertTrue(source.contains("coreApiClient.adminAddonState().summary"), "Addon state command must use the typed Core addon state API");
        assertTrue(source.contains("addonStateSummaryMessage(AdminAddonStateSummaryView"), "Addon state command must render a typed addon state view");
    }

    @Test
    void adminCoreConfigUsesTypedCoreClient() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/admin/AdminCommandBackend.java"));

        assertTrue(source.contains("coreApiClient.adminCoreConfig().config"), "Core config commands must use the typed Core config API");
        assertTrue(source.contains("coreConfigMessage(AdminCoreConfigView"), "Core config command must render a typed config view");
        assertTrue(source.contains("addonEndpointMessage(AdminCoreConfigView"), "Addon endpoint command must render a typed config view");
        assertTrue(source.contains("gameplayParityContract"), "Core config output must render the P5 gameplay parity contract");
        assertTrue(source.contains("gameplayParityAdminSurfaces"), "Core config output must render required gameplay admin surfaces");
    }

    @Test
    void adminMetricsAndNodeMenuUseTypedCoreClients() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/admin/AdminCommandBackend.java"));

        assertTrue(source.contains("coreApiClient.adminMetrics().summary"), "Metrics command must use the typed Core metrics API");
        assertTrue(source.contains("metricsMessage(AdminMetricsSummaryView"), "Metrics command must render a typed metrics view");
        assertTrue(source.contains("coreApiClient.adminNodes().nodeInfo(nodeId)"), "Node menu must use the typed Core node API");
        assertTrue(source.contains("heartbeatAge(node.secondsSinceHeartbeat())"), "Node info must expose heartbeat age");
        assertTrue(source.contains("node.storagePrimaryDegraded()"), "Node info must expose storage degraded state");
        assertTrue(source.contains("node.shutdownSafe()"), "Node info must expose safe shutdown readiness");
    }

    @Test
    void adminRouteRuntimeUsesTypedRoutingClient() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/admin/AdminCommandBackend.java"));
        String catalog = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/admin/AdminCommandCatalog.java"));
        String adminSurface = source + "\n" + catalog;

        assertTrue(adminSurface.contains("ciadmin route tickets <player>"), "Route tickets alias must be listed for operators");
        assertTrue(source.contains("args[1].equalsIgnoreCase(\"ticket\") || args[1].equalsIgnoreCase(\"tickets\")"), "Route tickets alias must use the typed ticket lookup path");
        assertTrue(source.contains("coreApiClient.routingCommands().routeTicketStatus(ticket)"), "Admin route polling must use the typed routing API");
        assertTrue(source.contains("coreApiClient.routingCommands().publishRouteSession(ticket)"), "Admin route publish must use the typed routing API");
        assertTrue(source.contains("coreApiClient.routingCommands().clearRoute(ticket, reason)"), "Admin route cleanup must use the typed routing API");
    }

    @Test
    void adminMigrationCommandIsSplitFromBackendAndUsesTypedClient() throws Exception {
        String backend = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/admin/AdminCommandBackend.java"));
        String catalog = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/admin/AdminCommandCatalog.java"));
        String handler = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/admin/AdminMigrationCommandHandler.java"));
        String formatter = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/admin/AdminMigrationMessageFormatter.java"));

        assertTrue(backend.contains("AdminMigrationCommandHandler"), "Admin backend must delegate migration commands to a focused handler");
        assertTrue(backend.contains("migrationHandler.handle(sender, migrationArgs(args))"), "Admin backend command routing must stay thin for migration");
        assertTrue(!backend.contains("private String migrationMessage("), "Migration response formatting must not live in AdminCommandBackend");
        assertTrue(handler.contains("coreApiClient.migrations().migrateSuperiorSkyblock2"), "Migration handler must use the typed migration client");
        assertTrue(formatter.contains("String format(MigrationRunSnapshot snapshot)"), "Migration formatter must accept typed migration snapshots");
        assertTrue(!formatter.contains("String format(String body)"), "Migration formatter must not reparse Core JSON after the typed client boundary");
        assertTrue(catalog.contains("\"wizard\", \"scan\""), "Migration wizard must be a first-class migration subcommand");
        assertTrue(catalog.contains("\"report\""), "Migration report must be a first-class migration subcommand");
        assertTrue(catalog.contains("\"approve\""), "Migration approve must be a first-class migration subcommand");
        assertTrue(catalog.contains("\"compare\""), "Migration compare must be a first-class migration subcommand");
        assertTrue(catalog.contains("\"rollback-plan\""), "Migration rollback-plan must be a first-class migration subcommand");
        assertTrue(catalog.contains("\"unlock\""), "Migration unlock must be a first-class migration subcommand");
        assertTrue(catalog.contains("\"migrate\", \"migrate-superiorskyblock2\""), "Spaced migration alias must be a first-class admin root command");
        assertTrue(catalog.contains("ciadmin migrate-superiorskyblock2 wizard"), "Migration wizard must be listed in admin help");
        assertTrue(catalog.contains("ciadmin migrate-superiorskyblock2 report"), "Migration report must be listed in admin help");
        assertTrue(catalog.contains("ciadmin migrate-superiorskyblock2 approve <approvalToken>"), "Migration approve must be listed in admin help");
        assertTrue(catalog.contains("ciadmin migrate-superiorskyblock2 compare <island>"), "Migration compare must be listed in admin help");
        assertTrue(catalog.contains("ciadmin migrate-superiorskyblock2 rollback-plan"), "Migration rollback-plan must be listed in admin help");
        assertTrue(catalog.contains("ciadmin migrate superiorskyblock2 unlock --confirm <token>"), "Spaced migration unlock must be listed in admin help");
        assertTrue(backend.contains("migrationArgs(args)"), "Spaced migration alias must normalize into the focused migration handler");
        assertTrue(backend.contains("cloudislands.admin.migrate-superiorskyblock2"), "Spaced migration alias must reuse the existing migration permission");
        assertTrue(handler.contains("action.equalsIgnoreCase(\"approve\") || action.equalsIgnoreCase(\"import\")"), "Migration approve/import must require an approval token");
        assertTrue(handler.contains("action.equalsIgnoreCase(\"compare\") && args.length < 3"), "Migration compare must require an island selector");
        assertTrue(handler.contains("action.equalsIgnoreCase(\"rollback-plan\")"), "Migration rollback-plan must not receive a default source path");
        assertTrue(handler.contains("action.equalsIgnoreCase(\"unlock\") && (args.length < 4 || !args[2].equalsIgnoreCase(\"--confirm\"))"), "Migration unlock must require explicit confirmation");
        assertTrue(handler.contains("AdminMigrationMenu.open(agent.plugin(), coreApiClient, player, messageProvider.messagesFor(player))"), "Migration wizard must open live typed status for player operators");
        assertTrue(handler.contains("AdminCommandCatalog.MIGRATION_HELP_COMMANDS"), "Migration wizard console fallback must reuse the migration command catalog");
        assertTrue(backend.contains("this::messagesFor"), "Admin backend must pass localized messages into the migration wizard handler");
    }

    @Test
    void adminRuntimeMessageTitleAndCommandDispatchAreGuardedFirstClassCommands() throws Exception {
        String backend = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/admin/AdminCommandBackend.java"));
        String catalog = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/admin/AdminCommandCatalog.java"));
        String plugin = Files.readString(Path.of("src/main/resources/plugin.yml"));
        String runtimeConfig = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/config/PaperRuntimeConfig.java"));
        String runtimeLoader = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/config/PaperRuntimeConfigLoader.java"));
        String securityConfig = Files.readString(Path.of("src/main/resources/config-v2/security.yml"));
        String parity = Files.readString(Path.of("../gradle/report-gates.gradle.kts"));
        String gameplayListener = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/IslandGameplayFlagListener.java"));
        String bootstrap = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/PaperPluginBootstrap.java"));
        String pluginMain = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/CloudIslandsPaperPlugin.java"));
        String adminSurface = backend + "\n" + catalog;

        assertTrue(catalog.contains("\"message\", \"title\", \"cmd\", \"fly\""), "Admin runtime broadcast commands must be root admin commands");
        assertTrue(adminSurface.contains("ciadmin message player <player> <message>"), "Admin message player help must be listed");
        assertTrue(adminSurface.contains("ciadmin message island <island> <message>"), "Admin message island help must be listed");
        assertTrue(adminSurface.contains("ciadmin message all <message>"), "Admin message all help must be listed");
        assertTrue(adminSurface.contains("ciadmin title player <player> <title> [subtitle]"), "Admin title player help must be listed");
        assertTrue(adminSurface.contains("ciadmin title island <island> <title> [subtitle]"), "Admin title island help must be listed");
        assertTrue(adminSurface.contains("ciadmin title all <title> [subtitle]"), "Admin title all help must be listed");
        assertTrue(adminSurface.contains("ciadmin cmd player <player> <command> --confirm"), "Admin cmd player help must advertise confirmation");
        assertTrue(adminSurface.contains("ciadmin cmd island <island> <command> --confirm"), "Admin cmd island help must advertise confirmation");
        assertTrue(adminSurface.contains("ciadmin cmd all <command> --confirm"), "Admin cmd all help must advertise confirmation");
        assertTrue(adminSurface.contains("ciadmin fly player <player> <true|false>"), "Admin fly player help must be listed");
        assertTrue(adminSurface.contains("ciadmin fly island <island> <true|false>"), "Admin fly island help must be listed");
        assertTrue(adminSurface.contains("ciadmin fly all <true|false>"), "Admin fly all help must be listed");
        assertTrue(backend.contains("handleAdminMessageCommand") && backend.contains("player.sendMessage(component)"), "Admin message must send Adventure components to resolved online players");
        assertTrue(backend.contains("handleAdminTitleCommand") && backend.contains("player.showTitle(title)"), "Admin title must show Adventure titles to resolved online players");
        assertTrue(backend.contains("handleAdminFlyCommand") && backend.contains("player.setAllowFlight(allowFlight)") && backend.contains("player.setFlying(false)") && backend.contains("overrides.set(player.getUniqueId(), allowFlight)"), "Admin fly must mutate Paper flight state and persist an admin override");
        assertTrue(gameplayListener.contains("flightService.refresh(player, block)") && gameplayListener.contains("flightService.clear(event.getPlayer())"), "Admin and personal flight ownership must be refreshed on movement and cleared on quit");
        assertTrue(bootstrap.contains("new IslandGameplayFlagListener(plugin.agent.protection(), plugin.messages, plugin.playerLocales, plugin.adminFlightOverrides, plugin.playerFlightPreferences)"), "Gameplay listener must receive shared admin and personal flight state");
        assertTrue(pluginMain.contains("adminFlightOverrides.clearAll()"), "Admin flight overrides must be cleared on plugin shutdown");
        assertTrue(backend.contains("coreApiClient.islands().listMembers(islandId)"), "Island-targeted runtime commands must resolve typed Core island members");
        assertTrue(backend.contains("auditAdminRuntimeAction"), "Admin runtime actions must emit an audit log line");
        assertTrue(backend.contains("sender.hasPermission(\"cloudislands.admin.cmd\")"), "Command dispatch must require the explicit high-risk permission");
        assertTrue(backend.contains("security().adminCommandDispatchEnabled()"), "Command dispatch must be disabled by default behind runtime config");
        assertTrue(backend.contains("confirmed(args)"), "Command dispatch must require --confirm");
        assertTrue(backend.contains("dispatchCommand(agent.plugin().getServer().getConsoleSender(), expanded)"), "Command dispatch must run as console after guards");
        assertTrue(plugin.contains("cloudislands.admin.message"), "Admin message command must have a plugin permission");
        assertTrue(plugin.contains("cloudislands.admin.title"), "Admin title command must have a plugin permission");
        assertTrue(plugin.contains("cloudislands.admin.cmd"), "Admin cmd command must have a plugin permission");
        assertTrue(plugin.contains("cloudislands.admin.fly"), "Admin fly command must have a plugin permission");
        assertTrue(runtimeConfig.contains("boolean adminCommandDispatchEnabled") && runtimeConfig.contains("true, false"), "Admin command dispatch must default to disabled");
        assertTrue(runtimeLoader.contains("admin-command-dispatch.enabled") && runtimeLoader.contains("security.admin-command-dispatch.enabled"), "Admin command dispatch config-v2 key must be mapped");
        assertTrue(securityConfig.contains("admin-command-dispatch:") && securityConfig.contains("enabled: false"), "Packaged config must keep command dispatch disabled by default");
        for (String permission : List.of("superior.admin.msg", "superior.admin.msgall", "superior.admin.title", "superior.admin.titleall", "superior.admin.cmdall")) {
            assertTrue(parity.contains("\"" + permission + "\", \"cloudislands.admin.") && parity.contains("\"" + permission + "\",") && parity.contains("\"SUPPORTED_VERIFIED\", \"P1\""), "Feature parity matrix must mark " + permission + " verified P1");
        }
        assertTrue(parity.contains("\"superior.admin.fly\", \"cloudislands.admin.fly\", \"SUPPORTED_VERIFIED\", \"P2\""), "Feature parity matrix must mark superior.admin.fly verified P2");
    }

    private static Set<String> declaredPermissionNodes(String plugin) {
        return Arrays.stream(plugin.split("\\R"))
            .map(String::trim)
            .filter(line -> line.startsWith("cloudislands."))
            .map(line -> line.substring(0, line.indexOf(':')))
            .collect(Collectors.toCollection(TreeSet::new));
    }

    private static Set<String> commandPermissionNodes(String plugin) {
        return Arrays.stream(plugin.split("\\R"))
            .map(String::trim)
            .filter(line -> line.startsWith("permission: cloudislands."))
            .map(line -> line.substring("permission: ".length()))
            .collect(Collectors.toCollection(TreeSet::new));
    }

    private static Set<String> explicitHasPermissionNodes(String source) {
        Matcher matcher = Pattern.compile("hasPermission\\(\"([^\"]+)\"\\)").matcher(source);
        Set<String> permissions = new TreeSet<>();
        while (matcher.find()) {
            permissions.add(matcher.group(1));
        }
        return permissions;
    }

    private static Set<String> explicitPermissionStringNodes(String source) {
        Matcher matcher = Pattern.compile("\"(cloudislands\\.(?:admin|bypass|island)\\.[^\"]+)\"").matcher(source);
        Set<String> permissions = new TreeSet<>();
        while (matcher.find()) {
            permissions.add(matcher.group(1));
        }
        return permissions;
    }

    private static Set<String> mappedAdminPermissionNodes(String backend) {
        Matcher matcher = Pattern.compile("case ([^;]+?) -> \"cloudislands\\.admin\\.\" \\+ root;").matcher(backend);
        assertTrue(matcher.find(), "Admin permission mapping switch must be present");
        return Arrays.stream(matcher.group(1).split(","))
            .map(String::trim)
            .map(root -> root.replace("\"", ""))
            .map(root -> "cloudislands.admin." + root)
            .collect(Collectors.toCollection(TreeSet::new));
    }

    private static Set<String> mappedIslandPermissionNodes(String source) {
        Matcher matcher = Pattern.compile("\"(cloudislands\\.island\\.[^\"]+)\"").matcher(source);
        Set<String> permissions = new TreeSet<>();
        while (matcher.find()) {
            permissions.add(matcher.group(1));
        }
        return permissions;
    }
}
