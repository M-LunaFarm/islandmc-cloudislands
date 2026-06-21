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
            layout:
              - "........."
              - ".I.T.LSM."
              - "....C...."
            items:
              I:
                material: WRITABLE_BOOK
                name-key: chat-menu-island-name
                fallback-name: 섬 채팅 보내기
                lore-keys:
                  - chat-menu-island-usage
                fallback-lore:
                  - "사용법: /섬 채팅 <메시지>"
              L:
                material: CLOCK
                name-key: chat-menu-log-name
                fallback-name: 최근 섬 로그
                action: logs
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
        assertEquals("I", definition.itemAt(10).orElseThrow().symbol());
        assertEquals("WRITABLE_BOOK", definition.itemAt(10).orElseThrow().materialKey());
        assertEquals("logs", definition.itemAt(14).orElseThrow().actionKey());
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
        assertEquals("I", definition.itemAt(10).orElseThrow().symbol());
        assertEquals("T", definition.itemAt(12).orElseThrow().symbol());
        assertEquals("logs", definition.itemAt(14).orElseThrow().actionKey());
    }
}
