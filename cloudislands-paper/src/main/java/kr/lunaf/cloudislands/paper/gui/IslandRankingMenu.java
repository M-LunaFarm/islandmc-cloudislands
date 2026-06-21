package kr.lunaf.cloudislands.paper.gui;

import java.util.List;
import java.util.Map;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.paper.application.view.PaperGuiViews;
import kr.lunaf.cloudislands.paper.application.view.PaperGuiViews.RankingView;
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

public final class IslandRankingMenu implements Listener {
    private static final String TITLE = "섬 랭킹";
    private static final GuiMenuDefinition MENU = GuiMenuDefinition.bundled(
        "config-v2/ui/menus/ranking.yml",
        new GuiMenuDefinition("island.ranking", 6, "menu.ranking.title", Map.of(
            "open", "island.ranking.open",
            "list", "island.ranking.list",
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
        GuiSession session = GuiSessions.begin(player, MENU_ID);
        GuiStateMenus.openLoading(plugin, player, session, messages, message(messages, MENU.titleKey(), TITLE));
        PaperGuiViews.rankings(client, 10)
            .thenAccept(data -> openSync(plugin, player, session, data.levels(), data.worths(), data.reviews(), messages))
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
        if (slot < 0 || slot >= 54) {
            return;
        }
        String actionId = GuiItems.actionId(event.getCurrentItem());
        if (actionId.isBlank()) {
            return;
        }
        player.closeInventory();
        actions.execute(player, actionId, GuiItems.data(event.getCurrentItem()), GuiClick.from(event));
    }

    private static void openSync(Plugin plugin, Player player, GuiSession session, List<RankingView> levels, List<RankingView> worths, List<RankingView> reviews, MessageRenderer messages) {
        GuiSessions.runIfCurrent(plugin, player, session, () -> {
            Inventory inventory = GuiMenuRenderer.render(MENU, session, messages, TITLE, item -> true);
            int slot = 9;
            for (RankingView ranking : levels.stream().limit(9).toList()) {
                inventory.setItem(slot++, rankingItem(Material.EXPERIENCE_BOTTLE, ranking, messages));
            }
            slot = 18;
            for (RankingView ranking : worths.stream().limit(9).toList()) {
                inventory.setItem(slot++, rankingItem(Material.EMERALD, ranking, messages));
            }
            slot = 27;
            for (RankingView ranking : reviews.stream().limit(9).toList()) {
                inventory.setItem(slot++, rankingItem(Material.WRITABLE_BOOK, ranking, messages));
            }
            player.openInventory(inventory);
        });
    }

    private static ItemStack rankingItem(Material material, RankingView ranking, MessageRenderer messages) {
        if ("reviews".equals(ranking.label())) {
            return GuiItems.action(material, rankingLabel(ranking.label(), messages) + " #" + ranking.rank(), "island.visit.target",
                Map.of("target", ranking.islandId()),
                message(messages, "ranking-menu-review-rating", "평점: ") + ranking.worth() + "/5",
                message(messages, "ranking-menu-review-count", "후기: ") + ranking.level(),
                message(messages, "ranking-menu-click-to-visit", "클릭하면 방문을 시도합니다."));
        }
        return GuiItems.action(material, rankingLabel(ranking.label(), messages) + " #" + ranking.rank(), "island.visit.target",
            Map.of("target", ranking.islandId()),
            message(messages, "ranking-menu-level", "레벨: ") + ranking.level(),
            message(messages, "ranking-menu-worth", "가치: ") + ranking.worth(),
            message(messages, "ranking-menu-click-to-visit", "클릭하면 방문을 시도합니다."));
    }

    private static String rankingLabel(String label, MessageRenderer messages) {
        if ("reviews".equals(label)) {
            return message(messages, "ranking-menu-review-label", "후기");
        }
        return "worth".equals(label) ? message(messages, "ranking-menu-worth-label", "가치") : message(messages, "ranking-menu-level-label", "레벨");
    }

    private static String message(MessageRenderer messages, String key, String fallback) {
        return GuiMenuRenderer.message(messages, key, fallback);
    }

}
