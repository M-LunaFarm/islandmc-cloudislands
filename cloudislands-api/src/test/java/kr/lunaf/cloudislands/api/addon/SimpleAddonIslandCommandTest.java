package kr.lunaf.cloudislands.api.addon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SimpleAddonIslandCommandTest {
    @Test
    void builderConnectsExecutorAndTabSuggestionsWithoutACommandSubclass() {
        SimpleAddonIslandCommand command = SimpleAddonIslandCommand.builder("example", "hello")
            .aliases("hi")
            .permission("example.hello")
            .arguments(0, 1)
            .executor(context -> AddonIslandCommandResult.message("hello " + context.playerUuid()))
            .suggestions(_context -> List.of("world", "island"))
            .build();
        AddonIslandCommandContext context = new AddonIslandCommandContext(UUID.randomUUID(), "is", "hello", List.of());

        assertEquals(List.of("hello", "hi"), command.aliases());
        assertEquals("example.hello", command.permission());
        assertTrue(command.execute(context).join().accepted());
        assertEquals(List.of("world", "island"), command.tabComplete(context).join());
    }
}
