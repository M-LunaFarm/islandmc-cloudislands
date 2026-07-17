package kr.lunaf.cloudislands.paper.gui;

import java.util.List;
import java.util.Map;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.paper.application.view.PaperGuiViews;
import kr.lunaf.cloudislands.paper.application.view.PaperGuiViews.InviteView;
import kr.lunaf.cloudislands.paper.message.MessageRenderer;
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
            "page", "island.invites.page",
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
        open(plugin, client, player, messages, 0);
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player, MessageRenderer messages, int page) {
        GuiSession session = GuiSessions.begin(player, MENU_ID);
        GuiStateMenus.openLoading(plugin, player, session, messages, message(messages, MENU.titleKey(), TITLE));
        PaperGuiViews.pendingInvites(client, player.getUniqueId())
            .thenAccept(invites -> openSync(plugin, player, session, invites, messages, page))
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
        if (slot < 0 || slot >= MENU.size()) {
            return;
        }
        Map<String, String> data = GuiItems.data(event.getCurrentItem());
        String inviteId = data.getOrDefault("inviteId", "");
        if (!inviteId.isBlank()) {
            player.closeInventory();
            actions.execute(player, new GuiAction.InviteAction(
                event.isRightClick() ? GuiAction.InviteActionType.DECLINE : GuiAction.InviteActionType.ACCEPT,
                java.util.UUID.fromString(inviteId)
            ), GuiClick.from(event));
            return;
        }
        String actionId = GuiItems.actionId(event.getCurrentItem());
        if (!actionId.isBlank()) {
            player.closeInventory();
            actions.execute(player, GuiActions.from(actionId, data).orElse(null), GuiClick.from(event));
        }
    }

    private static void openSync(Plugin plugin, Player player, GuiSession session, List<InviteView> invites, MessageRenderer messages, int requestedPage) {
        GuiSessions.runIfCurrent(plugin, player, session, () -> {
            List<Integer> inviteSlots = GuiMenuRenderer.slots(MENU, "_");
            int pageSize = Math.max(1, inviteSlots.size());
            int maxPage = Math.max(0, (invites.size() - 1) / pageSize);
            int page = Math.max(0, Math.min(requestedPage, maxPage));
            String title = message(messages, MENU.titleKey(), TITLE) + " " + (page + 1) + "/" + (maxPage + 1);
            Inventory inventory = GuiMenuRenderer.render(MENU, session, messages, title, item -> !List.of("E", "_", "P", "N").contains(item.symbol()));
            if (invites.isEmpty()) {
                setEmptyItem(inventory, messages);
            } else {
                int offset = page * pageSize;
                for (int index = 0; index < pageSize && offset + index < invites.size(); index++) {
                    inventory.setItem(inviteSlots.get(index), inviteItem(invites.get(offset + index), messages));
                }
                if (page > 0) {
                    setPageItem(inventory, "P", page - 1, messages);
                }
                if (page < maxPage) {
                    setPageItem(inventory, "N", page + 1, messages);
                }
            }
            player.openInventory(inventory);
        });
    }

    private static ItemStack inviteItem(InviteView invite, MessageRenderer messages) {
        return GuiItems.action(GuiMenuRenderer.material(MENU, "_", "WRITABLE_BOOK"), message(messages, "invite-menu-title-prefix", "섬 초대 ") + islandDisplay(invite), "island.invite.accept",
            Map.of("inviteId", invite.inviteId()),
            message(messages, "invite-menu-island", "섬: ") + islandDisplay(invite),
            message(messages, "invite-menu-inviter", "초대한 사람: ") + inviterDisplay(invite),
            invite.createdAt().isBlank() ? message(messages, "invite-menu-no-created-info", "생성 정보 없음") : message(messages, "invite-menu-created-at", "생성 시각: ") + invite.createdAt(),
            invite.expiresAt().isBlank() ? message(messages, "invite-menu-no-expire-info", "만료 정보 없음") : message(messages, "invite-menu-expires-at", "만료 시각: ") + invite.expiresAt(),
            message(messages, "invite-menu-left-click", "좌클릭: 초대 수락"),
            message(messages, "invite-menu-right-click", "우클릭: 초대 거절"));
    }

    private static String message(MessageRenderer messages, String key, String fallback) {
        return GuiMenuRenderer.message(messages, key, fallback);
    }

    private static void setEmptyItem(Inventory inventory, MessageRenderer messages) {
        GuiMenuRenderer.setSymbolItem(inventory, MENU, "E", messages, Map.of(), List.of());
    }

    private static void setPageItem(Inventory inventory, String symbol, int page, MessageRenderer messages) {
        String key = symbol.equals("P") ? "invite-menu-previous-page" : "invite-menu-next-page";
        String fallback = symbol.equals("P") ? "이전 페이지" : "다음 페이지";
        GuiMenuRenderer.setSymbolItem(inventory, MENU, symbol, messages, Map.of("page", Integer.toString(page)), List.of(message(messages, key, fallback)));
    }

    private static String shortUuid(String uuid) {
        return uuid.length() <= 8 ? uuid : uuid.substring(0, 8);
    }

    static String islandDisplay(InviteView invite) {
        return invite.islandName().isBlank() ? shortUuid(invite.islandId()) : invite.islandName().trim();
    }

    static String inviterDisplay(InviteView invite) {
        return invite.inviterName().isBlank() ? shortUuid(invite.inviterUuid()) : invite.inviterName().trim();
    }

}
