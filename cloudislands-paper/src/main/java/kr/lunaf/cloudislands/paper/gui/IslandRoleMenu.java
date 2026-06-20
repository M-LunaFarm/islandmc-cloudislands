package kr.lunaf.cloudislands.paper.gui;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.paper.application.view.PaperGuiViews;
import kr.lunaf.cloudislands.paper.application.view.PaperGuiViews.RoleView;
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
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 27) {
            return;
        }
        player.closeInventory();
        if (slot == 18) {
            GuiActionRegistry.execute(player, "island.roles.list", GuiClick.from(event));
            return;
        }
        if (slot == 19) {
            GuiActionRegistry.execute(player, "island.permissions.open", GuiClick.from(event));
            return;
        }
        if (slot == 20) {
            GuiActionRegistry.execute(player, "island.roles.open", GuiClick.from(event));
            return;
        }
        if (slot == 26) {
            GuiActionRegistry.execute(player, "island.settings.open", GuiClick.from(event));
            return;
        }
        String role = GuiItems.data(event.getCurrentItem()).getOrDefault("role", "");
        if (!role.isBlank()) {
            player.sendMessage(message(messages, "role-menu-edit-prefix", "역할 편집: /섬 역할편집 ") + role + message(messages, "role-menu-edit-suffix", " <weight> <displayName>"));
        }
    }

    private static void openSync(Plugin plugin, Player player, List<RoleView> roles, MessageRenderer messages) {
        kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers.run(plugin, () -> {
            Inventory inventory = GuiInventories.create(MENU_ID, 27, message(messages, TITLE_KEY, TITLE));
            int slot = 0;
            for (RoleView role : roles.stream().limit(18).toList()) {
                inventory.setItem(slot++, roleItem(role, messages));
            }
            if (roles.isEmpty()) {
                inventory.setItem(13, item(Material.GRAY_DYE, message(messages, "role-menu-empty-title", "커스텀 역할 없음"), message(messages, "role-menu-empty-example", "/섬 역할편집 CUSTOM_1 5 부관리자")));
            }
            inventory.setItem(18, item(Material.PAPER, message(messages, "role-menu-list-name", "역할 목록"), message(messages, "role-menu-list-command", "/섬 역할목록")));
            inventory.setItem(19, item(Material.COMPARATOR, message(messages, "role-menu-permission-name", "권한 설정"), message(messages, "role-menu-permission-command", "/섬 권한")));
            inventory.setItem(20, item(Material.CLOCK, message(messages, "role-menu-refresh-name", "새로고침"), message(messages, "role-menu-refresh-command", "/섬 역할")));
            inventory.setItem(26, item(Material.REDSTONE_TORCH, message(messages, "role-menu-settings-name", "설정"), message(messages, "role-menu-settings-command", "/섬 설정")));
            player.openInventory(inventory);
        });
    }

    private static ItemStack roleItem(RoleView role, MessageRenderer messages) {
        return GuiItems.action(material(role.role()), role.displayName().isBlank() ? role.role() : role.displayName(), "island.role.edit.help",
            Map.of("role", role.role()),
            message(messages, "role-menu-weight", "weight=") + role.weight(),
            message(messages, "role-menu-enum", "enum=") + role.role(),
            message(messages, "role-menu-click-edit", "클릭: 편집 명령어 안내"));
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
