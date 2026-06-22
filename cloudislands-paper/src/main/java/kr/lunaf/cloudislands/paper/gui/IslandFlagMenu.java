package kr.lunaf.cloudislands.paper.gui;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandFlag;
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

public final class IslandFlagMenu implements Listener {
    private static final String TITLE_KEY = "flag-menu-title";
    private static final String TITLE = "섬 플래그 설정";
    private static final GuiMenuDefinition MENU = GuiMenuDefinition.bundled(
        "config-v2/ui/menus/flags.yml",
        new GuiMenuDefinition("island.flags", 6, TITLE_KEY, Map.of(
            "open", "island.flags.open",
            "list", "island.flags.list",
            "set", "island.flag.set",
            "back", "island.settings.open"
        ))
    );
    private static final String MENU_ID = MENU.id();
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
        if (slot < 0 || slot >= MENU.size()) {
            return;
        }
        String actionId = GuiItems.actionId(event.getCurrentItem());
        if (!actionId.isBlank()) {
            player.closeInventory();
            actions.execute(player, GuiActions.from(actionId).orElse(null), GuiClick.from(event));
            return;
        }
        String flag = GuiItems.data(event.getCurrentItem()).getOrDefault("flag", "");
        if (flag.isBlank()) {
            return;
        }
        player.closeInventory();
        actions.execute(player, new GuiAction.FlagSet(IslandFlag.valueOf(flag)), GuiClick.from(event));
    }

    private static void openSync(Plugin plugin, Player player, GuiSession session, Map<IslandFlag, String> values, MessageRenderer messages) {
        GuiSessions.runIfCurrent(plugin, player, session, () -> {
            Inventory inventory = GuiMenuRenderer.render(MENU, session, messages, TITLE, item -> !"_".equals(item.symbol()));
            List<Integer> flagSlots = GuiMenuRenderer.slots(MENU, "_");
            List<IslandFlag> visibleFlags = java.util.Arrays.stream(IslandFlag.values()).limit(flagSlots.size()).toList();
            for (int index = 0; index < visibleFlags.size(); index++) {
                IslandFlag flag = visibleFlags.get(index);
                inventory.setItem(flagSlots.get(index), flagItem(flag, values.get(flag), messages));
            }
            player.openInventory(inventory);
        });
    }

    private static ItemStack flagItem(IslandFlag flag, String value, MessageRenderer messages) {
        String normalized = value == null ? "" : value;
        org.bukkit.Material material = GuiMenuRenderer.material(MENU, normalized.toUpperCase(java.util.Locale.ROOT), "_", "GRAY_DYE");
        String state = normalized.isBlank() ? message(messages, "flag-menu-default", "기본값") : normalized.equalsIgnoreCase("true") ? message(messages, "flag-menu-allow", "허용") : normalized.equalsIgnoreCase("false") ? message(messages, "flag-menu-deny", "거부") : normalized;
        return GuiItems.action(material, flag.name(), "island.flag.set", java.util.Map.of("flag", flag.name()), message(messages, "flag-menu-current-value", "현재 값: ") + state, message(messages, "flag-menu-click-actions", "좌클릭: 허용, 우클릭: 거부"));
    }

    private static String message(MessageRenderer messages, String key, String fallback) {
        return GuiMenuRenderer.message(messages, key, fallback);
    }

}
