package kr.lunaf.cloudislands.paper.gui;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.paper.application.view.PaperGuiViews;
import kr.lunaf.cloudislands.paper.application.view.PaperGuiViews.MemberView;
import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

public final class IslandMemberMenu implements Listener {
    private static final String TITLE = "섬 멤버 관리";
    private static final String MENU_ID = "island.members";
    private static final int MEMBERS_PER_PAGE = 45;
    private final MessageRenderer messages;
    private final GuiActionExecutor actions;

    public IslandMemberMenu() {
        this(null);
    }

    public IslandMemberMenu(MessageRenderer messages) {
        this(messages, GuiActionExecutor.noop());
    }

    public IslandMemberMenu(MessageRenderer messages, GuiActionExecutor actions) {
        this.messages = messages;
        this.actions = actions == null ? GuiActionExecutor.noop() : actions;
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player, UUID islandId) {
        open(plugin, client, player, islandId, null);
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player, UUID islandId, MessageRenderer messages) {
        open(plugin, client, player, islandId, messages, 0);
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player, UUID islandId, MessageRenderer messages, int page) {
        int safePage = Math.max(0, page);
        GuiSession session = GuiSessions.begin(player, MENU_ID);
        GuiStateMenus.openLoading(plugin, player, session, messages, TITLE);
        PaperGuiViews.islandMembers(client, islandId)
            .thenAccept(members -> openSync(plugin, player, session, members, messages, safePage))
            .exceptionally(error -> {
                GuiStateMenus.openError(plugin, player, session, messages, TITLE, message(messages, "member-menu-load-failed", "섬 멤버를 불러오지 못했습니다."), "island.members.open", "island.main.open");
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
        Map<String, String> data = GuiItems.data(event.getCurrentItem());
        if (actionId.equals("island.member.invite.help")) {
            player.sendMessage(message(messages, "member-menu-invite-usage", "사용법: /섬 초대 <플레이어>"));
            return;
        }
        if (actionId.equals("island.member.role")) {
            if (click == GuiClick.LEFT) {
                actionId = "island.member.detail";
            } else if (click == GuiClick.RIGHT) {
                actionId = "island.member.demote.prepare";
            } else if (click == GuiClick.SHIFT_LEFT) {
                actionId = "island.member.promote.prepare";
            } else if (click == GuiClick.SHIFT_RIGHT) {
                actionId = "island.member.remove.prepare";
            } else {
                return;
            }
        }
        player.closeInventory();
        actions.execute(player, actionId, data, click);
    }

    private static void openSync(Plugin plugin, Player player, GuiSession session, List<MemberView> members, MessageRenderer messages, int page) {
        GuiSessions.runIfCurrent(plugin, player, session, () -> {
            Inventory inventory = GuiInventories.create(MENU_ID, 54, TITLE);
            int maxPage = maxPage(members.size());
            int safePage = Math.max(0, Math.min(maxPage, page));
            inventory.setItem(45, GuiItems.action(Material.WRITABLE_BOOK, message(messages, "member-menu-invite-name", "멤버 초대"), "island.member.invite.help", message(messages, "member-menu-invite-usage", "사용법: /섬 초대 <플레이어>")));
            inventory.setItem(46, GuiItems.action(Material.ARROW, message(messages, "member-menu-prev-page-name", "이전 페이지"), "island.members.page", Map.of("page", String.valueOf(Math.max(0, safePage - 1))), pageLine(safePage, maxPage, members.size())));
            inventory.setItem(47, GuiItems.action(Material.COMPARATOR, message(messages, "member-menu-permission-name", "권한 설정"), "island.permissions.open"));
            inventory.setItem(48, GuiItems.action(Material.ARROW, message(messages, "member-menu-next-page-name", "다음 페이지"), "island.members.page", Map.of("page", String.valueOf(Math.min(maxPage, safePage + 1))), pageLine(safePage, maxPage, members.size())));
            inventory.setItem(49, GuiItems.action(Material.CLOCK, message(messages, "member-menu-refresh-name", "새로고침"), "island.members.open"));
            inventory.setItem(50, GuiItems.action(Material.PAPER, message(messages, "member-menu-invite-list-name", "초대 목록"), "island.invites.open"));
            inventory.setItem(51, GuiItems.action(Material.MAP, message(messages, "member-menu-page-name", "페이지"), "island.member.list", pageLine(safePage, maxPage, members.size())));
            inventory.setItem(52, GuiItems.action(Material.REDSTONE_TORCH, message(messages, "member-menu-settings-name", "설정"), "island.settings.open"));
            inventory.setItem(53, GuiItems.action(Material.COMPASS, message(messages, "member-menu-main-menu-name", "메인 메뉴"), "island.main.open"));
            int slot = 0;
            for (MemberView member : members.stream().skip((long) safePage * MEMBERS_PER_PAGE).limit(MEMBERS_PER_PAGE).toList()) {
                inventory.setItem(slot++, memberItem(member, messages));
            }
            player.openInventory(inventory);
        });
    }

    private static int maxPage(int size) {
        return Math.max(0, (Math.max(0, size) - 1) / MEMBERS_PER_PAGE);
    }

    private static String pageLine(int page, int maxPage, int memberCount) {
        return "Page " + (page + 1) + "/" + (maxPage + 1) + " (" + memberCount + " members)";
    }

    private static ItemStack memberItem(MemberView member, MessageRenderer messages) {
        Material material = switch (member.role()) {
            case "OWNER" -> Material.NETHER_STAR;
            case "CO_OWNER" -> Material.DIAMOND;
            case "MODERATOR" -> Material.IRON_SWORD;
            case "TRUSTED" -> Material.EMERALD;
            default -> Material.PLAYER_HEAD;
        };
        return GuiItems.action(material, displayName(member), "island.member.role",
            Map.of(
                "playerUuid", member.playerUuid(),
                "playerName", displayName(member),
                "role", member.role(),
                "lastSeenAt", member.lastSeenAt() == null ? "" : member.lastSeenAt(),
                "presenceState", member.presenceState() == null ? "" : member.presenceState()
            ),
            roleLine(member, messages),
            statusLine(member, messages),
            lastSeenLine(member, messages),
            member.joinedAt().isBlank() ? message(messages, "member-menu-no-join-info", "가입 정보 없음") : message(messages, "member-menu-joined-at", "가입 시각: ") + member.joinedAt(),
            message(messages, "member-menu-left-click", "좌클릭: 상세 관리"),
            message(messages, "member-menu-right-click", "우클릭: 강등 확인"),
            message(messages, "member-menu-shift-left-click", "Shift+좌클릭: 승급 확인"),
            message(messages, "member-menu-shift-right-click", "Shift+우클릭: 추방 확인"),
            message(messages, "member-menu-transfer-line", "소유권 이전은 별도 확인 경로에서 처리됩니다."),
            message(messages, "member-menu-debug-uuid", "UUID: ") + shortUuid(member.playerUuid()));
    }

    private static String message(MessageRenderer messages, String key, String fallback) {
        if (messages == null) {
            return fallback;
        }
        String rendered = messages.plain(key);
        return rendered.isBlank() ? fallback : rendered;
    }

    private static String shortUuid(String uuid) {
        return uuid.length() <= 8 ? uuid : uuid.substring(0, 8);
    }

    private static String displayName(MemberView member) {
        if (member.playerName() != null && !member.playerName().isBlank()) {
            return member.playerName();
        }
        return "Player " + shortUuid(member.playerUuid());
    }

    private static String roleLine(MemberView member, MessageRenderer messages) {
        return message(messages, "member-menu-role-prefix", "역할: ") + member.role();
    }

    private static String statusLine(MemberView member, MessageRenderer messages) {
        String state = member.presenceState() == null ? "" : member.presenceState();
        if (state.equals("RECENT_ACTIVITY")) {
            return message(messages, "member-menu-status-recent", "네트워크 상태: 최근 활동 확인됨");
        }
        return message(messages, "member-menu-status-unknown", "네트워크 상태: 알 수 없음");
    }

    private static String lastSeenLine(MemberView member, MessageRenderer messages) {
        if (member.lastSeenAt() == null || member.lastSeenAt().isBlank()) {
            return message(messages, "member-menu-last-seen-never", "마지막 활동: 기록 없음");
        }
        return message(messages, "member-menu-last-seen-prefix", "마지막 활동: ") + member.lastSeenAt();
    }

}
