package kr.lunaf.cloudislands.paper.gui;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.paper.application.view.PaperGuiViews;
import kr.lunaf.cloudislands.paper.application.view.PaperGuiViews.HomeView;
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

public final class IslandHomeMenu implements Listener {
    private static final String TITLE_KEY = "home-menu-title";
    private static final String TITLE = "섬 홈 관리";
    private static final String MENU_ID = "island.homes";
    private final MessageRenderer messages;

    public IslandHomeMenu() {
        this(null);
    }

    public IslandHomeMenu(MessageRenderer messages) {
        this.messages = messages;
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player, UUID islandId) {
        open(plugin, client, player, islandId, null);
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player, UUID islandId, MessageRenderer messages) {
        GuiStateMenus.openLoading(plugin, player, messages, message(messages, TITLE_KEY, TITLE));
        PaperGuiViews.islandHomes(client, islandId)
            .thenAccept(homes -> openSync(plugin, player, homes, messages))
            .exceptionally(error -> {
                GuiStateMenus.openError(plugin, player, messages, message(messages, TITLE_KEY, TITLE), message(messages, "home-menu-load-failed", "섬 홈을 불러오지 못했습니다."), "island.homes.open", "island.settings.open");
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
        if (slot == 45) {
            if (event.isRightClick()) {
                player.sendMessage(message(messages, "home-menu-set-usage", "사용법: /섬 셋홈 <이름>"));
                return;
            }
            GuiActionRegistry.execute(player, "island.home.set", java.util.Map.of("homeName", "default"), GuiClick.from(event));
            return;
        }
        if (slot == 49) {
            GuiActionRegistry.execute(player, "island.settings.open", GuiClick.from(event));
            return;
        }
        if (slot == 53) {
            GuiActionRegistry.execute(player, "island.main.open", GuiClick.from(event));
            return;
        }
        String homeName = GuiItems.data(event.getCurrentItem()).getOrDefault("homeName", "");
        if (!homeName.isBlank()) {
            if (event.isRightClick()) {
                GuiActionRegistry.execute(player, "island.home.set", java.util.Map.of("homeName", homeName), GuiClick.from(event));
                return;
            }
            GuiActionRegistry.execute(player, "island.home", java.util.Map.of("homeName", homeName), GuiClick.from(event));
        }
    }

    private static void openSync(Plugin plugin, Player player, List<HomeView> homes, MessageRenderer messages) {
        kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers.run(plugin, () -> {
            Inventory inventory = GuiInventories.create(MENU_ID, 54, message(messages, TITLE_KEY, TITLE));
            inventory.setItem(45, item(Material.RED_BED, message(messages, "home-menu-set-current-name", "현재 위치를 홈으로 설정"), message(messages, "home-menu-set-default-click", "좌클릭: default 홈으로 설정"), message(messages, "home-menu-set-named-click", "우클릭: ") + message(messages, "home-menu-set-usage", "사용법: /섬 셋홈 <이름>")));
            int slot = 0;
            for (HomeView home : homes.stream().limit(45).toList()) {
                inventory.setItem(slot++, homeItem(home, messages));
            }
            if (homes.isEmpty()) {
                inventory.setItem(22, item(Material.BARRIER, message(messages, "home-menu-empty-title", "홈 없음"), message(messages, "home-menu-empty", "현재 등록된 섬 홈이 없습니다.")));
            }
            inventory.setItem(49, item(Material.COMPARATOR, message(messages, "home-menu-settings-name", "설정"), message(messages, "home-menu-settings-command", "/섬 설정")));
            inventory.setItem(53, item(Material.COMPASS, message(messages, "home-menu-main-menu-name", "메인 메뉴"), message(messages, "home-menu-main-menu-command", "/섬 메뉴")));
            player.openInventory(inventory);
        });
    }

    private static String message(MessageRenderer messages, String key, String fallback) {
        if (messages == null) {
            return fallback;
        }
        String rendered = messages.plain(key);
        return rendered.isBlank() ? fallback : rendered;
    }

    private static ItemStack homeItem(HomeView home, MessageRenderer messages) {
        return GuiItems.action(Material.GREEN_BED, home.name(), "island.home",
            Map.of("homeName", home.name()),
            message(messages, "home-menu-location", "위치: ") + (long) home.x() + ", " + (long) home.y() + ", " + (long) home.z(),
            home.createdAt().isBlank() ? message(messages, "home-menu-no-created-info", "생성 정보 없음") : message(messages, "home-menu-created-at", "생성 시각: ") + home.createdAt(),
            message(messages, "home-menu-left-click", "좌클릭: 이 홈으로 이동"),
            message(messages, "home-menu-right-click", "우클릭: 현재 위치로 갱신"));
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
