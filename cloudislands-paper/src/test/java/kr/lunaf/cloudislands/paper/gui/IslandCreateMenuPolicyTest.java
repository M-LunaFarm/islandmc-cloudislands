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
        assertFalse(source.contains(".limit(14)"), "Enabled templates must not disappear after the first GUI page");
        assertTrue(source.contains("int maxPage = Math.max(0, (enabled.size() - 1) / pageSize)"), "Create menu must paginate the complete enabled template catalog");
        assertTrue(source.contains("canUse(player, template)"), "Every page must preserve per-template permission rendering");
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
        assertTrue(handler.contains("GuiSession session = GuiStateMenus.openSaving"), "Create progress must reserve a GUI session before asynchronous work");
        assertTrue(handler.contains("openSuccess(plugin, activePlayer, session"), "Late create success must not replace a newer menu");
        assertTrue(handler.contains("openError(plugin, activePlayer, session"), "Late create failure must not replace a newer menu");
        assertTrue(handler.contains("finishCreate(playerSession, session, messages, result)"), "Create completion must retain the initiating connection");
        assertTrue(handler.contains("Player activePlayer = currentPlayer(playerSession)"), "Create completion must re-resolve the exact online connection");
        assertFalse(handler.contains("GuiStateMenus.openSuccess(plugin, player, session"), "Create completion must not manipulate a captured Player from the Core callback");
        assertTrue(handler.contains("runtime.playerCodeMessage(result.code()"), "Create errors must preserve code-specific player messaging");
    }

    @Test
    void directCreateCommandHonorsTemplatePermission() throws IOException {
        String handler = read("src/main/java/kr/lunaf/cloudislands/paper/command/IslandLifecycleCommandHandler.java");
        String menu = read("src/main/java/kr/lunaf/cloudislands/paper/gui/IslandCreateMenu.java");
        String backend = read("src/main/java/kr/lunaf/cloudislands/paper/command/IslandCommandBackend.java");
        String messages = read("src/main/java/kr/lunaf/cloudislands/paper/command/IslandCommandMessages.java");

        assertTrue(handler.contains("coreApiClient.templates().get(normalizedTemplateId)"), "Direct /is create <template> must load template metadata before mutation");
        assertTrue(handler.contains("canUseTemplate(playerSession, template)"), "Direct create must check template requiredPermission");
        assertTrue(handler.contains("PaperSchedulers.supply(plugin, () -> canUseTemplate(playerSession, template))"), "Template permissions must be read on the Paper main thread after the Core response");
        assertTrue(handler.contains("return playerSession.isCurrent(activePlayer) ? activePlayer : null;"), "A replacement connection must not inherit delayed template permission checks");
        assertTrue(menu.contains("thenAccept(templates -> PaperSchedulers.run(plugin, () -> {"), "Confirmation permission checks must return to the Paper main thread");
        assertTrue(handler.contains("TEMPLATE_PERMISSION_DENIED"), "Direct create must reject locked templates without calling Core create");
        assertTrue(backend.contains("new IslandLifecycleCommandHandler(plugin, coreApiClient, economyBridge, runtimeServices)"), "Lifecycle create handler must receive the economy bridge");
        assertTrue(messages.contains("TEMPLATE_PERMISSION_DENIED"), "Template permission denial must have a player-safe message");
    }

    @Test
    void paidTemplatesChargeAndRefundAroundCreateMutation() throws IOException {
        String handler = read("src/main/java/kr/lunaf/cloudislands/paper/command/IslandLifecycleCommandHandler.java");
        String messages = read("src/main/java/kr/lunaf/cloudislands/paper/command/IslandCommandMessages.java");

        assertTrue(handler.contains("creationCost(template)"), "Create flow must parse template creationCost");
        assertTrue(handler.contains("economyBridge.withdraw(playerUuid, creationCost"), "Paid templates must charge before Core create");
        assertTrue(handler.contains("creationUseCase.create(playerUuid, templateId, runtime::mutate)"), "Core create must happen after the cost preflight");
        assertTrue(handler.contains("refundCreateCost(playerUuid, creationCost, template.id())"), "Failed Core create must refund the player");
        assertTrue(handler.contains("ECONOMY_CHARGE_FAILED"), "Charge failures must be user-visible");
        assertTrue(handler.contains("ECONOMY_REFUND_FAILED"), "Refund failures must be user-visible");
        assertTrue(handler.contains("pendingCreations.acquire(playerUuid)"), "Create flow must reject duplicate in-flight requests before charging");
        assertTrue(handler.contains("pendingCreations.release(playerUuid)"), "Create flow must release its player lock after settlement");
        assertTrue(handler.contains("CORE_CREATE_FAILED_REFUNDED"), "Core exceptions with a successful refund must be explicit");
        assertTrue(messages.contains("ECONOMY_CHARGE_FAILED"), "Charge failure must have a player-safe message");
        assertTrue(messages.contains("ECONOMY_REFUND_FAILED"), "Refund failure must have a player-safe message");
    }

    @Test
    void paidCreateRefundsBeforeCoreMutationWhenTheConnectionWasReplaced() throws IOException {
        String handler = read("src/main/java/kr/lunaf/cloudislands/paper/command/IslandLifecycleCommandHandler.java");

        assertTrue(handler.contains("PlayerConnectionSession playerSession = PlayerConnectionSession.capture(player)"));
        assertTrue(handler.contains("PaperSchedulers.supply(plugin, () -> currentPlayer(playerSession) != null)"),
            "the connection must be revalidated after the asynchronous economy charge");
        assertTrue(handler.contains("? settleChargedCreate(playerUuid, templateId, template, creationCost)"));
        assertTrue(handler.contains(": refundReplacedCreate(playerUuid, creationCost, template.id())"));
        assertTrue(handler.contains("new CreateIslandResult(false, \"PLAYER_SESSION_REPLACED\", null, null)"));
        assertTrue(handler.indexOf("currentPlayer(playerSession) != null") < handler.indexOf("settleChargedCreate(playerUuid"),
            "Core create must not start before the post-charge connection fence");
        assertTrue(handler.contains("deliverMessage(playerSession"),
            "delete and reset results must not be delivered to a replacement connection");
    }

    private static String read(String relative) throws IOException {
        return Files.readString(Path.of(relative));
    }
}
