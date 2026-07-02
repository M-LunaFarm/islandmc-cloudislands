package kr.lunaf.cloudislands.paper.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
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
        for (IslandCommandCatalog.IslandCommandDescriptor descriptor : IslandCommandCatalog.DESCRIPTORS) {
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
