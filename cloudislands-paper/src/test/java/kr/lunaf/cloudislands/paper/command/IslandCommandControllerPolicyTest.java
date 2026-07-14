package kr.lunaf.cloudislands.paper.command;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class IslandCommandControllerPolicyTest {
    @Test
    void playerRouteMessagesUsePlayerRouteTicketView() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandRoutingCommandHandler.java"));

        assertTrue(source.contains("import kr.lunaf.cloudislands.common.feature.PlayerRouteTicketView;"));
        assertTrue(source.contains("PlayerRouteTicketView.from(ticket).destination()"));
        assertTrue(source.contains("case \"my-island\" -> \"내 섬\";"));
        assertTrue(source.contains("case \"island-visit\" -> \"방문할 섬\";"));
        assertTrue(source.contains("case \"island-warps\" -> \"섬 워프\";"));
    }

    @Test
    void singlePaperRoutingConsumesReadyTicketsLocally() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandRoutingCommandHandler.java"));
        String consumer = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/RouteTicketConsumer.java"));
        String registrar = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/PaperCommandRegistrar.java"));
        String routeSessions = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/bootstrap/PaperRouteSessionRuntimeFactory.java"));
        String routeSessionListener = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/session/PaperRouteSessionListener.java"));

        assertTrue(source.contains("localConsumer.consumeAndTeleport(ticket.ticketId(), player.getUniqueId(), ticket.nonce())"));
        assertTrue(source.indexOf("if (localConsumer != null)") < source.indexOf("routingUseCase.publishRouteSession"));
        assertTrue(registrar.contains("routing().directLocalTeleport()"));
        assertTrue(registrar.contains("islandController.enableLocalRouting(agent.routeTickets(), plugin.runtimeConfig().routing().localFallbackWorld())"));
        assertTrue(source.contains("localRouteConsumer.teleportToWorldSpawn(player.getUniqueId(), localFallbackWorld)"));
        assertTrue(consumer.contains("supply(plugin, () -> worlds.worldSpawn(worldName))"), "single-Paper fallback world lookup must run on the Paper scheduler");
        assertTrue(consumer.contains("thenCompose(destination -> PaperSchedulers.supply(plugin"), "single-Paper fallback player lookup and teleport must return to the Paper scheduler");
        assertTrue(routeSessions.contains("islandNode && !safeConfig.routing().directLocalTeleport()"));
        assertTrue(routeSessions.contains("listener.disableRouteSessionConsumption()"));
        assertTrue(routeSessionListener.contains("if (!routeSessionConsumptionEnabled)"));
    }

    @Test
    void routeFutureCallbacksReturnToThePaperSchedulerBeforePlayerUiAccess() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandRoutingCommandHandler.java"));

        assertTrue(source.contains("thenAccept(ticket -> runSync(() -> routeTicket(player, ticket, failureMessage, 0)))"));
        assertTrue(source.contains("routeTicketStatus(ticket).thenAccept(status -> runSync(() ->"));
        assertTrue(source.contains("runSync(() -> {\n                clearRouteLoading(player);\n                connectWithTicket"));
        assertTrue(source.contains("PaperSchedulers.run(plugin, task)"));
    }

    @Test
    void schedulerCanReturnMainThreadBukkitValuesToAsyncPipelines() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/platform/scheduler/PaperSchedulers.java"));

        assertTrue(source.contains("public static <T> CompletableFuture<T> supply"));
        assertTrue(source.contains("result.complete(supplier.get())"));
        assertTrue(source.contains("result.completeExceptionally(error)"));
    }

    @Test
    void offlinePlayerNamesUseCoreProfilesInsteadOfInventedBukkitUuids() throws Exception {
        String resolver = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandCommandPlayerResolver.java"));
        String membership = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandMembershipCommandHandler.java"));

        assertTrue(resolver.contains("memberManagement.playerUuidByName(value)"));
        assertTrue(resolver.contains("player profile was not found"));
        assertFalse(resolver.contains("getOfflinePlayer("));
        assertTrue(membership.contains("resolveInviteTarget(target)"));
        assertTrue(membership.contains("memberManagement.playerUuidByName(target)"));
        assertTrue(membership.contains("member-invite-player-not-found"));
        assertFalse(membership.contains("getOfflinePlayer("));
    }

    @Test
    void tabCompletionIsSeparatedFromCommandBackend() throws Exception {
        String backend = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandCommandBackend.java"));
        String controller = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandCommandController.java"));
        String completer = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandCommandTabCompleter.java"));
        String catalog = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandCommandCatalog.java"));
        String registry = Files.readString(Path.of("../cloudislands-protocol/src/main/java/kr/lunaf/cloudislands/protocol/command/IslandPlayerCommandRegistry.java"));

        assertFalse(backend.contains("implements CommandExecutor, TabCompleter"), "command execution backend must not own tab completion");
        assertFalse(backend.contains("onTabComplete("), "tab completion belongs in IslandCommandTabCompleter");
        assertTrue(backend.contains("static final List<String> SUBCOMMANDS = IslandCommandCatalog.SUBCOMMANDS;"), "command keyword catalog must live outside the backend");
        assertTrue(backend.contains("static final List<String> HELP_COMMANDS = IslandCommandCatalog.HELP_COMMANDS;"), "help command catalog must live outside the backend");
        assertTrue(catalog.contains("final class IslandCommandCatalog"), "command catalog must be isolated in its own class");
        assertTrue(registry.contains("public final class IslandPlayerCommandRegistry"), "Paper and Velocity command descriptors must live in the shared protocol registry");
        assertTrue(controller.contains("private final IslandCommandTabCompleter tabCompleter;"));
        assertTrue(controller.contains("return tabCompleter.onTabComplete(sender, command, alias, args);"));
        assertTrue(completer.contains("implements TabCompleter"));
        assertTrue(completer.contains("IslandCommandBackend.SUBCOMMANDS"));
        assertTrue(completer.contains("IslandCommandBackend.HELP_COMMANDS.size()"));
        assertTrue(completer.contains("IslandCommandCatalog.helpCategoryNames()"));
        assertTrue(completer.contains("IslandCommandCatalog.helpCategory("));
        assertTrue(completer.contains("IslandCommandCatalog.upgradeKeys()"), "upgrade purchase tab completion must suggest known upgrade keys");
        assertTrue(completer.contains("first.equals(\"buyupgrade\")") && completer.contains("first.equals(\"upgrade-buy\")") && completer.contains("first.equals(\"rankup\")") && completer.contains("first.equals(\"업그레이드구매\")"));
    }

    @Test
    void commandRoutingIsSeparatedFromCommandBackend() throws Exception {
        String backend = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandCommandBackend.java"));
        String router = routerSource();

        assertTrue(backend.contains("private final IslandCommandRouter router;"));
        assertTrue(backend.contains("return router.handleCommand(sender, command, label, args);"));
        assertTrue(backend.contains("router.handleGuiAction(player, action, click);"));
        assertFalse(backend.contains("commandListPage("), "command route parsing belongs in IslandCommandRouter");
        assertFalse(backend.contains("sendCommandList(Player player"), "command list rendering belongs in IslandCommandRouter");
        assertTrue(router.contains("final class IslandCommandRouter"));
        assertTrue(router.contains("boolean handleCommand(@NotNull CommandSender sender"));
        assertTrue(router.contains("void handleGuiAction(Player player, GuiAction action, GuiClick click)"));
        assertTrue(router.contains("CommandListPolicy.page"));
        assertTrue(router.contains("helpCategoryRequest(effectiveArgs)"));
        assertTrue(router.contains("helpCategoryRequest.category().commands()"));
    }

    @Test
    void commandHelpUsesClickableAdventureComponents() throws Exception {
        String router = routerSource();
        String koMessages = Files.readString(Path.of("src/main/resources/config-v2/ui/messages/ko_kr.yml"));
        String enMessages = Files.readString(Path.of("src/main/resources/config-v2/ui/messages/en_us.yml"));

        assertTrue(router.contains("ClickEvent.suggestCommand(\"/\" + command)"), "command help entries must let players insert commands from the help list");
        assertTrue(router.contains("ClickEvent.runCommand(\"/\" + command)"), "command help pagination must be clickable");
        assertTrue(router.contains("hoverEvent(commandHoverComponent"), "command help entries must expose hover details");
        assertTrue(router.contains("commandListGuiButton(label)"), "command help must include a GUI open button");
        assertTrue(router.contains("command-list-gui-button"), "command help GUI button must be localized");
        assertTrue(router.contains("permission.node()"), "command help hover must expose the permission node");
        assertTrue(router.contains("runtime.hasCommandPermission(player, permission)"), "command help must disable entries the player cannot use");
        assertTrue(router.contains("sendCommandSuggestion(Player player, String label, String suggestion)"), "unknown command suggestions must use the same clickable UX");
        assertTrue(koMessages.contains("command-suggestion-hover:"));
        assertTrue(enMessages.contains("command-suggestion-hover:"));
        assertTrue(koMessages.contains("command-list-hover-permission:"));
        assertTrue(enMessages.contains("command-list-hover-permission:"));
        assertTrue(koMessages.contains("command-list-gui-hover:"));
        assertTrue(enMessages.contains("command-list-gui-hover:"));
    }

    @Test
    void rootIslandCommandsOpenMainMenuBeforeCommandListFallback() throws Exception {
        String router = routerSource();
        String factory = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandCommandRouterFactory.java"));
        String onboarding = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/gui/IslandOnboardingMenu.java"));
        String registrar = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/PaperCommandRegistrar.java"));

        assertTrue(router.contains("if (args.length == 0) {\n            openMainMenuOrCommandList(player, label);"), "bare /island and /섬 must open the GUI first");
        assertTrue(router.contains("subcommand.equals(\"menu\")") && router.contains("subcommand.equals(\"panel\")") && router.contains("subcommand.equals(\"manager\")") && router.contains("subcommand.equals(\"cp\")")
            && router.contains("openMainMenuOrCommandList(player, label);"), "canonical menu and SS2 panel aliases must open the GUI first");
        assertTrue(router.contains("if (action instanceof GuiAction.MainOpen) {\n            openMainMenuOrCommandList(player, \"섬\");"), "GUI main-open actions must return to the main menu");
        assertTrue(router.contains("private void openMainMenuOrCommandList(Player player, String label)"));
        assertTrue(router.contains("if (!runtime.openMainMenu(player)) {\n            sendCommandList(player, label, \"섬 명령어 목록\", allHelpCommands(), 1);"), "command list must remain only as the no-GUI/error fallback and include addon commands");
        assertFalse(router.contains("if (action instanceof GuiAction.MainOpen) {\n            sendCommandList"), "main-open GUI action must not show the command list directly");
        assertTrue(factory.contains("IslandOnboardingMenu.open(plugin, coreApiClient, player"), "router runtime must route bare /섬 through state-based onboarding");
        assertTrue(factory.contains("() -> IslandMainMenu.open(player, messages.messagesFor(player))"), "router runtime must keep IslandMainMenu as onboarding fallback");
        assertTrue(onboarding.contains("client.navigation().playerIslands(player.getUniqueId())"), "onboarding must inspect the player's real island state");
        assertTrue(onboarding.contains("if (islands.isEmpty()) {\n                IslandCreateMenu.open(plugin, client, player, messages);"), "players without islands must land on template comparison/create UX");
        assertTrue(onboarding.contains("IslandMyIslandsMenu.open(plugin, client, player, messages);"), "players with islands must land on their island list instead of generic help");
        assertTrue(factory.contains("if (!guiMenusEnabled) {\n                        return false;"), "disabled GUI config must keep command-list fallback");
        assertTrue(registrar.contains("plugin.runtimeConfig().guiEnabledForRole(agent.role())"), "command routing must use the same role-based GUI enablement as listener registration");
    }

    @Test
    void helpGuiRouteAndMainMenuHelpItemUseExistingHelpRenderer() throws Exception {
        String router = routerSource();
        String parser = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/gui/GuiActionParser.java"));
        String actions = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/gui/GuiAction.java"));
        String menu = Files.readString(Path.of("src/main/resources/config-v2/ui/menus/main.yml"));

        assertTrue(router.contains("isGuiHelpRequest(effectiveArgs)"), "/섬 도움말 gui must route to the GUI entry point");
        assertTrue(router.contains("noPayload.type() == GuiAction.NoPayloadType.HELP_OPEN"), "main menu help button must reuse the command help renderer");
        assertTrue(router.contains("sendCommandList(player, \"섬\", \"섬 명령어 목록\", allHelpCommands(), 1);"));
        assertTrue(actions.contains("HELP_OPEN(\"island.help.open\")"));
        assertTrue(parser.contains("case \"island.help.open\" -> Optional.of(new GuiAction.NoPayload(GuiAction.NoPayloadType.HELP_OPEN));"));
        assertTrue(menu.contains("action: island.help.open"));
        assertTrue(menu.contains("fallback-name: 도움말"));
    }

    @Test
    void islandPlayerCommandsUseGranularPermissionPolicy() throws Exception {
        String permissions = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandCommandPermission.java"));
        String router = routerSource();
        String completer = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandCommandTabCompleter.java"));
        String mainMenu = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/gui/IslandMainMenu.java"));
        String plugin = Files.readString(Path.of("src/main/resources/plugin.yml"));

        assertTrue(permissions.contains("MENU(\"cloudislands.island.menu\""));
        assertTrue(permissions.contains("CREATE(\"cloudislands.island.create\""));
        assertTrue(permissions.contains("BANK_DEPOSIT(\"cloudislands.island.bank.deposit\""));
        assertTrue(permissions.contains("BANK_WITHDRAW(\"cloudislands.island.bank.withdraw\""));
        assertTrue(permissions.contains("INVITE(\"cloudislands.island.invite\""));
        assertTrue(permissions.contains("SNAPSHOT(\"cloudislands.island.snapshot\""));
        assertTrue(permissions.contains("RESTORE(\"cloudislands.island.restore\""));
        assertTrue(permissions.contains("player.hasPermission(ADMIN_BYPASS) || player.hasPermission(node)"), "cloudislands.admin.bypass must bypass player sub-permissions");
        assertTrue(permissions.contains("legacyNodes.stream().anyMatch(player::hasPermission)"), "legacy player permissions must remain compatible");
        assertTrue(router.contains("IslandCommandPermission.fromSubcommand(subcommand)"), "subcommands must be permission-gated before handlers run");
        assertTrue(router.contains("IslandCommandPermission.fromGuiActionId(action.actionId())"), "GUI actions must use the same command permission policy");
        assertTrue(router.contains("island-command-no-permission"), "permission failures must use a localizable message key");
        assertTrue(completer.contains("!IslandCommandPermission.hasAccess(sender, args[0])"), "tab-complete must hide arguments for denied commands");
        assertTrue(completer.contains("IslandCommandPermission.hasAccess(sender, subcommand)"), "top-level tab-complete must hide denied subcommands");
        assertTrue(mainMenu.contains("Material.BARRIER"), "main menu must show locked entries for denied features");
        assertTrue(mainMenu.contains("IslandCommandPermission.fromGuiActionId(actionId)"), "main menu locks must share GUI action permission mapping");
        assertTrue(plugin.contains("cloudislands.island.bank.deposit:"));
        assertTrue(plugin.contains("cloudislands.island.bank.withdraw:"));
        assertTrue(plugin.contains("cloudislands.island.warehouse.view:"));
        assertTrue(plugin.contains("cloudislands.island.permissions:"));
        assertTrue(plugin.contains("cloudislands.island.restore:"));
    }

    @Test
    void islandPlayerCommandsUseCooldownAndWarmupPolicy() throws Exception {
        String router = routerSource();
        String factory = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandCommandRouterFactory.java"));
        String backend = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandCommandBackend.java"));
        String plugin = Files.readString(Path.of("src/main/resources/plugin.yml"));
        String koMessages = Files.readString(Path.of("src/main/resources/config-v2/ui/messages/ko_kr.yml"));
        String enMessages = Files.readString(Path.of("src/main/resources/config-v2/ui/messages/en_us.yml"));

        assertTrue(router.contains("private final IslandCommandDelayPolicy delayPolicy = new IslandCommandDelayPolicy();"));
        assertTrue(router.contains("private final IslandCommandWarmupPolicy warmupPolicy = new IslandCommandWarmupPolicy();"));
        assertTrue(router.contains("if (!checkCommandDelay(player, label, subcommand, effectiveArgs.clone()))"), "command delay policy must run before handlers mutate state");
        assertTrue(router.contains("return runIslandAction(player, label, subcommand, effectiveArgs);"), "warmup must split preflight from command execution");
        assertTrue(router.contains("runtime.hasPermission(player, IslandCommandDelayPolicy.BYPASS_COOLDOWN_PERMISSION)"));
        assertTrue(router.contains("runtime.hasPermission(player, IslandCommandDelayPolicy.BYPASS_WARMUP_PERMISSION)"));
        assertTrue(router.contains("runtime.scheduleCommandWarmup(player, delayTicks"), "warmup commands must run after the waiting window instead of immediately");
        assertTrue(router.contains("warmupPolicy.complete(player.getUniqueId())"), "scheduled commands must only execute while their pending warmup remains valid");
        assertTrue(router.contains("void cancelWarmupOnMove(Player player, Location from, Location to)"), "movement must cancel pending warmups");
        assertTrue(router.contains("void markCombat(Player player)"), "combat must block warmup-gated movement commands");
        assertTrue(router.contains("delayPolicy.clear(player.getUniqueId(), pending.subject())"), "cancelled warmups must not consume the command cooldown");
        assertTrue(router.contains("player.sendActionBar(runtime.component(player, message))"), "warmup state must be visible as a configured Adventure component in the actionbar");
        assertTrue(router.contains("player.showTitle(Title.title("), "warmup state must also be visible as a title");
        assertTrue(router.contains("IslandCommandDelayPolicy.COOLDOWN_MESSAGE_KEY"));
        assertTrue(router.contains("IslandCommandDelayPolicy.WARMUP_MESSAGE_KEY"));
        assertTrue(router.contains("IslandCommandDelayPolicy.WARMUP_TITLE_MESSAGE_KEY"));
        assertTrue(router.contains("IslandCommandDelayPolicy.WARMUP_SUBTITLE_MESSAGE_KEY"));
        assertTrue(router.contains("IslandCommandWarmupPolicy.WARMUP_CANCELLED_MESSAGE_KEY"));
        assertTrue(router.contains("IslandCommandWarmupPolicy.WARMUP_PENDING_MESSAGE_KEY"));
        assertTrue(router.contains("IslandCommandWarmupPolicy.COMBAT_BLOCKED_MESSAGE_KEY"));
        assertTrue(router.contains("void clearPlayerState(Player player)"));
        assertTrue(factory.contains("PaperSchedulers.runLater(plugin, task, delayTicks)::cancel"), "router runtime must use the Bukkit scheduler for delayed execution");
        assertTrue(factory.contains("public boolean hasPermission(Player player, String permission)"));
        assertTrue(factory.contains("player.hasPermission(permission)"));
        assertTrue(factory.contains("public String playerMessage(String message)"));
        assertTrue(backend.contains("router.clearPlayerState(event.getPlayer())"), "quit/kick cleanup must clear command delay state");
        assertTrue(backend.contains("public void onMove(PlayerMoveEvent event)"));
        assertTrue(backend.contains("router.cancelWarmupOnMove(event.getPlayer(), event.getFrom(), event.getTo())"));
        assertTrue(backend.contains("public void onDamage(EntityDamageByEntityEvent event)"));
        assertTrue(backend.contains("router.markCombat(player)"));
        assertTrue(plugin.contains("cloudislands.bypass.cooldown:"));
        assertTrue(plugin.contains("cloudislands.bypass.warmup:"));
        assertTrue(plugin.contains("cloudislands.island.home.cooldown:"));
        assertTrue(plugin.contains("cloudislands.island.visit.cooldown:"));
        assertTrue(plugin.contains("cloudislands.island.create.cooldown:"));
        assertTrue(plugin.contains("cloudislands.island.delete.cooldown:"));
        assertTrue(plugin.contains("cloudislands.island.reset.cooldown:"));
        assertTrue(plugin.contains("cloudislands.island.snapshot.cooldown:"));
        assertTrue(plugin.contains("cloudislands.island.restore.cooldown:"));
        assertTrue(koMessages.contains("island-command-cooldown:"));
        assertTrue(koMessages.contains("island-command-warmup:"));
        assertTrue(koMessages.contains("island-command-warmup-cancelled:"));
        assertTrue(koMessages.contains("island-command-warmup-pending:"));
        assertTrue(koMessages.contains("island-command-warmup-title:"));
        assertTrue(koMessages.contains("island-command-warmup-subtitle:"));
        assertTrue(koMessages.contains("island-command-combat-blocked:"));
        assertTrue(enMessages.contains("island-command-cooldown:"));
        assertTrue(enMessages.contains("island-command-warmup:"));
        assertTrue(enMessages.contains("island-command-warmup-cancelled:"));
        assertTrue(enMessages.contains("island-command-warmup-pending:"));
        assertTrue(enMessages.contains("island-command-warmup-title:"));
        assertTrue(enMessages.contains("island-command-warmup-subtitle:"));
        assertTrue(enMessages.contains("island-command-combat-blocked:"));
    }

    @Test
    void bankCommandsAreSeparatedFromCommandBackend() throws Exception {
        String backend = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandCommandBackend.java"));
        String bankHandler = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandBankCommandHandler.java"));
        String bankUseCase = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/application/BankUseCase.java"));
        String vaultBridge = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/economy/VaultEconomyBridge.java"));
        String logMenu = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/gui/IslandLogMenu.java"));
        String tabCompleter = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandCommandTabCompleter.java"));

        assertTrue(backend.contains("private final IslandBankCommandHandler bankCommands;"));
        assertTrue(routerSource().contains("bankCommands.handleCommand(player, subcommand, args)"));
        assertTrue(routerSource().contains("bankCommands.handleGuiAction(player, action"));
        assertFalse(backend.contains("depositIslandBank("), "bank deposit logic belongs in IslandBankCommandHandler");
        assertFalse(backend.contains("withdrawIslandBank("), "bank withdraw logic belongs in IslandBankCommandHandler");
        assertTrue(bankHandler.contains("boolean handleCommand(Player player, String subcommand, String[] args)"));
        assertTrue(bankHandler.contains("boolean handleGuiAction(Player player, GuiAction action)"));
        assertTrue(bankHandler.contains("private final BankUseCase bankUseCase;"));
        assertTrue(bankHandler.contains("bankUseCase.deposit("));
        assertTrue(bankHandler.contains("bankUseCase.withdraw("));
        assertTrue(bankHandler.contains("bankUseCase.depositAll("), "bank wildcard deposits must transfer the player's full economy balance");
        assertTrue(bankHandler.contains("bankUseCase.withdrawAll("), "bank wildcard withdrawals must transfer the island's full bank balance");
        assertTrue(tabCompleter.contains("List.of(\"*\", \"100\", \"1000\", \"10000\")"), "bank amount completion must advertise the all-balance wildcard");
        assertTrue(bankHandler.contains("args[1].equalsIgnoreCase(\"logs\")"), "canonical /is bank logs must not discard its logs argument");
        assertTrue(bankHandler.contains("IslandLogMenu.openBankLogs"), "bank logs must open the filtered transaction log view");
        assertTrue(logMenu.contains("ISLAND_BANK_DEPOSIT") && logMenu.contains("ISLAND_BANK_WITHDRAW"), "bank transaction view must exclude unrelated island audit entries");
        assertTrue(logMenu.contains("entries.stream().filter(filter).toList()"), "filtered transaction entries must retain the complete fetched history before pagination");
        assertTrue(logMenu.contains("int maxPage = Math.max(0, (entries.size() - 1) / pageSize)"), "bank transaction history must paginate instead of discarding entries beyond the first GUI page");
        assertFalse(bankHandler.contains("coreApiClient.depositIslandBank"), "bank mutation logic belongs in BankUseCase");
        assertFalse(bankHandler.contains("coreApiClient.withdrawIslandBank"), "bank mutation logic belongs in BankUseCase");
        assertTrue(bankUseCase.contains("BankCommandClient bankCommands"));
        assertTrue(bankUseCase.contains("bankCommands.deposit"));
        assertTrue(bankUseCase.contains("bankCommands.withdraw"));
        assertFalse(bankUseCase.contains("coreApiClient.depositIslandBank"));
        assertFalse(bankUseCase.contains("coreApiClient.withdrawIslandBank"));
        assertTrue(vaultBridge.contains("EconomyProviderState providerState()"));
        assertTrue(vaultBridge.contains("EconomyProviderState.NOT_INSTALLED"));
        assertTrue(vaultBridge.contains("EconomyProviderState.API_COMPATIBLE"));
        assertTrue(vaultBridge.contains("EconomyProviderState.OPERATION_FAILED"));
        assertTrue(vaultBridge.contains("PaperSchedulers.run(plugin"), "Vault provider calls must return to the Paper scheduler");
        assertTrue(vaultBridge.contains("onPaperThread(() -> callBoolean"), "Vault withdrawals must execute on the Paper scheduler");
        assertTrue(vaultBridge.contains("return onPaperThread(() -> balanceNow(playerUuid))"), "Vault balance reads must execute on the Paper scheduler");
    }

    @Test
    void snapshotCommandsAreSeparatedFromCommandBackend() throws Exception {
        String backend = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandCommandBackend.java"));
        String snapshotHandler = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandSnapshotCommandHandler.java"));
        String snapshotUseCase = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/application/SnapshotUseCase.java"));

        assertTrue(backend.contains("private final IslandSnapshotCommandHandler snapshotCommands;"));
        assertTrue(routerSource().contains("snapshotCommands.handleCommand(player, subcommand, args)"));
        assertTrue(routerSource().contains("snapshotCommands.handleGuiAction(player, action"));
        assertFalse(backend.contains("requestIslandSnapshot("), "snapshot create logic belongs in SnapshotUseCase");
        assertFalse(backend.contains("restoreIslandSnapshot("), "snapshot restore logic belongs in SnapshotUseCase");
        assertTrue(snapshotHandler.contains("boolean handleCommand(Player player, String subcommand, String[] args)"));
        assertTrue(snapshotHandler.contains("boolean handleGuiAction(Player player, GuiAction action, GuiClick click)"));
        assertTrue(snapshotHandler.contains("private final SnapshotUseCase snapshotUseCase;"));
        assertTrue(snapshotHandler.contains("private final SnapshotRetentionPolicy retentionPolicy;"), "snapshot command handler must retain Paper snapshot retention policy for UX");
        assertTrue(snapshotHandler.contains("snapshotUseCase.snapshotViews("));
        assertTrue(snapshotHandler.contains("snapshotUseCase.requestSnapshotAction("));
        assertTrue(snapshotHandler.contains("snapshotUseCase.restoreSnapshotAction("));
        assertTrue(snapshotHandler.contains("openRestoreConfirmation(player, SnapshotUseCase.positiveSnapshotNo(args[1]))"), "typed restore command must open confirmation before mutation");
        assertTrue(snapshotHandler.contains("private void openRestoreConfirmation(Player player, long snapshotNo)"), "snapshot restore confirmation must be shared by command and GUI flows");
        assertTrue(snapshotHandler.contains("\"island.snapshot.restore.confirm\""), "snapshot restore must use the token-protected confirm action");
        assertFalse(snapshotHandler.contains("restoreSnapshot(player, SnapshotUseCase.positiveSnapshotNo(args[1]))"), "typed restore command must not directly mutate Core");
        assertTrue(snapshotHandler.contains("IslandSnapshotMenu.open(plugin, coreApiClient, player, islandId, runtime.messagesFor(player), retentionPolicy)"), "snapshot menu must receive runtime retention policy");
        assertFalse(snapshotUseCase.contains("public CompletableFuture<String> listSnapshots("), "snapshot list usecase must expose typed views instead of raw JSON");
        assertFalse(snapshotUseCase.contains("public CompletableFuture<String> requestSnapshot("), "snapshot create usecase must expose typed actions instead of raw JSON");
        assertFalse(snapshotUseCase.contains("public CompletableFuture<String> restoreSnapshot("), "snapshot restore usecase must expose typed actions instead of raw JSON");
        assertFalse(snapshotHandler.contains("coreApiClient.requestIslandSnapshotResult"), "snapshot mutation logic belongs in SnapshotUseCase");
        assertFalse(snapshotHandler.contains("coreApiClient.restoreIslandSnapshotResult"), "snapshot mutation logic belongs in SnapshotUseCase");
        assertTrue(snapshotUseCase.contains("SnapshotCommandClient snapshotCommands"));
        assertTrue(snapshotUseCase.contains("snapshotCommands.requestSnapshot"));
        assertTrue(snapshotUseCase.contains("snapshotCommands.restoreSnapshot"));
        assertFalse(snapshotUseCase.contains("coreApiClient.requestIslandSnapshotResult"));
        assertFalse(snapshotUseCase.contains("coreApiClient.restoreIslandSnapshotResult"));
    }

    @Test
    void warehouseCommandsAreSeparatedFromCommandBackend() throws Exception {
        String backend = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandCommandBackend.java"));
        String warehouseHandler = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandWarehouseCommandHandler.java"));
        String warehouseItemPolicy = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/WarehouseItemPolicy.java"));
        String warehouseSettlement = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/WarehouseSettlement.java"));
        String warehouseUseCase = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/application/IslandWarehouseUseCase.java"));
        String controller = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandCommandController.java"));

        assertTrue(backend.contains("private final IslandWarehouseCommandHandler warehouseCommands;"));
        assertTrue(routerSource().contains("warehouseCommands.handleCommand(player, subcommand, args)"));
        assertTrue(routerSource().contains("warehouseCommands.handleGuiAction(player, action"));
        assertFalse(backend.contains("listIslandWarehouse("), "warehouse list logic belongs in IslandWarehouseCommandHandler");
        assertFalse(backend.contains("changeIslandWarehouse("), "warehouse mutation logic belongs in IslandWarehouseCommandHandler");
        assertTrue(warehouseHandler.contains("boolean handleCommand(Player player, String subcommand, String[] args)"));
        assertTrue(warehouseHandler.contains("boolean handleGuiAction(Player player, GuiAction action)"));
        assertTrue(warehouseHandler.contains("IslandWarehouseMenu.open"));
        assertTrue(warehouseHandler.contains("if (!canOpenWarehouse(player))"), "warehouse menu and list must enforce the island container permission before revealing contents");
        assertTrue(warehouseHandler.contains("runtime.allowed(player, IslandPermission.OPEN_CONTAINER)"), "warehouse reads must use the same island container permission as mutations");
        assertTrue(warehouseHandler.contains("warehouse-open-denied"), "denied warehouse reads must give explicit operator-configurable feedback");
        assertTrue(warehouseHandler.contains("isWarehouseMenuCommand(subcommand)"));
        assertTrue(warehouseHandler.contains("isWarehouseListCommand(subcommand)"));
        assertTrue(warehouseHandler.contains("subcommand.equals(\"chest\")"));
        assertTrue(warehouseHandler.indexOf("if (isWarehouseMenuCommand(subcommand))") < warehouseHandler.indexOf("openWarehouseMenu(player);"), "warehouse, chest, and vault commands must open the existing inventory menu");
        assertTrue(warehouseHandler.contains("subcommand.equals(\"warehouse-list\") || subcommand.equals(\"창고목록\")"), "explicit list aliases must preserve chat-list behavior");
        assertTrue(warehouseHandler.contains("IslandWarehouseUseCase"));
        assertTrue(warehouseHandler.contains("warehouseUseCase.listItems"));
        assertTrue(warehouseHandler.contains("warehouseUseCase.deposit"));
        assertTrue(warehouseHandler.contains("countStorableMaterial(player, material)"), "warehouse deposit must count only items representable by the material-and-amount schema");
        assertTrue(warehouseHandler.contains("WarehouseItemPolicy.storable(item, material)"), "warehouse removal and capacity checks must preserve item metadata instead of flattening custom items");
        assertTrue(warehouseItemPolicy.contains("item.getType() == material"), "warehouse rows must never accept a different material");
        assertTrue(warehouseItemPolicy.contains("!item.hasItemMeta()"), "warehouse rows cannot safely restore names, enchantments, damage, contents, or custom metadata");
        assertTrue(warehouseHandler.contains("warehouse-item-metadata-unsupported"), "unsupported metadata must give explicit operator-configurable feedback");
        assertTrue(warehouseHandler.contains("removeMaterial(activePlayer, material, settlement.amount())"), "warehouse deposit must remove real player inventory items only after shared preparation");
        assertTrue(warehouseHandler.contains("giveMaterial(player, material, settlement.amount())"), "warehouse withdraw and failed deposits must grant/refund the exact escrowed amount");
        assertTrue(warehouseHandler.contains("warehouseSuccessPrefix(settlement.deposit()) + settlement.materialKey() + \" x\" + settlement.amount()"), "warehouse success feedback must show the transferred amount rather than the resulting warehouse balance");
        assertTrue(warehouseHandler.contains("inventorySpace(player, material)"), "warehouse withdraw must verify inventory capacity before Core mutation");
        assertTrue(warehouseHandler.contains("pendingOperations.acquire(playerUuid)"), "warehouse mutations must reject overlapping operations for the same player");
        assertTrue(warehouseHandler.contains("} finally {\n                pendingOperations.release(playerUuid);"), "warehouse operation locks must remain held until inventory grant or refund is applied");
        assertTrue(warehouseHandler.contains("warehouse-operation-pending"), "overlapping warehouse operations must provide explicit feedback");
        assertTrue(warehouseHandler.contains("PersistentDataType.STRING") && warehouseHandler.contains("storeSettlement(activePlayer, settlement)"), "warehouse mutations must persist recovery state before inventory escrow or Core mutation");
        assertTrue(warehouseHandler.contains("warehouseCommands.prepareSettlement") && warehouseHandler.contains("warehouseCommands.escrowSettlement"), "warehouse inventory escrow must be registered with Core before the warehouse mutation");
        assertTrue(warehouseHandler.contains("warehouseQueries.pendingSettlement(playerUuid)"), "reconnect recovery must discover escrow created on another Paper node");
        assertTrue(warehouseHandler.indexOf("warehouseCommands.prepareSettlement") < warehouseHandler.indexOf("removeMaterial(activePlayer, material, settlement.amount())"), "shared PREPARED state must exist before inventory removal");
        assertTrue(warehouseSettlement.contains("idempotencyKey") && warehouseHandler.contains("settlement.idempotencyKey()"), "recovery must replay the exact Core idempotency key instead of issuing another mutation");
        assertTrue(warehouseHandler.contains("activePlayer == null || !activePlayer.isOnline()"), "late Core completions must not mutate a disconnected Player object");
        assertTrue(backend.contains("warehouseCommands.resumePendingSettlement(event.getPlayer())") && controller.contains("public void onJoin(PlayerJoinEvent event)"), "saved warehouse settlements must resume when the player reconnects");
        assertTrue(warehouseHandler.contains("warehouse-settlement-pending"), "ambiguous Core responses must keep escrowed items protected instead of guessing a refund or delivery");
        assertTrue(warehouseHandler.contains("IslandPermission permission = IslandPermission.OPEN_CONTAINER"), "warehouse deposit and withdraw must require the container permission");
        assertFalse(warehouseHandler.contains("deposit ? IslandPermission.OPEN_CONTAINER : IslandPermission.WITHDRAW_BANK"), "warehouse withdraw must not inherit bank withdrawal authority");
        assertTrue(warehouseHandler.contains("Material.matchMaterial"), "warehouse commands must resolve Bukkit materials before mutating Core warehouse state");
        assertTrue(warehouseHandler.contains("player.getInventory().addItem"), "warehouse withdraw must use the Bukkit inventory API");
        assertTrue(warehouseHandler.contains("player.getInventory().setStorageContents"), "warehouse deposit must persist inventory removal through the Bukkit inventory API");
        assertFalse(warehouseHandler.contains("coreApiClient.islandWarehouse"));
        assertFalse(warehouseHandler.contains("coreApiClient.depositIslandWarehouse"));
        assertFalse(warehouseHandler.contains("coreApiClient.withdrawIslandWarehouse"));
        assertFalse(warehouseUseCase.contains("public CompletableFuture<String> list("), "warehouse list usecase must expose typed item views instead of raw JSON");
        assertTrue(warehouseUseCase.contains("warehouseQueries.listItems"));
        assertFalse(warehouseUseCase.contains("coreApiClient.islandWarehouse"));
        assertTrue(warehouseUseCase.contains("WarehouseCommandClient warehouseCommands"));
        assertTrue(warehouseUseCase.contains("warehouseCommands.deposit"));
        assertTrue(warehouseUseCase.contains("warehouseCommands.withdraw"));
        assertFalse(warehouseUseCase.contains("coreApiClient.depositIslandWarehouse"));
        assertFalse(warehouseUseCase.contains("coreApiClient.withdrawIslandWarehouse"));
    }

    @Test
    void warehouseChestAliasesUseViewPermissionAndCatalogCoverage() throws Exception {
        String catalog = Files.readString(Path.of("../cloudislands-protocol/src/main/java/kr/lunaf/cloudislands/protocol/command/IslandPlayerCommandRegistry.java"));
        String permissions = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandCommandPermission.java"));
        String completer = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandCommandTabCompleter.java"));
        String plugin = Files.readString(Path.of("src/main/resources/plugin.yml"));

        assertTrue(catalog.contains("\"chest\""));
        assertTrue(catalog.contains("\"island-chest\""));
        assertTrue(catalog.contains("\"섬 chest\""));
        assertTrue(permissions.contains("WAREHOUSE(\"cloudislands.island.warehouse.view\""));
        assertTrue(permissions.contains("Set.of(\"cloudislands.island.warehouse\")"));
        assertTrue(permissions.contains("\"chest\""));
        assertTrue(completer.contains("first.equals(\"chest\")"));
        assertTrue(plugin.contains("cloudislands.island.warehouse.view:"));
        assertTrue(plugin.contains("cloudislands.island.warehouse.deposit:"));
        assertTrue(plugin.contains("cloudislands.island.warehouse.withdraw:"));
    }

    @Test
    void ss2AliasTabCompletionsMirrorCanonicalCommands() throws Exception {
        String completer = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandCommandTabCompleter.java"));

        assertTrue(completer.contains("first.equals(\"top\")"), "top alias must share ranking suggestions");
        assertTrue(completer.contains("first.equals(\"leaderboard\")"), "leaderboard alias must share ranking suggestions");
        assertTrue(completer.contains("first.equals(\"values\")"), "values alias must share block-value suggestions");
        assertTrue(completer.contains("first.equals(\"ratings\")"), "ratings alias must share review-list suggestions");
        assertTrue(completer.contains("first.equals(\"rankup\")"), "rankup alias must share upgrade-key suggestions");
    }

    @Test
    void superiorSkyblockLegacyAliasAdapterIsConfigGatedAndKeepsSharedCommandGates() throws Exception {
        String adapter = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/SuperiorSkyblock2CommandAliasAdapter.java"));
        String router = routerSource();
        String factory = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandCommandRouterFactory.java"));
        String registrar = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/PaperCommandRegistrar.java"));
        String runtimeConfig = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/config/PaperRuntimeConfig.java"));
        String runtimeLoader = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/config/PaperRuntimeConfigLoader.java"));
        String migrationConfig = Files.readString(Path.of("src/main/resources/config-v2/migration.yml"));
        String adminBackend = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/admin/AdminCommandBackend.java"));

        assertTrue(adapter.contains("public final class SuperiorSkyblock2CommandAliasAdapter"));
        assertTrue(adapter.contains("Map.entry(\"recalc\", new Mapping(\"levelcalc\", \"레벨계산\"))"), "SS2 recalc must route to the CloudIslands level calculation command");
        assertTrue(adapter.contains("Map.entry(\"team\", new Mapping(\"member-list-target\", \"멤버목록\"))"), "SS2 team must preserve its optional island or player target");
        assertTrue(adapter.contains("Map.entry(\"value\", new Mapping(\"value\", \"가치\"))"), "SS2 value <block> must preserve single-material lookup semantics");
        assertTrue(adapter.contains("Map.entry(\"teleport\", new Mapping(\"home\", \"홈\"))"), "SS2 teleport must route to the CloudIslands home command");
        assertTrue(adapter.contains("Map.entry(\"delwarp\", new Mapping(\"warp-delete\", \"워프삭제\"))"), "SS2 delwarp must route to the CloudIslands warp deletion command");
        assertTrue(adapter.contains("Map.entry(\"rankup\", new Mapping(\"upgrade-buy\", \"업그레이드구매\"))"), "SS2 rankup must route to the CloudIslands upgrade purchase command");
        assertTrue(adapter.contains("Map.entry(\"panel\", new Mapping(\"menu\", \"메뉴\"))"), "SS2 panel must route to the CloudIslands menu command");
        assertTrue(adapter.contains("Map.entry(\"close\", new Mapping(\"private\", \"비공개\"))"), "SS2 close must route to island private access");
        assertTrue(adapter.contains("Map.entry(\"open\", new Mapping(\"public\", \"공개\"))"), "SS2 open must route to island public access");
        assertTrue(adapter.contains("Map.entry(\"uncoop\", new Mapping(\"untrust\", \"신뢰해제\"))"), "SS2 uncoop must use the role-checked temporary cooperation removal path");
        assertTrue(router.contains("legacyAliases.translate(args)"), "Player aliases must be translated before legacy admin guidance is considered");
        assertTrue(adapter.contains("Map.entry(\"coops\", new Mapping(\"members\", \"멤버\"))"), "SS2 coops must open the member/co-op management surface");
        assertTrue(adapter.contains("AdminAliasGuidance"), "SS2 admin aliases must be guidance-only, not player command translations");
        assertTrue(adapter.contains("admin(\"purge\", \"island delete <island> --confirm\", true)"), "dangerous SS2 admin aliases must point at ciadmin confirmation flows");
        assertTrue(router.contains("sendLegacyAdminAliasGuidance(player, adminGuidance);"), "legacy admin aliases must get ciadmin guidance before normal player routing");
        assertTrue(router.contains("ClickEvent.suggestCommand(\"/\" + command)"), "legacy admin alias guidance must suggest the ciadmin command without running it");
        assertTrue(adapter.contains("USAGE.computeIfAbsent(alias"), "legacy alias usage must be counted for admin metrics");
        assertTrue(adminBackend.contains("SuperiorSkyblock2CommandAliasAdapter.metricsLine()"), "admin metrics must include local legacy alias usage");
        assertTrue(migrationConfig.contains("legacy-aliases:\n  superiorskyblock2:\n    enabled: false"), "legacy SS2 aliases must be disabled by default");
        assertTrue(runtimeConfig.contains("boolean superiorSkyblock2LegacyAliasesEnabled"), "runtime config must expose the legacy alias toggle");
        assertTrue(runtimeLoader.contains("\"legacy-aliases.superiorskyblock2.enabled\", \"migration.legacy-aliases.superiorskyblock2.enabled\""));
        assertTrue(runtimeLoader.contains("boolean legacyAliases = booleanValue(config, \"migration.legacy-aliases.superiorskyblock2.enabled\", false);"));
        assertTrue(registrar.contains("plugin.runtimeConfig().migration().superiorSkyblock2LegacyAliasesEnabled()"));
        assertTrue(factory.contains("new SuperiorSkyblock2CommandAliasAdapter(superiorSkyblock2LegacyAliasesEnabled, superiorSkyblock2MigrationMode)"));
        assertTrue(router.contains("SuperiorSkyblock2CommandAliasAdapter.ResolvedAlias legacyAlias = legacyAliases.translate(args).orElse(null);"));
        assertTrue(router.contains("legacyAliases.adminGuidance(args)"), "official /is admin <command> syntax must be checked as a nested legacy admin command");
        assertTrue(router.indexOf("legacyAliases.translate(args)") < router.indexOf("IslandCommandPermission.fromSubcommand(subcommand)"), "alias translation must happen before permission gating");
        assertTrue(router.indexOf("legacyAliases.translate(args)") < router.indexOf("checkCommandDelay(player, label, subcommand, effectiveArgs.clone())"), "alias translation must happen before cooldown and warmup gating");
    }

    @Test
    void chatAndLogCommandsAreSeparatedFromCommandBackend() throws Exception {
        String backend = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandCommandBackend.java"));
        String chatLogHandler = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandChatLogCommandHandler.java"));
        String communicationUseCase = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/application/IslandCommunicationUseCase.java"));

        assertTrue(backend.contains("private final IslandChatLogCommandHandler chatLogCommands;"));
        assertTrue(routerSource().contains("chatLogCommands.handleCommand(player, subcommand, args)"));
        assertTrue(routerSource().contains("chatLogCommands.handleGuiAction(player, action"));
        assertFalse(backend.contains("sendIslandChat("), "chat send logic belongs in IslandChatLogCommandHandler");
        assertFalse(backend.contains("listIslandLogs("), "log list logic belongs in IslandChatLogCommandHandler");
        assertTrue(chatLogHandler.contains("boolean handleCommand(Player player, String subcommand, String[] args)"));
        assertTrue(chatLogHandler.contains("boolean handleGuiAction(Player player, GuiAction action)"));
        assertTrue(chatLogHandler.contains("IslandCommunicationUseCase"));
        assertTrue(chatLogHandler.contains("communicationUseCase.sendChatAction"));
        assertTrue(chatLogHandler.contains("communicationUseCase.logViews"));
        assertFalse(chatLogHandler.contains("coreApiClient.sendIslandChat"));
        assertFalse(chatLogHandler.contains("coreApiClient.listIslandLogs"));
        assertFalse(communicationUseCase.contains("public CompletableFuture<String> sendChat("), "chat send usecase must expose typed actions instead of raw JSON");
        assertFalse(communicationUseCase.contains("public CompletableFuture<String> listLogs("), "log list usecase must expose typed log views instead of raw JSON");
        assertTrue(communicationUseCase.contains("CommunicationCommandClient communicationCommands"));
        assertTrue(communicationUseCase.contains("communicationCommands.sendChat"));
        assertFalse(communicationUseCase.contains("coreApiClient.sendIslandChat"));
        assertTrue(communicationUseCase.contains("communicationQueries.listLogs"));
        assertFalse(communicationUseCase.contains("coreApiClient.listIslandLogs"));
    }

    @Test
    void progressionCommandsAreSeparatedFromCommandBackend() throws Exception {
        String backend = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandCommandBackend.java"));
        String progressionHandler = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandProgressionCommandHandler.java"));
        String progressionUseCase = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/application/IslandProgressionUseCase.java"));
        String paperGuiViews = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/application/view/PaperGuiViews.java"));

        assertTrue(backend.contains("private final IslandProgressionCommandHandler progressionCommands;"));
        assertTrue(routerSource().contains("progressionCommands.handleCommand(player, subcommand, args)"));
        assertTrue(routerSource().contains("progressionCommands.handleGuiAction(player, action"));
        assertFalse(backend.contains("recalculateIslandLevel("), "level recalculation logic belongs in IslandProgressionCommandHandler");
        assertFalse(backend.contains("purchaseIslandUpgrade("), "upgrade purchase logic belongs in IslandProgressionCommandHandler");
        assertFalse(backend.contains("completeIslandTask("), "mission completion logic belongs in IslandProgressionCommandHandler");
        assertTrue(progressionHandler.contains("boolean handleCommand(Player player, String subcommand, String[] args)"));
        assertTrue(progressionHandler.contains("boolean handleGuiAction(Player player, GuiAction action)"));
        assertTrue(progressionHandler.contains("IslandProgressionUseCase"));
        assertTrue(progressionHandler.contains("progressionUseCase.islandLevel"));
        assertTrue(progressionHandler.contains("progressionUseCase.blockDetailsView"));
        assertTrue(progressionHandler.contains("new IslandTargetResolver(coreApiClient)"), "SS2 values/counts targets must share UUID, island-name, and player-primary resolution");
        assertTrue(progressionHandler.contains("args.length > 1 && !isInteger(args[1])"), "non-numeric values/counts arguments must be treated as targets instead of silently becoming limit 10");
        assertTrue(progressionHandler.contains("showBlockDetails(Player player, String target, int limit)"), "targeted block details must have an explicit execution path");
        assertTrue(progressionHandler.contains("block-details-target-not-found"), "unknown values/counts targets must report an error instead of showing the caller island");
        assertTrue(progressionHandler.contains("progressionUseCase.topWorthViews"));
        assertTrue(progressionHandler.contains("progressionUseCase.topLevelViews(100)"), "level command must calculate the next ranking target");
        assertTrue(progressionHandler.contains("progressionUseCase.topWorthViews(100)"), "worth command must calculate the next ranking target");
        assertTrue(progressionHandler.contains("growthTargetSuffix"), "progression messages must show the remaining growth target");
        assertTrue(progressionHandler.contains("progressionUseCase.recalculateLevelView"));
        assertTrue(progressionHandler.contains("progressionUseCase.upgradeViews"));
        assertTrue(progressionHandler.contains("progressionUseCase.purchaseUpgradeResult"));
        assertTrue(progressionHandler.contains("progressionUseCase.missionViews"));
        assertTrue(progressionHandler.contains("progressionUseCase.completeMissionResult"));
        assertFalse(progressionHandler.contains("coreApiClient.recalculateIslandLevel"));
        assertFalse(progressionHandler.contains("coreApiClient.purchaseIslandUpgrade"));
        assertFalse(progressionHandler.contains("coreApiClient.completeIslandMission"));
        assertFalse(progressionUseCase.contains("public CompletableFuture<String> islandInfo("), "progression island info usecase must expose typed views instead of raw JSON");
        assertFalse(progressionUseCase.contains("public CompletableFuture<String> blockDetails("), "block details usecase must expose typed views instead of raw JSON");
        assertFalse(progressionUseCase.contains("public CompletableFuture<String> topIslandsByWorth("), "worth ranking usecase must expose typed views instead of raw JSON");
        assertFalse(progressionUseCase.contains("public CompletableFuture<String> topIslandsByLevel("), "level ranking usecase must expose typed views instead of raw JSON");
        assertFalse(progressionUseCase.contains("public CompletableFuture<String> topIslandsByReviews("), "review ranking usecase must expose typed views instead of raw JSON");
        assertFalse(progressionUseCase.contains("public CompletableFuture<String> recalculateLevel("), "level recalculation usecase must expose typed views instead of raw JSON");
        assertFalse(progressionUseCase.contains("public CompletableFuture<String> listUpgrades("), "upgrade list usecase must expose typed views instead of raw JSON");
        assertFalse(progressionUseCase.contains("public CompletableFuture<String> purchaseUpgrade("), "upgrade mutation usecase must expose typed results instead of raw JSON");
        assertFalse(progressionUseCase.contains("public CompletableFuture<String> listMissions("), "mission list usecase must expose typed views instead of raw JSON");
        assertFalse(progressionUseCase.contains("public CompletableFuture<String> completeMission("), "mission mutation usecase must expose typed results instead of raw JSON");
        assertFalse(progressionUseCase.contains("CoreGuiViews.islandInfoView(body)"), "progression usecase must not parse raw Core JSON bodies");
        assertFalse(progressionUseCase.contains("levelView(String body)"), "progression usecase must not keep raw JSON overloads");
        assertTrue(progressionUseCase.contains("ProgressionQueryClient progressionQueries"), "progression reads must stay behind a typed core-client query boundary");
        assertTrue(progressionUseCase.contains("progressionQueries.blockDetails"));
        assertTrue(progressionUseCase.contains("progressionQueries.topWorth"));
        assertTrue(progressionUseCase.contains("progressionQueries.upgrades"));
        assertTrue(paperGuiViews.contains("client.progression().rankings(limit)"), "ranking GUI reads must stay behind a typed progression query boundary");
        assertFalse(paperGuiViews.contains("CoreGuiViews.rankings(client"), "ranking GUI reads must not call raw Core GUI ranking helpers directly");
        assertTrue(progressionUseCase.contains("ProgressionCommandClient progressionCommands"), "progression mutations must stay behind a typed core-client command boundary");
        assertTrue(progressionUseCase.contains("progressionCommands.recalculateLevel"));
        assertTrue(progressionUseCase.contains("progressionCommands.purchaseUpgrade"));
        assertTrue(progressionUseCase.contains("progressionCommands.completeMission"));
        assertFalse(progressionUseCase.contains("coreApiClient.islandBlockDetails"));
        assertFalse(progressionUseCase.contains("PaperGuiViews.islandUpgrades(coreApiClient"));
        assertFalse(progressionUseCase.contains("PaperGuiViews.islandMissions(coreApiClient"));
        assertFalse(progressionUseCase.contains("coreApiClient.recalculateIslandLevel"));
        assertFalse(progressionUseCase.contains("coreApiClient.purchaseIslandUpgrade"));
        assertFalse(progressionUseCase.contains("coreApiClient.completeIslandMission"));
    }

    @Test
    void environmentCommandsAreSeparatedFromCommandBackend() throws Exception {
        String backend = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandCommandBackend.java"));
        String environmentHandler = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandEnvironmentCommandHandler.java"));
        String environmentUseCase = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/application/IslandEnvironmentUseCase.java"));

        assertTrue(backend.contains("private final IslandEnvironmentCommandHandler environmentCommands;"));
        assertTrue(routerSource().contains("environmentCommands.handleCommand(player, subcommand, args)"));
        assertTrue(routerSource().contains("environmentCommands.handleGuiAction(player, action"));
        assertFalse(backend.contains("setIslandBiome("), "biome mutation logic belongs in IslandEnvironmentCommandHandler");
        assertFalse(backend.contains("setIslandLimit("), "limit mutation logic belongs in IslandEnvironmentCommandHandler");
        assertFalse(backend.contains("applyIslandBorder("), "border UI logic belongs in IslandEnvironmentCommandHandler");
        assertTrue(environmentHandler.contains("boolean handleCommand(Player player, String subcommand, String[] args)"));
        assertTrue(environmentHandler.contains("boolean handleGuiAction(Player player, GuiAction action)"));
        assertTrue(environmentHandler.contains("IslandEnvironmentUseCase"));
        assertTrue(environmentHandler.contains("environmentUseCase.setBiomeAction"));
        assertTrue(environmentHandler.contains("environmentUseCase.setLimitAction"));
        assertTrue(environmentHandler.contains("environmentUseCase.setFlagAction"));
        assertTrue(environmentHandler.contains("environmentUseCase.islandInfoView"));
        assertTrue(environmentHandler.contains("environmentUseCase.limitViews"));
        assertTrue(environmentHandler.contains("IslandBorderRuntimePolicy.refreshRequiredForLimit"), "size and border limit events must refresh online player borders");
        assertTrue(environmentHandler.contains("IslandBorderRuntimePolicy.refreshRequiredForFlag"), "border flag events must refresh every online player on the island");
        assertTrue(environmentHandler.contains("PaperSchedulers.run(plugin"), "async Core events must return to the Paper scheduler before reading online players or locations");
        assertTrue(environmentHandler.contains("pendingBorderRefreshes"), "bursty Core events must deduplicate per-island border refreshes");
        assertFalse(environmentHandler.contains("coreApiClient.setIslandBiomeResult"));
        assertFalse(environmentHandler.contains("coreApiClient.setIslandLimit"));
        assertFalse(environmentHandler.contains("coreApiClient.setIslandFlagResult"));
        assertFalse(environmentUseCase.contains("public CompletableFuture<String> islandBiome("), "biome read usecase must expose typed values instead of raw JSON");
        assertFalse(environmentUseCase.contains("public CompletableFuture<String> islandInfo("), "island info usecase must expose typed views instead of raw JSON");
        assertFalse(environmentUseCase.contains("public CompletableFuture<String> listFlags("), "flag list usecase must expose typed values instead of raw JSON");
        assertFalse(environmentUseCase.contains("public CompletableFuture<String> listLimits("), "limit list usecase must expose typed views instead of raw JSON");
        assertFalse(environmentUseCase.contains("public CompletableFuture<String> setBiome("), "biome mutation usecase must expose typed actions instead of raw JSON");
        assertFalse(environmentUseCase.contains("public CompletableFuture<String> setLimit("), "limit mutation usecase must expose typed actions instead of raw JSON");
        assertFalse(environmentUseCase.contains("public CompletableFuture<String> setFlag("), "flag mutation usecase must expose typed actions instead of raw JSON");
        assertTrue(environmentUseCase.contains("IslandEnvironmentCommandClient environmentCommands"));
        assertTrue(environmentUseCase.contains("environmentCommands.setBiome"));
        assertTrue(environmentUseCase.contains("environmentCommands.setLimit"));
        assertTrue(environmentUseCase.contains("environmentCommands.setFlag"));
        assertFalse(environmentUseCase.contains("coreApiClient.setIslandBiomeResult"));
        assertFalse(environmentUseCase.contains("coreApiClient.setIslandLimit"));
        assertFalse(environmentUseCase.contains("coreApiClient.setIslandFlagResult"));
    }

    @Test
    void settingsCommandsAreSeparatedFromCommandBackend() throws Exception {
        String backend = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandCommandBackend.java"));
        String settingsHandler = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandSettingsCommandHandler.java"));
        String settingsUseCase = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/application/IslandSettingsUseCase.java"));

        assertTrue(backend.contains("private final IslandSettingsCommandHandler settingsCommands;"));
        assertTrue(routerSource().contains("settingsCommands.handleCommand(player, subcommand, args)"));
        assertTrue(routerSource().contains("settingsCommands.handleGuiAction(player, action"));
        assertFalse(backend.contains("setIslandPublicAccess("), "public access logic belongs in IslandSettingsCommandHandler");
        assertFalse(backend.contains("setIslandFlag("), "flag mutation logic belongs in IslandSettingsCommandHandler");
        assertFalse(backend.contains("setIslandName("), "name mutation logic belongs in IslandSettingsCommandHandler");
        assertTrue(settingsHandler.contains("boolean handleCommand(Player player, String subcommand, String[] args)"));
        assertTrue(settingsHandler.contains("boolean handleGuiAction(Player player, GuiAction action, boolean rightClick)"));
        assertTrue(settingsHandler.contains("IslandSettingsUseCase"));
        assertTrue(settingsHandler.contains("settingsUseCase.setPublicAccessAction"));
        assertTrue(settingsHandler.contains("settingsUseCase.setFlagAction"));
        assertTrue(settingsHandler.contains("settingsUseCase.setNameAction"));
        assertFalse(settingsHandler.contains("coreApiClient.setIslandPublicAccessResult"));
        assertFalse(settingsHandler.contains("coreApiClient.setIslandFlagResult"));
        assertFalse(settingsHandler.contains("coreApiClient.setIslandNameResult"));
        assertFalse(settingsUseCase.contains("public CompletableFuture<String> setPublicAccess("), "public access usecase must expose typed actions instead of raw JSON");
        assertFalse(settingsUseCase.contains("public CompletableFuture<String> setLocked("), "locked usecase must expose typed actions instead of raw JSON");
        assertFalse(settingsUseCase.contains("public CompletableFuture<String> setName("), "name usecase must expose typed actions instead of raw JSON");
        assertFalse(settingsUseCase.contains("public CompletableFuture<String> setFlag("), "flag usecase must expose typed actions instead of raw JSON");
        assertFalse(settingsUseCase.contains("public CompletableFuture<String> listFlags("), "flag list usecase must expose typed values instead of raw JSON");
        assertTrue(settingsUseCase.contains("IslandSettingsCommandClient settingsCommands"));
        assertTrue(settingsUseCase.contains("settingsCommands.setPublicAccess"));
        assertTrue(settingsUseCase.contains("settingsCommands.setFlag"));
        assertTrue(settingsUseCase.contains("settingsCommands.setName"));
        assertFalse(settingsUseCase.contains("coreApiClient.setIslandPublicAccessResult"));
        assertFalse(settingsUseCase.contains("coreApiClient.setIslandFlagResult"));
        assertFalse(settingsUseCase.contains("coreApiClient.setIslandNameResult"));
        assertFalse(settingsUseCase.contains("coreApiClient.setIslandLockedResult"));
    }

    @Test
    void socialProfileSettingsMapSs2CommandsToTypedCoreFlags() throws Exception {
        String registry = Files.readString(Path.of("../cloudislands-protocol/src/main/java/kr/lunaf/cloudislands/protocol/command/IslandPlayerCommandRegistry.java"));
        String permissions = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandCommandPermission.java"));
        String settingsHandler = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandSettingsCommandHandler.java"));
        String completer = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandCommandTabCompleter.java"));
        String flags = Files.readString(Path.of("../cloudislands-api/src/main/java/kr/lunaf/cloudislands/api/model/IslandFlag.java"));
        String coreRoutes = Files.readString(Path.of("../cloudislands-core-service/src/main/java/kr/lunaf/cloudislands/coreservice/http/routes/IslandSettingsRoutes.java"));
        String koMessages = Files.readString(Path.of("src/main/resources/config-v2/ui/messages/ko_kr.yml"));
        String enMessages = Files.readString(Path.of("src/main/resources/config-v2/ui/messages/en_us.yml"));
        String gates = Files.readString(Path.of("../gradle/report-gates.gradle.kts"));

        assertTrue(registry.contains("\"setdiscord\""));
        assertTrue(registry.contains("\"setpaypal\""));
        assertTrue(registry.contains("\"섬 setdiscord <handle|clear>\""));
        assertTrue(registry.contains("\"섬 setpaypal <value|clear>\""));
        assertTrue(permissions.contains("\"setdiscord\"") && permissions.contains("\"setpaypal\""));
        assertTrue(settingsHandler.contains("setSocialFlag(player, IslandFlag.SOCIAL_DISCORD"));
        assertTrue(settingsHandler.contains("setSocialFlag(player, IslandFlag.SOCIAL_PAYPAL"));
        assertTrue(settingsHandler.contains("settingsUseCase.setFlagAction(islandId, player.getUniqueId(), flag, value, runtime::mutate)"));
        assertTrue(settingsHandler.contains("normalizeSocialValue"));
        assertTrue(settingsHandler.contains("lower.equals(\"clear\")"));
        assertTrue(flags.contains("SOCIAL_DISCORD"));
        assertTrue(flags.contains("SOCIAL_PAYPAL"));
        assertTrue(coreRoutes.contains("metadataRepository.setFlagResult(islandId, flag, value)"), "social metadata must persist through the audited idempotent Core island flag path");
        assertTrue(completer.contains("first.equals(\"setdiscord\")"));
        assertTrue(completer.contains("first.equals(\"setpaypal\")"));
        assertTrue(koMessages.contains("social-discord-action-label:"));
        assertTrue(koMessages.contains("social-paypal-action-label:"));
        assertTrue(enMessages.contains("social-discord-action-label:"));
        assertTrue(enMessages.contains("social-paypal-action-label:"));
        assertTrue(gates.contains("permissionParity(\"player\", \"superior.island.setdiscord\", \"cloudislands.island.settings\", \"SUPPORTED_VERIFIED\""));
        assertTrue(gates.contains("permissionParity(\"player\", \"superior.island.setpaypal\", \"cloudislands.island.settings\", \"SUPPORTED_VERIFIED\""));
    }

    @Test
    void homeWarpCommandsAreSeparatedFromCommandBackend() throws Exception {
        String backend = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandCommandBackend.java"));
        String homeWarpHandler = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandHomeWarpCommandHandler.java"));
        String homeWarpUseCase = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/application/IslandHomeWarpUseCase.java"));
        String localTeleports = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandCommandLocalTeleports.java"));

        assertTrue(backend.contains("private final IslandHomeWarpCommandHandler homeWarpCommands;"));
        assertTrue(routerSource().contains("homeWarpCommands.handleCommand(player, subcommand, args)"));
        assertTrue(routerSource().contains("homeWarpCommands.handleGuiAction(player, action"));
        assertFalse(backend.contains("setHome("), "home mutation logic belongs in IslandHomeWarpCommandHandler");
        assertFalse(backend.contains("setWarp("), "warp mutation logic belongs in IslandHomeWarpCommandHandler");
        assertFalse(backend.contains("teleportHome("), "home teleport logic belongs in IslandHomeWarpCommandHandler");
        assertFalse(backend.contains("teleportWarp("), "warp teleport logic belongs in IslandHomeWarpCommandHandler");
        assertFalse(backend.contains("listPublicWarps("), "public warp listing belongs in IslandHomeWarpCommandHandler");
        assertTrue(homeWarpHandler.contains("action instanceof GuiAction.PublicWarpCategory"));
        assertTrue(homeWarpHandler.contains("IslandWarpMenu.openPublic(plugin, coreApiClient, player, runtime.messagesFor(player), publicWarpCategory.category(), publicWarpCategory.query())"));
        assertTrue(homeWarpHandler.contains("boolean handleCommand(Player player, String subcommand, String[] args)"));
        assertTrue(homeWarpHandler.contains("boolean handleGuiAction(Player player, GuiAction action, GuiClick click)"));
        assertTrue(homeWarpHandler.contains("IslandHomeWarpUseCase"));
        assertTrue(homeWarpHandler.contains("homeWarpUseCase.setHomeAction"));
        assertTrue(homeWarpHandler.contains("homeWarpUseCase.setWarpAction"));
        assertTrue(homeWarpHandler.contains("setWarp(player, args[1], args.length > 2 ? args[2] : \"\")"));
        assertTrue(homeWarpHandler.contains("homeWarpUseCase.setWarpAction(islandId, player.getUniqueId(), name, runtime.location(player.getLocation()), false, category, runtime::mutate)"));
        assertTrue(homeWarpHandler.contains("homeWarpUseCase.deleteWarpAction"));
        assertTrue(homeWarpHandler.contains("homeWarpUseCase.homeViews"));
        assertTrue(homeWarpHandler.contains("Optional<UUID> currentIsland = runtime.currentIsland(player)"), "single-Paper home must detect lobby context without emitting a false island-required error");
        assertTrue(homeWarpHandler.contains("runtime.routeHome(player, name)"), "single-Paper home outside an island must use the Core selected-island route");
        assertTrue(homeWarpHandler.contains("homeWarpUseCase.warpViews"));
        assertTrue(homeWarpHandler.contains("homeWarpUseCase.publicWarpViews"));
        assertTrue(homeWarpHandler.contains("homePoint(homes, name)"), "home teleport must not fall back to the player's current world");
        assertTrue(homeWarpHandler.contains("warpPoint(warps, name)"), "warp teleport must not fall back to the player's current world");
        assertTrue(homeWarpHandler.contains("new Point(home.worldName(), home.x(), home.y(), home.z(), home.yaw(), home.pitch(), false)"), "home teleport point must preserve world and facing");
        assertTrue(homeWarpHandler.contains("new Point(warp.worldName(), warp.x(), warp.y(), warp.z(), warp.yaw(), warp.pitch(), warp.publicAccess())"), "warp teleport point must preserve world and facing");
        assertTrue(localTeleports.contains("if (point.worldName().isBlank())"), "local teleport must reject missing stored worlds");
        assertTrue(localTeleports.contains("protection.region(islandId)"), "local teleport coordinates must resolve against the target island rather than the player's current location");
        assertTrue(localTeleports.contains("worlds.safeDestination(requested, region.get())"), "local home and warp movement must reject blocked or hazardous destinations");
        assertFalse(localTeleports.contains("point.worldName().isBlank() ?"), "local teleport must not substitute the player's current world for stored home/warp worlds");
        assertFalse(homeWarpHandler.contains("coreApiClient.setIslandHomeResult"));
        assertFalse(homeWarpHandler.contains("coreApiClient.setIslandWarpResult"));
        assertFalse(homeWarpHandler.contains("coreApiClient.deleteIslandWarpResult"));
        assertFalse(homeWarpHandler.contains("coreApiClient.listPublicWarps"));
        assertFalse(homeWarpUseCase.contains("public CompletableFuture<String> setHome("), "home mutation usecase must expose typed actions instead of raw JSON");
        assertFalse(homeWarpUseCase.contains("public CompletableFuture<String> setWarp("), "warp mutation usecase must expose typed actions instead of raw JSON");
        assertFalse(homeWarpUseCase.contains("public CompletableFuture<String> listHomes("), "home list usecase must expose typed views instead of raw JSON");
        assertFalse(homeWarpUseCase.contains("public CompletableFuture<String> listWarps("), "warp list usecase must expose typed views instead of raw JSON");
        assertFalse(homeWarpUseCase.contains("public CompletableFuture<String> islandInfo("), "island info usecase must expose typed views instead of raw JSON");
        assertFalse(homeWarpUseCase.contains("public CompletableFuture<String> deleteWarp("), "warp delete usecase must expose typed actions instead of raw JSON");
        assertFalse(homeWarpUseCase.contains("public CompletableFuture<String> setWarpPublicAccess("), "warp access usecase must expose typed actions instead of raw JSON");
        assertFalse(homeWarpUseCase.contains("public CompletableFuture<String> listPublicWarps("), "public warp list usecase must expose typed views instead of raw JSON");
        assertTrue(homeWarpUseCase.contains("HomeWarpCommandClient homeWarpCommands"));
        assertTrue(homeWarpUseCase.contains("homeWarpCommands.setHome"));
        assertTrue(homeWarpUseCase.contains("homeWarpCommands.setWarp"));
        assertTrue(homeWarpUseCase.contains("homeWarpCommands.deleteWarp"));
        assertFalse(homeWarpUseCase.contains("coreApiClient.setIslandHomeResult"));
        assertFalse(homeWarpUseCase.contains("coreApiClient.setIslandWarpResult"));
        assertFalse(homeWarpUseCase.contains("coreApiClient.deleteIslandWarpResult"));
        assertFalse(homeWarpUseCase.contains("coreApiClient.setIslandWarpPublicAccessResult"));
        assertTrue(homeWarpUseCase.contains("homeWarpQueries.publicWarps"));
        assertFalse(homeWarpUseCase.contains("PaperGuiViews.publicWarps(coreApiClient"));
    }

    @Test
    void visitReviewCommandsAreSeparatedFromCommandBackend() throws Exception {
        String backend = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandCommandBackend.java"));
        String visitReviewHandler = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandVisitReviewCommandHandler.java"));
        String navigationUseCase = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/application/IslandNavigationUseCase.java"));

        assertTrue(backend.contains("private final IslandVisitReviewCommandHandler visitReviewCommands;"));
        assertTrue(routerSource().contains("visitReviewCommands.handleCommand(player, subcommand, args)"));
        assertTrue(routerSource().contains("visitReviewCommands.handleGuiAction(player, action"));
        assertFalse(backend.contains("routeVisitTarget("), "visit target resolution belongs in IslandVisitReviewCommandHandler");
        assertFalse(backend.contains("routeRandomVisit("), "random visit routing belongs in IslandVisitReviewCommandHandler");
        assertFalse(backend.contains("listPublicIslands("), "public island listing belongs in IslandVisitReviewCommandHandler");
        assertFalse(backend.contains("listIslandReviews("), "review listing belongs in IslandVisitReviewCommandHandler");
        assertFalse(backend.contains("rateIslandReview("), "review mutation logic belongs in IslandVisitReviewCommandHandler");
        assertFalse(backend.contains("deleteIslandReview("), "review delete logic belongs in IslandVisitReviewCommandHandler");
        assertTrue(visitReviewHandler.contains("boolean handleCommand(Player player, String subcommand, String[] args)"));
        assertTrue(visitReviewHandler.contains("boolean handleGuiAction(Player player, GuiAction action)"));
        assertTrue(visitReviewHandler.contains("IslandNavigationUseCase"));
        assertTrue(visitReviewHandler.contains("navigationUseCase.resolveVisitTarget"));
        assertTrue(visitReviewHandler.contains("navigationUseCase.publicIslandViews"));
        assertTrue(visitReviewHandler.contains("navigationUseCase.reviewViews"));
        assertTrue(visitReviewHandler.contains("navigationUseCase.visitorStats"));
        assertTrue(visitReviewHandler.contains("IslandVisitorStatsMenu.open"));
        assertTrue(visitReviewHandler.contains("if (subcommand.equals(\"visitors\"))"), "canonical SS2 visitors command must have a dedicated current-visitor branch");
        assertTrue(visitReviewHandler.contains("coreApiClient.islands().memberSnapshots(islandId)"), "current visitors must distinguish permanent team roles from guests using typed Core membership data");
        assertTrue(visitReviewHandler.contains("PaperSchedulers.run(plugin"), "Bukkit online-player and island lookups must return to the main thread after the Core query");
        assertTrue(visitReviewHandler.contains("activeViewer::canSee"), "current visitor output must not reveal vanished players hidden from the viewer");
        assertTrue(visitReviewHandler.contains("CurrentIslandVisitorPolicy.visitor"), "current visitor classification must keep temporary co-ops distinct from permanent members");
        assertTrue(visitReviewHandler.indexOf("if (subcommand.equals(\"visitors\"))") < visitReviewHandler.indexOf("if (subcommand.equals(\"visitor-stats\")"), "visitors must list current guests before historical-stat aliases are evaluated");
        assertTrue(visitReviewHandler.contains("navigationUseCase.setReviewAction"));
        assertTrue(visitReviewHandler.contains("navigationUseCase.deleteReviewAction"));
        assertFalse(visitReviewHandler.contains("coreApiClient.createVisitTicket"));
        assertFalse(visitReviewHandler.contains("coreApiClient.createRandomVisitTicket"));
        assertFalse(visitReviewHandler.contains("coreApiClient.listPublicIslands"));
        assertFalse(visitReviewHandler.contains("coreApiClient.setIslandReview"));
        assertTrue(navigationUseCase.contains("navigationCommands.createVisitTicket"));
        assertTrue(navigationUseCase.contains("navigationCommands.createRandomVisitTicket"));
        assertFalse(navigationUseCase.contains("coreApiClient.createVisitTicket"));
        assertFalse(navigationUseCase.contains("coreApiClient.createRandomVisitTicket"));
        assertTrue(navigationUseCase.contains("NavigationQueryClient navigationQueries"), "navigation reads must stay behind a typed core-client query boundary");
        assertTrue(navigationUseCase.contains("navigationQueries.publicIslands"), "public island list usecase must read through the typed navigation query client");
        assertTrue(navigationUseCase.contains("NavigationCommandClient navigationCommands"), "navigation mutations must stay behind a typed core-client command boundary");
        assertTrue(navigationUseCase.contains("navigationCommands.setReview"));
        assertTrue(navigationUseCase.contains("navigationCommands.deleteReview"));
        assertFalse(navigationUseCase.contains("coreApiClient.setIslandReview"));
        assertFalse(navigationUseCase.contains("PaperGuiViews.publicIslands(coreApiClient"), "public island list usecase must not parse raw Core bodies in Paper");
        assertFalse(navigationUseCase.contains("public CompletableFuture<String> listPublicIslands("), "public island list usecase must expose typed views instead of raw JSON");
        assertFalse(navigationUseCase.contains("public CompletableFuture<String> listReviews("), "review list usecase must expose typed views instead of raw JSON");
        assertFalse(navigationUseCase.contains("public CompletableFuture<String> setReview("), "review mutation usecase must expose typed actions instead of raw JSON");
    }

    @Test
    void teamChatModeRoutesNormalChatThroughCoreTeamChannel() throws Exception {
        String handler = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandChatLogCommandHandler.java"));
        String listener = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/session/PaperChatListener.java"));
        String bootstrap = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/PaperPluginBootstrap.java"));

        assertTrue(handler.contains("teamChatModes.toggle(player.getUniqueId())"), "no-argument teamchat must toggle mode like canonical SS2");
        assertTrue(handler.contains("teamChatModes.set(player.getUniqueId(), true)") && handler.contains("teamChatModes.set(player.getUniqueId(), false)"), "explicit on/off modes must be deterministic");
        assertTrue(listener.contains("event.setCancelled(true)"), "team-mode chat must not leak to global chat viewers");
        assertTrue(listener.contains("@EventHandler(priority = EventPriority.LOWEST)"), "team chat must be cancelled before ordinary chat integrations observe it");
        assertTrue(listener.contains("event.viewers().clear()"), "team chat must remove the global audience even if another plugin re-enables the event");
        assertTrue(listener.contains("@EventHandler(priority = EventPriority.HIGHEST)"), "team chat isolation must be reasserted after intermediate plugins");
        assertTrue(listener.contains("event.renderer((_source, _sourceDisplayName, _message, _viewer) -> Component.empty())"), "team chat must fail closed to an empty renderer");
        assertTrue(listener.contains("PaperSchedulers.run(plugin, () -> sendTeamChat"), "async chat must return to the Paper scheduler before location access");
        assertTrue(listener.contains("communicationCommands().sendChat(islandId, playerUuid, \"TEAM\""), "team-mode messages must use the typed Core team-chat channel with a scheduler-captured UUID");
        assertTrue(listener.contains("teamChatModes.clear(event.getPlayer().getUniqueId())"), "disconnects must clear local chat mode state");
        assertTrue(bootstrap.contains("plugin.teamChatModes"), "commands and chat listener must share one runtime mode registry");
    }

    @Test
    void localChatModeRoutesNormalChatThroughCoreIslandChannel() throws Exception {
        String handler = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandChatLogCommandHandler.java"));
        String listener = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/session/PaperChatListener.java"));

        assertTrue(handler.contains("teamChatModes.toggleIsland(player.getUniqueId())"));
        assertTrue(listener.contains("teamChatModes.islandEnabled(event.getPlayer().getUniqueId())"));
        assertTrue(listener.contains("PaperSchedulers.run(plugin, () -> sendLocalChat"), "async local chat must return to the Paper scheduler before location access");
        assertTrue(listener.contains("communicationCommands().sendChat(islandId, playerUuid, \"ISLAND\""));
        assertTrue(listener.contains("teamChatEnabled(event) || islandChatEnabled(event)"), "both private modes must be re-isolated at HIGHEST");
    }

    @Test
    void asynchronousPlayerActionsCaptureIdentityBeforeCoreCallbacks() throws Exception {
        String permissions = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandPermissionCommandHandler.java"));
        String membership = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandMembershipCommandHandler.java"));
        String overview = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandOverviewCommandHandler.java"));
        String chat = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/session/PaperChatListener.java"));

        assertTrue(permissions.contains("saveStagedChangesSequentially(islandId, actorUuid, changes)"));
        assertFalse(permissions.contains("setPermissionOverrideAction(islandId, player.getUniqueId()"), "permission resolution callbacks must use a command-thread identity snapshot");
        assertFalse(membership.contains("removeMemberAction(islandId, player.getUniqueId()"), "membership callbacks must not read Bukkit Player identity");
        assertFalse(membership.contains("setRoleAction(islandId, player.getUniqueId()"), "role callbacks must not read Bukkit Player identity");
        assertFalse(membership.contains("transferOwnershipAction(islandId, player.getUniqueId()"), "ownership callbacks must not read Bukkit Player identity");
        assertFalse(membership.contains("banVisitorAction(islandId, player.getUniqueId()"), "visitor callbacks must not read Bukkit Player identity");
        assertTrue(membership.contains("resolveInviteTarget(UUID actorUuid, String target)"), "invite resolution must carry immutable actor identity");
        assertTrue(overview.contains("selectPrimaryIsland(actorUuid, islandId)"), "primary-island selection must use the pre-resolved actor identity");
        assertTrue(chat.contains("deliverChatFailure(playerUuid"), "Core chat failures must return to the Paper scheduler by UUID");
        assertTrue(chat.contains("plugin.getServer().getPlayer(playerUuid)"), "chat failure delivery must re-resolve the current online Player");
        assertFalse(chat.contains("exceptionally(error -> {\n                    player.sendMessage"), "Core callbacks must not send through a captured Player");
    }

    @Test
    void lifecycleCommandsAreSeparatedFromCommandBackend() throws Exception {
        String backend = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandCommandBackend.java"));
        String lifecycleHandler = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandLifecycleCommandHandler.java"));
        String creationUseCase = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/application/IslandCreationUseCase.java"));

        assertTrue(backend.contains("private final IslandLifecycleCommandHandler lifecycleCommands;"));
        assertTrue(routerSource().contains("lifecycleCommands.handleCommand(player, subcommand, args)"));
        assertTrue(routerSource().contains("lifecycleCommands.handleGuiAction(player, action"));
        assertFalse(backend.contains("createIsland("), "island creation logic belongs in IslandLifecycleCommandHandler");
        assertFalse(backend.contains("deleteIsland("), "island deletion logic belongs in IslandLifecycleCommandHandler");
        assertFalse(backend.contains("resetIsland("), "island reset logic belongs in IslandLifecycleCommandHandler");
        assertFalse(backend.contains("dangerConfirmed("), "danger confirmation logic belongs in IslandLifecycleCommandHandler");
        assertTrue(lifecycleHandler.contains("boolean handleCommand(Player player, String subcommand, String[] args)"));
        assertTrue(lifecycleHandler.contains("boolean handleGuiAction(Player player, GuiAction action, GuiClick click)"));
        assertTrue(lifecycleHandler.contains("IslandCreationUseCase"));
        assertTrue(lifecycleHandler.contains("creationUseCase.create("));
        assertTrue(lifecycleHandler.contains("creationUseCase.resetAction("));
        assertFalse(lifecycleHandler.contains("coreApiClient.createIsland"));
        assertFalse(lifecycleHandler.contains("coreApiClient.deleteIsland"));
        assertFalse(lifecycleHandler.contains("coreApiClient.resetIslandResult"));
        assertFalse(creationUseCase.contains("public CompletableFuture<String> reset("), "reset usecase must expose typed actions instead of raw JSON");
        assertTrue(creationUseCase.contains("lifecycleCommands.createIsland"));
        assertTrue(creationUseCase.contains("lifecycleCommands.deleteIsland"));
        assertFalse(creationUseCase.contains("coreApiClient.createIsland"));
        assertFalse(creationUseCase.contains("coreApiClient.deleteIsland"));
        assertTrue(creationUseCase.contains("IslandLifecycleCommandClient lifecycleCommands"), "reset mutation must stay behind a typed core-client command boundary");
        assertTrue(creationUseCase.contains("lifecycleCommands.resetIsland"));
        assertFalse(creationUseCase.contains("coreApiClient.resetIslandResult"));
        assertTrue(lifecycleHandler.contains("DangerousGuiActionPolicy.confirmed"));
        assertTrue(lifecycleHandler.contains("resetConfirm.operation()"), "danger reset confirmation must use typed action fields");
        assertTrue(lifecycleHandler.contains("deleteConfirm.operation()"), "danger delete confirmation must use typed action fields");
        assertFalse(lifecycleHandler.contains("resetConfirm.data()"), "danger reset confirmation must not re-read raw action maps");
        assertFalse(lifecycleHandler.contains("deleteConfirm.data()"), "danger delete confirmation must not re-read raw action maps");
    }

    @Test
    void overviewCommandsAreSeparatedFromCommandBackend() throws Exception {
        String backend = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandCommandBackend.java"));
        String overviewHandler = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandOverviewCommandHandler.java"));

        assertTrue(backend.contains("private final IslandOverviewCommandHandler overviewCommands;"));
        assertTrue(routerSource().contains("overviewCommands.handleCommand(player, subcommand, args)"));
        assertTrue(routerSource().contains("overviewCommands.handleGuiAction(player, action"));
        assertFalse(backend.contains("openIslandInfoMenu("), "info menu routing belongs in IslandOverviewCommandHandler");
        assertFalse(backend.contains("IslandMyIslandsMenu.open("), "my islands menu routing belongs in IslandOverviewCommandHandler");
        assertTrue(overviewHandler.contains("boolean handleCommand(Player player, String subcommand, String[] args)"));
        assertTrue(overviewHandler.contains("boolean handleGuiAction(Player player, GuiAction action)"));
        assertTrue(overviewHandler.contains("IslandInfoMenu.open"));
        assertTrue(overviewHandler.contains("IslandMyIslandsMenu.open"));
        assertTrue(overviewHandler.contains("GuiSession session = GuiSessions.begin(player, \"island.info-target\")"), "target lookup must reserve a GUI session before the asynchronous Core request");
        assertTrue(overviewHandler.contains("thenAccept(islandId -> GuiSessions.runIfCurrent(plugin, player, session"), "resolved target info must return to the Paper scheduler and discard stale responses");
        assertTrue(overviewHandler.contains("if (player.isOnline())"), "resolved target info must not open an inventory after disconnect");
        assertTrue(overviewHandler.contains("GuiStateMenus.openError(plugin, player, session"), "target lookup failures must replace the matching loading session with an actionable error state");
    }

    @Test
    void membershipCommandsRouteOutsideCommandBackend() throws Exception {
        String backend = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandCommandBackend.java"));
        String membershipHandler = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandMembershipCommandHandler.java"));
        String memberUseCase = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/application/MemberManagementUseCase.java"));
        String permissionHandler = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandPermissionCommandHandler.java"));
        String permissionUseCase = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/application/PermissionManagementUseCase.java"));
        String permissionMenu = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/gui/IslandPermissionMenu.java"));
        String paperGuiViews = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/application/view/PaperGuiViews.java"));

        assertTrue(backend.contains("private final IslandMembershipCommandHandler membershipCommands;"));
        assertTrue(routerSource().contains("membershipCommands.handleCommand(player, subcommand, args)"));
        assertTrue(routerSource().contains("membershipCommands.handleGuiAction(player, action"));
        assertFalse(backend.contains("subcommand.equals(\"members\")"), "membership command routing belongs in IslandMembershipCommandHandler");
        assertFalse(backend.contains("case \"island.members.open\""), "membership GUI routing belongs in IslandMembershipCommandHandler");
        assertFalse(backend.contains("case \"island.permissions.open\""), "permission GUI routing belongs in IslandMembershipCommandHandler");
        assertTrue(membershipHandler.contains("boolean handleCommand(Player player, String subcommand, String[] args)"));
        assertTrue(membershipHandler.contains("boolean handleGuiAction(Player player, GuiAction action, GuiClick click)"));
        assertTrue(membershipHandler.contains("subcommand.equals(\"members\")"));
        assertTrue(membershipHandler.contains("case PERMISSIONS_OPEN"));
        assertTrue(membershipHandler.contains("private void listPendingInvites(Player player)"), "invite list execution belongs in IslandMembershipCommandHandler");
        assertTrue(membershipHandler.contains("private void inviteIslandMember(Player player, String target)"), "invite creation execution belongs in IslandMembershipCommandHandler");
        assertTrue(membershipHandler.contains("private void sendIslandInvite(Player player, UUID islandId, UUID actorUuid, UUID targetUuid)"), "invite creation mutation belongs in IslandMembershipCommandHandler");
        assertTrue(membershipHandler.contains("private void acceptIslandInviteTarget(Player player, String target)"), "invite accept execution belongs in IslandMembershipCommandHandler");
        assertTrue(membershipHandler.contains("private void declineIslandInviteTarget(Player player, String target)"), "invite decline execution belongs in IslandMembershipCommandHandler");
        assertTrue(membershipHandler.contains("private void listIslandMembers(Player player)"), "member list execution belongs in IslandMembershipCommandHandler");
        assertTrue(membershipHandler.contains("private void listIslandBans(Player player)"), "ban list execution belongs in IslandMembershipCommandHandler");
        assertTrue(membershipHandler.contains("private void removeIslandMember(Player player, String target)"), "member removal execution belongs in IslandMembershipCommandHandler");
        assertFalse(membershipHandler.contains("setIslandMemberRole(player, args[1], \"MEMBER\", message(\"member-role-untrust-action-label\""), "untrust must remove co-op access instead of turning a co-op into a permanent member");
        assertTrue(membershipHandler.contains("private void setIslandMemberRole(Player player, String target, String roleKey, String successMessage)"), "member role execution belongs in IslandMembershipCommandHandler");
        assertTrue(membershipHandler.contains("permissionExceptionCommand(memberDetail.playerUuid().toString()"), "member detail must expose the individual permission exception entrypoint");
        assertTrue(membershipHandler.contains("IslandPermission.BUILD.name()"), "member detail must show a concrete build permission exception example");
        assertTrue(membershipHandler.contains("IslandPermission.OPEN_CONTAINER.name()"), "member detail must show a concrete container permission exception example");
        assertTrue(membershipHandler.contains("static String permissionExceptionCommand"), "permission exception command formatting must be testable outside the GUI branch");
        assertTrue(membershipHandler.contains("private void trustIslandMemberTemporary(Player player, String target, String duration)"), "temporary trust execution belongs in IslandMembershipCommandHandler");
        assertTrue(membershipHandler.contains("/섬 협동 <플레이어> <30m|2h|1d>"), "member help must surface temporary co-op trust syntax");
        assertTrue(membershipHandler.contains("private void transferIslandOwnership(Player player, String target)"), "ownership transfer execution belongs in IslandMembershipCommandHandler");
        assertTrue(membershipHandler.contains("private void banIslandVisitor(Player player, String target, String reason)"), "visitor ban execution belongs in IslandMembershipCommandHandler");
        assertTrue(membershipHandler.contains("private void pardonIslandVisitor(Player player, String target)"), "visitor pardon execution belongs in IslandMembershipCommandHandler");
        assertTrue(membershipHandler.contains("private void kickIslandVisitor(Player player, String target)"), "visitor kick execution belongs in IslandMembershipCommandHandler");
        assertFalse(backend.contains("private void listPendingInvites(Player player)"), "invite list execution must not stay in IslandCommandBackend");
        assertFalse(backend.contains("private void inviteIslandMember(Player player, String target)"), "invite creation execution must not stay in IslandCommandBackend");
        assertFalse(backend.contains("private void sendIslandInvite(Player player, UUID islandId, UUID targetUuid)"), "invite creation mutation must not stay in IslandCommandBackend");
        assertFalse(backend.contains("private void acceptIslandInviteTarget(Player player, String target)"), "invite accept execution must not stay in IslandCommandBackend");
        assertFalse(backend.contains("private void declineIslandInviteTarget(Player player, String target)"), "invite decline execution must not stay in IslandCommandBackend");
        assertFalse(backend.contains("private void listIslandMembers(Player player)"), "member list execution must not stay in IslandCommandBackend");
        assertFalse(backend.contains("private void listIslandBans(Player player)"), "ban list execution must not stay in IslandCommandBackend");
        assertFalse(backend.contains("private final Plugin plugin;"), "IslandCommandBackend must not retain plugin once handlers are wired");
        assertFalse(backend.contains("private final CoreApiClient coreApiClient;"), "IslandCommandBackend must not retain Core client once handlers are wired");
        assertFalse(backend.contains("private final ProtectionController protection;"), "IslandCommandBackend must not retain protection once handlers are wired");
        assertFalse(backend.contains("private final IslandLevelScanService levelScanService;"), "IslandCommandBackend must not retain level scanner once handlers are wired");
        assertFalse(backend.contains("private void openIslandMemberMenu(Player player)"), "member menu presentation wrapper must not stay in IslandCommandBackend");
        assertFalse(backend.contains("private void openIslandBanMenu(Player player)"), "ban menu presentation wrapper must not stay in IslandCommandBackend");
        assertFalse(backend.contains("private boolean moveVisitorToFallback("), "visitor fallback presentation wrapper must not stay in IslandCommandBackend");
        assertFalse(backend.contains("private void removeIslandMember(Player player, String target)"), "member removal execution must not stay in IslandCommandBackend");
        assertFalse(backend.contains("private void setIslandMemberRole(Player player, String target, String roleKey, String successMessage)"), "member role execution must not stay in IslandCommandBackend");
        assertFalse(backend.contains("private void trustIslandMemberTemporary(Player player, String target, String duration)"), "temporary trust execution must not stay in IslandCommandBackend");
        assertFalse(backend.contains("private void transferIslandOwnership(Player player, String target)"), "ownership transfer execution must not stay in IslandCommandBackend");
        assertFalse(backend.contains("private void banIslandVisitor(Player player, String target, String reason)"), "visitor ban execution must not stay in IslandCommandBackend");
        assertFalse(backend.contains("private void pardonIslandVisitor(Player player, String target)"), "visitor pardon execution must not stay in IslandCommandBackend");
        assertFalse(backend.contains("private void kickIslandVisitor(Player player, String target)"), "visitor kick execution must not stay in IslandCommandBackend");
        assertFalse(memberUseCase.contains("public CompletableFuture<String> removeMember("), "member removal usecase must expose typed actions instead of raw JSON");
        assertFalse(memberUseCase.contains("public CompletableFuture<String> playerInfoByName("), "player lookup usecase must expose typed UUID lookup instead of raw JSON");
        assertFalse(memberUseCase.contains("public CompletableFuture<String> islandInfoByName("), "island lookup usecase must expose typed invite resolution instead of raw JSON");
        assertFalse(memberUseCase.contains("public CompletableFuture<String> createInvite("), "invite create usecase must expose typed views instead of raw JSON");
        assertFalse(memberUseCase.contains("public CompletableFuture<String> acceptInvite("), "invite accept usecase must expose typed actions instead of raw JSON");
        assertFalse(memberUseCase.contains("public CompletableFuture<String> declineInvite("), "invite decline usecase must expose typed actions instead of raw JSON");
        assertFalse(memberUseCase.contains("public CompletableFuture<String> setRole("), "member role usecase must expose typed actions instead of raw JSON");
        assertFalse(memberUseCase.contains("public CompletableFuture<String> trustTemporarily("), "temporary trust usecase must expose typed actions instead of raw JSON");
        assertTrue(memberUseCase.contains("MemberQueryClient memberQueries"), "member reads must stay behind a typed core-client query boundary");
        assertTrue(memberUseCase.contains("MemberCommandClient memberCommands"), "member mutations must stay behind a typed core-client command boundary");
        assertTrue(memberUseCase.contains("memberQueries.playerProfileByName"));
        assertTrue(memberUseCase.contains("memberQueries.pendingInvites"));
        assertTrue(memberUseCase.contains("memberQueries.bans"));
        assertTrue(memberUseCase.contains("memberCommands.removeMember"));
        assertTrue(memberUseCase.contains("memberCommands.createInvite"));
        assertTrue(memberUseCase.contains("memberCommands.acceptInvite"));
        assertTrue(memberUseCase.contains("memberCommands.setRole"));
        assertFalse(memberUseCase.contains("coreApiClient.playerInfoByName("));
        assertFalse(memberUseCase.contains("coreApiClient.listPendingInvites("));
        assertFalse(memberUseCase.contains("coreApiClient.listIslandBans("));
        assertFalse(memberUseCase.contains("coreApiClient.removeIslandMemberResult"));
        assertFalse(memberUseCase.contains("coreApiClient.createIslandInvite"));
        assertFalse(memberUseCase.contains("coreApiClient.acceptIslandInviteResult"));
        assertFalse(memberUseCase.contains("coreApiClient.declineIslandInviteResult"));
        assertFalse(memberUseCase.contains("coreApiClient.setIslandMemberResult"));
        assertFalse(memberUseCase.contains("public CompletableFuture<String> transferOwnership("), "ownership transfer usecase must expose typed actions instead of raw JSON");
        assertFalse(memberUseCase.contains("public CompletableFuture<String> banVisitor("), "visitor ban usecase must expose typed actions instead of raw JSON");
        assertFalse(memberUseCase.contains("public CompletableFuture<String> pardonVisitor("), "visitor pardon usecase must expose typed actions instead of raw JSON");
        assertFalse(memberUseCase.contains("public CompletableFuture<String> kickVisitor("), "visitor kick usecase must expose typed actions instead of raw JSON");
        assertTrue(permissionHandler.contains("permissionUseCase.listPermissionViews"));
        assertTrue(permissionHandler.contains("permissionUseCase.listRoleViews"));
        assertTrue(permissionHandler.contains("permissionUseCase.saveSequentiallyTyped"));
        assertTrue(permissionHandler.contains("permissionUseCase.upsertRoleTyped"));
        assertTrue(permissionHandler.contains("permissionUseCase.resetRoleTyped"));
        assertTrue(permissionHandler.contains("permissionUseCase.setPermissionAction"));
        assertTrue(permissionHandler.contains("permissionUseCase.setPermissionOverrideAction"));
        assertFalse(permissionUseCase.contains("public CompletableFuture<String> listPermissions("), "permission list usecase must expose typed views instead of raw JSON");
        assertFalse(permissionUseCase.contains("public CompletableFuture<String> listRoles("), "role list usecase must expose typed views instead of raw JSON");
        assertFalse(permissionUseCase.contains("public CompletableFuture<String> upsertRole("), "role upsert usecase must expose typed results instead of raw JSON");
        assertFalse(permissionUseCase.contains("public CompletableFuture<String> resetRole("), "role reset usecase must expose typed results instead of raw JSON");
        assertFalse(permissionUseCase.contains("public CompletableFuture<String> setPermission("), "permission mutation usecase must expose typed actions instead of raw JSON");
        assertFalse(permissionUseCase.contains("public CompletableFuture<String> setPermissionOverride("), "permission override usecase must expose typed actions instead of raw JSON");
        assertFalse(permissionUseCase.contains("public CompletableFuture<String> saveSequentially("), "permission save usecase must expose typed mutation results instead of raw strings");
        assertTrue(permissionUseCase.contains("PermissionQueryClient permissionQueries"), "permission reads must stay behind a typed core-client query boundary");
        assertTrue(permissionUseCase.contains("PermissionCommandClient permissionCommands"), "permission mutations must stay behind a typed core-client command boundary");
        assertTrue(permissionUseCase.contains("permissionQueries.permissions"));
        assertTrue(permissionUseCase.contains("permissionQueries.roles"));
        assertTrue(permissionUseCase.contains("permissionCommands.setPermission"));
        assertTrue(permissionUseCase.contains("permissionCommands.setPermissionOverride"));
        assertTrue(paperGuiViews.contains("islandPermissionOverrides"), "permission GUI view model must expose player-specific permission exceptions");
        assertTrue(permissionMenu.contains("PaperGuiViews.islandPermissionOverrides"), "permission menu must load player-specific permission exceptions");
        assertTrue(permissionMenu.contains("overrideSummary(overrides)"), "permission menu must surface exception UX instead of hiding overrides behind commands only");
        assertFalse(permissionUseCase.contains("coreApiClient.listIslandPermissions"));
        assertFalse(permissionUseCase.contains("coreApiClient.setIslandPermissionResult"));
        assertFalse(permissionUseCase.contains("coreApiClient.setIslandPermissionOverride"));
        assertFalse(permissionUseCase.contains("CoreGuiViews.islandRoles(coreApiClient"));
    }

    @Test
    void adminNodeCommandsRouteOutsideCommandBackend() throws Exception {
        String backend = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandCommandBackend.java"));
        String adminHandler = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandAdminNodeCommandHandler.java"));
        String adminUseCase = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/application/IslandAdminNodeUseCase.java"));

        assertTrue(backend.contains("private final IslandAdminNodeCommandHandler adminCommands;"));
        assertTrue(routerSource().contains("adminCommands.handleGuiAction(player, action"));
        assertFalse(backend.contains("case \"admin.node.list\""), "admin node GUI routing belongs in IslandAdminNodeCommandHandler");
        assertFalse(backend.contains("openAdminNodeMenu("), "admin menu opening belongs in IslandAdminNodeCommandHandler");
        assertFalse(backend.contains("drainAdminNode("), "admin node mutations belong in IslandAdminNodeCommandHandler");
        assertFalse(backend.contains("shutdownAdminNodeSafely("), "admin danger mutations belong in IslandAdminNodeCommandHandler");
        assertTrue(adminHandler.contains("boolean handleGuiAction(Player player, GuiAction action, GuiClick click)"));
        assertTrue(adminHandler.contains("action instanceof GuiAction.AdminNodeAction"));
        assertTrue(adminHandler.contains("case LIST ->"));
        assertTrue(adminHandler.contains("IslandAdminNodeUseCase"));
        assertTrue(adminHandler.contains("adminNodeUseCase.drainAction"));
        assertTrue(adminHandler.contains("adminNodeUseCase.shutdownSafelyAction"));
        assertFalse(adminHandler.contains("coreApiClient.drainNode"));
        assertFalse(adminHandler.contains("coreApiClient.shutdownNodeSafely"));
        assertFalse(adminUseCase.contains("public CompletableFuture<String> drain("), "admin drain usecase must expose typed actions instead of raw JSON");
        assertFalse(adminUseCase.contains("public CompletableFuture<String> undrain("), "admin undrain usecase must expose typed actions instead of raw JSON");
        assertFalse(adminUseCase.contains("public CompletableFuture<String> sweep("), "admin sweep usecase must expose typed actions instead of raw JSON");
        assertFalse(adminUseCase.contains("public CompletableFuture<String> kickAll("), "admin kick-all usecase must expose typed actions instead of raw JSON");
        assertFalse(adminUseCase.contains("public CompletableFuture<String> shutdownSafely("), "admin shutdown usecase must expose typed actions instead of raw JSON");
        assertTrue(adminUseCase.contains("AdminNodeQueryClient adminNodeQueries"), "admin node reads must stay behind a typed core-client query boundary");
        assertTrue(adminUseCase.contains("adminNodeQueries.nodeIslandsSummary"));
        assertFalse(adminUseCase.contains("coreApiClient.listNodes"));
        assertFalse(adminUseCase.contains("coreApiClient.nodeInfo"));
        assertFalse(adminUseCase.contains("coreApiClient.nodeIslands"));
        assertTrue(adminUseCase.contains("AdminNodeCommandClient adminNodeCommands"));
        assertTrue(adminUseCase.contains("adminNodeCommands.drainNode"));
        assertTrue(adminUseCase.contains("adminNodeCommands.shutdownNodeSafely"));
        assertFalse(adminUseCase.contains("coreApiClient.drainNode"));
        assertFalse(adminUseCase.contains("coreApiClient.shutdownNodeSafely"));
    }

    @Test
    void routingCommandsAreSeparatedFromCommandBackend() throws Exception {
        String backend = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandCommandBackend.java"));
        String homeWarpRuntime = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandHomeWarpRuntimeAdapter.java"));
        String visitReviewRuntime = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandVisitReviewRuntimeAdapter.java"));
        String routingHandler = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandRoutingCommandHandler.java"));
        String routingUseCase = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/application/IslandRoutingUseCase.java"));

        assertTrue(backend.contains("private final IslandRoutingCommandHandler routingCommands;"));
        assertTrue(backend.contains("new IslandHomeWarpRuntimeAdapter(runtimeServices, routingCommands)"));
        assertTrue(backend.contains("new IslandVisitReviewRuntimeAdapter(runtimeServices, routingCommands)"));
        assertTrue(homeWarpRuntime.contains("routingCommands.routeWarp(player, islandId, warpName)"));
        assertTrue(homeWarpRuntime.contains("routingCommands.routeHome(player, homeName)"));
        assertTrue(visitReviewRuntime.contains("routingCommands.routeTicket(player, ticketFuture, failureMessage)"));
        assertTrue(backend.contains("routingCommands.clearRouteLoading(event.getPlayer())"));
        assertFalse(backend.contains("routeBossBars"), "route loading state belongs in IslandRoutingCommandHandler");
        assertFalse(backend.contains("sendPluginMessage(plugin, \"BungeeCord\""), "Bungee plugin messaging belongs in IslandRoutingCommandHandler");
        assertFalse(backend.contains("RoutePreparationProgressPolicy"), "route preparation polling belongs in IslandRoutingCommandHandler");
        assertTrue(routingHandler.contains("void routeWarp(Player player, UUID islandId, String warpName)"));
        assertTrue(routingHandler.contains("void routeTicket(Player player, CompletableFuture<RouteTicket> ticketFuture, String failureMessage)"));
        assertTrue(routingHandler.contains("routeBossBars"));
        assertTrue(routingHandler.contains("sendPluginMessage(plugin, \"BungeeCord\""));
        assertTrue(routingHandler.contains("RoutePreparationProgressPolicy"));
        assertTrue(routingHandler.contains("IslandRoutingUseCase"));
        assertTrue(routingHandler.contains("routingUseCase.createWarpTicket"));
        assertTrue(routingHandler.contains("routingUseCase.createHomeTicket"));
        assertTrue(routingHandler.contains("routingUseCase.clearRouteAction"));
        assertFalse(routingHandler.contains("coreApiClient.createWarpTicket"));
        assertFalse(routingHandler.contains("coreApiClient.routeTicketStatus"));
        assertFalse(routingHandler.contains("coreApiClient.publishRouteSession"));
        assertFalse(routingHandler.contains("coreApiClient.clearRoute"));
        assertFalse(routingUseCase.contains("public CompletableFuture<String> clearRoute("), "route clear usecase must expose typed actions instead of raw strings");
        assertTrue(routingUseCase.contains("RoutingCommandClient routingCommands"));
        assertTrue(routingUseCase.contains("routingCommands.createWarpTicket"));
        assertTrue(routingUseCase.contains("navigationCommands.createHomeTicket"));
        assertTrue(routingUseCase.contains("routingCommands.routeTicketStatus"));
        assertTrue(routingUseCase.contains("routingCommands.publishRouteSession"));
        assertTrue(routingUseCase.contains("routingCommands.clearRoute"));
        assertFalse(routingUseCase.contains("coreApiClient.createWarpTicket"));
        assertFalse(routingUseCase.contains("coreApiClient.routeTicketStatus"));
        assertFalse(routingUseCase.contains("coreApiClient.publishRouteSession"));
        assertFalse(routingUseCase.contains("coreApiClient.clearRoute"));
    }

    private static String routerSource() throws Exception {
        return Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandCommandRouter.java"));
    }
}
