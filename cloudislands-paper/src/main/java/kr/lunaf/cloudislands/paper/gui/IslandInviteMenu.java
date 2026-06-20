package kr.lunaf.cloudislands.paper.gui;

import java.util.List;
import java.util.Map;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.paper.application.view.PaperGuiViews;
import kr.lunaf.cloudislands.paper.application.view.PaperGuiViews.InviteView;
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

public final class IslandInviteMenu implements Listener {
    private static final String TITLE_KEY = "invite-menu-title";
    private static final String TITLE = "섬 초대 목록";
    private static final String MENU_ID = "island.invites";
    private final MessageRenderer messages;

    public IslandInviteMenu() {
        this(null);
    }

    public IslandInviteMenu(MessageRenderer messages) {
        this.messages = messages;
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player) {
        open(plugin, client, player, null);
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player, MessageRenderer messages) {
        PaperGuiViews.pendingInvites(client, player.getUniqueId())
            .thenAccept(invites -> openSync(plugin, player, invites, messages))
            .exceptionally(error -> {
                kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers.run(plugin, () -> player.sendMessage(message(messages, "invite-menu-load-failed", "섬 초대 목록을 불러오지 못했습니다.")));
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
        if (slot < 0 || slot >= 54) {
            return;
        }
        player.closeInventory();
        if (slot == 49) {
            GuiActionRegistry.execute(player, "island.invites.open", GuiClick.from(event));
            return;
        }
        if (slot == 45) {
            GuiActionRegistry.execute(player, "island.members.open", GuiClick.from(event));
            return;
        }
        if (slot == 53) {
            GuiActionRegistry.execute(player, "island.main.open", GuiClick.from(event));
            return;
        }
        String inviteId = GuiItems.data(event.getCurrentItem()).getOrDefault("inviteId", "");
        if (inviteId.isBlank()) {
            return;
        }
        GuiActionRegistry.execute(player, event.isRightClick() ? "island.invite.decline" : "island.invite.accept", java.util.Map.of("inviteId", inviteId), GuiClick.from(event));
    }

    private static void openSync(Plugin plugin, Player player, List<InviteView> invites, MessageRenderer messages) {
        kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers.run(plugin, () -> {
            Inventory inventory = GuiInventories.create(MENU_ID, 54, message(messages, TITLE_KEY, TITLE));
            if (invites.isEmpty()) {
                inventory.setItem(22, item(Material.BARRIER, message(messages, "invite-menu-empty-title", "대기 중인 초대 없음"), message(messages, "invite-menu-empty", "받은 섬 초대가 없습니다.")));
            } else {
                int slot = 0;
                for (InviteView invite : invites.stream().limit(45).toList()) {
                    inventory.setItem(slot++, inviteItem(invite, messages));
                }
            }
            inventory.setItem(45, item(Material.NAME_TAG, message(messages, "invite-menu-member-name", "멤버 관리"), message(messages, "invite-menu-member-command", "/섬 멤버관리")));
            inventory.setItem(49, item(Material.CLOCK, message(messages, "invite-menu-refresh-name", "새로고침"), message(messages, "invite-menu-refresh-command", "/섬 초대목록")));
            inventory.setItem(53, item(Material.COMPASS, message(messages, "invite-menu-main-menu-name", "메인 메뉴"), message(messages, "invite-menu-main-menu-command", "/섬 메뉴")));
            player.openInventory(inventory);
        });
    }

    private static ItemStack inviteItem(InviteView invite, MessageRenderer messages) {
        return GuiItems.action(Material.WRITABLE_BOOK, message(messages, "invite-menu-title-prefix", "섬 초대 ") + shortUuid(invite.islandId()), "island.invite.accept",
            Map.of("inviteId", invite.inviteId()),
            message(messages, "invite-menu-island-id", "섬 ID: ") + shortUuid(invite.islandId()),
            message(messages, "invite-menu-inviter", "초대한 사람: ") + shortUuid(invite.inviterUuid()),
            invite.createdAt().isBlank() ? message(messages, "invite-menu-no-created-info", "생성 정보 없음") : message(messages, "invite-menu-created-at", "생성 시각: ") + invite.createdAt(),
            invite.expiresAt().isBlank() ? message(messages, "invite-menu-no-expire-info", "만료 정보 없음") : message(messages, "invite-menu-expires-at", "만료 시각: ") + invite.expiresAt(),
            message(messages, "invite-menu-left-click", "좌클릭: 초대 수락"),
            message(messages, "invite-menu-right-click", "우클릭: 초대 거절"));
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

    private static String shortUuid(String uuid) {
        return uuid.length() <= 8 ? uuid : uuid.substring(0, 8);
    }

}
