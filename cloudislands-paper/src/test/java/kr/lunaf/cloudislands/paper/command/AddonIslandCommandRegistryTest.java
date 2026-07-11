package kr.lunaf.cloudislands.paper.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.addon.AddonIslandCommand;
import kr.lunaf.cloudislands.api.addon.AddonIslandCommandContext;
import kr.lunaf.cloudislands.api.addon.AddonIslandCommandResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AddonIslandCommandRegistryTest {
    private final AddonIslandCommandRegistry registry = AddonIslandCommandRegistry.global();

    @BeforeEach
    @AfterEach
    void clearRegistry() {
        registry.clear();
    }

    @Test
    void registersAliasesHelpAndSnapshotsThenCleansUpByAddon() {
        var snapshot = registry.register(command("test-addon", List.of("near", "nearby")));

        assertEquals("near", snapshot.primaryAlias());
        assertEquals(List.of("near", "nearby"), snapshot.aliases());
        assertEquals(List.of("섬 near [radius]"), registry.helpCommands());
        assertEquals(1, registry.snapshots().size());

        registry.unregisterAddon("test-addon");
        assertTrue(registry.snapshots().isEmpty());
        assertTrue(registry.helpCommands().isEmpty());
    }

    @Test
    void rejectsBuiltInAndCrossAddonAliasCollisions() {
        assertThrows(IllegalArgumentException.class, () -> registry.register(command("test-addon", List.of("home"))));
        assertThrows(IllegalArgumentException.class, () -> registry.register(command("test-addon", List.of("uncoop"))));
        assertThrows(IllegalArgumentException.class, () -> registry.register(command("test-addon", List.of("bad alias"))));
        registry.register(command("first-addon", List.of("near")));
        assertThrows(IllegalArgumentException.class, () -> registry.register(command("second-addon", List.of("near"))));
    }

    @Test
    void oneAddonCanRegisterMultipleCommands() {
        registry.register(command("test-addon", List.of("near", "nearby")));
        registry.register(command("test-addon", List.of("locate")));

        assertEquals(2, registry.snapshots().size());
        assertEquals(List.of("locate", "near"), registry.snapshots().stream().map(snapshot -> snapshot.primaryAlias()).sorted().toList());
    }


    private static AddonIslandCommand command(String addonId, List<String> aliases) {
        return new AddonIslandCommand() {
            @Override public String addonId() { return addonId; }
            @Override public List<String> aliases() { return aliases; }
            @Override public String usage() { return "[radius]"; }
            @Override public String description() { return "Find nearby islands"; }
            @Override public CompletableFuture<AddonIslandCommandResult> execute(AddonIslandCommandContext context) { return CompletableFuture.completedFuture(AddonIslandCommandResult.success()); }
        };
    }

}
