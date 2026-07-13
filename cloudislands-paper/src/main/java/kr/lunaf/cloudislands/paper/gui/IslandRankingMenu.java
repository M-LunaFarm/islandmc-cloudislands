package kr.lunaf.cloudislands.paper.gui;

import java.util.List;
import java.util.Map;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.paper.application.view.PaperGuiViews;
import kr.lunaf.cloudislands.paper.application.view.PaperGuiViews.RankingView;
import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

public final class IslandRankingMenu implements Listener {
    private static final String TITLE = "섬 랭킹";
    private static final GuiMenuDefinition MENU = GuiMenuDefinition.bundled(
        "config-v2/ui/menus/ranking.yml",
        new GuiMenuDefinition("island.ranking", 6, "menu.ranking.title", Map.of(
            "open", "island.ranking.open",
            "list", "island.ranking.list",
            "page", "island.ranking.page",
            "visit", "island.visit.target",
            "public", "island.visit.open",
            "random", "island.visit.random",
            "back", "island.main.open"
        ))
    );
    private static final String MENU_ID = MENU.id();
    private final MessageRenderer messages;
    private final GuiActionRegistry actions;

    public IslandRankingMenu() {
        this(null);
    }

    public IslandRankingMenu(MessageRenderer messages) {
        this(messages, new GuiActionRegistry(GuiActionExecutor.noop()));
    }

    public IslandRankingMenu(MessageRenderer messages, GuiActionRegistry actions) {
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
        PaperGuiViews.rankings(client, 10)
            .thenAccept(data -> openSync(plugin, player, session, data.levels(), data.worths(), data.banks(), data.reviews(), page, messages))
            .exceptionally(error -> {
                GuiStateMenus.openError(plugin, player, session, messages, message(messages, MENU.titleKey(), TITLE), message(messages, "ranking-menu-load-failed", "섬 랭킹을 불러오지 못했습니다."), "island.ranking.open", "island.main.open");
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
        String actionId = GuiItems.actionId(event.getCurrentItem());
        if (actionId.isBlank()) {
            return;
        }
        player.closeInventory();
        actions.execute(player, GuiActions.from(actionId, GuiItems.data(event.getCurrentItem())).orElse(null), GuiClick.from(event));
    }

    private static void openSync(Plugin plugin, Player player, GuiSession session, List<RankingView> levels, List<RankingView> worths, List<RankingView> banks, List<RankingView> reviews, int requestedPage, MessageRenderer messages) {
        GuiSessions.runIfCurrent(plugin, player, session, () -> {
            int pageSize = Math.max(1, GuiMenuRenderer.slots(MENU, "L").size());
            int rankingCount = Math.max(Math.max(levels.size(), worths.size()), Math.max(banks.size(), reviews.size()));
            int maxPage = Math.max(0, (rankingCount - 1) / pageSize);
            int page = Math.max(0, Math.min(requestedPage, maxPage));
            Inventory inventory = GuiMenuRenderer.render(MENU, session, messages,
                TITLE + " (" + (page + 1) + "/" + (maxPage + 1) + ")",
                item -> !List.of("L", "W", "K", "C", "B", "N").contains(item.symbol()));
            setRankingItems(inventory, "L", levels, page, pageSize, messages);
            setRankingItems(inventory, "W", worths, page, pageSize, messages);
            setRankingItems(inventory, "K", banks, page, pageSize, messages);
            setRankingItems(inventory, "C", reviews, page, pageSize, messages);
            if (page > 0) {
                setPageItem(inventory, "B", page - 1, messages);
            }
            if (page < maxPage) {
                setPageItem(inventory, "N", page + 1, messages);
            }
            player.openInventory(inventory);
        });
    }

    private static void setRankingItems(Inventory inventory, String symbol, List<RankingView> rankings, int page, int pageSize, MessageRenderer messages) {
        List<Integer> slots = GuiMenuRenderer.slots(MENU, symbol);
        List<RankingView> visibleRankings = rankings.stream().skip((long) page * pageSize).limit(pageSize).toList();
        for (int index = 0; index < visibleRankings.size(); index++) {
            inventory.setItem(slots.get(index), rankingItem(symbol, visibleRankings.get(index), messages));
        }
    }

    private static void setPageItem(Inventory inventory, String symbol, int page, MessageRenderer messages) {
        GuiMenuRenderer.setSymbolItem(inventory, MENU, symbol, messages,
            Map.of("page", Integer.toString(page)), List.of());
    }

    private static ItemStack rankingItem(String symbol, RankingView ranking, MessageRenderer messages) {
        String displayName = rankingLabel(ranking.label(), messages) + " #" + ranking.rank();
        if ("reviews".equals(ranking.label())) {
            return GuiMenuRenderer.symbolItem(MENU, symbol, "_", messages, displayName, Map.of("target", ranking.islandId()), List.of(
                message(messages, "ranking-menu-review-rating", "평점: ") + ranking.worth() + "/5",
                message(messages, "ranking-menu-review-count", "후기: ") + ranking.level(),
                message(messages, "ranking-menu-click-to-visit", "클릭하면 방문을 시도합니다.")));
        }
        if ("bank".equals(ranking.label())) {
            return GuiMenuRenderer.symbolItem(MENU, symbol, "_", messages, displayName, Map.of("target", ranking.islandId()), List.of(
                message(messages, "ranking-menu-bank-balance", "은행 잔액: ") + ranking.worth(),
                message(messages, "ranking-menu-click-to-visit", "클릭하면 방문을 시도합니다.")));
        }
        return GuiMenuRenderer.symbolItem(MENU, symbol, "_", messages, displayName, Map.of("target", ranking.islandId()), List.of(
            message(messages, "ranking-menu-level", "레벨: ") + ranking.level(),
            message(messages, "ranking-menu-worth", "가치: ") + ranking.worth(),
            message(messages, "ranking-menu-click-to-visit", "클릭하면 방문을 시도합니다.")));
    }

    private static String rankingLabel(String label, MessageRenderer messages) {
        if ("reviews".equals(label)) {
            return message(messages, "ranking-menu-review-label", "후기");
        }
        if ("bank".equals(label)) {
            return message(messages, "ranking-menu-bank-label", "은행");
        }
        return "worth".equals(label) ? message(messages, "ranking-menu-worth-label", "가치") : message(messages, "ranking-menu-level-label", "레벨");
    }

    private static String message(MessageRenderer messages, String key, String fallback) {
        return GuiMenuRenderer.message(messages, key, fallback);
    }

}
