package kr.lunaf.cloudislands.paper.gui;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.model.IslandFlag;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.paper.application.IslandEnvironmentUseCase;
import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;

public final class IslandBorderMenu implements Listener {
    private static final String TITLE = "보더 설정";
    private static final GuiMenuDefinition MENU = GuiMenuDefinition.bundled(
        "config-v2/ui/menus/border.yml",
        new GuiMenuDefinition("island.border", 3, "menu.border.title", Map.of(
            "open", "island.border.open",
            "color", "island.border.color.set",
            "info", "island.info.open",
            "back", "island.main.open"
        ))
    );
    private static final String MENU_ID = MENU.id();
    private final MessageRenderer messages;
    private final GuiActionRegistry actions;

    public IslandBorderMenu() {
        this(null);
    }

    public IslandBorderMenu(MessageRenderer messages) {
        this(messages, new GuiActionRegistry(GuiActionExecutor.noop()));
    }

    public IslandBorderMenu(MessageRenderer messages, GuiActionRegistry actions) {
        this.messages = messages;
        this.actions = actions == null ? new GuiActionRegistry(GuiActionExecutor.noop()) : actions;
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player, UUID islandId, MessageRenderer messages) {
        GuiSession session = GuiSessions.begin(player, MENU_ID);
        GuiStateMenus.openLoading(plugin, player, session, messages, message(messages, MENU.titleKey(), TITLE));
        IslandEnvironmentUseCase useCase = new IslandEnvironmentUseCase(client);
        CompletableFuture<kr.lunaf.cloudislands.coreclient.CoreGuiViews.IslandInfoView> info = useCase.islandInfoView(islandId);
        CompletableFuture<Map<IslandFlag, String>> flags = useCase.flagValues(islandId);
        info.thenCombine(flags, BorderView::new)
            .thenAccept(view -> openSync(plugin, player, session, view, messages))
            .exceptionally(error -> {
                GuiStateMenus.openError(plugin, player, session, messages, message(messages, MENU.titleKey(), TITLE), message(messages, "border-menu-load-failed", "섬 보더 설정을 불러오지 못했습니다."), "island.border.open", "island.settings.open");
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

    private static void openSync(Plugin plugin, Player player, GuiSession session, BorderView view, MessageRenderer messages) {
        GuiSessions.runIfCurrent(plugin, player, session, () -> {
            Inventory inventory = GuiMenuRenderer.render(MENU, session, messages, TITLE, item -> true);
            setItem(inventory, "S", messages, message(messages, "border-menu-size-current", "현재 크기: ") + view.info().border());
            setColorItems(inventory, view, messages);
            setItem(inventory, "I", messages, message(messages, "border-menu-visible", "표시: ") + view.flags().getOrDefault(IslandFlag.BORDER_VISIBLE, "true"));
            player.openInventory(inventory);
        });
    }

    private static void setColorItems(Inventory inventory, BorderView view, MessageRenderer messages) {
        for (int slot = 0; slot < MENU.size(); slot++) {
            int currentSlot = slot;
            MENU.itemAt(slot)
                .filter(item -> item.actionKey().equals("color"))
                .ifPresent(item -> inventory.setItem(currentSlot, GuiMenuRenderer.item(MENU, item, messages, item.data(), List.of(colorLore(item.data().getOrDefault("color", ""), view, messages)))));
        }
    }

    private static void setItem(Inventory inventory, String symbol, MessageRenderer messages, String... lore) {
        GuiMenuRenderer.slots(MENU, symbol).forEach(slot -> MENU.itemAt(slot)
            .ifPresent(item -> inventory.setItem(slot, GuiMenuRenderer.item(MENU, item, messages, item.data(), List.of(lore)))));
    }

    private static String colorLore(String color, BorderView view, MessageRenderer messages) {
        String current = view.flags().getOrDefault(IslandFlag.BORDER_COLOR, "blue");
        return message(messages, "border-menu-current-color", "현재 색상: ") + current + (current.equalsIgnoreCase(color) ? " *" : "");
    }

    private static String message(MessageRenderer messages, String key, String fallback) {
        return GuiMenuRenderer.message(messages, key, fallback);
    }

    private record BorderView(kr.lunaf.cloudislands.coreclient.CoreGuiViews.IslandInfoView info, Map<IslandFlag, String> flags) {
        private BorderView {
            flags = flags == null ? Map.of() : Map.copyOf(flags);
        }
    }
}
