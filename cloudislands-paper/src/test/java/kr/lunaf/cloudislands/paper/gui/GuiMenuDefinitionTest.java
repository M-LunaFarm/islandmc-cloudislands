package kr.lunaf.cloudislands.paper.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GuiMenuDefinitionTest {
    @Test
    void parsesMenuMetadataAndActionAliases() throws Exception {
        String yaml = """
            id: island.chat
            rows: 3
            title-key: menu.chat.title
            actions:
              logs: island.logs.open
              settings: island.settings.open
            """;

        GuiMenuDefinition definition = GuiMenuDefinition.parse(
            new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)),
            new GuiMenuDefinition("fallback", 1, "fallback.title", Map.of("back", "island.main.open"))
        );

        assertEquals("island.chat", definition.id());
        assertEquals(27, definition.size());
        assertEquals("menu.chat.title", definition.titleKey());
        assertEquals("island.logs.open", definition.action("logs", ""));
        assertEquals("island.main.open", definition.action("back", ""));
    }

    @Test
    void bundledChatMenuDefinitionStaysAlignedWithConfigV2Resource() {
        GuiMenuDefinition definition = GuiMenuDefinition.bundled(
            "config-v2/ui/menus/chat.yml",
            new GuiMenuDefinition("fallback", 1, "fallback.title", Map.of())
        );

        assertEquals("island.chat", definition.id());
        assertEquals(27, definition.size());
        assertEquals("menu.chat.title", definition.titleKey());
        assertEquals("island.logs.open", definition.action("logs", ""));
        assertEquals("island.settings.open", definition.action("settings", ""));
    }
}
