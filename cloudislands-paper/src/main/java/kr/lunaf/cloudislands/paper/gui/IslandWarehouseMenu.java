package kr.lunaf.cloudislands.paper.gui;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.model.IslandLimitSnapshot;
import kr.lunaf.cloudislands.common.feature.GameplayParityPolicy;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.paper.application.IslandWarehouseUseCase;
import kr.lunaf.cloudislands.paper.application.IslandWarehouseUseCase.WarehouseItemView;
import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;

public final class IslandWarehouseMenu implements Listener {
    private static final String TITLE = "공동 창고";
    private static final GuiMenuDefinition MENU = GuiMenuDefinition.bundled(
        "config-v2/ui/menus/warehouse.yml",
        new GuiMenuDefinition("island.warehouse", 6, "menu.warehouse.title", Map.of(
            "open", "island.warehouse.open",
            "list", "island.warehouse.open",
            "deposit-help", "island.warehouse.deposit.help",
            "page", "island.warehouse.page",
            "back", "island.main.open"
        ))
    );
    private static final String MENU_ID = MENU.id();
    private final MessageRenderer messages;
    private final GuiActionRegistry actions;

    public IslandWarehouseMenu() {
        this(null);
    }

    public IslandWarehouseMenu(MessageRenderer messages) {
        this(messages, new GuiActionRegistry(GuiActionExecutor.noop()));
    }

    public IslandWarehouseMenu(MessageRenderer messages, GuiActionRegistry actions) {
        this.messages = messages;
        this.actions = actions == null ? new GuiActionRegistry(GuiActionExecutor.noop()) : actions;
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player, UUID islandId, MessageRenderer messages) {
        open(plugin, client, player, islandId, messages, 0);
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player, UUID islandId, MessageRenderer messages, int page) {
        GuiSession session = GuiSessions.begin(player, MENU_ID);
        GuiStateMenus.openLoading(plugin, player, session, messages, message(messages, MENU.titleKey(), TITLE));
        warehouseRows(client, islandId)
            .thenCompose(rows -> new IslandWarehouseUseCase(client).listItems(islandId, rows * 9))
            .thenAccept(items -> openSync(plugin, player, session, islandId, items, messages, page))
            .exceptionally(error -> {
                GuiStateMenus.openError(plugin, player, session, messages, message(messages, MENU.titleKey(), TITLE), message(messages, "warehouse-menu-load-failed", "섬 창고를 불러오지 못했습니다."), "island.warehouse.open", "island.settings.open");
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
        String actionId = GuiItems.actionId(event.getCurrentItem());
        if (actionId.isBlank()) {
            return;
        }
        player.closeInventory();
        actions.execute(player, GuiActions.from(actionId, GuiItems.data(event.getCurrentItem())).orElse(null), GuiClick.from(event));
    }

    private static void openSync(Plugin plugin, Player player, GuiSession session, UUID islandId, List<WarehouseItemView> items, MessageRenderer messages, int requestedPage) {
        GuiSessions.runIfCurrent(plugin, player, session, () -> {
            List<Integer> slots = GuiMenuRenderer.slots(MENU, "_");
            List<WarehouseItemView> entries = items == null ? List.of() : items;
            int pageSize = Math.max(1, slots.size());
            int maxPage = Math.max(0, (entries.size() - 1) / pageSize);
            int page = Math.max(0, Math.min(requestedPage, maxPage));
            String title = message(messages, MENU.titleKey(), TITLE) + " " + (page + 1) + "/" + (maxPage + 1);
            Inventory inventory = GuiMenuRenderer.render(MENU, session, messages, title, item -> !List.of("E", "_", "W", "N").contains(item.symbol()));
            int offset = page * pageSize;
            for (int index = 0; index < pageSize && offset + index < entries.size(); index++) {
                WarehouseItemView itemView = entries.get(offset + index);
                int slot = slots.get(index);
                MENU.item("_").ifPresent(item -> inventory.setItem(slot, GuiMenuRenderer.item(MENU, item, messages, Map.of(), itemLore(itemView, messages))));
            }
            if (entries.isEmpty() && !slots.isEmpty()) {
                GuiMenuRenderer.setSymbolItem(inventory, MENU, "E", messages, Map.of(), List.of());
            } else {
                if (page > 0) {
                    setPageItem(inventory, "W", islandId, page - 1, messages);
                }
                if (page < maxPage) {
                    setPageItem(inventory, "N", islandId, page + 1, messages);
                }
            }
            player.openInventory(inventory);
        });
    }

    private static List<String> itemLore(WarehouseItemView item, MessageRenderer messages) {
        return List.of(
            message(messages, "warehouse-menu-material", "재료: ") + item.materialKey(),
            message(messages, "warehouse-menu-amount", "수량: ") + item.amount()
        );
    }

    private static CompletableFuture<Integer> warehouseRows(CoreApiClient client, UUID islandId) {
        return client.environment().limits(islandId)
            .thenApply(IslandWarehouseMenu::warehouseRows)
            .exceptionally(_error -> 6);
    }

    private static int warehouseRows(List<IslandLimitSnapshot> limits) {
        return limits.stream()
            .filter(limit -> GameplayParityPolicy.WAREHOUSE_ROWS_LIMIT_KEY.equalsIgnoreCase(limit.limitKey()))
            .findFirst()
            .map(IslandLimitSnapshot::value)
            .map(Long::intValue)
            .map(rows -> Math.max(1, Math.min(rows, 6)))
            .orElse(6);
    }

    private static String message(MessageRenderer messages, String key, String fallback) {
        return GuiMenuRenderer.message(messages, key, fallback);
    }

    private static void setPageItem(Inventory inventory, String symbol, UUID islandId, int page, MessageRenderer messages) {
        String key = symbol.equals("W") ? "warehouse-menu-previous-page" : "warehouse-menu-next-page";
        String fallback = symbol.equals("W") ? "이전 페이지" : "다음 페이지";
        GuiMenuRenderer.setSymbolItem(inventory, MENU, symbol, messages,
            Map.of("islandId", islandId.toString(), "page", Integer.toString(page)), List.of(message(messages, key, fallback)));
    }
}
