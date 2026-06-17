package kr.seungmin.satisskyfactory.command;

import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;
import kr.seungmin.satisskyfactory.storage.SatisLegacyMigrationPolicy;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminFactoryCommandTest {
    @Test
    void exposesDedicatedAdminCommandEntrypoints() throws Exception {
        assertTrue(Modifier.isFinal(AdminFactoryCommand.class.getModifiers()));
        assertEquals(boolean.class, AdminFactoryCommand.class
                .getMethod("execute", CommandSender.class, String[].class)
                .getReturnType());
        assertEquals(List.class, AdminFactoryCommand.class
                .getMethod("complete", CommandSender.class, String[].class)
                .getReturnType());
    }

    @Test
    void listsMigrationCommandsAsOneLineEntriesWhenFeatureIsEnabled() throws Exception {
        List<String> commands = visibleHelpCommands(command(feature -> true), "ci");

        assertTrue(commands.stream().allMatch(command -> command.startsWith("ci ")));
        assertTrue(commands.stream().allMatch(command -> !command.contains("\n") && !command.contains("\r")));
        assertEquals(commands.size(), commands.stream().distinct().count());
        assertTrue(commands.contains("ci admin migration"));
        for (String policyCommand : SatisLegacyMigrationPolicy.adminCommands()) {
            assertTrue(commands.contains(policyCommand.replaceFirst("^factory", "ci")));
        }
    }

    @Test
    void exposesPagedAdminCommandSuggestions() throws Exception {
        List<String> suggestions = helpPageSuggestions(command(feature -> true));

        assertTrue(suggestions.size() > 1);
        assertEquals("1", suggestions.get(0));
        assertEquals(String.valueOf(suggestions.size()), suggestions.get(suggestions.size() - 1));
    }

    @Test
    void hidesMigrationCommandsWhenMigrationFeatureIsDisabled() throws Exception {
        List<String> commands = visibleHelpCommands(command(feature -> !"migration".equals(feature)), "factory");

        assertFalse(commands.stream().anyMatch(command -> command.contains(" migration")));
        assertTrue(commands.contains("factory admin state"));
    }

    @SuppressWarnings("unchecked")
    private List<String> visibleHelpCommands(AdminFactoryCommand command, String label) throws Exception {
        Method method = AdminFactoryCommand.class.getDeclaredMethod("visibleHelpCommands", String.class);
        method.setAccessible(true);
        return (List<String>) method.invoke(command, label);
    }

    @SuppressWarnings("unchecked")
    private List<String> helpPageSuggestions(AdminFactoryCommand command) throws Exception {
        Method method = AdminFactoryCommand.class.getDeclaredMethod("helpPageSuggestions");
        method.setAccessible(true);
        return (List<String>) method.invoke(command);
    }

    private AdminFactoryCommand command(Predicate<String> featureEnabled) {
        return new AdminFactoryCommand(
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
