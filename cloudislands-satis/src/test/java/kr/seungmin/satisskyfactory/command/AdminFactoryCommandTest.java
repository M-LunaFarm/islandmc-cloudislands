package kr.seungmin.satisskyfactory.command;

import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;
import kr.seungmin.satisskyfactory.storage.SatisLegacyMigrationPolicy;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
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
        assertEquals(void.class, AdminFactoryCommand.class
                .getDeclaredMethod("verifyMigrationAddonState", CommandSender.class, String[].class)
                .getReturnType());
        assertEquals(void.class, AdminFactoryCommand.class
                .getDeclaredMethod("verifyNoLegacyProvider", CommandSender.class)
                .getReturnType());
    }

    @Test
    void listsMigrationCommandsAsOneLineEntriesWhenFeatureIsEnabled() throws Exception {
        List<String> commands = visibleHelpCommands(command(feature -> true), "ci");

        assertTrue(commands.stream().allMatch(command -> command.startsWith("ci ")));
        assertTrue(commands.stream().allMatch(command -> !command.contains("\n") && !command.contains("\r")));
        assertEquals(commands.size(), commands.stream().distinct().count());
        assertTrue(commands.contains("ci admin migration"));
        assertTrue(commands.contains("ci admin doctor"));
        assertTrue(commands.contains("ci admin database"));
        assertTrue(commands.contains("ci admin runtime"));
        assertTrue(commands.contains("ci admin routes"));
        assertTrue(commands.contains("ci admin support"));
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

    @Test
    void exposesCoreApiSetupAndIslandEndpointStateKeys() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/seungmin/satisskyfactory/command/AdminFactoryCommand.java"));

        assertTrue(source.contains("\"database-core-api-island-endpoint\""));
        assertTrue(source.contains("\"addon-state-sync-island-endpoint\""));
        assertTrue(source.contains("\"database-recommended-fallback-order\""));
        assertTrue(source.contains("\"database-multi-node-warning\""));
        assertTrue(source.contains("\"database-node-local-cache-active\""));
        assertTrue(source.contains("\"runtime-standalone-island-runtime-policy\""));
        assertTrue(source.contains("\"runtime-island-runtime-authority\""));
        assertTrue(source.contains("\"runtime-tick-authority-policy\""));
        assertTrue(source.contains("\"runtime-tick-authority-local-fallback-policy\""));
        assertTrue(source.contains("\"runtime-tick-authority-core-hydrated-islands\""));
        assertTrue(source.contains("\"runtime-write-authority-policy\""));
        assertTrue(source.contains("\"runtime-write-authority-local-fallback-policy\""));
        assertTrue(source.contains("\"runtime-route-events-gate\""));
        assertTrue(source.contains("\"runtime-route-events-last-block-reason\""));
        assertTrue(source.contains("\"runtime-route-authority-policy\""));
        assertTrue(source.contains("\"runtime-route-ticket-privacy-policy\""));
        assertTrue(source.contains("\"runtime-player-surface-policy\""));
        assertTrue(source.contains("\"runtime-player-surface-hide-policy\""));
        assertTrue(source.contains("\"runtime-player-surface-command-owner-policy\""));
        assertTrue(source.contains("\"runtime-velocity-forwarding-policy\""));
        assertTrue(source.contains("\"runtime-paper-backend-access-policy\""));
        assertTrue(source.contains("\"runtime-plugin-message-security-policy\""));
        assertTrue(source.contains("\"runtime-authoritative-store-policy\""));
        assertTrue(source.contains("\"runtime-redis-advisory-policy\""));
        assertTrue(source.contains("\"runtime-redis-failure-policy\""));
        assertTrue(source.contains("\"write-gate-route-events\""));
        assertTrue(source.contains("\"addon-removal-dirty-save-detach-policy\""));
        assertTrue(source.contains("\"addon-removal-dirty-save-reattach-policy\""));
        assertTrue(source.contains("\"addon-reload-runtime-restart-policy\""));
        assertTrue(source.contains("\"addon-core-refresh-reapply-policy\""));
        assertTrue(source.contains("\"runtime-core-refresh-reapply-policy\""));
        assertTrue(source.contains("\"core-refresh-reapply-state-keys\""));
        assertTrue(source.contains("\"last-core-refresh-result\""));
        assertTrue(source.contains("\"island-state-ab-server-new-island-scenario\""));
        assertTrue(source.contains("\"island-state-ab-server-existing-island-scenario\""));
        assertTrue(source.contains("\"island-state-reload-reenable-scenario\""));
        assertTrue(source.contains("\"satis-operation-scenarios\""));
        assertTrue(source.contains("\"satis-completion-criteria\""));
        assertTrue(source.contains("\"lifecycle-event-source\""));
        assertTrue(source.contains("\"lifecycle-state-machine\""));
        assertTrue(source.contains("\"lifecycle-authority-policy\""));
        assertTrue(source.contains("\"lifecycle-error-policy\""));
        assertTrue(source.contains("\"lifecycle-recovery-policy\""));
        assertTrue(source.contains("\"route-event-last-block-reason\""));
        assertTrue(source.contains("\"last-route-player-visible-topology\""));
        assertTrue(source.contains("\"last-route-ticket-player-visible\""));
        assertTrue(source.contains("\"player-surface-policy\""));
        assertTrue(source.contains("\"player-surface-hide-policy\""));
        assertTrue(source.contains("\"player-surface-command-owner-policy\""));
        assertTrue(source.contains("\"velocity-forwarding-policy\""));
        assertTrue(source.contains("\"paper-backend-access-policy\""));
        assertTrue(source.contains("\"plugin-message-security-policy\""));
        assertTrue(source.contains("\"object-storage-access-policy\""));
        assertTrue(source.contains("\"bundle-manifest-policy\""));
        assertTrue(source.contains("\"bundle-checksum-policy\""));
        assertTrue(source.contains("\"bundle-restore-policy\""));
        assertTrue(source.contains("\"bundle-quarantine-policy\""));
        assertTrue(source.contains("\"island-state-lifecycle-error-policy\""));
        assertTrue(source.contains("\"island-state-redis-advisory-policy\""));
        assertTrue(source.contains("\"island-state-five-six-node-policy\""));
        assertTrue(source.contains("\"island-state-seven-plus-node-policy\""));
    }

    @Test
    void exposesOperationalDoctorDatabaseRuntimeRoutesAndSupportCommands() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/seungmin/satisskyfactory/command/AdminFactoryCommand.java"));
        String plugin = Files.readString(Path.of("src/main/resources/plugin.yml"));
        String messages = Files.readString(Path.of("src/main/resources/messages.yml"));

        assertTrue(source.contains("\"factory admin doctor\""));
        assertTrue(source.contains("\"factory admin database\""));
        assertTrue(source.contains("\"factory admin runtime\""));
        assertTrue(source.contains("\"factory admin routes\""));
        assertTrue(source.contains("\"factory admin support\""));
        assertTrue(source.contains("case \"doctor\" -> showDoctor(sender);"));
        assertTrue(source.contains("case \"database\" -> showDatabase(sender);"));
        assertTrue(source.contains("case \"runtime\" -> showRuntime(sender);"));
        assertTrue(source.contains("case \"routes\" -> showRoutes(sender);"));
        assertTrue(source.contains("case \"support\" -> showSupport(sender);"));
        assertTrue(source.contains("doctorAction(state)"));
        assertTrue(source.contains("\"runtime-owner-fence-ready\""));
        assertTrue(source.contains("\"database-fallback-operator-remediation\""));
        assertTrue(source.contains("\"runtime-route-events-publish-failures\""));
        assertTrue(source.contains("visibleHelpCommands(label, sender)"));
        assertTrue(plugin.contains("satisskyfactory.admin.doctor"));
        assertTrue(plugin.contains("satisskyfactory.admin.database"));
        assertTrue(plugin.contains("satisskyfactory.admin.runtime"));
        assertTrue(plugin.contains("satisskyfactory.admin.routes"));
        assertTrue(plugin.contains("satisskyfactory.admin.support"));
        assertTrue(messages.contains("admin-doctor-title"));
        assertTrue(messages.contains("admin-database-title"));
        assertTrue(messages.contains("admin-runtime-title"));
        assertTrue(messages.contains("admin-routes-title"));
        assertTrue(messages.contains("admin-support-title"));
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
                12,
                () -> {
                }
        );
    }
}
