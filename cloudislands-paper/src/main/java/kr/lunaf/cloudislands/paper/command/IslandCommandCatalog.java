package kr.lunaf.cloudislands.paper.command;

import java.util.List;
import kr.lunaf.cloudislands.protocol.command.IslandPlayerCommandRegistry;

final class IslandCommandCatalog {
    static final List<String> UPGRADE_KEYS = List.of(
        "size",
        "members",
        "warps",
        "hoppers",
        "spawners",
        "generator",
        "mob",
        "crop",
        "fly",
        "redstone",
        "bank",
        "border",
        "homes",
        "biome",
        "keep-inventory",
        "border-color"
    );

    static final List<HelpCategory> HELP_CATEGORIES = IslandPlayerCommandRegistry.helpCategories().stream()
        .map(category -> new HelpCategory(category.name(), category.aliases(), category.title(), category.commands()))
        .toList();

    static final List<IslandCommandDescriptor> DESCRIPTORS = IslandPlayerCommandRegistry.playerDescriptors().stream()
        .map(descriptor -> new IslandCommandDescriptor(
            descriptor.id(),
            descriptor.aliases(),
            descriptor.category(),
            descriptor.permission(),
            descriptor.usage(),
            descriptor.helpCommands(),
            descriptor.descriptionKey(),
            descriptor.guiActionId(),
            RequiredIslandState.valueOf(descriptor.requiredIslandState()),
            descriptor.handler(),
            descriptor.suggestionProvider()
        ))
        .toList();

    static final List<String> SUBCOMMANDS = DESCRIPTORS.stream()
        .flatMap(descriptor -> descriptor.aliases().stream())
        .distinct()
        .toList();

    static final List<String> HELP_COMMANDS = IslandPlayerCommandRegistry.playerCommands();

    static List<String> helpCategoryNames() {
        return HELP_CATEGORIES.stream()
            .map(HelpCategory::name)
            .toList();
    }

    static List<String> upgradeKeys() {
        return UPGRADE_KEYS;
    }

    static HelpCategory helpCategory(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String lower = value.toLowerCase(java.util.Locale.ROOT);
        for (HelpCategory category : HELP_CATEGORIES) {
            if (category.aliases().stream().anyMatch(alias -> alias.toLowerCase(java.util.Locale.ROOT).equals(lower))) {
                return category;
            }
        }
        return null;
    }

    private static IslandCommandDescriptor descriptor(
        String id,
        List<String> aliases,
        String category,
        String permission,
        String usage,
        List<String> examples,
        String descriptionKey,
        String guiActionId,
        RequiredIslandState requiredIslandState,
        String handler,
        String suggestionProvider
    ) {
        return new IslandCommandDescriptor(
            id,
            aliases,
            category,
            permission,
            usage,
            examples,
            descriptionKey,
            guiActionId,
            requiredIslandState,
            handler,
            suggestionProvider
        );
    }

    private IslandCommandCatalog() {
    }

    record IslandCommandDescriptor(
        String id,
        List<String> aliases,
        String category,
        String permission,
        String usage,
        List<String> examples,
        String descriptionKey,
        String guiActionId,
        RequiredIslandState requiredIslandState,
        String handler,
        String suggestionProvider
    ) {
        IslandCommandDescriptor {
            id = blankDefault(id, "island.command");
            aliases = aliases == null ? List.of() : List.copyOf(aliases);
            category = blankDefault(category, "기본");
            permission = blankDefault(permission, "IslandCommandPermission.fromSubcommand");
            usage = blankDefault(usage, "섬");
            examples = examples == null ? List.of() : List.copyOf(examples);
            descriptionKey = blankDefault(descriptionKey, "island-command-description");
            guiActionId = blankDefault(guiActionId, "IslandCommandPermission.fromGuiActionId");
            requiredIslandState = requiredIslandState == null ? RequiredIslandState.ANY : requiredIslandState;
            handler = blankDefault(handler, "IslandCommandRouter");
            suggestionProvider = blankDefault(suggestionProvider, "IslandCommandTabCompleter");
        }

        List<String> helpCommands() {
            return examples.isEmpty() ? List.of(usage) : examples;
        }
    }

    enum RequiredIslandState {
        ANY,
        NO_ISLAND,
        OWNS_ISLAND,
        VISITING_ISLAND,
        ADMIN
    }

    record HelpCategory(String name, List<String> aliases, String title, List<String> commands) {
    }

    private static String blankDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
