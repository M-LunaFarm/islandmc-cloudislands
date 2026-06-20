package kr.lunaf.cloudislands.paper.gui;

import java.util.List;
import java.util.Map;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.paper.application.view.PaperGuiViews;
import kr.lunaf.cloudislands.paper.application.view.PaperGuiViews.PlayerIslandView;
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

public final class IslandMyIslandsMenu implements Listener {
    private static final String TITLE_KEY = "my-islands-menu-title";
    private static final String TITLE = "내 섬 목록";
    private static final String MENU_ID = "island.my-islands";
    private final MessageRenderer messages;
    private final GuiActionRegistry actions;

    public IslandMyIslandsMenu() {
        this(null);
    }

    public IslandMyIslandsMenu(MessageRenderer messages) {
        this(messages, new GuiActionRegistry(GuiActionExecutor.noop()));
    }

    public IslandMyIslandsMenu(MessageRenderer messages, GuiActionRegistry actions) {
        this.messages = messages;
        this.actions = actions == null ? new GuiActionRegistry(GuiActionExecutor.noop()) : actions;
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player) {
        open(plugin, client, player, null);
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player, MessageRenderer messages) {
        GuiSession session = GuiSessions.begin(player, MENU_ID);
        GuiStateMenus.openLoading(plugin, player, session, messages, message(messages, TITLE_KEY, TITLE));
        PaperGuiViews.playerIslands(client, player.getUniqueId())
            .thenAccept(islands -> openSync(plugin, player, session, islands, messages))
            .exceptionally(error -> {
                GuiStateMenus.openError(plugin, player, session, messages, message(messages, TITLE_KEY, TITLE), message(messages, "my-islands-menu-load-failed", "내 섬 목록을 불러오지 못했습니다."), "island.list.open", "island.main.open");
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
        if (slot == 49) {
            actions.execute(player, "island.list.open", GuiClick.from(event));
            return;
        }
        if (slot == 48) {
            actions.execute(player, "island.create.open", GuiClick.from(event));
            return;
        }
        if (slot == 45) {
            actions.execute(player, "island.main.open", GuiClick.from(event));
            return;
        }
        if (slot == 53) {
            actions.execute(player, "island.visit.open", GuiClick.from(event));
            return;
        }
        String islandId = GuiItems.data(event.getCurrentItem()).getOrDefault("target", "");
        if (!islandId.isBlank()) {
            actions.execute(player, "island.visit.target", java.util.Map.of("target", String.valueOf(islandId)), GuiClick.from(event));
        }
    }

    private static void openSync(Plugin plugin, Player player, GuiSession session, List<PlayerIslandView> islands, MessageRenderer messages) {
        GuiSessions.runIfCurrent(plugin, player, session, () -> {
            Inventory inventory = GuiInventories.create(MENU_ID, 54, message(messages, TITLE_KEY, TITLE));
            if (islands.isEmpty()) {
                inventory.setItem(22, item(Material.BARRIER, message(messages, "my-islands-menu-empty-title", "섬 없음"), message(messages, "my-islands-menu-empty", "속한 섬이 없습니다.")));
            } else {
                for (int index = 0; index < islands.size() && index < 45; index++) {
                    PlayerIslandView island = islands.get(index);
                    inventory.setItem(index, GuiItems.action(material(island.role()), island.name(), "island.visit.target",
                        Map.of("target", island.islandId()),
                        message(messages, "my-islands-menu-role", "역할: ") + island.role(),
                        message(messages, "my-islands-menu-state", "상태: ") + island.state(),
                        message(messages, "my-islands-menu-level", "레벨: ") + island.level(),
                        message(messages, "my-islands-menu-worth", "가치: ") + island.worth(),
                        message(messages, "my-islands-menu-click-to-visit", "클릭하면 이 섬으로 이동합니다.")));
                }
            }
            inventory.setItem(45, item(Material.COMPASS, message(messages, "my-islands-menu-main-menu-name", "메인 메뉴"), message(messages, "my-islands-menu-main-menu-command", "/섬 메뉴")));
            inventory.setItem(48, item(Material.OAK_SAPLING, message(messages, "my-islands-menu-create-name", "섬 생성"), message(messages, "my-islands-menu-create-command", "/섬 생성메뉴")));
            inventory.setItem(49, item(Material.CLOCK, message(messages, "my-islands-menu-refresh-name", "새로고침"), message(messages, "my-islands-menu-refresh-command", "/섬 목록")));
            inventory.setItem(53, item(Material.ENDER_PEARL, message(messages, "my-islands-menu-public-islands-name", "공개 섬"), message(messages, "my-islands-menu-public-islands-command", "/섬 방문")));
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

    private static Material material(String role) {
        return switch (role) {
            case "OWNER" -> Material.NETHER_STAR;
            case "CO_OWNER" -> Material.DIAMOND;
            case "MODERATOR" -> Material.IRON_SWORD;
            case "TRUSTED" -> Material.EMERALD;
            default -> Material.GRASS_BLOCK;
        };
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

}
