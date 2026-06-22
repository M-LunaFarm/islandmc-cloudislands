package kr.lunaf.cloudislands.paper.gui;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.paper.application.view.PaperGuiViews;
import kr.lunaf.cloudislands.paper.application.view.PaperGuiViews.HomeView;
import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

public final class IslandHomeMenu implements Listener {
    private static final String TITLE_KEY = "home-menu-title";
    private static final String TITLE = "섬 홈 관리";
    private static final GuiMenuDefinition MENU = GuiMenuDefinition.bundled(
        "config-v2/ui/menus/homes.yml",
        new GuiMenuDefinition("island.homes", 6, TITLE_KEY, Map.of(
            "open", "island.homes.open",
            "home", "island.home",
            "set", "island.home.set",
            "settings", "island.settings.open",
            "back", "island.main.open"
        ))
    );
    private static final String MENU_ID = MENU.id();
    private final MessageRenderer messages;
    private final GuiActionRegistry actions;

    public IslandHomeMenu() {
        this(null);
    }

    public IslandHomeMenu(MessageRenderer messages) {
        this(messages, new GuiActionRegistry(GuiActionExecutor.noop()));
    }

    public IslandHomeMenu(MessageRenderer messages, GuiActionRegistry actions) {
        this.messages = messages;
        this.actions = actions == null ? new GuiActionRegistry(GuiActionExecutor.noop()) : actions;
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player, UUID islandId) {
        open(plugin, client, player, islandId, null);
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player, UUID islandId, MessageRenderer messages) {
        GuiSession session = GuiSessions.begin(player, MENU_ID);
        GuiStateMenus.openLoading(plugin, player, session, messages, message(messages, MENU.titleKey(), TITLE));
        PaperGuiViews.islandHomes(client, islandId)
            .thenAccept(homes -> openSync(plugin, player, session, homes, messages))
            .exceptionally(error -> {
                GuiStateMenus.openError(plugin, player, session, messages, message(messages, MENU.titleKey(), TITLE), message(messages, "home-menu-load-failed", "섬 홈을 불러오지 못했습니다."), "island.homes.open", "island.settings.open");
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
        if (data.getOrDefault("mode", "").equals("set-default")) {
            player.closeInventory();
            if (event.isRightClick()) {
                player.sendMessage(message(messages, "home-menu-set-usage", "사용법: /섬 셋홈 <이름>"));
                return;
            }
            actions.execute(player, new GuiAction.HomeTeleport("default"), GuiClick.from(event));
            return;
        }
        String homeName = data.getOrDefault("homeName", "");
        if (!homeName.isBlank()) {
            player.closeInventory();
            if (event.isRightClick()) {
                actions.execute(player, new GuiAction.HomeSet(homeName), GuiClick.from(event));
                return;
            }
            actions.execute(player, new GuiAction.HomeTeleport(homeName), GuiClick.from(event));
            return;
        }
        String actionId = GuiItems.actionId(event.getCurrentItem());
        if (!actionId.isBlank()) {
            player.closeInventory();
            actions.execute(player, GuiActions.from(actionId, data).orElse(null), GuiClick.from(event));
        }
    }

    private static void openSync(Plugin plugin, Player player, GuiSession session, List<HomeView> homes, MessageRenderer messages) {
        GuiSessions.runIfCurrent(plugin, player, session, () -> {
            Inventory inventory = GuiMenuRenderer.render(MENU, session, messages, TITLE, item -> !"E".equals(item.symbol()) && !"_".equals(item.symbol()));
            List<Integer> homeSlots = GuiMenuRenderer.slots(MENU, "_");
            List<HomeView> visibleHomes = homes.stream().limit(homeSlots.size()).toList();
            for (int index = 0; index < visibleHomes.size(); index++) {
                inventory.setItem(homeSlots.get(index), homeItem(visibleHomes.get(index), messages));
            }
            if (homes.isEmpty()) {
                setEmptyItem(inventory, messages);
            }
            player.openInventory(inventory);
        });
    }

    private static String message(MessageRenderer messages, String key, String fallback) {
        return GuiMenuRenderer.message(messages, key, fallback);
    }

    private static ItemStack homeItem(HomeView home, MessageRenderer messages) {
        return GuiItems.action(
            GuiMenuRenderer.material(MENU, "_", "GREEN_BED"),
            home.name(),
            "island.home",
            Map.of("homeName", home.name()),
            message(messages, "home-menu-location", "위치: ") + (long) home.x() + ", " + (long) home.y() + ", " + (long) home.z(),
            home.createdAt().isBlank() ? message(messages, "home-menu-no-created-info", "생성 정보 없음") : message(messages, "home-menu-created-at", "생성 시각: ") + home.createdAt(),
            message(messages, "home-menu-left-click", "좌클릭: 이 홈으로 이동"),
            message(messages, "home-menu-right-click", "우클릭: 현재 위치로 갱신")
        );
    }

    private static void setEmptyItem(Inventory inventory, MessageRenderer messages) {
        MENU.itemAt(22)
            .ifPresent(item -> inventory.setItem(22, GuiMenuRenderer.item(MENU, item, messages, Map.of(), List.of())));
    }

}
