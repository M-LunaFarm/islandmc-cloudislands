package kr.lunaf.cloudislands.paper.gui;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.paper.application.view.PaperGuiViews;
import kr.lunaf.cloudislands.paper.application.view.PaperGuiViews.BanView;
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

public final class IslandBanMenu implements Listener {
    private static final String TITLE = "방문자 밴 목록";
    private static final String MENU_ID = "island.bans";
    private final MessageRenderer messages;
    private final GuiActionRegistry actions;

    public IslandBanMenu() {
        this(null);
    }

    public IslandBanMenu(MessageRenderer messages) {
        this(messages, new GuiActionRegistry(GuiActionExecutor.noop()));
    }

    public IslandBanMenu(MessageRenderer messages, GuiActionRegistry actions) {
        this.messages = messages;
        this.actions = actions == null ? new GuiActionRegistry(GuiActionExecutor.noop()) : actions;
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player, UUID islandId) {
        open(plugin, client, player, islandId, null);
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player, UUID islandId, MessageRenderer messages) {
        GuiSession session = GuiSessions.begin(player, MENU_ID);
        GuiStateMenus.openLoading(plugin, player, session, messages, TITLE);
        PaperGuiViews.islandBans(client, islandId)
            .thenAccept(bans -> openSync(plugin, player, session, bans, messages))
            .exceptionally(error -> {
                GuiStateMenus.openError(plugin, player, session, messages, TITLE, message(messages, "ban-menu-load-failed", "섬 밴 목록을 불러오지 못했습니다."), "island.bans.open", "island.settings.open");
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
            actions.execute(player, "island.bans.open", GuiClick.from(event));
            return;
        }
        if (slot == 53) {
            actions.execute(player, "island.settings.open", GuiClick.from(event));
            return;
        }
        Map<String, String> data = GuiItems.data(event.getCurrentItem());
        String bannedUuid = data.getOrDefault("playerUuid", "");
        if (bannedUuid.isBlank()) {
            return;
        }
        if (event.isRightClick()) {
            actions.execute(player, "island.ban.pardon.prepare", java.util.Map.of("playerUuid", bannedUuid), GuiClick.from(event));
            return;
        }
        player.sendMessage(message(messages, "ban-menu-detail-title", "방문자 밴 상세"));
        player.sendMessage("- " + message(messages, "ban-menu-player", "대상: ") + shortUuid(bannedUuid));
        player.sendMessage("- " + message(messages, "ban-menu-actor", "처리자: ") + shortUuid(data.getOrDefault("actorUuid", "")));
        player.sendMessage("- " + message(messages, "ban-menu-reason", "사유: ") + fallback(data.get("reason"), message(messages, "ban-menu-none", "없음")));
        player.sendMessage("- " + message(messages, "ban-menu-created-at", "생성 시각: ") + fallback(data.get("createdAt"), message(messages, "ban-menu-no-created-info", "생성 정보 없음")));
        player.sendMessage("- " + message(messages, "ban-menu-expires-at", "만료 시각: ") + fallback(data.get("expiresAt"), message(messages, "ban-menu-no-expire", "만료 없음")));
    }

    private static void openSync(Plugin plugin, Player player, GuiSession session, List<BanView> bans, MessageRenderer messages) {
        GuiSessions.runIfCurrent(plugin, player, session, () -> {
            Inventory inventory = GuiInventories.create(MENU_ID, 54, TITLE);
            if (bans.isEmpty()) {
                inventory.setItem(22, item(Material.BARRIER, message(messages, "ban-menu-empty-title", "밴 기록 없음"), message(messages, "ban-menu-empty", "현재 밴된 방문자가 없습니다.")));
            } else {
                for (int index = 0; index < bans.size() && index < 45; index++) {
                    inventory.setItem(index, banItem(bans.get(index), messages));
                }
            }
            inventory.setItem(49, item(Material.CLOCK, message(messages, "ban-menu-refresh-name", "새로고침"), message(messages, "ban-menu-refresh-command", "/섬 밴목록")));
            inventory.setItem(53, item(Material.COMPARATOR, message(messages, "ban-menu-settings-name", "설정"), message(messages, "ban-menu-settings-command", "/섬 설정")));
            player.openInventory(inventory);
        });
    }

    private static ItemStack banItem(BanView ban, MessageRenderer messages) {
        return GuiItems.action(Material.BARRIER, message(messages, "ban-menu-title-prefix", "밴 ") + shortUuid(ban.bannedUuid()), "island.ban.pardon.prepare",
            Map.of(
                "playerUuid", ban.bannedUuid(),
                "actorUuid", ban.actorUuid(),
                "reason", ban.reason(),
                "createdAt", ban.createdAt(),
                "expiresAt", ban.expiresAt()
            ),
            message(messages, "ban-menu-actor", "처리자: ") + shortUuid(ban.actorUuid()),
            message(messages, "ban-menu-reason", "사유: ") + (ban.reason().isBlank() ? message(messages, "ban-menu-none", "없음") : ban.reason()),
            ban.createdAt().isBlank() ? message(messages, "ban-menu-no-created-info", "생성 정보 없음") : message(messages, "ban-menu-created-at", "생성 시각: ") + ban.createdAt(),
            ban.expiresAt().isBlank() ? message(messages, "ban-menu-no-expire", "만료 없음") : message(messages, "ban-menu-expires-at", "만료 시각: ") + ban.expiresAt(),
            message(messages, "ban-menu-left-click", "좌클릭: 상세 보기"),
            message(messages, "ban-menu-right-click", "우클릭: 밴 해제"));
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

    private static String fallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

}
