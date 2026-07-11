package kr.lunaf.cloudislands.paper.gui;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.paper.application.view.PaperGuiViews;
import kr.lunaf.cloudislands.paper.application.view.PaperGuiViews.RoleView;
import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

public final class IslandRoleMenu implements Listener {
    private static final String TITLE_KEY = "role-menu-title";
    private static final String TITLE = "섬 역할 설정";
    private static final GuiMenuDefinition MENU = GuiMenuDefinition.bundled(
        "config-v2/ui/menus/roles.yml",
        new GuiMenuDefinition("island.roles", 3, TITLE_KEY, Map.of(
            "open", "island.roles.open",
            "list", "island.roles.list",
            "page", "island.roles.page",
            "adjust-weight", "island.role.weight.adjust",
            "permissions", "island.permissions.open",
            "settings", "island.settings.open"
        ))
    );
    private static final String MENU_ID = MENU.id();
    private final MessageRenderer messages;
    private final GuiActionRegistry actions;

    public IslandRoleMenu() {
        this(null);
    }

    public IslandRoleMenu(MessageRenderer messages) {
        this(messages, new GuiActionRegistry(GuiActionExecutor.noop()));
    }

    public IslandRoleMenu(MessageRenderer messages, GuiActionRegistry actions) {
        this.messages = messages;
        this.actions = actions == null ? new GuiActionRegistry(GuiActionExecutor.noop()) : actions;
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player, UUID islandId) {
        open(plugin, client, player, islandId, null);
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player, UUID islandId, MessageRenderer messages) {
        open(plugin, client, player, islandId, messages, 0);
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player, UUID islandId, MessageRenderer messages, int page) {
        GuiSession session = GuiSessions.begin(player, MENU_ID);
        GuiStateMenus.openLoading(plugin, player, session, messages, message(messages, MENU.titleKey(), TITLE));
        PaperGuiViews.islandRoles(client, islandId)
            .thenAccept(roles -> openSync(plugin, player, session, islandId, roles, messages, page))
            .exceptionally(error -> {
                GuiStateMenus.openError(plugin, player, session, messages, message(messages, MENU.titleKey(), TITLE), message(messages, "role-menu-load-failed", "섬 역할을 불러오지 못했습니다."), "island.roles.open", "island.settings.open");
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
        GuiClick click = GuiClick.from(event);
        if (!click.supported()) {
            return;
        }
        String actionId = GuiItems.actionId(event.getCurrentItem());
        if (actionId.isBlank()) {
            return;
        }
        player.closeInventory();
        actions.execute(player, GuiActions.from(actionId, GuiItems.data(event.getCurrentItem())).orElse(null), click);
    }

    private static void openSync(Plugin plugin, Player player, GuiSession session, UUID islandId, List<RoleView> roles, MessageRenderer messages, int requestedPage) {
        GuiSessions.runIfCurrent(plugin, player, session, () -> {
            List<Integer> roleSlots = GuiMenuRenderer.slots(MENU, "_");
            int pageSize = Math.max(1, roleSlots.size());
            int maxPage = Math.max(0, (roles.size() - 1) / pageSize);
            int page = Math.max(0, Math.min(requestedPage, maxPage));
            String title = message(messages, MENU.titleKey(), TITLE) + " " + (page + 1) + "/" + (maxPage + 1);
            Inventory inventory = GuiMenuRenderer.render(MENU, session, messages, title, item -> !List.of("E", "_", "W", "N").contains(item.symbol()));
            int offset = page * pageSize;
            for (int index = 0; index < pageSize && offset + index < roles.size(); index++) {
                inventory.setItem(roleSlots.get(index), roleItem(roles.get(offset + index), messages));
            }
            if (roles.isEmpty()) {
                setEmptyItem(inventory, messages);
            } else {
                if (page > 0) {
                    setPageItem(inventory, "W", islandId, page - 1, messages);
                }
                if (page < maxPage) {
                    setPageItem(inventory, "N", islandId, page + 1, messages);
                }
            }
            player.openInventory(inventory);
        });
    }

    private static ItemStack roleItem(RoleView role, MessageRenderer messages) {
        return GuiItems.action(material(role.role()), role.displayName().isBlank() ? role.role() : role.displayName(), "island.role.weight.adjust",
            Map.of(
                "role", role.role(),
                "weight", String.valueOf(role.weight()),
                "displayName", role.displayName()
            ),
            message(messages, "role-menu-weight", "weight=") + role.weight(),
            message(messages, "role-menu-role-key", "역할: ") + role.role(),
            message(messages, "role-menu-click-edit", "좌클릭: weight +1, 우클릭: weight -1, Shift: 기본값 복원"));
    }

    private static String message(MessageRenderer messages, String key, String fallback) {
        return GuiMenuRenderer.message(messages, key, fallback);
    }

    private static org.bukkit.Material material(String role) {
        String symbol = role.startsWith("CUSTOM_") ? "CUSTOM" : role;
        return GuiMenuRenderer.material(MENU, symbol, "_", "PAPER");
    }

    private static void setEmptyItem(Inventory inventory, MessageRenderer messages) {
        GuiMenuRenderer.setSymbolItem(inventory, MENU, "E", messages, Map.of(), List.of());
    }

    private static void setPageItem(Inventory inventory, String symbol, UUID islandId, int page, MessageRenderer messages) {
        String key = symbol.equals("W") ? "role-menu-previous-page" : "role-menu-next-page";
        String fallback = symbol.equals("W") ? "이전 페이지" : "다음 페이지";
        GuiMenuRenderer.setSymbolItem(inventory, MENU, symbol, messages,
            Map.of("islandId", islandId.toString(), "page", Integer.toString(page)), List.of(message(messages, key, fallback)));
    }

}
