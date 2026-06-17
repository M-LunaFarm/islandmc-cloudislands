package kr.seungmin.satisskyfactory.command;

import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FactoryCommandTest {
    @Test
    void listsPlayerCommandsAsOneLineEntriesWhenFeatureIsEnabled() throws Exception {
        FactoryCommand command = command(feature -> true);

        List<String> commands = visibleHelpCommands(command, "ci", null);

        assertTrue(commands.stream().allMatch(commandLine -> commandLine.startsWith("ci ")));
        assertTrue(commands.stream().allMatch(commandLine -> !commandLine.contains("\n") && !commandLine.contains("\r")));
        assertEquals(commands.size(), commands.stream().distinct().count());
        assertTrue(commands.contains("ci help [page]"));
        assertTrue(commands.contains("ci command list [page]"));
        assertTrue(commands.contains("ci status"));
        assertTrue(commands.contains("ci withdraw <itemId> <amount>"));
        assertTrue(commands.contains("ci research unlock <researchId>"));
    }

    @Test
    void exposesPagedPlayerCommandSuggestions() throws Exception {
        List<String> suggestions = helpPageSuggestions(command(feature -> true), null);

        assertTrue(suggestions.size() > 1);
        assertEquals("1", suggestions.get(0));
        assertEquals(String.valueOf(suggestions.size()), suggestions.get(suggestions.size() - 1));
    }

    @Test
    void hidesOnlyGuiEntrypointsWhenGuiFeatureIsDisabled() throws Exception {
        FactoryCommand command = command(feature -> !"gui".equals(feature));

        List<String> commands = visibleHelpCommands(command, "factory", null);

        assertFalse(commands.contains("factory main"));
        assertFalse(commands.contains("factory storage"));
        assertFalse(commands.contains("factory market"));
        assertFalse(commands.contains("factory contracts"));
        assertTrue(commands.contains("factory sell hand"));
        assertTrue(commands.contains("factory sell <itemId> <amount>"));
        assertTrue(commands.contains("factory contracts complete"));
        assertTrue(commands.contains("factory emergency"));
    }

    @Test
    void hidesStorageBackedCommandsWhenStorageFeatureIsDisabled() throws Exception {
        FactoryCommand command = command(feature -> !"storage".equals(feature));

        List<String> commands = visibleHelpCommands(command, "factory", null);

        assertFalse(commands.contains("factory storage"));
        assertFalse(commands.contains("factory deposit"));
        assertFalse(commands.contains("factory withdraw <itemId> <amount>"));
        assertFalse(commands.contains("factory market"));
        assertFalse(commands.contains("factory sell hand"));
        assertFalse(commands.contains("factory contracts"));
        assertFalse(commands.contains("factory contracts complete"));
        assertFalse(commands.contains("factory emergency"));
        assertTrue(commands.contains("factory main"));
        assertTrue(commands.contains("factory machines"));
    }

    @Test
    void tabCompleteHidesGuiOnlyEntrypointsWhenGuiFeatureIsDisabled() {
        FactoryCommand command = command(feature -> !"gui".equals(feature));

        List<String> suggestions = command.onTabComplete(null, null, "factory", new String[]{""});

        assertFalse(suggestions.contains("main"));
        assertFalse(suggestions.contains("storage"));
        assertFalse(suggestions.contains("market"));
        assertTrue(suggestions.contains("sell"));
        assertTrue(suggestions.contains("contracts"));
        assertTrue(suggestions.contains("emergency"));
    }

    @Test
    void tabCompleteHidesStorageBackedCommandsWhenStorageFeatureIsDisabled() {
        FactoryCommand command = command(feature -> !"storage".equals(feature));

        List<String> suggestions = command.onTabComplete(null, null, "factory", new String[]{""});

        assertFalse(suggestions.contains("storage"));
        assertFalse(suggestions.contains("deposit"));
        assertFalse(suggestions.contains("withdraw"));
        assertFalse(suggestions.contains("market"));
        assertFalse(suggestions.contains("sell"));
        assertFalse(suggestions.contains("contracts"));
        assertFalse(suggestions.contains("emergency"));
        assertTrue(suggestions.contains("main"));
        assertTrue(suggestions.contains("machines"));
    }

    @SuppressWarnings("unchecked")
    private List<String> visibleHelpCommands(FactoryCommand command, String label, CommandSender viewer) throws Exception {
        Method method = FactoryCommand.class.getDeclaredMethod("visibleHelpCommands", String.class, CommandSender.class);
        method.setAccessible(true);
        return (List<String>) method.invoke(command, label, viewer);
    }

    @SuppressWarnings("unchecked")
    private List<String> helpPageSuggestions(FactoryCommand command, CommandSender sender) throws Exception {
        Method method = FactoryCommand.class.getDeclaredMethod("helpPageSuggestions", CommandSender.class);
        method.setAccessible(true);
        return (List<String>) method.invoke(command, sender);
    }

    private FactoryCommand command(Predicate<String> featureEnabled) {
        return new FactoryCommand(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                featureEnabled,
                Map::of,
                Map::of,
                _islandId -> Map.of(),
                () -> {
                }
        );
    }
}
