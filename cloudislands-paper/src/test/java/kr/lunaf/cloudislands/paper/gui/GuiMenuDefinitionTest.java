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
                data:
                  amount: "1000"
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
        assertEquals("1000", definition.itemAt(14).orElseThrow().data().get("amount"));
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
    void bundledBankMenuDefinitionCoversBankActionsAndPayloadsDeclaratively() {
        GuiMenuDefinition definition = GuiMenuDefinition.bundled(
            "config-v2/ui/menus/bank.yml",
            new GuiMenuDefinition("fallback", 1, "fallback.title", Map.of())
        );

        assertEquals("island.bank", definition.id());
        assertEquals(27, definition.size());
        assertEquals("menu.bank.title", definition.titleKey());
        assertEquals("B", definition.itemAt(4).orElseThrow().symbol());
        assertEquals("a", definition.itemAt(10).orElseThrow().symbol());
        assertEquals("island.bank.deposit", definition.action(definition.itemAt(10).orElseThrow().actionKey(), ""));
        assertEquals("1000", definition.itemAt(10).orElseThrow().data().get("amount"));
        assertEquals("b", definition.itemAt(11).orElseThrow().symbol());
        assertEquals("10000", definition.itemAt(11).orElseThrow().data().get("amount"));
        assertEquals("w", definition.itemAt(15).orElseThrow().symbol());
        assertEquals("island.bank.withdraw", definition.action(definition.itemAt(15).orElseThrow().actionKey(), ""));
        assertEquals("1000", definition.itemAt(15).orElseThrow().data().get("amount"));
        assertEquals("x", definition.itemAt(16).orElseThrow().symbol());
        assertEquals("10000", definition.itemAt(16).orElseThrow().data().get("amount"));
        assertEquals("M", definition.itemAt(18).orElseThrow().symbol());
        assertEquals("island.main.open", definition.action(definition.itemAt(18).orElseThrow().actionKey(), ""));
        assertEquals("R", definition.itemAt(22).orElseThrow().symbol());
        assertEquals("island.bank.open", definition.action(definition.itemAt(22).orElseThrow().actionKey(), ""));
        assertEquals("S", definition.itemAt(26).orElseThrow().symbol());
        assertEquals("island.settings.open", definition.action(definition.itemAt(26).orElseThrow().actionKey(), ""));
    }

    @Test
    void bundledInfoMenuDefinitionCoversInfoNavigationDeclaratively() {
        GuiMenuDefinition definition = GuiMenuDefinition.bundled(
            "config-v2/ui/menus/info.yml",
            new GuiMenuDefinition("fallback", 1, "fallback.title", Map.of())
        );

        assertEquals("island.info", definition.id());
        assertEquals(27, definition.size());
        assertEquals("menu.info.title", definition.titleKey());
        assertEquals("A", definition.itemAt(10).orElseThrow().symbol());
        assertEquals("B", definition.itemAt(11).orElseThrow().symbol());
        assertEquals("E", definition.itemAt(14).orElseThrow().symbol());
        assertEquals("S", definition.itemAt(16).orElseThrow().symbol());
        assertEquals("island.settings.open", definition.action(definition.itemAt(16).orElseThrow().actionKey(), ""));
        assertEquals("R", definition.itemAt(21).orElseThrow().symbol());
        assertEquals("island.ranking.open", definition.action(definition.itemAt(21).orElseThrow().actionKey(), ""));
        assertEquals("L", definition.itemAt(22).orElseThrow().symbol());
        assertEquals("island.logs.open", definition.action(definition.itemAt(22).orElseThrow().actionKey(), ""));
        assertEquals("M", definition.itemAt(23).orElseThrow().symbol());
        assertEquals("island.level.recalculate", definition.action(definition.itemAt(23).orElseThrow().actionKey(), ""));
        assertEquals("F", definition.itemAt(24).orElseThrow().symbol());
        assertEquals("island.main.open", definition.action(definition.itemAt(24).orElseThrow().actionKey(), ""));
        assertEquals("O", definition.itemAt(25).orElseThrow().symbol());
        assertEquals("island.info.open", definition.action(definition.itemAt(25).orElseThrow().actionKey(), ""));
        assertEquals("X", definition.itemAt(26).orElseThrow().symbol());
        assertEquals("gui.close", definition.action(definition.itemAt(26).orElseThrow().actionKey(), ""));
    }

    @Test
    void bundledRoleMenuDefinitionCoversRoleControlsDeclaratively() {
        GuiMenuDefinition definition = GuiMenuDefinition.bundled(
            "config-v2/ui/menus/roles.yml",
            new GuiMenuDefinition("fallback", 1, "fallback.title", Map.of())
        );

        assertEquals("island.roles", definition.id());
        assertEquals(27, definition.size());
        assertEquals("menu.roles.title", definition.titleKey());
        assertEquals("L", definition.itemAt(18).orElseThrow().symbol());
        assertEquals("island.roles.list", definition.action(definition.itemAt(18).orElseThrow().actionKey(), ""));
        assertEquals("P", definition.itemAt(19).orElseThrow().symbol());
        assertEquals("island.permissions.open", definition.action(definition.itemAt(19).orElseThrow().actionKey(), ""));
        assertEquals("R", definition.itemAt(20).orElseThrow().symbol());
        assertEquals("island.roles.open", definition.action(definition.itemAt(20).orElseThrow().actionKey(), ""));
        assertEquals("S", definition.itemAt(26).orElseThrow().symbol());
        assertEquals("island.settings.open", definition.action(definition.itemAt(26).orElseThrow().actionKey(), ""));
    }

    @Test
    void bundledMemberMenuDefinitionCoversFooterControlsDeclaratively() {
        GuiMenuDefinition definition = GuiMenuDefinition.bundled(
            "config-v2/ui/menus/members.yml",
            new GuiMenuDefinition("fallback", 1, "fallback.title", Map.of())
        );

        assertEquals("island.members", definition.id());
        assertEquals(54, definition.size());
        assertEquals("menu.members.title", definition.titleKey());
        assertEquals("I", definition.itemAt(45).orElseThrow().symbol());
        assertEquals("island.member.invite.help", definition.action(definition.itemAt(45).orElseThrow().actionKey(), ""));
        assertEquals("P", definition.itemAt(46).orElseThrow().symbol());
        assertEquals("island.members.page", definition.action(definition.itemAt(46).orElseThrow().actionKey(), ""));
        assertEquals("X", definition.itemAt(47).orElseThrow().symbol());
        assertEquals("island.permissions.open", definition.action(definition.itemAt(47).orElseThrow().actionKey(), ""));
        assertEquals("N", definition.itemAt(48).orElseThrow().symbol());
        assertEquals("R", definition.itemAt(49).orElseThrow().symbol());
        assertEquals("island.members.open", definition.action(definition.itemAt(49).orElseThrow().actionKey(), ""));
        assertEquals("L", definition.itemAt(50).orElseThrow().symbol());
        assertEquals("island.invites.open", definition.action(definition.itemAt(50).orElseThrow().actionKey(), ""));
        assertEquals("G", definition.itemAt(51).orElseThrow().symbol());
        assertEquals("island.member.list", definition.action(definition.itemAt(51).orElseThrow().actionKey(), ""));
        assertEquals("S", definition.itemAt(52).orElseThrow().symbol());
        assertEquals("island.settings.open", definition.action(definition.itemAt(52).orElseThrow().actionKey(), ""));
        assertEquals("B", definition.itemAt(53).orElseThrow().symbol());
        assertEquals("island.main.open", definition.action(definition.itemAt(53).orElseThrow().actionKey(), ""));
    }

    @Test
    void bundledPermissionMenuDefinitionCoversFooterControlsDeclaratively() {
        GuiMenuDefinition definition = GuiMenuDefinition.bundled(
            "config-v2/ui/menus/permissions.yml",
            new GuiMenuDefinition("fallback", 1, "fallback.title", Map.of())
        );

        assertEquals("island.permissions", definition.id());
        assertEquals(54, definition.size());
        assertEquals("menu.permissions.title", definition.titleKey());
        assertEquals("O", definition.itemAt(45).orElseThrow().symbol());
        assertEquals("island.permissions.page", definition.action(definition.itemAt(45).orElseThrow().actionKey(), ""));
        assertEquals("N", definition.itemAt(46).orElseThrow().symbol());
        assertEquals("P", definition.itemAt(47).orElseThrow().symbol());
        assertEquals("Q", definition.itemAt(48).orElseThrow().symbol());
        assertEquals("S", definition.itemAt(49).orElseThrow().symbol());
        assertEquals("island.permissions.save", definition.action(definition.itemAt(49).orElseThrow().actionKey(), ""));
        assertEquals("R", definition.itemAt(50).orElseThrow().symbol());
        assertEquals("island.permissions.reset", definition.action(definition.itemAt(50).orElseThrow().actionKey(), ""));
        assertEquals("L", definition.itemAt(51).orElseThrow().symbol());
        assertEquals("island.roles.open", definition.action(definition.itemAt(51).orElseThrow().actionKey(), ""));
        assertEquals("G", definition.itemAt(52).orElseThrow().symbol());
        assertEquals("island.permissions.list", definition.action(definition.itemAt(52).orElseThrow().actionKey(), ""));
        assertEquals("B", definition.itemAt(53).orElseThrow().symbol());
        assertEquals("island.settings.open", definition.action(definition.itemAt(53).orElseThrow().actionKey(), ""));
    }

    @Test
    void bundledFlagAndLimitMenuDefinitionsCoverFooterControlsDeclaratively() {
        GuiMenuDefinition flags = GuiMenuDefinition.bundled(
            "config-v2/ui/menus/flags.yml",
            new GuiMenuDefinition("fallback", 1, "fallback.title", Map.of())
        );
        GuiMenuDefinition limits = GuiMenuDefinition.bundled(
            "config-v2/ui/menus/limits.yml",
            new GuiMenuDefinition("fallback", 1, "fallback.title", Map.of())
        );

        assertEquals("island.flags", flags.id());
        assertEquals(54, flags.size());
        assertEquals("R", flags.itemAt(49).orElseThrow().symbol());
        assertEquals("island.flags.open", flags.action(flags.itemAt(49).orElseThrow().actionKey(), ""));
        assertEquals("S", flags.itemAt(53).orElseThrow().symbol());
        assertEquals("island.settings.open", flags.action(flags.itemAt(53).orElseThrow().actionKey(), ""));

        assertEquals("island.limits", limits.id());
        assertEquals(54, limits.size());
        assertEquals("M", limits.itemAt(45).orElseThrow().symbol());
        assertEquals("island.main.open", limits.action(limits.itemAt(45).orElseThrow().actionKey(), ""));
        assertEquals("R", limits.itemAt(49).orElseThrow().symbol());
        assertEquals("island.limits.open", limits.action(limits.itemAt(49).orElseThrow().actionKey(), ""));
        assertEquals("S", limits.itemAt(53).orElseThrow().symbol());
        assertEquals("island.settings.open", limits.action(limits.itemAt(53).orElseThrow().actionKey(), ""));
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

    @Test
    void bundledDangerMenuDefinitionCoversDangerActionsDeclaratively() {
        GuiMenuDefinition definition = GuiMenuDefinition.bundled(
            "config-v2/ui/menus/danger.yml",
            new GuiMenuDefinition("fallback", 1, "fallback.title", Map.of())
        );

        assertEquals("island.danger", definition.id());
        assertEquals(27, definition.size());
        assertEquals("menu.danger.title", definition.titleKey());
        assertEquals("S", definition.itemAt(10).orElseThrow().symbol());
        assertEquals("island.snapshots.open", definition.action(definition.itemAt(10).orElseThrow().actionKey(), ""));
        assertEquals("R", definition.itemAt(12).orElseThrow().symbol());
        assertEquals("island.danger.reset.prepare", definition.action(definition.itemAt(12).orElseThrow().actionKey(), ""));
        assertEquals("D", definition.itemAt(14).orElseThrow().symbol());
        assertEquals("island.danger.delete.prepare", definition.action(definition.itemAt(14).orElseThrow().actionKey(), ""));
        assertEquals("B", definition.itemAt(22).orElseThrow().symbol());
        assertEquals("island.settings.open", definition.action(definition.itemAt(22).orElseThrow().actionKey(), ""));
    }

    @Test
    void bundledDangerConfirmDefinitionsCoverConfirmationActionsDeclaratively() {
        GuiMenuDefinition reset = GuiMenuDefinition.bundled(
            "config-v2/ui/menus/danger-reset-confirm.yml",
            new GuiMenuDefinition("fallback", 1, "fallback.title", Map.of())
        );
        GuiMenuDefinition delete = GuiMenuDefinition.bundled(
            "config-v2/ui/menus/danger-delete-confirm.yml",
            new GuiMenuDefinition("fallback", 1, "fallback.title", Map.of())
        );

        assertEquals("island.danger.reset-confirm", reset.id());
        assertEquals(27, reset.size());
        assertEquals("C", reset.itemAt(11).orElseThrow().symbol());
        assertEquals("island.danger.open", reset.action(reset.itemAt(11).orElseThrow().actionKey(), ""));
        assertEquals("R", reset.itemAt(15).orElseThrow().symbol());
        assertEquals("island.danger.reset.confirm", reset.action(reset.itemAt(15).orElseThrow().actionKey(), ""));

        assertEquals("island.danger.delete-confirm", delete.id());
        assertEquals(27, delete.size());
        assertEquals("C", delete.itemAt(11).orElseThrow().symbol());
        assertEquals("island.danger.open", delete.action(delete.itemAt(11).orElseThrow().actionKey(), ""));
        assertEquals("D", delete.itemAt(15).orElseThrow().symbol());
        assertEquals("island.danger.delete.confirm", delete.action(delete.itemAt(15).orElseThrow().actionKey(), ""));
    }
}
