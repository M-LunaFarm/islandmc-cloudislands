package kr.lunaf.cloudislands.paper.gui;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.paper.application.view.PaperGuiViews;
import kr.lunaf.cloudislands.paper.application.view.PaperGuiViews.LimitView;
import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

public final class IslandLimitMenu implements Listener {
    private static final String TITLE_KEY = "limit-menu-title";
    private static final String TITLE = "섬 제한";
    private static final GuiMenuDefinition MENU = GuiMenuDefinition.bundled(
        "config-v2/ui/menus/limits.yml",
        new GuiMenuDefinition("island.limits", 6, TITLE_KEY, Map.of(
            "open", "island.limits.open",
            "list", "island.limits.list",
            "set", "island.limit.set",
            "main", "island.main.open",
            "back", "island.settings.open"
        ))
    );
    private static final String MENU_ID = MENU.id();
    private final MessageRenderer messages;
    private final GuiActionRegistry actions;

    public IslandLimitMenu() {
        this(null);
    }

    public IslandLimitMenu(MessageRenderer messages) {
        this(messages, new GuiActionRegistry(GuiActionExecutor.noop()));
    }

    public IslandLimitMenu(MessageRenderer messages, GuiActionRegistry actions) {
        this.messages = messages;
        this.actions = actions == null ? new GuiActionRegistry(GuiActionExecutor.noop()) : actions;
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player, UUID islandId) {
        open(plugin, client, player, islandId, null);
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player, UUID islandId, MessageRenderer messages) {
        GuiSession session = GuiSessions.begin(player, MENU_ID);
        GuiStateMenus.openLoading(plugin, player, session, messages, message(messages, TITLE_KEY, TITLE));
        PaperGuiViews.islandLimits(client, islandId)
            .thenAccept(limits -> openSync(plugin, player, session, limits, messages))
            .exceptionally(error -> {
                GuiStateMenus.openError(plugin, player, session, messages, message(messages, TITLE_KEY, TITLE), message(messages, "limit-menu-load-failed", "섬 제한을 불러오지 못했습니다."), "island.limits.open", "island.settings.open");
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
        if (slot < 0 || slot >= 54) {
            return;
        }
        String actionId = GuiItems.actionId(event.getCurrentItem());
        if (!actionId.isBlank()) {
            player.closeInventory();
            actions.execute(player, actionId, GuiClick.from(event));
            return;
        }
        Map<String, String> data = GuiItems.data(event.getCurrentItem());
        String limitKey = data.getOrDefault("limitKey", "");
        if (limitKey.isBlank()) {
            return;
        }
        long value = number(data.getOrDefault("value", "0"));
        long step = event.isShiftClick() ? 10L : 1L;
        long nextValue = event.isRightClick() ? Math.max(0L, value - step) : value + step;
        player.closeInventory();
        actions.execute(player, "island.limit.set", java.util.Map.of("limitKey", limitKey, "value", String.valueOf(nextValue)), GuiClick.from(event));
    }

    private static void openSync(Plugin plugin, Player player, GuiSession session, List<LimitView> limits, MessageRenderer messages) {
        GuiSessions.runIfCurrent(plugin, player, session, () -> {
            Inventory inventory = GuiMenuRenderer.render(MENU, session, messages, TITLE, item -> !"E".equals(item.symbol()));
            int slot = 0;
            for (LimitView limit : limits.stream().limit(45).toList()) {
                inventory.setItem(slot++, limitItem(limit, messages));
            }
            if (limits.isEmpty()) {
                setEmptyItem(inventory, messages);
            }
            player.openInventory(inventory);
        });
    }

    private static ItemStack limitItem(LimitView limit, MessageRenderer messages) {
        return GuiItems.action(Material.HOPPER, limit.key(), "island.limit.set",
            Map.of("limitKey", limit.key(), "value", String.valueOf(limit.value())),
            message(messages, "limit-menu-current-value", "현재 값: ") + limit.value(),
            limit.updatedAt().isBlank() ? message(messages, "limit-menu-no-update", "업데이트 정보 없음") : message(messages, "limit-menu-updated-at", "갱신 시각: ") + limit.updatedAt(),
            message(messages, "limit-menu-left-click", "좌클릭: +1"),
            message(messages, "limit-menu-right-click", "우클릭: -1"),
            message(messages, "limit-menu-shift-click", "Shift+클릭: 10 단위로 조정"));
    }

    private static String message(MessageRenderer messages, String key, String fallback) {
        return GuiMenuRenderer.message(messages, key, fallback);
    }

    private static void setEmptyItem(Inventory inventory, MessageRenderer messages) {
        MENU.itemAt(22)
            .ifPresent(item -> inventory.setItem(22, GuiMenuRenderer.item(MENU, item, messages, Map.of(), List.of())));
    }

    private static long number(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            return 0L;
        }
    }

}
