package kr.lunaf.cloudislands.paper.gui;

import java.util.List;
import java.util.Map;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.paper.application.view.PaperGuiViews;
import kr.lunaf.cloudislands.paper.application.view.PaperGuiViews.PublicIslandView;
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

public final class IslandVisitMenu implements Listener {
    private static final String TITLE_KEY = "visit-menu-title";
    private static final String TITLE = "섬 방문";
    private static final String MENU_ID = "island.visit";
    private final MessageRenderer messages;
    private final GuiActionRegistry actions;

    public IslandVisitMenu() {
        this(null);
    }

    public IslandVisitMenu(MessageRenderer messages) {
        this(messages, new GuiActionRegistry(GuiActionExecutor.noop()));
    }

    public IslandVisitMenu(MessageRenderer messages, GuiActionRegistry actions) {
        this.messages = messages;
        this.actions = actions == null ? new GuiActionRegistry(GuiActionExecutor.noop()) : actions;
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player) {
        open(plugin, client, player, null);
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player, MessageRenderer messages) {
        GuiSession session = GuiSessions.begin(player, MENU_ID);
        GuiStateMenus.openLoading(plugin, player, session, messages, message(messages, TITLE_KEY, TITLE));
        PaperGuiViews.publicIslands(client, 45)
            .thenAccept(islands -> openSync(plugin, player, session, islands, messages))
            .exceptionally(error -> {
                GuiStateMenus.openError(plugin, player, session, messages, message(messages, TITLE_KEY, TITLE), message(messages, "visit-menu-load-failed", "공개 섬 목록을 불러오지 못했습니다."), "island.visit.open", "island.main.open");
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
        if (slot == 4) {
            actions.execute(player, "island.visit.random", GuiClick.from(event));
            return;
        }
        if (slot == 45) {
            actions.execute(player, "island.visit.public.open", GuiClick.from(event));
            return;
        }
        if (slot == 49) {
            actions.execute(player, "island.visit.open", GuiClick.from(event));
            return;
        }
        String islandId = GuiItems.data(event.getCurrentItem()).getOrDefault("target", "");
        if (!islandId.isBlank()) {
            actions.execute(player, "island.visit.target", java.util.Map.of("target", String.valueOf(islandId)), GuiClick.from(event));
        }
    }

    private static void openSync(Plugin plugin, Player player, GuiSession session, List<PublicIslandView> islands, MessageRenderer messages) {
        GuiSessions.runIfCurrent(plugin, player, session, () -> {
            Inventory inventory = GuiInventories.create(MENU_ID, 54, message(messages, TITLE_KEY, TITLE));
            inventory.setItem(4, item(Material.COMPASS, message(messages, "visit-menu-random-name", "랜덤 공개 섬"), message(messages, "visit-menu-random-description", "공개된 섬 중 하나로 이동합니다.")));
            if (islands.isEmpty()) {
                inventory.setItem(22, item(Material.BARRIER, message(messages, "visit-menu-empty-title", "공개 섬 없음"), message(messages, "visit-menu-empty", "방문 가능한 공개 섬이 없습니다.")));
            } else {
                for (int index = 0; index < islands.size() && index < 36; index++) {
                    PublicIslandView island = islands.get(index);
                    inventory.setItem(index + 9, GuiItems.action(Material.GRASS_BLOCK, island.name(), "island.visit.target",
                        Map.of("target", island.islandId()),
                        message(messages, "visit-menu-owner", "소유자: ") + shortId(island.ownerUuid()),
                        message(messages, "visit-menu-level", "레벨: ") + island.level(),
                        message(messages, "visit-menu-worth", "가치: ") + island.worth(),
                        message(messages, "visit-menu-click-to-visit", "클릭하면 방문합니다.")));
                }
            }
            inventory.setItem(45, item(Material.ENDER_EYE, message(messages, "visit-menu-public-warps-name", "공개 워프 목록"), message(messages, "visit-menu-public-warps-command", "/섬 공개워프목록")));
            inventory.setItem(49, item(Material.CLOCK, message(messages, "visit-menu-refresh-name", "새로고침"), message(messages, "visit-menu-refresh-command", "/섬 방문")));
            player.openInventory(inventory);
        });
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

    private static String shortId(String value) {
        if (value == null || value.isBlank()) {
            return "알 수 없음";
        }
        return value.length() <= 8 ? value : value.substring(0, 8);
    }

}
