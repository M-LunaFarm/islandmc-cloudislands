package kr.lunaf.cloudislands.protocol.command;

import java.util.List;

public record CommandDescriptor(
    String id,
    List<CommandAlias> commandAliases,
    String category,
    CommandPermission commandPermission,
    String usage,
    List<String> helpCommands,
    String descriptionKey,
    String guiActionId,
    String requiredIslandState,
    String handler,
    String suggestionProvider,
    CommandExecutionTarget executionTarget
) {
    public CommandDescriptor {
        id = blankDefault(id, "island.command");
        commandAliases = commandAliases == null ? List.of() : List.copyOf(commandAliases);
        category = blankDefault(category, "기본");
        commandPermission = commandPermission == null ? new CommandPermission("") : commandPermission;
        usage = blankDefault(usage, "섬");
        helpCommands = helpCommands == null ? List.of(usage) : List.copyOf(helpCommands);
        descriptionKey = blankDefault(descriptionKey, "island-command-description");
        guiActionId = blankDefault(guiActionId, "IslandCommandPermission.fromGuiActionId");
        requiredIslandState = blankDefault(requiredIslandState, "ANY");
        handler = blankDefault(handler, "IslandCommandRouter");
        suggestionProvider = blankDefault(suggestionProvider, "IslandCommandCatalog.SUBCOMMANDS");
        executionTarget = executionTarget == null ? CommandExecutionTarget.BOTH : executionTarget;
    }

    public List<String> aliases() {
        return commandAliases.stream().map(CommandAlias::value).toList();
    }

    public String permission() {
        return commandPermission.node();
    }

    private static String blankDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
