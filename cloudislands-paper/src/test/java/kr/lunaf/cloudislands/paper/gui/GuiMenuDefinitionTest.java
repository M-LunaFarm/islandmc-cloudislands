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

    @Test
    void bundledMainMenuDefinitionCoversLegacyMainMenuActionsDeclaratively() {
        GuiMenuDefinition definition = GuiMenuDefinition.bundled(
            "config-v2/ui/menus/main.yml",
            new GuiMenuDefinition("fallback", 1, "fallback.title", Map.of())
        );

        assertEquals("island.main", definition.id());
        assertEquals(54, definition.size());
        assertEquals("menu.main.title", definition.titleKey());
        assertEquals("H", definition.itemAt(10).orElseThrow().symbol());
        assertEquals("island.home", definition.itemAt(10).orElseThrow().actionKey());
        assertEquals("C", definition.itemAt(12).orElseThrow().symbol());
        assertEquals("island.create.open", definition.itemAt(12).orElseThrow().actionKey());
        assertEquals("Y", definition.itemAt(19).orElseThrow().symbol());
        assertEquals("island.list.open", definition.itemAt(19).orElseThrow().actionKey());
        assertEquals("B", definition.itemAt(32).orElseThrow().symbol());
        assertEquals("island.bank.open", definition.itemAt(32).orElseThrow().actionKey());
        assertEquals("N", definition.itemAt(43).orElseThrow().symbol());
        assertEquals("island.missions.open", definition.itemAt(43).orElseThrow().actionKey());
        assertEquals("E", definition.itemAt(46).orElseThrow().symbol());
        assertEquals("island.biome.open", definition.itemAt(46).orElseThrow().actionKey());
        assertEquals("A", definition.itemAt(52).orElseThrow().symbol());
        assertEquals("admin.node.open", definition.itemAt(52).orElseThrow().actionKey());
        assertEquals("D", definition.itemAt(53).orElseThrow().symbol());
    }

    @Test
    void bundledSettingsMenuDefinitionCoversSettingsNavigationDeclaratively() {
        GuiMenuDefinition definition = GuiMenuDefinition.bundled(
            "config-v2/ui/menus/settings.yml",
            new GuiMenuDefinition("fallback", 1, "fallback.title", Map.of())
        );

        assertEquals("island.settings", definition.id());
        assertEquals(27, definition.size());
        assertEquals("menu.settings.title", definition.titleKey());
        assertEquals("P", definition.itemAt(10).orElseThrow().symbol());
        assertEquals("island.public.toggle", definition.action(definition.itemAt(10).orElseThrow().actionKey(), ""));
        assertEquals("L", definition.itemAt(11).orElseThrow().symbol());
        assertEquals("island.lock.toggle", definition.action(definition.itemAt(11).orElseThrow().actionKey(), ""));
        assertEquals("M", definition.itemAt(12).orElseThrow().symbol());
        assertEquals("island.members.open", definition.action(definition.itemAt(12).orElseThrow().actionKey(), ""));
        assertEquals("X", definition.itemAt(13).orElseThrow().symbol());
        assertEquals("island.permissions.open", definition.action(definition.itemAt(13).orElseThrow().actionKey(), ""));
        assertEquals("R", definition.itemAt(17).orElseThrow().symbol());
        assertEquals("island.roles.open", definition.action(definition.itemAt(17).orElseThrow().actionKey(), ""));
        assertEquals("D", definition.itemAt(26).orElseThrow().symbol());
        assertEquals("island.danger.open", definition.action(definition.itemAt(26).orElseThrow().actionKey(), ""));
    }
}
