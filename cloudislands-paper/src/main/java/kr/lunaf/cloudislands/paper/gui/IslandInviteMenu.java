package kr.lunaf.cloudislands.paper.gui;

import java.util.List;
import java.util.Map;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.paper.application.view.PaperGuiViews;
import kr.lunaf.cloudislands.paper.application.view.PaperGuiViews.InviteView;
import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

public final class IslandInviteMenu implements Listener {
    private static final String TITLE_KEY = "invite-menu-title";
    private static final String TITLE = "섬 초대 목록";
    private static final GuiMenuDefinition MENU = GuiMenuDefinition.bundled(
        "config-v2/ui/menus/invites.yml",
        new GuiMenuDefinition("island.invites", 6, TITLE_KEY, Map.of(
            "open", "island.invites.open",
            "accept", "island.invite.accept",
            "decline", "island.invite.decline",
            "back", "island.members.open",
            "members", "island.members.open",
            "main", "island.main.open"
        ))
    );
    private static final String MENU_ID = MENU.id();
    private final MessageRenderer messages;
    private final GuiActionRegistry actions;

    public IslandInviteMenu() {
        this(null);
    }

    public IslandInviteMenu(MessageRenderer messages) {
        this(messages, new GuiActionRegistry(GuiActionExecutor.noop()));
    }

    public IslandInviteMenu(MessageRenderer messages, GuiActionRegistry actions) {
        this.messages = messages;
        this.actions = actions == null ? new GuiActionRegistry(GuiActionExecutor.noop()) : actions;
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player) {
        open(plugin, client, player, null);
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player, MessageRenderer messages) {
        GuiSession session = GuiSessions.begin(player, MENU_ID);
        GuiStateMenus.openLoading(plugin, player, session, messages, message(messages, MENU.titleKey(), TITLE));
        PaperGuiViews.pendingInvites(client, player.getUniqueId())
            .thenAccept(invites -> openSync(plugin, player, session, invites, messages))
            .exceptionally(error -> {
                GuiStateMenus.openError(plugin, player, session, messages, message(messages, MENU.titleKey(), TITLE), message(messages, "invite-menu-load-failed", "섬 초대 목록을 불러오지 못했습니다."), "island.invites.open", "island.members.open");
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
        Map<String, String> data = GuiItems.data(event.getCurrentItem());
        String inviteId = data.getOrDefault("inviteId", "");
        if (!inviteId.isBlank()) {
            player.closeInventory();
            actions.execute(player, event.isRightClick() ? "island.invite.decline" : "island.invite.accept", java.util.Map.of("inviteId", inviteId), GuiClick.from(event));
            return;
        }
        String actionId = GuiItems.actionId(event.getCurrentItem());
        if (!actionId.isBlank()) {
            player.closeInventory();
            actions.execute(player, actionId, data, GuiClick.from(event));
        }
    }

    private static void openSync(Plugin plugin, Player player, GuiSession session, List<InviteView> invites, MessageRenderer messages) {
        GuiSessions.runIfCurrent(plugin, player, session, () -> {
            Inventory inventory = GuiMenuRenderer.render(MENU, session, messages, TITLE, item -> !"E".equals(item.symbol()));
            if (invites.isEmpty()) {
                setEmptyItem(inventory, messages);
            } else {
                int slot = 0;
                for (InviteView invite : invites.stream().limit(45).toList()) {
                    inventory.setItem(slot++, inviteItem(invite, messages));
                }
            }
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
        return GuiMenuRenderer.message(messages, key, fallback);
    }

    private static void setEmptyItem(Inventory inventory, MessageRenderer messages) {
        MENU.itemAt(22)
            .ifPresent(item -> inventory.setItem(22, GuiMenuRenderer.item(MENU, item, messages, Map.of(), List.of())));
    }

    private static String shortUuid(String uuid) {
        return uuid.length() <= 8 ? uuid : uuid.substring(0, 8);
    }

}
