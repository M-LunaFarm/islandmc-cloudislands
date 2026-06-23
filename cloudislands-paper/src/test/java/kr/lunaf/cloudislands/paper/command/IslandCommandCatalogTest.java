package kr.lunaf.cloudislands.paper.command;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
