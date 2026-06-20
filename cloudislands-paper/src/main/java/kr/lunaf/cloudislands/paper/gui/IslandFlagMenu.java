package kr.lunaf.cloudislands.paper.gui;

import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandFlag;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.paper.application.view.PaperGuiViews;
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

public final class IslandFlagMenu implements Listener {
    private static final String MENU_ID = "island.flags";
    private static final String TITLE_KEY = "flag-menu-title";
    private static final String TITLE = "섬 플래그 설정";
    private final MessageRenderer messages;
    private final GuiActionRegistry actions;

    public IslandFlagMenu() {
        this(null);
    }

    public IslandFlagMenu(MessageRenderer messages) {
        this(messages, new GuiActionRegistry(GuiActionExecutor.noop()));
    }

    public IslandFlagMenu(MessageRenderer messages, GuiActionRegistry actions) {
        this.messages = messages;
        this.actions = actions == null ? new GuiActionRegistry(GuiActionExecutor.noop()) : actions;
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player, UUID islandId) {
        open(plugin, client, player, islandId, null);
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player, UUID islandId, MessageRenderer messages) {
        GuiSession session = GuiSessions.begin(player, MENU_ID);
        GuiStateMenus.openLoading(plugin, player, session, messages, message(messages, TITLE_KEY, TITLE));
        PaperGuiViews.islandFlags(client, islandId)
            .thenAccept(values -> openSync(plugin, player, session, values, messages))
            .exceptionally(error -> {
                GuiStateMenus.openError(plugin, player, session, messages, message(messages, TITLE_KEY, TITLE), message(messages, "flag-menu-load-failed", "섬 플래그를 불러오지 못했습니다."), "island.flags.open", "island.settings.open");
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
        if (slot < 0 || slot >= 54) {
            return;
        }
        player.closeInventory();
        if (slot == 49) {
            actions.execute(player, "island.flags.open", GuiClick.from(event));
            return;
        }
        if (slot == 53) {
            actions.execute(player, "island.settings.open", GuiClick.from(event));
            return;
        }
        String flag = GuiItems.data(event.getCurrentItem()).getOrDefault("flag", "");
        if (flag.isBlank()) {
            return;
        }
        actions.execute(player, "island.flag.set", java.util.Map.of("flag", flag), GuiClick.from(event));
    }

    private static void openSync(Plugin plugin, Player player, GuiSession session, Map<IslandFlag, String> values, MessageRenderer messages) {
        GuiSessions.runIfCurrent(plugin, player, session, () -> {
            Inventory inventory = GuiInventories.create(MENU_ID, 54, message(messages, TITLE_KEY, TITLE));
            int slot = 0;
            for (IslandFlag flag : java.util.Arrays.stream(IslandFlag.values()).limit(49).toList()) {
                inventory.setItem(slot++, flagItem(flag, values.get(flag), messages));
            }
            inventory.setItem(49, item(Material.CLOCK, message(messages, "flag-menu-refresh-name", "새로고침"), message(messages, "flag-menu-refresh-command", "/섬 플래그")));
            inventory.setItem(53, item(Material.COMPARATOR, message(messages, "flag-menu-settings-name", "설정"), message(messages, "flag-menu-settings-command", "/섬 설정")));
            player.openInventory(inventory);
        });
    }

    private static ItemStack flagItem(IslandFlag flag, String value, MessageRenderer messages) {
        String normalized = value == null ? "" : value;
        Material material = normalized.equalsIgnoreCase("true") ? Material.LIME_DYE : normalized.equalsIgnoreCase("false") ? Material.RED_DYE : Material.GRAY_DYE;
        String state = normalized.isBlank() ? message(messages, "flag-menu-default", "기본값") : normalized.equalsIgnoreCase("true") ? message(messages, "flag-menu-allow", "허용") : normalized.equalsIgnoreCase("false") ? message(messages, "flag-menu-deny", "거부") : normalized;
        return GuiItems.action(material, flag.name(), "island.flag.set", java.util.Map.of("flag", flag.name()), message(messages, "flag-menu-current-value", "현재 값: ") + state, message(messages, "flag-menu-click-actions", "좌클릭: 허용, 우클릭: 거부"));
    }

    private static ItemStack item(Material material, String name, String lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(java.util.List.of(lore));
            item.setItemMeta(meta);
        }
        return item;
    }

    private static String message(MessageRenderer messages, String key, String fallback) {
        if (messages == null) {
            return fallback;
        }
        String rendered = messages.plain(key);
        return rendered.isBlank() ? fallback : rendered;
    }

}
