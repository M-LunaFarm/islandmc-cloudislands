package kr.lunaf.cloudislands.paper.gui;

import java.util.List;
import java.util.Map;
import java.util.UUID;
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

public final class IslandBiomeMenu implements Listener {
    private static final String TITLE = "섬 바이옴";
    private static final String MENU_ID = "island.biome";
    private final MessageRenderer messages;
    private final GuiActionRegistry actions;
    private static final List<String> BIOMES = List.of(
        "minecraft:plains",
        "minecraft:forest",
        "minecraft:cherry_grove",
        "minecraft:desert",
        "minecraft:snowy_plains",
        "minecraft:jungle",
        "minecraft:swamp",
        "minecraft:badlands",
        "minecraft:taiga",
        "minecraft:mushroom_fields"
    );

    public IslandBiomeMenu() {
        this(null);
    }

    public IslandBiomeMenu(MessageRenderer messages) {
        this(messages, new GuiActionRegistry(GuiActionExecutor.noop()));
    }

    public IslandBiomeMenu(MessageRenderer messages, GuiActionRegistry actions) {
        this.messages = messages;
        this.actions = actions == null ? new GuiActionRegistry(GuiActionExecutor.noop()) : actions;
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player, UUID islandId) {
        open(plugin, client, player, islandId, null);
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player, UUID islandId, MessageRenderer messages) {
        GuiSession session = GuiSessions.begin(player, MENU_ID);
        GuiStateMenus.openLoading(plugin, player, session, messages, TITLE);
        PaperGuiViews.islandBiome(client, islandId)
            .thenAccept(currentBiome -> openSync(plugin, player, session, currentBiome, messages))
            .exceptionally(error -> {
                GuiStateMenus.openError(plugin, player, session, messages, TITLE, message(messages, "biome-menu-load-failed", "섬 바이옴을 불러오지 못했습니다."), "island.biome.open", "island.settings.open");
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
        if (slot < 0 || slot >= 27) {
            return;
        }
        player.closeInventory();
        if (slot == 4) {
            actions.execute(player, "island.biome.show", GuiClick.from(event));
            return;
        }
        if (slot == 22) {
            actions.execute(player, "island.biome.open", GuiClick.from(event));
            return;
        }
        if (slot == 24) {
            actions.execute(player, "island.settings.open", GuiClick.from(event));
            return;
        }
        if (slot == 26) {
            actions.execute(player, "island.main.open", GuiClick.from(event));
            return;
        }
        String biomeKey = GuiItems.data(event.getCurrentItem()).getOrDefault("biomeKey", "");
        if (biomeKey.isBlank()) {
            return;
        }
        actions.execute(player, "island.biome.set", java.util.Map.of("biomeKey", biomeKey), GuiClick.from(event));
    }

    private static void openSync(Plugin plugin, Player player, GuiSession session, String currentBiome, MessageRenderer messages) {
        GuiSessions.runIfCurrent(plugin, player, session, () -> {
            Inventory inventory = GuiInventories.create(MENU_ID, 27, TITLE);
            int slot = 9;
            for (String biome : BIOMES.stream().limit(13).toList()) {
                inventory.setItem(slot++, biomeItem(biome, biome.equalsIgnoreCase(currentBiome), messages));
            }
            inventory.setItem(4, item(Material.GRASS_BLOCK, message(messages, "biome-menu-current-name", "현재 바이옴"), currentBiome.isBlank() ? message(messages, "biome-menu-not-set", "설정 없음") : currentBiome));
            inventory.setItem(22, item(Material.CLOCK, message(messages, "biome-menu-refresh-name", "새로고침"), message(messages, "biome-menu-refresh-command", "/섬 바이옴")));
            inventory.setItem(24, item(Material.COMPARATOR, message(messages, "biome-menu-settings-name", "설정"), message(messages, "biome-menu-settings-command", "/섬 설정")));
            inventory.setItem(26, item(Material.COMPASS, message(messages, "biome-menu-main-menu-name", "메인 메뉴"), message(messages, "biome-menu-main-menu-command", "/섬 메뉴")));
            player.openInventory(inventory);
        });
    }

    private static ItemStack biomeItem(String biome, boolean selected, MessageRenderer messages) {
        Material material = selected ? Material.LIME_DYE : Material.GRASS_BLOCK;
        return GuiItems.action(material, biome, "island.biome.set", Map.of("biomeKey", biome), selected ? message(messages, "biome-menu-selected", "현재 적용됨") : message(messages, "biome-menu-click-to-change", "클릭하면 이 바이옴으로 변경합니다."));
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

}
