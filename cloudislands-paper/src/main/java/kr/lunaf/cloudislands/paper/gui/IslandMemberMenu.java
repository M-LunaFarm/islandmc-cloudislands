package kr.lunaf.cloudislands.paper.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
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

    public static void open(Plugin plugin, CoreApiClient client, Player player, UUID islandId) {
        client.listIslandMembers(islandId)
            .thenAccept(body -> openSync(plugin, player, members(body)))
            .exceptionally(error -> {
                plugin.getServer().getScheduler().runTask(plugin, () -> player.sendMessage("섬 멤버를 불러오지 못했습니다."));
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
        if (name.equals("멤버 초대")) {
            player.sendMessage("사용법: /섬 초대 <플레이어>");
            return;
        }
        if (name.equals("초대 목록")) {
            player.performCommand("섬 초대목록");
            return;
        }
        if (name.equals("권한 설정")) {
            player.performCommand("섬 권한");
            return;
        }
        if (name.equals("설정")) {
            player.performCommand("섬 설정");
            return;
        }
        if (name.equals("새로고침")) {
            player.performCommand("섬 멤버관리");
            return;
        }
        if (name.equals("메인 메뉴")) {
            player.performCommand("섬 메뉴");
            return;
        }
        String playerUuid = loreValue(meta, "플레이어=");
        if (!playerUuid.isBlank()) {
            if (event.isShiftClick() && event.isRightClick()) {
                player.performCommand("섬 추방 " + playerUuid);
                return;
            }
            if (event.isRightClick()) {
                player.performCommand("섬 강등 " + playerUuid);
                return;
            }
            if (event.isLeftClick()) {
                player.performCommand("섬 승급 " + playerUuid);
                return;
            }
            player.sendMessage("소유권 이전은 명령어로 직접 확인해주세요: /섬 양도 " + playerUuid);
        }
    }

    private static void openSync(Plugin plugin, Player player, List<Member> members) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Inventory inventory = Bukkit.createInventory(null, 54, TITLE);
            inventory.setItem(45, item(Material.WRITABLE_BOOK, "멤버 초대", "사용법: /섬 초대 <플레이어>"));
            inventory.setItem(46, item(Material.PAPER, "초대 목록", "/섬 초대목록"));
            inventory.setItem(47, item(Material.COMPARATOR, "권한 설정", "/섬 권한"));
            inventory.setItem(48, item(Material.REDSTONE_TORCH, "설정", "/섬 설정"));
            inventory.setItem(49, item(Material.CLOCK, "새로고침", "/섬 멤버관리"));
            inventory.setItem(53, item(Material.COMPASS, "메인 메뉴", "/섬 메뉴"));
            int slot = 0;
            for (Member member : members.stream().limit(45).toList()) {
                inventory.setItem(slot++, memberItem(member));
            }
            player.openInventory(inventory);
        });
    }

    private static ItemStack memberItem(Member member) {
        Material material = switch (member.role()) {
            case "OWNER" -> Material.NETHER_STAR;
            case "CO_OWNER" -> Material.DIAMOND;
            case "MODERATOR" -> Material.IRON_SWORD;
            case "TRUSTED" -> Material.EMERALD;
            default -> Material.PLAYER_HEAD;
        };
        return item(material, member.role() + " " + shortUuid(member.playerUuid()),
            "플레이어=" + member.playerUuid(),
            member.joinedAt().isBlank() ? "가입 정보 없음" : "가입 시각: " + member.joinedAt(),
            "좌클릭: 승급",
            "우클릭: 강등",
            "Shift+우클릭: 추방",
            "양도: /섬 양도 " + member.playerUuid());
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

    private static List<Member> members(String body) {
        List<Member> members = new ArrayList<>();
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
            String playerUuid = text(object, "playerUuid");
            if (!playerUuid.isBlank()) {
                members.add(new Member(playerUuid, text(object, "role"), text(object, "joinedAt")));
            }
            index = objectEnd + 1;
        }
        return members;
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
        int end = jsonStringEnd(body, start);
        if (end < start) {
            return "";
        }
        return body.substring(start, end).replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private static int jsonStringEnd(String body, int start) {
        boolean escaped = false;
        for (int i = start; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c == '"' && !escaped) {
                return i;
            }
            escaped = c == '\\' && !escaped;
            if (c != '\\') {
                escaped = false;
            }
        }
        return -1;
    }

    private static String shortUuid(String uuid) {
        return uuid.length() <= 8 ? uuid : uuid.substring(0, 8);
    }

    private record Member(String playerUuid, String role, String joinedAt) {
    }
}
