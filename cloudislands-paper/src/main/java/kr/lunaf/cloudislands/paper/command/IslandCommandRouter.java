package kr.lunaf.cloudislands.paper.command;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import kr.lunaf.cloudislands.paper.gui.GuiAction;
import kr.lunaf.cloudislands.paper.gui.GuiClick;
import kr.lunaf.cloudislands.paper.platform.scheduler.TaskHandle;
import kr.lunaf.cloudislands.protocol.command.CommandListPolicy;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

final class IslandCommandRouter {
    private final IslandBankCommandHandler bankCommands;
    private final IslandSnapshotCommandHandler snapshotCommands;
    private final IslandWarehouseCommandHandler warehouseCommands;
    private final IslandChatLogCommandHandler chatLogCommands;
    private final IslandProgressionCommandHandler progressionCommands;
    private final IslandEnvironmentCommandHandler environmentCommands;
    private final IslandSettingsCommandHandler settingsCommands;
    private final IslandHomeWarpCommandHandler homeWarpCommands;
    private final IslandVisitReviewCommandHandler visitReviewCommands;
    private final IslandLifecycleCommandHandler lifecycleCommands;
    private final IslandOverviewCommandHandler overviewCommands;
    private final IslandMembershipCommandHandler membershipCommands;
    private final IslandAdminNodeCommandHandler adminCommands;
    private final Runtime runtime;
    private final SuperiorSkyblock2CommandAliasAdapter legacyAliases;
    private final IslandCommandSuggestionService suggestions = new IslandCommandSuggestionService();
    private final IslandCommandDelayPolicy delayPolicy = new IslandCommandDelayPolicy();
    private final IslandCommandWarmupPolicy warmupPolicy = new IslandCommandWarmupPolicy();

    IslandCommandRouter(
        IslandBankCommandHandler bankCommands,
        IslandSnapshotCommandHandler snapshotCommands,
        IslandWarehouseCommandHandler warehouseCommands,
        IslandChatLogCommandHandler chatLogCommands,
        IslandProgressionCommandHandler progressionCommands,
        IslandEnvironmentCommandHandler environmentCommands,
        IslandSettingsCommandHandler settingsCommands,
        IslandHomeWarpCommandHandler homeWarpCommands,
        IslandVisitReviewCommandHandler visitReviewCommands,
        IslandLifecycleCommandHandler lifecycleCommands,
        IslandOverviewCommandHandler overviewCommands,
        IslandMembershipCommandHandler membershipCommands,
        IslandAdminNodeCommandHandler adminCommands,
        Runtime runtime
    ) {
        this(
            bankCommands,
            snapshotCommands,
            warehouseCommands,
            chatLogCommands,
            progressionCommands,
            environmentCommands,
            settingsCommands,
            homeWarpCommands,
            visitReviewCommands,
            lifecycleCommands,
            overviewCommands,
            membershipCommands,
            adminCommands,
            runtime,
            SuperiorSkyblock2CommandAliasAdapter.disabled()
        );
    }

    IslandCommandRouter(
        IslandBankCommandHandler bankCommands,
        IslandSnapshotCommandHandler snapshotCommands,
        IslandWarehouseCommandHandler warehouseCommands,
        IslandChatLogCommandHandler chatLogCommands,
        IslandProgressionCommandHandler progressionCommands,
        IslandEnvironmentCommandHandler environmentCommands,
        IslandSettingsCommandHandler settingsCommands,
        IslandHomeWarpCommandHandler homeWarpCommands,
        IslandVisitReviewCommandHandler visitReviewCommands,
        IslandLifecycleCommandHandler lifecycleCommands,
        IslandOverviewCommandHandler overviewCommands,
        IslandMembershipCommandHandler membershipCommands,
        IslandAdminNodeCommandHandler adminCommands,
        Runtime runtime,
        SuperiorSkyblock2CommandAliasAdapter legacyAliases
    ) {
        this.bankCommands = bankCommands;
        this.snapshotCommands = snapshotCommands;
        this.warehouseCommands = warehouseCommands;
        this.chatLogCommands = chatLogCommands;
        this.progressionCommands = progressionCommands;
        this.environmentCommands = environmentCommands;
        this.settingsCommands = settingsCommands;
        this.homeWarpCommands = homeWarpCommands;
        this.visitReviewCommands = visitReviewCommands;
        this.lifecycleCommands = lifecycleCommands;
        this.overviewCommands = overviewCommands;
        this.membershipCommands = membershipCommands;
        this.adminCommands = adminCommands;
        this.runtime = runtime;
        this.legacyAliases = legacyAliases == null ? SuperiorSkyblock2CommandAliasAdapter.disabled() : legacyAliases;
    }

    boolean handleCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(runtime.routeMessage("player-only-command", "플레이어만 사용할 수 있습니다."));
            return true;
        }
        if (args.length == 0) {
            openMainMenuOrCommandList(player, label);
            return true;
        }
        String[] effectiveArgs = args;
        SuperiorSkyblock2CommandAliasAdapter.ResolvedAlias legacyAlias = legacyAliases.translate(args).orElse(null);
        if (legacyAlias != null) {
            effectiveArgs = legacyAlias.args();
            sendLegacyAliasAdvice(player, legacyAlias);
        }
        String subcommand = effectiveArgs[0].toLowerCase(Locale.ROOT);
        if (isGuiHelpRequest(effectiveArgs)) {
            openMainMenuOrCommandList(player, label);
            return true;
        }
        HelpCategoryRequest helpCategoryRequest = helpCategoryRequest(effectiveArgs);
        if (helpCategoryRequest != null) {
            sendCommandList(player, label, helpCategoryRequest.category().title(), helpCategoryRequest.category().commands(), helpCategoryRequest.page());
            return true;
        }
        int commandListPage = commandListPage(effectiveArgs);
        if (commandListPage > 0) {
            sendCommandList(player, label, "섬 명령어 목록", IslandCommandCatalog.HELP_COMMANDS, commandListPage);
            return true;
        }
        IslandCommandPermission permission = IslandCommandPermission.fromSubcommand(subcommand);
        if (permission != null && !runtime.hasCommandPermission(player, permission)) {
            runtime.message(player, runtime.routeMessage("island-command-no-permission", "이 섬 명령을 사용할 권한이 없습니다."));
            return true;
        }
        if (!checkCommandDelay(player, label, subcommand, effectiveArgs.clone())) {
            return true;
        }
        return runIslandAction(player, label, subcommand, effectiveArgs);
    }

    private void sendLegacyAliasAdvice(Player player, SuperiorSkyblock2CommandAliasAdapter.ResolvedAlias alias) {
        runtime.message(player, runtime.routeMessage("legacy-ss2-alias-advice-prefix", "CloudIslands 명령은 /섬 ") + alias.displayCommand() + runtime.routeMessage("legacy-ss2-alias-advice-suffix", "입니다."));
        if (alias.migrationMode()) {
            runtime.message(player, runtime.routeMessage("legacy-ss2-alias-migration-mode", "SuperiorSkyblock2 migration mode: legacy /is aliases are being translated to CloudIslands commands."));
        }
    }

    private boolean runIslandAction(Player player, String label, String subcommand, String[] args) {
        if (subcommand.equals("menu") || subcommand.equals("메뉴")) {
            openMainMenuOrCommandList(player, label);
            return true;
        }
        if (overviewCommands.handleCommand(player, subcommand)) {
            return true;
        }
        if (lifecycleCommands.handleCommand(player, subcommand, args)) {
            return true;
        }
        if (homeWarpCommands.handleCommand(player, subcommand, args)) {
            return true;
        }
        if (settingsCommands.handleCommand(player, subcommand, args)) {
            return true;
        }
        if (visitReviewCommands.handleCommand(player, subcommand, args)) {
            return true;
        }
        if (progressionCommands.handleCommand(player, subcommand, args)) {
            return true;
        }
        if (bankCommands.handleCommand(player, subcommand, args)) {
            return true;
        }
        if (warehouseCommands.handleCommand(player, subcommand, args)) {
            return true;
        }
        if (chatLogCommands.handleCommand(player, subcommand, args)) {
            return true;
        }
        if (environmentCommands.handleCommand(player, subcommand, args)) {
            return true;
        }
        if (snapshotCommands.handleCommand(player, subcommand, args)) {
            return true;
        }
        if (membershipCommands.handleCommand(player, subcommand, args)) {
            return true;
        }
        suggestions.suggest(subcommand, IslandCommandCatalog.SUBCOMMANDS)
            .ifPresent(suggestion -> runtime.message(player, runtime.routeMessage("command-suggestion-prefix", "혹시 /") + label + " " + suggestion + runtime.routeMessage("command-suggestion-suffix", " 를 찾으셨나요?")));
        sendCommandList(player, label, "섬 명령어 목록", IslandCommandCatalog.HELP_COMMANDS, 1);
        return true;
    }

    void clearPlayerState(Player player) {
        if (player != null) {
            delayPolicy.clear(player.getUniqueId());
            warmupPolicy.clear(player.getUniqueId());
        }
    }

    void cancelWarmupOnMove(Player player, Location from, Location to) {
        if (player == null || from == null || to == null || !movedBlock(from, to)) {
            return;
        }
        warmupPolicy.cancelOnMove(player.getUniqueId(), IslandCommandWarmupPolicy.BlockPosition.from(to))
            .ifPresent(pending -> {
                delayPolicy.clear(player.getUniqueId(), pending.subject());
                runtime.message(player, runtime.routeMessage(IslandCommandWarmupPolicy.WARMUP_CANCELLED_MESSAGE_KEY, "움직여서 섬 명령 준비가 취소되었습니다."));
            });
    }

    void markCombat(Player player) {
        if (player == null) {
            return;
        }
        warmupPolicy.markCombat(player.getUniqueId(), System.currentTimeMillis());
        warmupPolicy.cancel(player.getUniqueId())
            .ifPresent(pending -> {
                delayPolicy.clear(player.getUniqueId(), pending.subject());
                runtime.message(player, runtime.routeMessage(IslandCommandWarmupPolicy.COMBAT_BLOCKED_MESSAGE_KEY, "전투 중에는 이 섬 이동 명령을 사용할 수 없습니다."));
            });
    }

    void handleGuiAction(Player player, GuiAction action, GuiClick click) {
        if (action == null) {
            return;
        }
        if (action instanceof GuiAction.Close) {
            player.closeInventory();
            return;
        }
        IslandCommandPermission permission = IslandCommandPermission.fromGuiActionId(action.actionId());
        if (permission != null && !runtime.hasCommandPermission(player, permission)) {
            runtime.message(player, runtime.routeMessage("island-command-no-permission", "이 섬 명령을 사용할 권한이 없습니다."));
            return;
        }
        if (action instanceof GuiAction.MainOpen) {
            openMainMenuOrCommandList(player, "섬");
            return;
        }
        if (action instanceof GuiAction.NoPayload noPayload && noPayload.type() == GuiAction.NoPayloadType.HELP_OPEN) {
            sendCommandList(player, "섬", "섬 명령어 목록", IslandCommandCatalog.HELP_COMMANDS, 1);
            return;
        }
        if (bankCommands.handleGuiAction(player, action)) {
            return;
        }
        if (snapshotCommands.handleGuiAction(player, action, click)) {
            return;
        }
        if (warehouseCommands.handleGuiAction(player, action)) {
            return;
        }
        if (chatLogCommands.handleGuiAction(player, action)) {
            return;
        }
        if (progressionCommands.handleGuiAction(player, action)) {
            return;
        }
        if (environmentCommands.handleGuiAction(player, action)) {
            return;
        }
        if (settingsCommands.handleGuiAction(player, action, click.right())) {
            return;
        }
        if (homeWarpCommands.handleGuiAction(player, action, click)) {
            return;
        }
        if (visitReviewCommands.handleGuiAction(player, action)) {
            return;
        }
        if (lifecycleCommands.handleGuiAction(player, action, click)) {
            return;
        }
        if (overviewCommands.handleGuiAction(player, action)) {
            return;
        }
        if (membershipCommands.handleGuiAction(player, action, click)) {
            return;
        }
        if (adminCommands.handleGuiAction(player, action, click)) {
            return;
        }
        runtime.message(player, runtime.routeMessage("gui-action-unknown", "알 수 없는 GUI 작업입니다: ") + action.actionId());
    }

    private void sendCommandList(Player player, String label, String title, List<String> commands, int page) {
        List<String> labelledCommands = commands.stream()
            .map(command -> command.replaceFirst("^섬", label))
            .toList();
        CommandListPolicy.Page commandPage = CommandListPolicy.page(labelledCommands, page, label + " command list");
        String headerTitle = runtime.routeMessage("command-list-title", title + " ");
        String headerSuffix = runtime.routeMessage("command-list-suffix", CommandListPolicy.HEADER_SUFFIX);
        player.sendMessage(headerTitle + commandPage.page() + "/" + commandPage.pages() + " commands=" + commandPage.rangeSummary() + headerSuffix);
        for (String command : commandPage.entries()) {
            player.sendMessage(CommandListPolicy.ENTRY_PREFIX + command);
        }
        if (commandPage.previousCommand() != null) {
            player.sendMessage(CommandListPolicy.ENTRY_PREFIX + commandPage.previousCommand());
        }
        if (commandPage.nextCommand() != null) {
            player.sendMessage(CommandListPolicy.ENTRY_PREFIX + commandPage.nextCommand());
        }
    }

    private void openMainMenuOrCommandList(Player player, String label) {
        if (!runtime.openMainMenu(player)) {
            sendCommandList(player, label, "섬 명령어 목록", IslandCommandCatalog.HELP_COMMANDS, 1);
        }
    }

    private boolean checkCommandDelay(Player player, String label, String subcommand, String[] args) {
        long nowMillis = System.currentTimeMillis();
        IslandCommandDelayPolicy.Decision decision = delayPolicy.evaluate(
            player.getUniqueId(),
            subcommand,
            runtime.hasPermission(player, IslandCommandDelayPolicy.BYPASS_COOLDOWN_PERMISSION),
            runtime.hasPermission(player, IslandCommandDelayPolicy.BYPASS_WARMUP_PERMISSION),
            nowMillis
        );
        if (!decision.allowed()) {
            runtime.message(player, runtime.routeMessage(IslandCommandDelayPolicy.COOLDOWN_MESSAGE_KEY, "잠시 후 다시 시도해주세요. 남은 시간: " + decision.secondsRemaining() + "초"));
            return false;
        }
        if (decision.warmupRequired()) {
            if (warmupPolicy.combatBlocked(player.getUniqueId(), nowMillis)) {
                delayPolicy.clear(player.getUniqueId(), decision.subject());
                runtime.message(player, runtime.routeMessage(IslandCommandWarmupPolicy.COMBAT_BLOCKED_MESSAGE_KEY, "전투 중에는 이 섬 이동 명령을 사용할 수 없습니다."));
                return false;
            }
            if (warmupPolicy.hasPending(player.getUniqueId())) {
                delayPolicy.clear(player.getUniqueId(), decision.subject());
                runtime.message(player, runtime.routeMessage(IslandCommandWarmupPolicy.WARMUP_PENDING_MESSAGE_KEY, "이미 준비 중인 섬 명령이 있습니다."));
                return false;
            }
            sendWarmupWaitingState(player);
            long delayTicks = Math.max(1L, decision.secondsRemaining()) * 20L;
            TaskHandle task = runtime.scheduleCommandWarmup(player, delayTicks, () -> {
                if (!player.isOnline() || !warmupPolicy.complete(player.getUniqueId())) {
                    return;
                }
                runIslandAction(player, label, subcommand, args);
            });
            warmupPolicy.start(player.getUniqueId(), decision.subject(), IslandCommandWarmupPolicy.BlockPosition.from(player.getLocation()), task);
            return false;
        }
        return true;
    }

    private boolean movedBlock(Location from, Location to) {
        return from.getBlockX() != to.getBlockX()
            || from.getBlockY() != to.getBlockY()
            || from.getBlockZ() != to.getBlockZ()
            || !Objects.equals(from.getWorld(), to.getWorld());
    }

    private void sendWarmupWaitingState(Player player) {
        String message = runtime.routeMessage(IslandCommandDelayPolicy.WARMUP_MESSAGE_KEY, "섬 이동을 준비하고 있습니다. 움직이면 취소될 수 있습니다.");
        String title = runtime.routeMessage(IslandCommandDelayPolicy.WARMUP_TITLE_MESSAGE_KEY, "섬 이동 준비 중");
        String subtitle = runtime.routeMessage(IslandCommandDelayPolicy.WARMUP_SUBTITLE_MESSAGE_KEY, "잠시 후 명령이 실행됩니다.");
        player.sendActionBar(Component.text(runtime.playerMessage(message)));
        player.showTitle(Title.title(
            Component.text(runtime.playerMessage(title)),
            Component.text(runtime.playerMessage(subtitle))
        ));
    }

    private int helpPage(String[] args, int index) {
        if (args.length <= index) {
            return 1;
        }
        return integer(args[index], 1);
    }

    private int commandListPage(String[] args) {
        if (args.length == 0) {
            return 0;
        }
        String first = args[0].toLowerCase(Locale.ROOT);
        if (isCommandListRoot(args)) {
            return helpPage(args, 2);
        }
        if (isHelpRoot(first)) {
            return helpPage(args, 1);
        }
        return 0;
    }

    private HelpCategoryRequest helpCategoryRequest(String[] args) {
        int categoryIndex = helpCategoryIndex(args);
        if (args.length <= categoryIndex) {
            return null;
        }
        IslandCommandCatalog.HelpCategory category = IslandCommandCatalog.helpCategory(args[categoryIndex]);
        if (category == null) {
            return null;
        }
        return new HelpCategoryRequest(category, helpPage(args, categoryIndex + 1));
    }

    private int helpCategoryIndex(String[] args) {
        if (isCommandListRoot(args)) {
            return 2;
        }
        if (args.length > 0 && isHelpRoot(args[0].toLowerCase(Locale.ROOT))) {
            return 1;
        }
        return args.length;
    }

    private boolean isCommandListRoot(String[] args) {
        return args.length > 1 && args[0].equalsIgnoreCase("command") && (args[1].equalsIgnoreCase("list") || args[1].equals("목록"));
    }

    private boolean isHelpRoot(String first) {
        return first.equals("help") || first.equals("도움말") || first.equals("commands") || first.equals("command") || first.equals("command-list") || first.equals("명령어") || first.equals("명령어목록");
    }

    private boolean isGuiHelpRequest(String[] args) {
        return args.length > 1
            && isHelpRoot(args[0].toLowerCase(Locale.ROOT))
            && (args[1].equalsIgnoreCase("gui") || args[1].equalsIgnoreCase("menu") || args[1].equals("메뉴"));
    }

    private int integer(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    interface Runtime {
        void message(Player player, String message);

        String routeMessage(String key, String fallback);

        boolean openMainMenu(Player player);

        boolean hasCommandPermission(Player player, IslandCommandPermission permission);

        boolean hasPermission(Player player, String permission);

        String playerMessage(String message);

        TaskHandle scheduleCommandWarmup(Player player, long delayTicks, Runnable task);
    }

    private record HelpCategoryRequest(IslandCommandCatalog.HelpCategory category, int page) {
    }
}
