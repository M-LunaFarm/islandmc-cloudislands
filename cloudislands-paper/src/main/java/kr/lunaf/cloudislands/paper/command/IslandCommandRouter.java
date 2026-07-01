package kr.lunaf.cloudislands.paper.command;

import java.util.List;
import java.util.Locale;
import kr.lunaf.cloudislands.paper.gui.GuiAction;
import kr.lunaf.cloudislands.paper.gui.GuiClick;
import kr.lunaf.cloudislands.protocol.command.CommandListPolicy;
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
    private final IslandCommandSuggestionService suggestions = new IslandCommandSuggestionService();

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
        String subcommand = args[0].toLowerCase(Locale.ROOT);
        HelpCategoryRequest helpCategoryRequest = helpCategoryRequest(args);
        if (helpCategoryRequest != null) {
            sendCommandList(player, label, helpCategoryRequest.category().title(), helpCategoryRequest.category().commands(), helpCategoryRequest.page());
            return true;
        }
        int commandListPage = commandListPage(args);
        if (commandListPage > 0) {
            sendCommandList(player, label, "섬 명령어 목록", IslandCommandCatalog.HELP_COMMANDS, commandListPage);
            return true;
        }
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

    void handleGuiAction(Player player, GuiAction action, GuiClick click) {
        if (action == null) {
            return;
        }
        if (action instanceof GuiAction.Close) {
            player.closeInventory();
            return;
        }
        if (action instanceof GuiAction.MainOpen) {
            openMainMenuOrCommandList(player, "섬");
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
    }

    private record HelpCategoryRequest(IslandCommandCatalog.HelpCategory category, int page) {
    }
}
