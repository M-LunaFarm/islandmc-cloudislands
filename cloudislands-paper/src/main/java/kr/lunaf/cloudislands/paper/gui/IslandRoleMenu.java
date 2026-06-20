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
    private static final String MENU_ID = "island.roles";
    private final MessageRenderer messages;

    public IslandRoleMenu() {
        this(null);
    }

    public IslandRoleMenu(MessageRenderer messages) {
        this.messages = messages;
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player, UUID islandId) {
        open(plugin, client, player, islandId, null);
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player, UUID islandId, MessageRenderer messages) {
        GuiStateMenus.openLoading(plugin, player, messages, message(messages, TITLE_KEY, TITLE));
        PaperGuiViews.islandRoles(client, islandId)
            .thenAccept(roles -> openSync(plugin, player, roles, messages))
            .exceptionally(error -> {
                GuiStateMenus.openError(plugin, player, messages, message(messages, TITLE_KEY, TITLE), message(messages, "role-menu-load-failed", "섬 역할을 불러오지 못했습니다."), "island.roles.open", "island.settings.open");
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
        GuiActionRegistry.execute(player, actionId, GuiItems.data(event.getCurrentItem()), click);
    }

    private static void openSync(Plugin plugin, Player player, List<RoleView> roles, MessageRenderer messages) {
        kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers.run(plugin, () -> {
            Inventory inventory = GuiInventories.create(MENU_ID, 27, message(messages, TITLE_KEY, TITLE));
            int slot = 0;
            for (RoleView role : roles.stream().limit(18).toList()) {
                inventory.setItem(slot++, roleItem(role, messages));
            }
            if (roles.isEmpty()) {
                inventory.setItem(13, item(Material.GRAY_DYE, message(messages, "role-menu-empty-title", "커스텀 역할 없음"), message(messages, "role-menu-empty-example", "Core 역할 카탈로그가 비어 있습니다.")));
            }
            inventory.setItem(18, GuiItems.action(Material.PAPER, message(messages, "role-menu-list-name", "역할 목록"), "island.roles.list", message(messages, "role-menu-list-lore", "Core 역할 카탈로그를 채팅에 출력합니다.")));
            inventory.setItem(19, GuiItems.action(Material.COMPARATOR, message(messages, "role-menu-permission-name", "권한 설정"), "island.permissions.open", message(messages, "role-menu-permission-lore", "역할별 권한 매트릭스를 엽니다.")));
            inventory.setItem(20, GuiItems.action(Material.CLOCK, message(messages, "role-menu-refresh-name", "새로고침"), "island.roles.open", message(messages, "role-menu-refresh-lore", "Core에서 역할을 다시 불러옵니다.")));
            inventory.setItem(26, GuiItems.action(Material.REDSTONE_TORCH, message(messages, "role-menu-settings-name", "설정"), "island.settings.open", message(messages, "role-menu-settings-lore", "섬 설정으로 돌아갑니다.")));
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
        if (messages == null) {
            return fallback;
        }
        String rendered = messages.plain(key);
        return rendered.isBlank() ? fallback : rendered;
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
