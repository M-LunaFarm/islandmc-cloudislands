package kr.seungmin.satisskyfactory.command;

import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FactoryCommandTest {
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

    @SuppressWarnings("unchecked")
    private List<String> visibleHelpCommands(FactoryCommand command, String label, CommandSender viewer) throws Exception {
        Method method = FactoryCommand.class.getDeclaredMethod("visibleHelpCommands", String.class, CommandSender.class);
        method.setAccessible(true);
        return (List<String>) method.invoke(command, label, viewer);
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
