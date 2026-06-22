package kr.lunaf.cloudislands.paper.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;
import kr.lunaf.cloudislands.common.config.ConfigV2Validator;
import org.junit.jupiter.api.Test;

class GuiMenuDefinitionTest {
    @Test
    void allBundledMenuDefinitionsParseAndValidateFromDiscoveredResources() throws Exception {
        Path menuRoot = Path.of("src/main/resources/config-v2/ui/menus");
        try (Stream<Path> menuFiles = Files.walk(menuRoot)) {
            java.util.List<Path> files = menuFiles
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".yml") || path.toString().endsWith(".yaml"))
                .sorted()
                .toList();

            assertTrue(files.size() >= 30, "config-v2 menu resources must be discovered from the directory, not a hardcoded test list");
            for (Path file : files) {
                String yaml = Files.readString(file, StandardCharsets.UTF_8);
                String resource = "config-v2/ui/menus/" + menuRoot.relativize(file).toString().replace('\\', '/');
                GuiMenuDefinition definition = GuiMenuDefinition.bundled(
                    resource,
                    new GuiMenuDefinition("fallback", 1, "fallback.title", Map.of())
                );

                assertFalse(definition.id().isBlank(), resource + " must define a menu id");
                assertFalse(definition.layout().isEmpty(), resource + " must define a layout");
                assertTrue(ConfigV2Validator.validateMenuYaml(resource, yaml, GuiActionSchema.registeredActionIds()).valid(), resource + " must pass menu schema validation");
            }
        }
    }

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
    void bundledLogMenuDefinitionCoversFooterControlsDeclaratively() {
        GuiMenuDefinition definition = GuiMenuDefinition.bundled(
            "config-v2/ui/menus/logs.yml",
            new GuiMenuDefinition("fallback", 1, "fallback.title", Map.of())
        );

        assertEquals("island.logs", definition.id());
        assertEquals(36, definition.size());
        assertEquals("menu.logs.title", definition.titleKey());
        assertEquals(26, GuiMenuRenderer.slots(definition, "_").size());
        assertEquals("_", definition.itemAt(0).orElseThrow().symbol());
        assertEquals("M", definition.itemAt(30).orElseThrow().symbol());
        assertEquals("island.main.open", definition.action(definition.itemAt(30).orElseThrow().actionKey(), ""));
        assertEquals("R", definition.itemAt(31).orElseThrow().symbol());
        assertEquals("island.logs.open", definition.action(definition.itemAt(31).orElseThrow().actionKey(), ""));
        assertEquals("S", definition.itemAt(32).orElseThrow().symbol());
        assertEquals("island.settings.open", definition.action(definition.itemAt(32).orElseThrow().actionKey(), ""));
        assertEquals("X", definition.itemAt(35).orElseThrow().symbol());
        assertEquals("gui.close", definition.action(definition.itemAt(35).orElseThrow().actionKey(), ""));
    }

    @Test
    void bundledBanSnapshotAndUpgradeMenuDefinitionsCoverFooterControlsDeclaratively() {
        GuiMenuDefinition bans = GuiMenuDefinition.bundled(
            "config-v2/ui/menus/bans.yml",
            new GuiMenuDefinition("fallback", 1, "fallback.title", Map.of())
        );
        GuiMenuDefinition snapshots = GuiMenuDefinition.bundled(
            "config-v2/ui/menus/snapshots.yml",
            new GuiMenuDefinition("fallback", 1, "fallback.title", Map.of())
        );
        GuiMenuDefinition upgrades = GuiMenuDefinition.bundled(
            "config-v2/ui/menus/upgrades.yml",
            new GuiMenuDefinition("fallback", 1, "fallback.title", Map.of())
        );

        assertEquals("island.bans", bans.id());
        assertEquals(44, GuiMenuRenderer.slots(bans, "_").size());
        assertEquals("_", bans.itemAt(0).orElseThrow().symbol());
        assertEquals("R", bans.itemAt(49).orElseThrow().symbol());
        assertEquals("island.bans.open", bans.action(bans.itemAt(49).orElseThrow().actionKey(), ""));
        assertEquals("S", bans.itemAt(53).orElseThrow().symbol());
        assertEquals("island.settings.open", bans.action(bans.itemAt(53).orElseThrow().actionKey(), ""));

        assertEquals("island.snapshots", snapshots.id());
        assertEquals("C", snapshots.itemAt(45).orElseThrow().symbol());
        assertEquals("manual", snapshots.itemAt(45).orElseThrow().data().get("reason"));
        assertEquals("island.snapshot.create", snapshots.action(snapshots.itemAt(45).orElseThrow().actionKey(), ""));
        assertEquals("R", snapshots.itemAt(49).orElseThrow().symbol());
        assertEquals("island.snapshots.open", snapshots.action(snapshots.itemAt(49).orElseThrow().actionKey(), ""));
        assertEquals("S", snapshots.itemAt(53).orElseThrow().symbol());
        assertEquals("island.settings.open", snapshots.action(snapshots.itemAt(53).orElseThrow().actionKey(), ""));

        assertEquals("island.upgrades", upgrades.id());
        assertEquals("B", upgrades.itemAt(45).orElseThrow().symbol());
        assertEquals("island.bank.open", upgrades.action(upgrades.itemAt(45).orElseThrow().actionKey(), ""));
        assertEquals("R", upgrades.itemAt(49).orElseThrow().symbol());
        assertEquals("island.upgrades.open", upgrades.action(upgrades.itemAt(49).orElseThrow().actionKey(), ""));
        assertEquals("S", upgrades.itemAt(53).orElseThrow().symbol());
        assertEquals("island.settings.open", upgrades.action(upgrades.itemAt(53).orElseThrow().actionKey(), ""));
    }

    @Test
    void bundledCreateAndBiomeMenuDefinitionsMatchRuntimeInventoryShape() {
        GuiMenuDefinition create = GuiMenuDefinition.bundled(
            "config-v2/ui/menus/create.yml",
            new GuiMenuDefinition("fallback", 1, "fallback.title", Map.of())
        );
        GuiMenuDefinition biome = GuiMenuDefinition.bundled(
            "config-v2/ui/menus/biome.yml",
            new GuiMenuDefinition("fallback", 1, "fallback.title", Map.of())
        );

        assertEquals("island.create", create.id());
        assertEquals(27, create.size());
        assertEquals(14, GuiMenuRenderer.slots(create, "_").size());
        assertEquals("_", create.itemAt(9).orElseThrow().symbol());
        assertEquals("M", create.itemAt(18).orElseThrow().symbol());
        assertEquals("island.main.open", create.action(create.itemAt(18).orElseThrow().actionKey(), ""));
        assertEquals("R", create.itemAt(22).orElseThrow().symbol());
        assertEquals("island.create.open", create.action(create.itemAt(22).orElseThrow().actionKey(), ""));

        assertEquals("island.biome", biome.id());
        assertEquals(27, biome.size());
        assertEquals("C", biome.itemAt(4).orElseThrow().symbol());
        assertEquals("island.biome.show", biome.action(biome.itemAt(4).orElseThrow().actionKey(), ""));
        assertEquals("R", biome.itemAt(22).orElseThrow().symbol());
        assertEquals("island.biome.open", biome.action(biome.itemAt(22).orElseThrow().actionKey(), ""));
        assertEquals("S", biome.itemAt(24).orElseThrow().symbol());
        assertEquals("island.settings.open", biome.action(biome.itemAt(24).orElseThrow().actionKey(), ""));
        assertEquals("M", biome.itemAt(26).orElseThrow().symbol());
        assertEquals("island.main.open", biome.action(biome.itemAt(26).orElseThrow().actionKey(), ""));
    }

    @Test
    void bundledHomeAndInviteMenuDefinitionsCoverFooterControlsDeclaratively() {
        GuiMenuDefinition homes = GuiMenuDefinition.bundled(
            "config-v2/ui/menus/homes.yml",
            new GuiMenuDefinition("fallback", 1, "fallback.title", Map.of())
        );
        GuiMenuDefinition invites = GuiMenuDefinition.bundled(
            "config-v2/ui/menus/invites.yml",
            new GuiMenuDefinition("fallback", 1, "fallback.title", Map.of())
        );

        assertEquals("island.homes", homes.id());
        assertEquals("D", homes.itemAt(45).orElseThrow().symbol());
        assertEquals("default", homes.itemAt(45).orElseThrow().data().get("homeName"));
        assertEquals("island.home.set", homes.action(homes.itemAt(45).orElseThrow().actionKey(), ""));
        assertEquals("S", homes.itemAt(49).orElseThrow().symbol());
        assertEquals("island.settings.open", homes.action(homes.itemAt(49).orElseThrow().actionKey(), ""));
        assertEquals("M", homes.itemAt(53).orElseThrow().symbol());
        assertEquals("island.main.open", homes.action(homes.itemAt(53).orElseThrow().actionKey(), ""));

        assertEquals("island.invites", invites.id());
        assertEquals("M", invites.itemAt(45).orElseThrow().symbol());
        assertEquals("island.members.open", invites.action(invites.itemAt(45).orElseThrow().actionKey(), ""));
        assertEquals("R", invites.itemAt(49).orElseThrow().symbol());
        assertEquals("island.invites.open", invites.action(invites.itemAt(49).orElseThrow().actionKey(), ""));
        assertEquals("B", invites.itemAt(53).orElseThrow().symbol());
        assertEquals("island.main.open", invites.action(invites.itemAt(53).orElseThrow().actionKey(), ""));
    }

    @Test
    void bundledMyIslandsAndVisitMenuDefinitionsCoverNavigationDeclaratively() {
        GuiMenuDefinition myIslands = GuiMenuDefinition.bundled(
            "config-v2/ui/menus/my-islands.yml",
            new GuiMenuDefinition("fallback", 1, "fallback.title", Map.of())
        );
        GuiMenuDefinition visit = GuiMenuDefinition.bundled(
            "config-v2/ui/menus/visit.yml",
            new GuiMenuDefinition("fallback", 1, "fallback.title", Map.of())
        );

        assertEquals("island.my-islands", myIslands.id());
        assertEquals(44, GuiMenuRenderer.slots(myIslands, "_").size());
        assertEquals("_", myIslands.itemAt(0).orElseThrow().symbol());
        assertEquals("M", myIslands.itemAt(45).orElseThrow().symbol());
        assertEquals("island.main.open", myIslands.action(myIslands.itemAt(45).orElseThrow().actionKey(), ""));
        assertEquals("C", myIslands.itemAt(48).orElseThrow().symbol());
        assertEquals("island.create.open", myIslands.action(myIslands.itemAt(48).orElseThrow().actionKey(), ""));
        assertEquals("R", myIslands.itemAt(49).orElseThrow().symbol());
        assertEquals("island.list.open", myIslands.action(myIslands.itemAt(49).orElseThrow().actionKey(), ""));
        assertEquals("V", myIslands.itemAt(53).orElseThrow().symbol());
        assertEquals("island.visit.open", myIslands.action(myIslands.itemAt(53).orElseThrow().actionKey(), ""));

        assertEquals("island.visit", visit.id());
        assertEquals(35, GuiMenuRenderer.slots(visit, "_").size());
        assertEquals("_", visit.itemAt(9).orElseThrow().symbol());
        assertEquals("R", visit.itemAt(4).orElseThrow().symbol());
        assertEquals("island.visit.random", visit.action(visit.itemAt(4).orElseThrow().actionKey(), ""));
        assertEquals("P", visit.itemAt(45).orElseThrow().symbol());
        assertEquals("island.visit.public.open", visit.action(visit.itemAt(45).orElseThrow().actionKey(), ""));
        assertEquals("O", visit.itemAt(49).orElseThrow().symbol());
        assertEquals("island.visit.open", visit.action(visit.itemAt(49).orElseThrow().actionKey(), ""));
    }

    @Test
    void bundledRankingAndMissionMenuDefinitionsCoverNavigationDeclaratively() {
        GuiMenuDefinition ranking = GuiMenuDefinition.bundled(
            "config-v2/ui/menus/ranking.yml",
            new GuiMenuDefinition("fallback", 1, "fallback.title", Map.of())
        );
        GuiMenuDefinition missions = GuiMenuDefinition.bundled(
            "config-v2/ui/menus/missions.yml",
            new GuiMenuDefinition("fallback", 1, "fallback.title", Map.of())
        );

        assertEquals("island.ranking", ranking.id());
        assertEquals("T", ranking.itemAt(4).orElseThrow().symbol());
        assertEquals("P", ranking.itemAt(45).orElseThrow().symbol());
        assertEquals("island.visit.open", ranking.action(ranking.itemAt(45).orElseThrow().actionKey(), ""));
        assertEquals("R", ranking.itemAt(49).orElseThrow().symbol());
        assertEquals("island.ranking.open", ranking.action(ranking.itemAt(49).orElseThrow().actionKey(), ""));
        assertEquals("V", ranking.itemAt(53).orElseThrow().symbol());
        assertEquals("island.visit.random", ranking.action(ranking.itemAt(53).orElseThrow().actionKey(), ""));

        assertEquals("island.missions", missions.id());
        assertEquals("M", missions.itemAt(45).orElseThrow().symbol());
        assertEquals("MISSION", missions.itemAt(45).orElseThrow().data().get("kind"));
        assertEquals("island.missions.open", missions.action(missions.itemAt(45).orElseThrow().actionKey(), ""));
        assertEquals("R", missions.itemAt(49).orElseThrow().symbol());
        assertEquals("island.missions.open", missions.action(missions.itemAt(49).orElseThrow().actionKey(), ""));
        assertEquals("C", missions.itemAt(53).orElseThrow().symbol());
        assertEquals("CHALLENGE", missions.itemAt(53).orElseThrow().data().get("kind"));
    }

    @Test
    void bundledWarpMenuDefinitionsCoverPrivateAndPublicNavigationDeclaratively() {
        GuiMenuDefinition warps = GuiMenuDefinition.bundled(
            "config-v2/ui/menus/warps.yml",
            new GuiMenuDefinition("fallback", 1, "fallback.title", Map.of())
        );
        GuiMenuDefinition publicWarps = GuiMenuDefinition.bundled(
            "config-v2/ui/menus/public-warps.yml",
            new GuiMenuDefinition("fallback", 1, "fallback.title", Map.of())
        );

        assertEquals("island.warps", warps.id());
        assertEquals("W", warps.itemAt(45).orElseThrow().symbol());
        assertEquals("set-current", warps.itemAt(45).orElseThrow().data().get("mode"));
        assertEquals("S", warps.itemAt(49).orElseThrow().symbol());
        assertEquals("island.settings.open", warps.action(warps.itemAt(49).orElseThrow().actionKey(), ""));
        assertEquals("M", warps.itemAt(53).orElseThrow().symbol());
        assertEquals("island.main.open", warps.action(warps.itemAt(53).orElseThrow().actionKey(), ""));

        assertEquals("island.public-warps", publicWarps.id());
        assertEquals("R", publicWarps.itemAt(45).orElseThrow().symbol());
        assertEquals("island.visit.public.open", publicWarps.action(publicWarps.itemAt(45).orElseThrow().actionKey(), ""));
        assertEquals("S", publicWarps.itemAt(49).orElseThrow().symbol());
        assertEquals("island.settings.open", publicWarps.action(publicWarps.itemAt(49).orElseThrow().actionKey(), ""));
        assertEquals("M", publicWarps.itemAt(53).orElseThrow().symbol());
        assertEquals("island.main.open", publicWarps.action(publicWarps.itemAt(53).orElseThrow().actionKey(), ""));
    }

    @Test
    void bundledAdminNodeMenuDefinitionCoversAdminActionsDeclaratively() {
        GuiMenuDefinition definition = GuiMenuDefinition.bundled(
            "config-v2/ui/menus/admin-node.yml",
            new GuiMenuDefinition("fallback", 1, "fallback.title", Map.of())
        );

        assertEquals("admin.node", definition.id());
        assertEquals(27, definition.size());
        assertEquals("N", definition.itemAt(4).orElseThrow().symbol());
        assertEquals("L", definition.itemAt(10).orElseThrow().symbol());
        assertEquals("admin.node.list", definition.action(definition.itemAt(10).orElseThrow().actionKey(), ""));
        assertEquals("I", definition.itemAt(11).orElseThrow().symbol());
        assertEquals("admin.node.info", definition.action(definition.itemAt(11).orElseThrow().actionKey(), ""));
        assertEquals("A", definition.itemAt(12).orElseThrow().symbol());
        assertEquals("admin.node.islands", definition.action(definition.itemAt(12).orElseThrow().actionKey(), ""));
        assertEquals("W", definition.itemAt(16).orElseThrow().symbol());
        assertEquals("admin.island.where.prompt", definition.action(definition.itemAt(16).orElseThrow().actionKey(), ""));
        assertEquals("M", definition.itemAt(17).orElseThrow().symbol());
        assertEquals("admin.island.migrate.prompt", definition.action(definition.itemAt(17).orElseThrow().actionKey(), ""));
        assertEquals("K", definition.itemAt(18).orElseThrow().symbol());
        assertEquals("true", definition.itemAt(18).orElseThrow().data().get("requireShiftRight"));
        assertEquals("Q", definition.itemAt(19).orElseThrow().symbol());
        assertEquals("H", definition.itemAt(22).orElseThrow().symbol());
        assertEquals("help", definition.itemAt(22).orElseThrow().data().get("mode"));
        assertEquals("C", definition.itemAt(26).orElseThrow().symbol());
        assertEquals("gui.close", definition.action(definition.itemAt(26).orElseThrow().actionKey(), ""));
    }

    @Test
    void bundledConfirmationMenuDefinitionCoversConfirmationLayoutDeclaratively() {
        GuiMenuDefinition definition = GuiMenuDefinition.bundled(
            "config-v2/ui/menus/confirmation.yml",
            new GuiMenuDefinition("fallback", 1, "fallback.title", Map.of())
        );

        assertEquals("island.confirmation", definition.id());
        assertEquals(27, definition.size());
        assertEquals("T", definition.itemAt(4).orElseThrow().symbol());
        assertEquals("C", definition.itemAt(11).orElseThrow().symbol());
        assertEquals("gui.close", definition.action(definition.itemAt(11).orElseThrow().actionKey(), ""));
        assertEquals("A", definition.itemAt(15).orElseThrow().symbol());
        assertEquals("gui.close", definition.action(definition.itemAt(15).orElseThrow().actionKey(), ""));
    }

    @Test
    void bundledStateMenuDefinitionCoversStateSlotsDeclaratively() {
        GuiMenuDefinition definition = GuiMenuDefinition.bundled(
            "config-v2/ui/menus/state.yml",
            new GuiMenuDefinition("fallback", 1, "fallback.title", Map.of())
        );

        assertEquals("gui.state", definition.id());
        assertEquals(27, definition.size());
        assertEquals("R", definition.itemAt(11).orElseThrow().symbol());
        assertEquals("gui.close", definition.action(definition.itemAt(11).orElseThrow().actionKey(), ""));
        assertEquals("I", definition.itemAt(13).orElseThrow().symbol());
        assertEquals("B", definition.itemAt(15).orElseThrow().symbol());
        assertEquals("gui.close", definition.action(definition.itemAt(15).orElseThrow().actionKey(), ""));
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
        assertEquals("LIME_DYE", definition.itemAt(10).orElseThrow().materialKey(true));
        assertEquals("GRAY_DYE", definition.itemAt(10).orElseThrow().materialKey(false));
        assertEquals("island.public.toggle", definition.action(definition.itemAt(10).orElseThrow().actionKey(), ""));
        assertEquals("L", definition.itemAt(11).orElseThrow().symbol());
        assertEquals("IRON_DOOR", definition.itemAt(11).orElseThrow().materialKey(true));
        assertEquals("OAK_DOOR", definition.itemAt(11).orElseThrow().materialKey(false));
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
