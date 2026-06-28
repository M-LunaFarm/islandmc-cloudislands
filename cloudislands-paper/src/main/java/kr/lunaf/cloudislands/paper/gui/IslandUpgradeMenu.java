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
        GuiSession session = GuiSessions.begin(player, MENU_ID);
        GuiStateMenus.openLoading(plugin, player, session, messages, message(messages, MENU.titleKey(), TITLE));
        PaperGuiViews.islandUpgrades(client, islandId)
            .thenAccept(upgrades -> openSync(plugin, player, session, upgrades, messages))
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

    private static void openSync(Plugin plugin, Player player, GuiSession session, List<UpgradeView> upgrades, MessageRenderer messages) {
        GuiSessions.runIfCurrent(plugin, player, session, () -> {
            Inventory inventory = GuiMenuRenderer.render(MENU, session, messages, TITLE, item -> !"E".equals(item.symbol()) && !"_".equals(item.symbol()));
            List<Integer> upgradeSlots = GuiMenuRenderer.slots(MENU, "_");
            List<UpgradeView> visibleUpgrades = upgrades.stream().limit(upgradeSlots.size()).toList();
            for (int index = 0; index < visibleUpgrades.size(); index++) {
                inventory.setItem(upgradeSlots.get(index), upgradeItem(visibleUpgrades.get(index), messages));
            }
            if (upgrades.isEmpty()) {
                setEmptyItem(inventory, messages);
            }
            player.openInventory(inventory);
        });
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
