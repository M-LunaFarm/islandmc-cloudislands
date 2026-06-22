package kr.lunaf.cloudislands.paper.gui;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.paper.application.view.PaperGuiViews;
import kr.lunaf.cloudislands.paper.application.view.PaperGuiViews.BanView;
import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

public final class IslandBanMenu implements Listener {
    private static final String TITLE = "방문자 밴 목록";
    private static final GuiMenuDefinition MENU = GuiMenuDefinition.bundled(
        "config-v2/ui/menus/bans.yml",
        new GuiMenuDefinition("island.bans", 6, "menu.bans.title", Map.of(
            "open", "island.bans.open",
            "list", "island.bans.list",
            "pardon-prepare", "island.ban.pardon.prepare",
            "pardon-confirm", ConfirmationTokenPolicy.BAN_PARDON_CONFIRM_ACTION,
            "back", "island.members.open",
            "settings", "island.settings.open"
        ))
    );
    private static final String MENU_ID = MENU.id();
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
        GuiStateMenus.openLoading(plugin, player, session, messages, message(messages, MENU.titleKey(), TITLE));
        PaperGuiViews.islandBans(client, islandId)
            .thenAccept(bans -> openSync(plugin, player, session, bans, messages))
            .exceptionally(error -> {
                GuiStateMenus.openError(plugin, player, session, messages, message(messages, MENU.titleKey(), TITLE), message(messages, "ban-menu-load-failed", "섬 밴 목록을 불러오지 못했습니다."), "island.bans.open", "island.settings.open");
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
        String bannedUuid = data.getOrDefault("playerUuid", "");
        if (bannedUuid.isBlank()) {
            String actionId = GuiItems.actionId(event.getCurrentItem());
            if (actionId.isBlank()) {
                return;
            }
            player.closeInventory();
            actions.execute(player, GuiActions.from(actionId, data).orElse(null), GuiClick.from(event));
            return;
        }
        player.closeInventory();
        if (bannedUuid.isBlank()) {
            return;
        }
        if (event.isRightClick()) {
            actions.execute(player, new GuiAction.BanPardon(GuiAction.BanPardonType.PREPARE, java.util.UUID.fromString(bannedUuid), ""), GuiClick.from(event));
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
            Inventory inventory = GuiMenuRenderer.render(MENU, session, messages, TITLE, item -> !"E".equals(item.symbol()));
            if (bans.isEmpty()) {
                setEmptyItem(inventory, messages);
            } else {
                for (int index = 0; index < bans.size() && index < 45; index++) {
                    inventory.setItem(index, banItem(bans.get(index), messages));
                }
            }
            player.openInventory(inventory);
        });
    }

    private static ItemStack banItem(BanView ban, MessageRenderer messages) {
        return GuiItems.action(GuiMenuRenderer.material(MENU, "_", "BARRIER"), message(messages, "ban-menu-title-prefix", "밴 ") + shortUuid(ban.bannedUuid()), "island.ban.pardon.prepare",
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
        return GuiMenuRenderer.message(messages, key, fallback);
    }

    private static void setEmptyItem(Inventory inventory, MessageRenderer messages) {
        MENU.itemAt(22)
            .ifPresent(item -> inventory.setItem(22, GuiMenuRenderer.item(MENU, item, messages, Map.of(), List.of())));
    }

    private static String shortUuid(String uuid) {
        return uuid.length() <= 8 ? uuid : uuid.substring(0, 8);
    }

    private static String fallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

}
