package kr.lunaf.cloudislands.paper.gui;

import java.util.List;
import java.util.UUID;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.paper.application.view.PaperGuiViews;
import kr.lunaf.cloudislands.paper.application.view.PaperGuiViews.BankView;
import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;

public final class IslandBankMenu implements Listener {
    private static final GuiMenuDefinition MENU = GuiMenuDefinition.bundled(
        "config-v2/ui/menus/bank.yml",
        new GuiMenuDefinition("island.bank", 3, "bank-menu-title", java.util.Map.of(
            "open", "island.bank.open",
            "deposit", "island.bank.deposit",
            "withdraw", "island.bank.withdraw",
            "back", "island.main.open",
            "settings", "island.settings.open"
        ))
    );
    private static final String MENU_ID = MENU.id();
    private static final String TITLE = "섬 은행";
    private final MessageRenderer messages;
    private final GuiActionRegistry actions;

    public IslandBankMenu() {
        this(null);
    }

    public IslandBankMenu(MessageRenderer messages) {
        this(messages, new GuiActionRegistry(GuiActionExecutor.noop()));
    }

    public IslandBankMenu(MessageRenderer messages, GuiActionRegistry actions) {
        this.messages = messages;
        this.actions = actions == null ? new GuiActionRegistry(GuiActionExecutor.noop()) : actions;
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player, UUID islandId) {
        open(plugin, client, player, islandId, null);
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player, UUID islandId, MessageRenderer messages) {
        GuiSession session = GuiSessions.begin(player, MENU_ID);
        GuiStateMenus.openLoading(plugin, player, session, messages, message(messages, MENU.titleKey(), TITLE));
        PaperGuiViews.islandBank(client, islandId)
            .thenAccept(view -> openSync(plugin, player, session, view, messages))
            .exceptionally(error -> {
                GuiStateMenus.openError(plugin, player, session, messages, message(messages, MENU.titleKey(), TITLE), message(messages, "bank-menu-load-failed", "섬 은행을 불러오지 못했습니다."), "island.bank.open", "island.settings.open");
                return null;
            });
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!GuiItems.menuClick(event, MENU_ID)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getCurrentItem() == null) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 27) {
            return;
        }
        String actionId = GuiItems.actionId(event.getCurrentItem());
        if (actionId.isBlank()) {
            return;
        }
        player.closeInventory();
        actions.execute(player, actionId, GuiItems.data(event.getCurrentItem()), GuiClick.from(event));
    }

    private static void openSync(Plugin plugin, Player player, GuiSession session, BankView view, MessageRenderer messages) {
        GuiSessions.runIfCurrent(plugin, player, session, () -> {
            Inventory inventory = GuiMenuRenderer.render(MENU, session, messages, TITLE, item -> true);
            setBalanceItem(inventory, messages, view);
            player.openInventory(inventory);
        });
    }

    private static String message(MessageRenderer messages, String key, String fallback) {
        return GuiMenuRenderer.message(messages, key, fallback);
    }

    private static void setBalanceItem(Inventory inventory, MessageRenderer messages, BankView view) {
        MENU.itemAt(4)
            .ifPresent(item -> inventory.setItem(4, GuiMenuRenderer.item(MENU, item, messages, java.util.Map.of(), List.of(
                message(messages, "bank-menu-current-balance", "현재 잔액: ") + (view.balance().isBlank() ? "0" : view.balance()),
                view.updatedAt().isBlank() ? message(messages, "bank-menu-no-update", "업데이트 정보 없음") : message(messages, "bank-menu-updated-at", "갱신 시각: ") + view.updatedAt()
            ), "")));
    }

}
