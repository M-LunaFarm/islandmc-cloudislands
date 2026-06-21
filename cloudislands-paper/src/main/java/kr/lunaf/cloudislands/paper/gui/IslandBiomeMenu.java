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
    private static final GuiMenuDefinition MENU = GuiMenuDefinition.bundled(
        "config-v2/ui/menus/biome.yml",
        new GuiMenuDefinition("island.biome", 3, "menu.biome.title", Map.of(
            "open", "island.biome.open",
            "show", "island.biome.show",
            "set", "island.biome.set",
            "back", "island.settings.open",
            "main", "island.main.open"
        ))
    );
    private static final String MENU_ID = MENU.id();
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
        GuiStateMenus.openLoading(plugin, player, session, messages, message(messages, MENU.titleKey(), TITLE));
        PaperGuiViews.islandBiome(client, islandId)
            .thenAccept(currentBiome -> openSync(plugin, player, session, currentBiome, messages))
            .exceptionally(error -> {
                GuiStateMenus.openError(plugin, player, session, messages, message(messages, MENU.titleKey(), TITLE), message(messages, "biome-menu-load-failed", "섬 바이옴을 불러오지 못했습니다."), "island.biome.open", "island.settings.open");
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
        String actionId = GuiItems.actionId(event.getCurrentItem());
        if (!actionId.isBlank()) {
            player.closeInventory();
            actions.execute(player, actionId, GuiItems.data(event.getCurrentItem()), GuiClick.from(event));
            return;
        }
        String biomeKey = GuiItems.data(event.getCurrentItem()).getOrDefault("biomeKey", "");
        if (biomeKey.isBlank()) {
            return;
        }
        player.closeInventory();
        actions.execute(player, "island.biome.set", java.util.Map.of("biomeKey", biomeKey), GuiClick.from(event));
    }

    private static void openSync(Plugin plugin, Player player, GuiSession session, String currentBiome, MessageRenderer messages) {
        GuiSessions.runIfCurrent(plugin, player, session, () -> {
            Inventory inventory = GuiMenuRenderer.render(MENU, session, messages, TITLE, item -> true);
            int slot = 9;
            for (String biome : BIOMES.stream().limit(13).toList()) {
                inventory.setItem(slot++, biomeItem(biome, biome.equalsIgnoreCase(currentBiome), messages));
            }
            MENU.itemAt(4).ifPresent(item -> inventory.setItem(4, GuiMenuRenderer.item(MENU, item, messages, Map.of(), List.of(currentBiome.isBlank() ? message(messages, "biome-menu-not-set", "설정 없음") : currentBiome))));
            player.openInventory(inventory);
        });
    }

    private static ItemStack biomeItem(String biome, boolean selected, MessageRenderer messages) {
        Material material = selected ? Material.LIME_DYE : Material.GRASS_BLOCK;
        return GuiItems.action(material, biome, "island.biome.set", Map.of("biomeKey", biome), selected ? message(messages, "biome-menu-selected", "현재 적용됨") : message(messages, "biome-menu-click-to-change", "클릭하면 이 바이옴으로 변경합니다."));
    }

    private static String message(MessageRenderer messages, String key, String fallback) {
        return GuiMenuRenderer.message(messages, key, fallback);
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
