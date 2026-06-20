package kr.lunaf.cloudislands.paper.gui;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.paper.application.view.PaperGuiViews;
import kr.lunaf.cloudislands.paper.application.view.PaperGuiViews.LimitView;
import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

public final class IslandLimitMenu implements Listener {
    private static final String TITLE_KEY = "limit-menu-title";
    private static final String TITLE = "섬 제한";
    private static final String MENU_ID = "island.limits";
    private final MessageRenderer messages;

    public IslandLimitMenu() {
        this(null);
    }

    public IslandLimitMenu(MessageRenderer messages) {
        this.messages = messages;
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player, UUID islandId) {
        open(plugin, client, player, islandId, null);
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player, UUID islandId, MessageRenderer messages) {
        PaperGuiViews.islandLimits(client, islandId)
            .thenAccept(limits -> openSync(plugin, player, limits, messages))
            .exceptionally(error -> {
                kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers.run(plugin, () -> player.sendMessage(message(messages, "limit-menu-load-failed", "섬 제한을 불러오지 못했습니다.")));
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
        player.closeInventory();
        if (slot == 45) {
            GuiActionRegistry.execute(player, "island.main.open", GuiClick.from(event));
            return;
        }
        if (slot == 49) {
            GuiActionRegistry.execute(player, "island.limits.open", GuiClick.from(event));
            return;
        }
        if (slot == 53) {
            GuiActionRegistry.execute(player, "island.settings.open", GuiClick.from(event));
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
        GuiActionRegistry.execute(player, "island.limit.set", java.util.Map.of("limitKey", limitKey, "value", String.valueOf(nextValue)), GuiClick.from(event));
    }

    private static void openSync(Plugin plugin, Player player, List<LimitView> limits, MessageRenderer messages) {
        kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers.run(plugin, () -> {
            Inventory inventory = GuiInventories.create(MENU_ID, 54, message(messages, TITLE_KEY, TITLE));
            int slot = 0;
            for (LimitView limit : limits.stream().limit(45).toList()) {
                inventory.setItem(slot++, limitItem(limit, messages));
            }
            if (limits.isEmpty()) {
                inventory.setItem(22, item(Material.BARRIER, message(messages, "limit-menu-empty-title", "제한 없음"), message(messages, "limit-menu-empty", "현재 설정된 섬 제한이 없습니다.")));
            }
            inventory.setItem(45, item(Material.COMPASS, message(messages, "limit-menu-main-menu-name", "메인 메뉴"), message(messages, "limit-menu-main-menu-command", "/섬 메뉴")));
            inventory.setItem(49, item(Material.CLOCK, message(messages, "limit-menu-refresh-name", "새로고침"), message(messages, "limit-menu-refresh-command", "/섬 제한")));
            inventory.setItem(53, item(Material.COMPARATOR, message(messages, "limit-menu-settings-name", "설정"), message(messages, "limit-menu-settings-command", "/섬 설정")));
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
        if (messages == null) {
            return fallback;
        }
        String rendered = messages.plain(key);
        return rendered.isBlank() ? fallback : rendered;
    }

    private static ItemStack item(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(List.of(lore));
            item.setItemMeta(meta);
        }
        return item;
    }

    private static long number(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            return 0L;
        }
    }

}
