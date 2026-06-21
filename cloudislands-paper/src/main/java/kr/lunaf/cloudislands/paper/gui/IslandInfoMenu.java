package kr.lunaf.cloudislands.paper.gui;

import java.util.List;
import java.util.UUID;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.paper.application.view.PaperGuiViews;
import kr.lunaf.cloudislands.paper.application.view.PaperGuiViews.IslandInfoView;
import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;

public final class IslandInfoMenu implements Listener {
    private static final GuiMenuDefinition MENU = GuiMenuDefinition.bundled(
        "config-v2/ui/menus/info.yml",
        new GuiMenuDefinition("island.info", 3, "info-menu-title", java.util.Map.of(
            "open", "island.info.open",
            "settings", "island.settings.open",
            "ranking", "island.ranking.open",
            "logs", "island.logs.open",
            "recalculate", "island.level.recalculate",
            "back", "island.main.open",
            "close", "gui.close"
        ))
    );
    private static final String MENU_ID = MENU.id();
    private static final String TITLE = "섬 정보";
    private final MessageRenderer messages;
    private final GuiActionRegistry actions;

    public IslandInfoMenu() {
        this(null);
    }

    public IslandInfoMenu(MessageRenderer messages) {
        this(messages, new GuiActionRegistry(GuiActionExecutor.noop()));
    }

    public IslandInfoMenu(MessageRenderer messages, GuiActionRegistry actions) {
        this.messages = messages;
        this.actions = actions == null ? new GuiActionRegistry(GuiActionExecutor.noop()) : actions;
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player, UUID islandId) {
        open(plugin, client, player, islandId, null);
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player, UUID islandId, MessageRenderer messages) {
        GuiSession session = GuiSessions.begin(player, MENU_ID);
        GuiStateMenus.openLoading(plugin, player, session, messages, message(messages, MENU.titleKey(), TITLE));
        PaperGuiViews.islandInfo(client, islandId)
            .thenAccept(view -> openSync(plugin, player, session, view, messages))
            .exceptionally(error -> {
                GuiStateMenus.openError(plugin, player, session, messages, message(messages, MENU.titleKey(), TITLE), message(messages, "info-menu-load-failed", "섬 정보를 불러오지 못했습니다."), "island.info.open", "island.main.open");
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
        if (actionId.equals("gui.close")) {
            return;
        }
        actions.execute(player, actionId, GuiItems.data(event.getCurrentItem()), GuiClick.from(event));
    }

    private static void openSync(Plugin plugin, Player player, GuiSession session, IslandInfoView view, MessageRenderer messages) {
        GuiSessions.runIfCurrent(plugin, player, session, () -> {
            Inventory inventory = GuiMenuRenderer.render(MENU, session, messages, TITLE, item -> true);
            setInfoItem(inventory, 10, messages,
                message(messages, "info-menu-island-name", "섬 이름: ") + fallback(view.name(), message(messages, "info-menu-no-name", "이름 없음")),
                message(messages, "info-menu-state", "상태: ") + fallback(view.state(), message(messages, "info-menu-unknown", "알 수 없음")),
                message(messages, "info-menu-island-id", "섬 ID: ") + shortId(view.islandId(), messages));
            setInfoItem(inventory, 11, messages,
                message(messages, "info-menu-level", "레벨: ") + view.level(),
                message(messages, "info-menu-worth", "가치: ") + fallback(view.worth(), "0"));
            setInfoItem(inventory, 12, messages,
                message(messages, "info-menu-public-access", "공개 여부: ") + yesNo(view.publicAccess(), messages),
                message(messages, "info-menu-locked", "잠금 여부: ") + yesNo(view.locked(), messages));
            setInfoItem(inventory, 13, messages,
                message(messages, "info-menu-size", "섬 크기: ") + view.size(),
                message(messages, "info-menu-border", "경계: ") + view.border());
            setInfoItem(inventory, 14, messages,
                message(messages, "info-menu-owner", "소유자: ") + shortId(view.ownerUuid(), messages));
            player.openInventory(inventory);
        });
    }

    private static String message(MessageRenderer messages, String key, String fallback) {
        return GuiMenuRenderer.message(messages, key, fallback);
    }

    private static void setInfoItem(Inventory inventory, int slot, MessageRenderer messages, String... lore) {
        MENU.itemAt(slot)
            .ifPresent(item -> inventory.setItem(slot, GuiMenuRenderer.item(MENU, item, messages, java.util.Map.of(), List.of(lore))));
    }

    private static String fallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String yesNo(boolean value, MessageRenderer messages) {
        return value ? message(messages, "info-menu-yes", "예") : message(messages, "info-menu-no", "아니오");
    }

    private static String shortId(String value, MessageRenderer messages) {
        if (value == null || value.isBlank()) {
            return message(messages, "info-menu-unknown", "알 수 없음");
        }
        return value.length() <= 8 ? value : value.substring(0, 8);
    }
}
