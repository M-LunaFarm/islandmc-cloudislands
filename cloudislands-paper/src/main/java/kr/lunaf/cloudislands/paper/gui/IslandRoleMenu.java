package kr.lunaf.cloudislands.paper.gui;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.paper.application.view.PaperGuiViews;
import kr.lunaf.cloudislands.paper.application.view.PaperGuiViews.RoleView;
import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

public final class IslandRoleMenu implements Listener {
    private static final String TITLE_KEY = "role-menu-title";
    private static final String TITLE = "섬 역할 설정";
    private static final GuiMenuDefinition MENU = GuiMenuDefinition.bundled(
        "config-v2/ui/menus/roles.yml",
        new GuiMenuDefinition("island.roles", 3, TITLE_KEY, Map.of(
            "open", "island.roles.open",
            "list", "island.roles.list",
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
        GuiSession session = GuiSessions.begin(player, MENU_ID);
        GuiStateMenus.openLoading(plugin, player, session, messages, message(messages, MENU.titleKey(), TITLE));
        PaperGuiViews.islandRoles(client, islandId)
            .thenAccept(roles -> openSync(plugin, player, session, roles, messages))
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
        actions.execute(player, actionId, GuiItems.data(event.getCurrentItem()), click);
    }

    private static void openSync(Plugin plugin, Player player, GuiSession session, List<RoleView> roles, MessageRenderer messages) {
        GuiSessions.runIfCurrent(plugin, player, session, () -> {
            Inventory inventory = GuiMenuRenderer.render(MENU, session, messages, TITLE, item -> true);
            int slot = 0;
            for (RoleView role : roles.stream().limit(18).toList()) {
                inventory.setItem(slot++, roleItem(role, messages));
            }
            if (roles.isEmpty()) {
                inventory.setItem(13, item(Material.GRAY_DYE, message(messages, "role-menu-empty-title", "커스텀 역할 없음"), message(messages, "role-menu-empty-example", "Core 역할 카탈로그가 비어 있습니다.")));
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

    private static Material material(String role) {
        if (role.startsWith("CUSTOM_")) {
            return Material.NAME_TAG;
        }
        return switch (role) {
            case "CO_OWNER" -> Material.DIAMOND;
            case "MODERATOR" -> Material.IRON_SWORD;
            case "TRUSTED" -> Material.EMERALD;
            case "MEMBER" -> Material.PLAYER_HEAD;
            default -> Material.PAPER;
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
