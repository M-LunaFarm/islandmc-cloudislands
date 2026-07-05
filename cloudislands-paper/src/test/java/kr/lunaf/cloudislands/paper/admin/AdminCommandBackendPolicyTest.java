package kr.lunaf.cloudislands.paper.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        assertTrue(source.contains("configHandler.validationDiagnosticSection()"), "Diagnostics export must include local config validation");
        assertTrue(source.contains("configHandler.effectiveConfigDiagnosticSection()"), "Diagnostics export must include redacted effective config");
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
            "ciadmin island member setleader <island> <player>"
        )) {
            assertTrue(adminSurface.contains(command), command);
        }
        assertTrue(adminSurface.contains("\"member\""), "Island member must be cataloged for admin completion");
        assertTrue(source.contains("handleIslandMember"), "Admin member commands must have a dedicated handler before island id resolution");
        assertTrue(source.contains("coreApiClient.memberCommands().adminAddMember(islandId, playerUuid"), "Admin member add must use the typed member client");
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
        for (String permission : List.of("superior.admin.add", "superior.admin.kick", "superior.admin.promote", "superior.admin.demote", "superior.admin.setleader")) {
            assertTrue(parity.contains("\"" + permission + "\", \"cloudislands.admin.island\", \"SUPPORTED_VERIFIED\""), "Feature parity matrix must mark " + permission + " verified");
        }
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
            "ciadmin setup verify"
        )) {
            assertTrue(adminSurface.contains(command), command);
        }
        assertTrue(catalog.contains("\"setup\""), "Setup root command must be registered");
        assertTrue(catalog.contains("SETUP_COMMANDS"), "Setup subcommands must be cataloged for tab completion");
        assertTrue(source.contains("handleSetup"), "Setup command must have a handler");
        assertTrue(source.contains("return handleDoctor(sender, new String[] {\"doctor\"})"), "Setup verify must delegate to doctor checks");
        assertTrue(source.contains("args.length == 2 && args[0].equalsIgnoreCase(\"setup\")"), "Setup tab completion must use setup subcommands");
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
        assertTrue(source.contains("dashboardMessage(List<CharSequence> parts)"), "Dashboard must render a focused overview message");
        assertTrue(source.contains("coreApiClient.adminMetrics().summary()"), "Dashboard must include typed metrics");
        assertTrue(source.contains("coreApiClient.adminNodes().listNodesSummary()"), "Dashboard must include typed node state");
        assertTrue(source.contains("coreApiClient.jobs().list()"), "Dashboard must include typed job queue state");
        assertTrue(source.contains("coreApiClient.adminRoutes().debug(new UUID(0L, 0L))"), "Dashboard must include typed route state");
        assertTrue(source.contains("coreApiClient.adminStorage().status()"), "Dashboard must include typed storage health");
        assertTrue(source.contains("integrationStatusMessage()"), "Dashboard must include integration state");
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
        assertTrue(configHandler.contains("if (!validation.valid())"), "Config reload must keep the current config when validation fails");
        assertTrue(configSurface.contains("reloadRuntimeConfig()"), "Config reload must refresh the active Config v2 runtime snapshot after validation passes");
        assertTrue(configHandler.contains("plugin.reloadRuntimeConfig()"), "Admin config reload must call the Paper runtime snapshot reload boundary");
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
        assertTrue(backend.contains("migrationHandler.handle(sender, args)"), "Admin backend command routing must stay thin for migration");
        assertTrue(!backend.contains("private String migrationMessage("), "Migration response formatting must not live in AdminCommandBackend");
        assertTrue(handler.contains("coreApiClient.migrations().migrateSuperiorSkyblock2"), "Migration handler must use the typed migration client");
        assertTrue(formatter.contains("String format(MigrationRunSnapshot snapshot)"), "Migration formatter must accept typed migration snapshots");
        assertTrue(!formatter.contains("String format(String body)"), "Migration formatter must not reparse Core JSON after the typed client boundary");
        assertTrue(catalog.contains("\"wizard\", \"scan\""), "Migration wizard must be a first-class migration subcommand");
        assertTrue(catalog.contains("\"report\""), "Migration report must be a first-class migration subcommand");
        assertTrue(catalog.contains("\"compare\""), "Migration compare must be a first-class migration subcommand");
        assertTrue(catalog.contains("ciadmin migrate-superiorskyblock2 wizard"), "Migration wizard must be listed in admin help");
        assertTrue(catalog.contains("ciadmin migrate-superiorskyblock2 report"), "Migration report must be listed in admin help");
        assertTrue(catalog.contains("ciadmin migrate-superiorskyblock2 compare <island>"), "Migration compare must be listed in admin help");
        assertTrue(handler.contains("action.equalsIgnoreCase(\"compare\") && args.length < 3"), "Migration compare must require an island selector");
        assertTrue(handler.contains("AdminMigrationMenu.open(player, messageProvider.messagesFor(player))"), "Migration wizard must open the existing GUI for player operators");
        assertTrue(handler.contains("AdminCommandCatalog.MIGRATION_HELP_COMMANDS"), "Migration wizard console fallback must reuse the migration command catalog");
        assertTrue(backend.contains("this::messagesFor"), "Admin backend must pass localized messages into the migration wizard handler");
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
