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
    private static final String MENU_ID = "island.ranking";
    private final MessageRenderer messages;

    public IslandRankingMenu() {
        this(null);
    }

    public IslandRankingMenu(MessageRenderer messages) {
        this.messages = messages;
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player) {
        open(plugin, client, player, null);
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player, MessageRenderer messages) {
        GuiStateMenus.openLoading(plugin, player, messages, TITLE);
        PaperGuiViews.rankings(client, 10)
            .thenAccept(data -> openSync(plugin, player, data.levels(), data.worths(), messages))
            .exceptionally(error -> {
                GuiStateMenus.openError(plugin, player, messages, TITLE, message(messages, "ranking-menu-load-failed", "섬 랭킹을 불러오지 못했습니다."), "island.ranking.open", "island.main.open");
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
        player.closeInventory();
        if (slot == 45) {
            GuiActionRegistry.execute(player, "island.visit.open", GuiClick.from(event));
            return;
        }
        if (slot == 53) {
            GuiActionRegistry.execute(player, "island.visit.random", GuiClick.from(event));
            return;
        }
        if (slot == 49) {
            GuiActionRegistry.execute(player, "island.ranking.open", GuiClick.from(event));
            return;
        }
        String islandId = GuiItems.data(event.getCurrentItem()).getOrDefault("target", "");
        if (islandId.isBlank()) {
            return;
        }
        GuiActionRegistry.execute(player, "island.visit.target", java.util.Map.of("target", String.valueOf(islandId)), GuiClick.from(event));
    }

    private static void openSync(Plugin plugin, Player player, List<RankingView> levels, List<RankingView> worths, MessageRenderer messages) {
        kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers.run(plugin, () -> {
            Inventory inventory = GuiInventories.create(MENU_ID, 54, message(messages, "ranking-menu-title", TITLE));
            inventory.setItem(4, item(Material.GOLD_BLOCK, message(messages, "ranking-menu-title", "섬 랭킹"), message(messages, "ranking-menu-level-side", "좌측: 레벨 TOP"), message(messages, "ranking-menu-worth-side", "우측: 가치 TOP")));
            int slot = 9;
            for (RankingView ranking : levels.stream().limit(18).toList()) {
                inventory.setItem(slot++, rankingItem(Material.EXPERIENCE_BOTTLE, ranking, messages));
            }
            slot = 27;
            for (RankingView ranking : worths.stream().limit(18).toList()) {
                inventory.setItem(slot++, rankingItem(Material.EMERALD, ranking, messages));
            }
            inventory.setItem(45, item(Material.COMPASS, message(messages, "ranking-menu-public-islands-name", "공개 섬"), message(messages, "ranking-menu-public-islands-command", "/섬 방문")));
            inventory.setItem(49, item(Material.CLOCK, message(messages, "ranking-menu-refresh-name", "새로고침"), message(messages, "ranking-menu-refresh-command", "/섬 랭킹")));
            inventory.setItem(53, item(Material.ENDER_PEARL, message(messages, "ranking-menu-random-visit-name", "랜덤 방문"), message(messages, "ranking-menu-random-visit-command", "/섬 랜덤방문")));
            player.openInventory(inventory);
        });
    }

    private static ItemStack rankingItem(Material material, RankingView ranking, MessageRenderer messages) {
        return GuiItems.action(material, rankingLabel(ranking.label(), messages) + " #" + ranking.rank(), "island.visit.target",
            Map.of("target", ranking.islandId()),
            message(messages, "ranking-menu-level", "레벨: ") + ranking.level(),
            message(messages, "ranking-menu-worth", "가치: ") + ranking.worth(),
            message(messages, "ranking-menu-click-to-visit", "클릭하면 방문을 시도합니다."));
    }

    private static String rankingLabel(String label, MessageRenderer messages) {
        return "worth".equals(label) ? message(messages, "ranking-menu-worth-label", "가치") : message(messages, "ranking-menu-level-label", "레벨");
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

}
