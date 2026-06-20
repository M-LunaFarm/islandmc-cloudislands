package kr.lunaf.cloudislands.paper.gui;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.paper.application.view.PaperGuiViews;
import kr.lunaf.cloudislands.paper.application.view.PaperGuiViews.MemberView;
import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import kr.lunaf.cloudislands.paper.platform.player.BukkitPlayerGateway;
import kr.lunaf.cloudislands.paper.platform.player.PaperPlayerGateway;
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

public final class IslandMemberMenu implements Listener {
    private static final String TITLE = "섬 멤버 관리";
    private static final String MENU_ID = "island.members";
    private static final PaperPlayerGateway PLAYERS = new BukkitPlayerGateway();
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
        PaperGuiViews.islandMembers(client, islandId)
            .thenAccept(members -> openSync(plugin, player, members, messages))
            .exceptionally(error -> {
                kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers.run(plugin, () -> player.sendMessage(message(messages, "member-menu-load-failed", "섬 멤버를 불러오지 못했습니다.")));
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
                actionId = "island.member.promote";
            } else if (click == GuiClick.RIGHT) {
                actionId = "island.member.demote";
            } else if (click == GuiClick.SHIFT_RIGHT) {
                actionId = "island.member.remove.prepare";
            } else {
                return;
            }
        }
        player.closeInventory();
        actions.execute(player, actionId, data, click);
    }

    private static void openSync(Plugin plugin, Player player, List<MemberView> members, MessageRenderer messages) {
        kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers.run(plugin, () -> {
            Inventory inventory = GuiInventories.create(MENU_ID, 54, TITLE);
            inventory.setItem(45, GuiItems.action(Material.WRITABLE_BOOK, message(messages, "member-menu-invite-name", "멤버 초대"), "island.member.invite.help", message(messages, "member-menu-invite-usage", "사용법: /섬 초대 <플레이어>")));
            inventory.setItem(46, GuiItems.action(Material.PAPER, message(messages, "member-menu-invite-list-name", "초대 목록"), "island.invites.open"));
            inventory.setItem(47, GuiItems.action(Material.COMPARATOR, message(messages, "member-menu-permission-name", "권한 설정"), "island.permissions.open"));
            inventory.setItem(48, GuiItems.action(Material.REDSTONE_TORCH, message(messages, "member-menu-settings-name", "설정"), "island.settings.open"));
            inventory.setItem(49, GuiItems.action(Material.CLOCK, message(messages, "member-menu-refresh-name", "새로고침"), "island.members.open"));
            inventory.setItem(53, GuiItems.action(Material.COMPASS, message(messages, "member-menu-main-menu-name", "메인 메뉴"), "island.main.open"));
            int slot = 0;
            for (MemberView member : members.stream().limit(45).toList()) {
                inventory.setItem(slot++, memberItem(member, messages));
            }
            player.openInventory(inventory);
        });
    }

    private static ItemStack memberItem(MemberView member, MessageRenderer messages) {
        Material material = switch (member.role()) {
            case "OWNER" -> Material.NETHER_STAR;
            case "CO_OWNER" -> Material.DIAMOND;
            case "MODERATOR" -> Material.IRON_SWORD;
            case "TRUSTED" -> Material.EMERALD;
            default -> Material.PLAYER_HEAD;
        };
        return GuiItems.action(material, member.role() + " " + shortUuid(member.playerUuid()), "island.member.role",
            Map.of("playerUuid", member.playerUuid()),
            statusLine(member.playerUuid(), messages),
            lastSeenLine(member.playerUuid(), messages),
            member.joinedAt().isBlank() ? message(messages, "member-menu-no-join-info", "가입 정보 없음") : message(messages, "member-menu-joined-at", "가입 시각: ") + member.joinedAt(),
            message(messages, "member-menu-left-click", "좌클릭: 승급"),
            message(messages, "member-menu-right-click", "우클릭: 강등"),
            message(messages, "member-menu-shift-right-click", "Shift+우클릭: 추방 확인"),
            message(messages, "member-menu-transfer-line", "소유권 이전은 별도 확인 경로에서 처리됩니다."));
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

    private static String statusLine(String playerUuid, MessageRenderer messages) {
        UUID uuid = uuid(playerUuid);
        if (uuid == null) {
            return message(messages, "member-menu-status-unknown", "접속 상태: 알 수 없음");
        }
        return PLAYERS.onlinePlayer(uuid) == null
                ? message(messages, "member-menu-status-offline", "접속 상태: 오프라인")
                : message(messages, "member-menu-status-online", "접속 상태: 온라인");
    }

    private static String lastSeenLine(String playerUuid, MessageRenderer messages) {
        UUID uuid = uuid(playerUuid);
        if (uuid == null) {
            return message(messages, "member-menu-last-seen-unknown", "마지막 접속: 알 수 없음");
        }
        long lastPlayed = Bukkit.getOfflinePlayer(uuid).getLastPlayed();
        if (lastPlayed <= 0L) {
            return message(messages, "member-menu-last-seen-never", "마지막 접속: 기록 없음");
        }
        return message(messages, "member-menu-last-seen-prefix", "마지막 접속: ") + java.time.Instant.ofEpochMilli(lastPlayed);
    }

    private static UUID uuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

}
