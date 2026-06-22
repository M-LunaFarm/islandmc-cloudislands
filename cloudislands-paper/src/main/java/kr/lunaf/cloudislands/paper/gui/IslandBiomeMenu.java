package kr.lunaf.cloudislands.paper.gui;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.paper.application.view.PaperGuiViews;
import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
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
            .thenAccept(currentBiome -> openSync(plugin, player, session, currentBiome.key(), messages))
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
        if (slot < 0 || slot >= MENU.size()) {
            return;
        }
        String actionId = GuiItems.actionId(event.getCurrentItem());
        if (!actionId.isBlank()) {
            player.closeInventory();
            actions.execute(player, GuiActions.from(actionId, GuiItems.data(event.getCurrentItem())).orElse(null), GuiClick.from(event));
            return;
        }
        String biomeKey = GuiItems.data(event.getCurrentItem()).getOrDefault("biomeKey", "");
        if (biomeKey.isBlank()) {
            return;
        }
        player.closeInventory();
        actions.execute(player, new GuiAction.BiomeSet(biomeKey), GuiClick.from(event));
    }

    private static void openSync(Plugin plugin, Player player, GuiSession session, String currentBiome, MessageRenderer messages) {
        GuiSessions.runIfCurrent(plugin, player, session, () -> {
            Inventory inventory = GuiMenuRenderer.render(MENU, session, messages, TITLE, item -> !item.symbol().equals("_"));
            List<Integer> biomeSlots = GuiMenuRenderer.slots(MENU, "_");
            List<String> biomes = BIOMES.stream().limit(biomeSlots.size()).toList();
            for (int index = 0; index < biomes.size(); index++) {
                String biome = biomes.get(index);
                inventory.setItem(biomeSlots.get(index), biomeItem(biome, biome.equalsIgnoreCase(currentBiome), messages));
            }
            GuiMenuRenderer.setSymbolItem(
                inventory,
                MENU,
                "C",
                messages,
                Map.of(),
                List.of(currentBiome.isBlank() ? message(messages, "biome-menu-not-set", "설정 없음") : currentBiome)
            );
            player.openInventory(inventory);
        });
    }

    private static ItemStack biomeItem(String biome, boolean selected, MessageRenderer messages) {
        String symbol = selected ? "SELECTED" : "_";
        String lore = selected ? message(messages, "biome-menu-selected", "현재 적용됨") : message(messages, "biome-menu-click-to-change", "클릭하면 이 바이옴으로 변경합니다.");
        return MENU.item(symbol)
            .map(item -> GuiMenuRenderer.item(MENU, item, messages, biome, Map.of("biomeKey", biome), List.of(lore)))
            .orElseThrow(() -> new IllegalStateException("Missing biome menu item symbol " + symbol));
    }

    private static String message(MessageRenderer messages, String key, String fallback) {
        return GuiMenuRenderer.message(messages, key, fallback);
    }

}
