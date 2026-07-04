package kr.lunaf.cloudislands.paper.gui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class IslandCreateMenuPolicyTest {
    @Test
    void createMenuShowsLockedTemplatesInsteadOfHidingThem() throws IOException {
        String source = read("src/main/java/kr/lunaf/cloudislands/paper/gui/IslandCreateMenu.java");

        assertFalse(source.contains(".filter(template -> template.requiredPermission().isBlank() || player.hasPermission(template.requiredPermission()))"),
            "Create menu must not hide locked templates from the comparison UI");
        assertTrue(source.contains("\"island.create.locked\""), "Locked templates must render as a typed locked action");
        assertTrue(source.contains("Material.BARRIER"), "Locked templates must be visually distinct");
        assertTrue(source.contains("create-menu-locked"), "Locked templates must explain the missing permission state");
    }

    @Test
    void createMenuRequiresConfirmationBeforeCreateMutation() throws IOException {
        String menu = read("src/main/java/kr/lunaf/cloudislands/paper/gui/IslandCreateMenu.java");
        String handler = read("src/main/java/kr/lunaf/cloudislands/paper/command/IslandLifecycleCommandHandler.java");
        String menuResource = read("src/main/resources/config-v2/ui/menus/create.yml");

        assertTrue(menu.contains("\"create\", \"island.create.prepare\""), "Template list clicks must prepare a confirm screen");
        assertTrue(menuResource.contains("create: island.create.prepare"), "Bundled create menu must emit prepare, not direct mutation");
        assertTrue(menu.contains("CONFIRM_MENU"), "Create flow must provide a dedicated confirmation menu");
        assertTrue(menu.contains("\"confirm\", \"island.create\""), "Only the confirm item may emit the create mutation action");
        assertTrue(handler.contains("GuiAction.IslandCreatePrepare"), "Lifecycle handler must route prepare actions to confirmation UI");
        assertTrue(handler.contains("IslandCreateMenu.openConfirm"), "Create prepare must fetch and render the selected template before mutation");
    }

    @Test
    void createMutationShowsProgressAndTerminalState() throws IOException {
        String handler = read("src/main/java/kr/lunaf/cloudislands/paper/command/IslandLifecycleCommandHandler.java");

        assertTrue(handler.contains("GuiStateMenus.openSaving"), "Create mutation must show a progress state item");
        assertTrue(handler.contains("GuiStateMenus.openSuccess"), "Accepted create requests must show a success state");
        assertTrue(handler.contains("GuiStateMenus.openError"), "Rejected or failed create requests must show an error state");
        assertTrue(handler.contains("runtime.playerCodeMessage(result.code()"), "Create errors must preserve code-specific player messaging");
    }

    @Test
    void directCreateCommandHonorsTemplatePermission() throws IOException {
        String handler = read("src/main/java/kr/lunaf/cloudislands/paper/command/IslandLifecycleCommandHandler.java");
        String messages = read("src/main/java/kr/lunaf/cloudislands/paper/command/IslandCommandMessages.java");

        assertTrue(handler.contains("coreApiClient.templates().get(normalizedTemplateId)"), "Direct /is create <template> must load template metadata before mutation");
        assertTrue(handler.contains("canUseTemplate(player, template)"), "Direct create must check template requiredPermission");
        assertTrue(handler.contains("TEMPLATE_PERMISSION_DENIED"), "Direct create must reject locked templates without calling Core create");
        assertTrue(messages.contains("TEMPLATE_PERMISSION_DENIED"), "Template permission denial must have a player-safe message");
    }

    private static String read(String relative) throws IOException {
        return Files.readString(Path.of(relative));
    }
}
