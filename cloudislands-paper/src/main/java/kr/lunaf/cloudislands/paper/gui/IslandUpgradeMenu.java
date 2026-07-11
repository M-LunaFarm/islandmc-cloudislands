package kr.lunaf.cloudislands.paper.gui;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.paper.application.view.PaperGuiViews;
import kr.lunaf.cloudislands.paper.application.view.PaperGuiViews.UpgradeView;
import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

public final class IslandUpgradeMenu implements Listener {
    private static final String TITLE = "섬 업그레이드";
    private static final GuiMenuDefinition MENU = GuiMenuDefinition.bundled(
        "config-v2/ui/menus/upgrades.yml",
        new GuiMenuDefinition("island.upgrades", 6, "menu.upgrades.title", Map.of(
            "open", "island.upgrades.open",
            "page", "island.upgrades.page",
            "list", "island.upgrades.list",
            "purchase", "island.upgrade.purchase",
            "bank", "island.bank.open",
            "settings", "island.settings.open",
            "back", "island.main.open"
        ))
    );
    private static final String MENU_ID = MENU.id();
    private final MessageRenderer messages;
    private final GuiActionRegistry actions;

    public IslandUpgradeMenu() {
        this(null);
    }

    public IslandUpgradeMenu(MessageRenderer messages) {
        this(messages, new GuiActionRegistry(GuiActionExecutor.noop()));
    }

    public IslandUpgradeMenu(MessageRenderer messages, GuiActionRegistry actions) {
        this.messages = messages;
        this.actions = actions == null ? new GuiActionRegistry(GuiActionExecutor.noop()) : actions;
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player, UUID islandId) {
        open(plugin, client, player, islandId, null);
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player, UUID islandId, MessageRenderer messages) {
        open(plugin, client, player, islandId, messages, 0);
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player, UUID islandId, MessageRenderer messages, int page) {
        GuiSession session = GuiSessions.begin(player, MENU_ID);
        GuiStateMenus.openLoading(plugin, player, session, messages, message(messages, MENU.titleKey(), TITLE));
        PaperGuiViews.islandUpgrades(client, islandId)
            .thenAccept(upgrades -> openSync(plugin, player, session, islandId, page, upgrades, messages))
            .exceptionally(error -> {
                GuiStateMenus.openError(plugin, player, session, messages, message(messages, MENU.titleKey(), TITLE), message(messages, "upgrade-menu-load-failed", "섬 업그레이드를 불러오지 못했습니다."), "island.upgrades.open", "island.settings.open");
                return null;
            });
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!GuiInventories.isMenu(event.getView().getTopInventory(), MENU_ID)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getCurrentItem() == null || !GuiItems.topInventoryClick(event)) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= MENU.size()) {
            return;
        }
        Map<String, String> data = GuiItems.data(event.getCurrentItem());
        String actionId = GuiItems.actionId(event.getCurrentItem());
        String key = data.getOrDefault("upgradeKey", "");
        if (key.isBlank()) {
            if (actionId.isBlank()) {
                return;
            }
            player.closeInventory();
            actions.execute(player, GuiActions.from(actionId, data).orElse(null), GuiClick.from(event));
            return;
        }
        player.closeInventory();
        actions.execute(player, new GuiAction.UpgradePurchase(key), GuiClick.from(event));
    }

    private static void openSync(Plugin plugin, Player player, GuiSession session, UUID islandId, int requestedPage, List<UpgradeView> upgrades, MessageRenderer messages) {
        GuiSessions.runIfCurrent(plugin, player, session, () -> {
            List<Integer> upgradeSlots = GuiMenuRenderer.slots(MENU, "_");
            int pageSize = Math.max(1, upgradeSlots.size());
            int maxPage = Math.max(0, (upgrades.size() - 1) / pageSize);
            int page = Math.max(0, Math.min(requestedPage, maxPage));
            Inventory inventory = GuiMenuRenderer.render(MENU, session, messages,
                TITLE + " (" + (page + 1) + "/" + (maxPage + 1) + ")",
                item -> !List.of("E", "_", "P", "N").contains(item.symbol()));
            List<UpgradeView> visibleUpgrades = upgrades.stream().skip((long) page * pageSize).limit(pageSize).toList();
            for (int index = 0; index < visibleUpgrades.size(); index++) {
                inventory.setItem(upgradeSlots.get(index), upgradeItem(visibleUpgrades.get(index), messages));
            }
            if (upgrades.isEmpty()) {
                setEmptyItem(inventory, messages);
            }
            if (page > 0) {
                setPageItem(inventory, "P", islandId, page - 1, messages);
            }
            if (page < maxPage) {
                setPageItem(inventory, "N", islandId, page + 1, messages);
            }
            player.openInventory(inventory);
        });
    }

    private static void setPageItem(Inventory inventory, String symbol, UUID islandId, int page, MessageRenderer messages) {
        GuiMenuRenderer.setSymbolItem(inventory, MENU, symbol, messages,
            Map.of("islandId", islandId.toString(), "page", Integer.toString(page)), List.of());
    }

    private static ItemStack upgradeItem(UpgradeView upgrade, MessageRenderer messages) {
        GuiButtonState state = GuiButtonState.ENABLED;
        return GuiItems.action(GuiMenuRenderer.material(MENU, upgrade.type(), "_", "BEACON"), upgrade.key(), "island.upgrade.purchase",
            Map.of("upgradeKey", upgrade.key()),
            state.lore(messages),
            message(messages, "upgrade-menu-type", "유형: ") + upgrade.type(),
            message(messages, "upgrade-menu-current-level", "현재 레벨: ") + upgrade.level(),
            message(messages, "upgrade-menu-click-to-buy", "클릭하면 다음 레벨 구매를 요청합니다."));
    }

    private static String message(MessageRenderer messages, String key, String fallback) {
        return GuiMenuRenderer.message(messages, key, fallback);
    }

    private static void setEmptyItem(Inventory inventory, MessageRenderer messages) {
        GuiMenuRenderer.setSymbolItem(inventory, MENU, "E", messages, Map.of(), List.of());
    }

}
