package kr.lunaf.cloudislands.paper.gui;

import java.util.ArrayList;
import java.util.List;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
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
    private static final String TITLE = "섬 초대 목록";
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
        client.listPendingInvites(player.getUniqueId())
            .thenAccept(body -> openSync(plugin, player, invites(body), messages))
            .exceptionally(error -> {
                plugin.getServer().getScheduler().runTask(plugin, () -> player.sendMessage(message(messages, "invite-menu-load-failed", "섬 초대 목록을 불러오지 못했습니다.")));
                return null;
            });
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!TITLE.equals(event.getView().getTitle())) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getCurrentItem() == null) {
            return;
        }
        ItemMeta meta = event.getCurrentItem().getItemMeta();
        if (meta == null || !meta.hasDisplayName()) {
            return;
        }
        String name = meta.getDisplayName();
        player.closeInventory();
        if (name.equals("새로고침")) {
            player.performCommand("섬 초대목록");
            return;
        }
        if (name.equals("멤버 관리")) {
            player.performCommand("섬 멤버관리");
            return;
        }
        if (name.equals("메인 메뉴")) {
            player.performCommand("섬 메뉴");
            return;
        }
        String inviteId = loreValue(meta, "초대 ID=");
        if (inviteId.isBlank()) {
            return;
        }
        player.performCommand((event.isRightClick() ? "섬 초대거절 " : "섬 초대수락 ") + inviteId);
    }

    private static void openSync(Plugin plugin, Player player, List<Invite> invites, MessageRenderer messages) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Inventory inventory = Bukkit.createInventory(null, 54, TITLE);
            if (invites.isEmpty()) {
                inventory.setItem(22, item(Material.BARRIER, "대기 중인 초대 없음", message(messages, "invite-menu-empty", "받은 섬 초대가 없습니다.")));
            } else {
                int slot = 0;
                for (Invite invite : invites.stream().limit(45).toList()) {
                    inventory.setItem(slot++, inviteItem(invite, messages));
                }
            }
            inventory.setItem(45, item(Material.NAME_TAG, "멤버 관리", "/섬 멤버관리"));
            inventory.setItem(49, item(Material.CLOCK, "새로고침", "/섬 초대목록"));
            inventory.setItem(53, item(Material.COMPASS, "메인 메뉴", "/섬 메뉴"));
            player.openInventory(inventory);
        });
    }

    private static ItemStack inviteItem(Invite invite, MessageRenderer messages) {
        return item(Material.WRITABLE_BOOK, "섬 초대 " + shortUuid(invite.islandId()),
            "초대 ID=" + invite.inviteId(),
            "섬 ID: " + shortUuid(invite.islandId()),
            "초대한 사람: " + shortUuid(invite.inviterUuid()),
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

    private static List<Invite> invites(String body) {
        List<Invite> invites = new ArrayList<>();
        int index = 0;
        while (body != null && index < body.length()) {
            int objectStart = body.indexOf('{', index);
            if (objectStart < 0) {
                break;
            }
            int objectEnd = body.indexOf('}', objectStart);
            if (objectEnd < 0) {
                break;
            }
            String object = body.substring(objectStart, objectEnd + 1);
            String inviteId = text(object, "inviteId");
            if (!inviteId.isBlank()) {
                invites.add(new Invite(inviteId, text(object, "islandId"), text(object, "inviterUuid"), text(object, "createdAt"), text(object, "expiresAt")));
            }
            index = objectEnd + 1;
        }
        return invites;
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

    private static String loreValue(ItemMeta meta, String prefix) {
        if (meta.getLore() == null) {
            return "";
        }
        for (String line : meta.getLore()) {
            if (line.startsWith(prefix)) {
                return line.substring(prefix.length());
            }
        }
        return "";
    }

    private static String text(String body, String key) {
        String needle = "\"" + key + "\":\"";
        int start = body.indexOf(needle);
        if (start < 0) {
            return "";
        }
        start += needle.length();
        int end = body.indexOf('"', start);
        return end < start ? "" : body.substring(start, end).replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private static String shortUuid(String uuid) {
        return uuid.length() <= 8 ? uuid : uuid.substring(0, 8);
    }

    private record Invite(String inviteId, String islandId, String inviterUuid, String createdAt, String expiresAt) {
    }
}
