package kr.lunaf.cloudislands.paper.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import kr.lunaf.cloudislands.protocol.command.IslandPlayerCommandRegistry;
import org.junit.jupiter.api.Test;

class IslandCommandCatalogTest {
    private static final Pattern SUBCOMMAND_EQUALS = Pattern.compile("subcommand\\.equals\\(\"([^\"]+)\"\\)");
    private static final Pattern FIRST_EQUALS = Pattern.compile("first\\.equals\\(\"([^\"]+)\"\\)");

    @Test
    void everyAdvertisedSubcommandHasHandlerCoverage() throws Exception {
        Set<String> handled = handledSubcommands();
        List<String> missing = IslandCommandCatalog.SUBCOMMANDS.stream()
            .filter(subcommand -> !handled.contains(subcommand))
            .toList();

        assertEquals(List.of(), missing, "Every command catalog subcommand must be routed by IslandCommandRouter or a command handler");
    }

    @Test
    void everyHelpCommandReferencesKnownExecutableSubcommand() {
        List<String> missing = new ArrayList<>();
        for (String command : IslandCommandCatalog.HELP_COMMANDS) {
            List<String> tokens = List.of(command.split("\\s+"));
            if (tokens.size() <= 1) {
                continue;
            }
            String subcommand = tokens.get(1);
            if (!IslandCommandCatalog.SUBCOMMANDS.contains(subcommand)) {
                missing.add(command);
            }
        }

        assertEquals(List.of(), missing, "Help output must not advertise commands outside the executable subcommand catalog");
    }

    @Test
    void categorizedHelpOnlyReferencesAdvertisedCommands() {
        assertEquals(List.of("기본", "멤버", "방문", "성장", "설정", "관리자"), IslandCommandCatalog.helpCategoryNames());

        List<String> missing = new ArrayList<>();
        for (IslandCommandCatalog.HelpCategory category : IslandCommandCatalog.HELP_CATEGORIES) {
            assertTrue(category.aliases().contains(category.name()), "Category aliases must include the displayed category name");
            for (String command : category.commands()) {
                if (!IslandCommandCatalog.HELP_COMMANDS.contains(command)) {
                    missing.add(category.name() + ": " + command);
                }
            }
        }

        assertEquals(List.of(), missing, "Categorized help must be a subset of the advertised command list");
    }

    @Test
    void descriptorsOwnAliasesHelpAndRoutingPolicyFields() {
        assertTrue(!IslandCommandCatalog.DESCRIPTORS.isEmpty(), "Command descriptors must be the command catalog source of truth");
        assertTrue(IslandCommandCatalog.DESCRIPTORS.size() > 8, "Command descriptors must be feature-scoped, not one catch-all registry descriptor");
        assertTrue(
            IslandCommandCatalog.DESCRIPTORS.stream().map(IslandCommandCatalog.IslandCommandDescriptor::handler).distinct().count() > 8,
            "Command descriptors must preserve handler ownership boundaries"
        );

        List<String> descriptorAliases = IslandCommandCatalog.DESCRIPTORS.stream()
            .flatMap(descriptor -> descriptor.aliases().stream())
            .distinct()
            .toList();
        List<String> descriptorHelp = IslandCommandCatalog.DESCRIPTORS.stream()
            .flatMap(descriptor -> descriptor.helpCommands().stream())
            .distinct()
            .toList();

        assertEquals(descriptorAliases, IslandCommandCatalog.SUBCOMMANDS, "Subcommand aliases must be generated from descriptors");
        assertEquals(descriptorHelp, IslandCommandCatalog.HELP_COMMANDS, "Help output must be generated from descriptors");
        assertEquals(IslandPlayerCommandRegistry.playerCommands(), IslandCommandCatalog.HELP_COMMANDS, "Paper help output must consume the shared Paper/Velocity player command registry");
        for (IslandCommandCatalog.IslandCommandDescriptor descriptor : IslandCommandCatalog.DESCRIPTORS) {
            assertFalse(descriptor.id().equals("island.command.registry"), "Descriptor registry must not collapse every command into one catch-all descriptor");
            assertTrue(descriptor.aliases().size() < IslandCommandCatalog.SUBCOMMANDS.size(), "No descriptor may own the full command alias list");
            assertTrue(!descriptor.id().isBlank(), "descriptor id is required");
            assertTrue(!descriptor.permission().isBlank(), "descriptor permission policy is required");
            assertTrue(!descriptor.descriptionKey().isBlank(), "descriptor description key is required");
            assertTrue(!descriptor.guiActionId().isBlank(), "descriptor GUI action policy is required");
            assertTrue(descriptor.requiredIslandState() != null, "descriptor island-state requirement is required");
            assertTrue(!descriptor.handler().isBlank(), "descriptor handler is required");
            assertTrue(!descriptor.suggestionProvider().isBlank(), "descriptor suggestion provider is required");
        }
    }

    @Test
    void descriptorAliasesArePermissionMappedAndNoLegacyCatchAllListsRemain() throws Exception {
        String catalogSource = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandCommandCatalog.java"));
        assertFalse(catalogSource.contains("COMMAND_ALIASES"), "Aliases must live on feature descriptors, not a legacy catch-all list");
        assertFalse(catalogSource.contains("COMMAND_HELP"), "Help entries must live on feature descriptors, not a legacy catch-all list");

        List<String> missingPermission = IslandCommandCatalog.SUBCOMMANDS.stream()
            .filter(alias -> IslandCommandPermission.fromSubcommand(alias) == null)
            .toList();

        assertEquals(List.of(), missingPermission, "Every descriptor alias must map to the shared command permission policy");
    }

    @Test
    void superiorSkyblockToggleAliasesAreAdvertisedAndRouted() throws Exception {
        String environmentHandler = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandEnvironmentCommandHandler.java"));
        String chatHandler = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandChatLogCommandHandler.java"));
        String completer = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandCommandTabCompleter.java"));

        assertTrue(IslandCommandCatalog.SUBCOMMANDS.contains("toggle"), "SS2-style /is toggle border alias must be advertised");
        assertTrue(IslandCommandCatalog.HELP_COMMANDS.contains("섬 toggle border"), "SS2-style toggle border help must be advertised");
        assertTrue(IslandCommandCatalog.HELP_COMMANDS.contains("섬 toggle blocks"), "SS2-style toggle blocks help must be advertised");
        assertTrue(IslandCommandCatalog.HELP_COMMANDS.contains("섬 teamchat toggle"), "Team chat toggle-mode help must be advertised");
        assertEquals(IslandCommandPermission.ENVIRONMENT, IslandCommandPermission.fromSubcommand("toggle"), "toggle must use the environment permission policy");
        assertEquals(IslandCommandPermission.CHAT, IslandCommandPermission.fromSubcommand("teamchat-toggle"), "teamchat toggle must use the chat permission policy");
        assertTrue(environmentHandler.contains("handleToggle(Player player, String[] args)"), "toggle border must route through the environment handler");
        assertTrue(environmentHandler.contains("toggleBorderVisibility(player)"), "toggle border must flip current border visibility when no explicit value is supplied");
        assertTrue(environmentHandler.contains("toggleStackedBlockVisibility(player)"), "toggle blocks must flip Core-backed stacked block visibility when no explicit value is supplied");
        assertTrue(environmentHandler.contains("GameplayParityPolicy.STACKED_BLOCKS_VISIBLE_LIMIT_KEY"), "toggle blocks must persist through the shared stacked block limit key");
        assertTrue(chatHandler.contains("isTeamChatToggle(args[1])"), "teamchat toggle mode must be handled before message dispatch");
        assertTrue(completer.contains("List.of(\"border\", \"border-visible\", \"blocks\", \"stacked-blocks\", \"경계\", \"경계표시\", \"블록\", \"스택블록\")"), "toggle completions must expose SS2-style border and blocks targets");
    }

    @Test
    void superiorSkyblockStackerPermissionParityUsesCoreEnvironmentWithoutFakeRuntimeStateTransfer() throws Exception {
        String parity = Files.readString(Path.of("../gradle/report-gates.gradle.kts"));
        String stacker = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/integration/stacker/StackerIntegration.java"));
        String registry = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/integration/PaperIntegrationRegistry.java"));
        String environmentHandler = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandEnvironmentCommandHandler.java"));

        assertTrue(parity.contains("superior.island.stacker.*\", \"cloudislands.island.environment\", \"SUPPORTED_VERIFIED\""), "stacker wildcard permission must map to the environment command surface");
        assertTrue(parity.contains("superior.island.stacker.<block-type>\", \"cloudislands.island.environment\", \"COVERED_BY\""), "per-block stacker permission must be covered by the same environment and block-limit surface");
        assertFalse(stacker.contains("IntegrationCapability.RUNTIME_AUTHORITY"), "Probe-only stacker adapters must not claim runtime authority");
        assertFalse(stacker.contains("IntegrationCapability.STATE_EXPORT"), "Probe-only stacker adapters must not claim state export");
        assertFalse(stacker.contains("IntegrationCapability.STATE_RESTORE"), "Probe-only stacker adapters must not claim state restore");
        assertTrue(registry.contains("\"RoseStacker\", \"WildStacker\", \"AdvancedSpawners\""), "Supported stacker plugins must be registered");
        assertTrue(environmentHandler.contains("STACKED_BLOCKS_VISIBLE_LIMIT_KEY"), "Player stacker visibility must persist through Core environment limits");
    }

    @Test
    void upgradeKeySuggestionsCoverConfiguredUpgradeEffects() {
        assertEquals(List.of(
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
        ), IslandCommandCatalog.upgradeKeys());
    }

    private static Set<String> handledSubcommands() throws IOException {
        Set<String> handled = new LinkedHashSet<>();
        Path commandSource = Path.of("src/main/java/kr/lunaf/cloudislands/paper/command");
        try (var files = Files.list(commandSource)) {
            for (Path file : files
                    .filter(path -> path.getFileName().toString().startsWith("Island"))
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .sorted()
                    .toList()) {
                String source = Files.readString(file);
                SUBCOMMAND_EQUALS.matcher(source).results().forEach(result -> handled.add(result.group(1)));
                FIRST_EQUALS.matcher(source).results().forEach(result -> handled.add(result.group(1)));
            }
        }
        return handled;
    }
}
