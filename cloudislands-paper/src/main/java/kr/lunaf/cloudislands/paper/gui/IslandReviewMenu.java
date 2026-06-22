package kr.lunaf.cloudislands.paper.gui;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.paper.application.IslandNavigationUseCase;
import kr.lunaf.cloudislands.paper.application.IslandNavigationUseCase.ReviewListView;
import kr.lunaf.cloudislands.paper.application.IslandNavigationUseCase.ReviewView;
import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;

public final class IslandReviewMenu implements Listener {
    private static final String TITLE = "섬 후기";
    private static final GuiMenuDefinition MENU = GuiMenuDefinition.bundled(
        "config-v2/ui/menus/reviews.yml",
        new GuiMenuDefinition("island.reviews", 6, "menu.reviews.title", Map.of(
            "open", "island.reviews.open",
            "list", "island.reviews.open",
            "ranking", "island.ranking.open",
            "back", "island.main.open"
        ))
    );
    private static final String MENU_ID = MENU.id();
    private final MessageRenderer messages;
    private final GuiActionRegistry actions;

    public IslandReviewMenu() {
        this(null);
    }

    public IslandReviewMenu(MessageRenderer messages) {
        this(messages, new GuiActionRegistry(GuiActionExecutor.noop()));
    }

    public IslandReviewMenu(MessageRenderer messages, GuiActionRegistry actions) {
        this.messages = messages;
        this.actions = actions == null ? new GuiActionRegistry(GuiActionExecutor.noop()) : actions;
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player, UUID islandId, MessageRenderer messages) {
        GuiSession session = GuiSessions.begin(player, MENU_ID);
        GuiStateMenus.openLoading(plugin, player, session, messages, message(messages, MENU.titleKey(), TITLE));
        new IslandNavigationUseCase(client).reviewViews(islandId, 36)
            .thenAccept(reviews -> openSync(plugin, player, session, reviews, messages))
            .exceptionally(error -> {
                GuiStateMenus.openError(plugin, player, session, messages, message(messages, MENU.titleKey(), TITLE), message(messages, "reviews-menu-load-failed", "섬 후기를 불러오지 못했습니다."), "island.reviews.open", "island.settings.open");
                return null;
            });
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!GuiItems.menuClick(event, MENU_ID)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getCurrentItem() == null) {
            return;
        }
        String actionId = GuiItems.actionId(event.getCurrentItem());
        if (actionId.isBlank()) {
            return;
        }
        player.closeInventory();
        actions.execute(player, GuiActions.from(actionId, GuiItems.data(event.getCurrentItem())).orElse(null), GuiClick.from(event));
    }

    private static void openSync(Plugin plugin, Player player, GuiSession session, ReviewListView reviews, MessageRenderer messages) {
        GuiSessions.runIfCurrent(plugin, player, session, () -> {
            Inventory inventory = GuiMenuRenderer.render(MENU, session, messages, TITLE, item -> !"E".equals(item.symbol()) && !"_".equals(item.symbol()));
            List<Integer> slots = GuiMenuRenderer.slots(MENU, "_");
            List<ReviewView> entries = reviews == null ? List.of() : reviews.reviews();
            for (int index = 0; index < entries.size() && index < slots.size(); index++) {
                ReviewView review = entries.get(index);
                int slot = slots.get(index);
                MENU.item("_").ifPresent(item -> inventory.setItem(slot, GuiMenuRenderer.item(MENU, item, messages, Map.of(), reviewLore(review, messages))));
            }
            if (entries.isEmpty() && !slots.isEmpty()) {
                GuiMenuRenderer.setSymbolItem(inventory, MENU, "E", messages, Map.of(), List.of());
            }
            player.openInventory(inventory);
        });
    }

    private static List<String> reviewLore(ReviewView review, MessageRenderer messages) {
        return List.of(
            message(messages, "reviews-menu-reviewer", "작성자: ") + shortId(review.reviewerUuid()),
            message(messages, "reviews-menu-rating", "평점: ") + review.rating() + "/5",
            message(messages, "reviews-menu-comment", "내용: ") + fallback(review.comment(), message(messages, "reviews-menu-no-comment", "내용 없음"))
        );
    }

    private static String message(MessageRenderer messages, String key, String fallback) {
        return GuiMenuRenderer.message(messages, key, fallback);
    }

    private static String shortId(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.length() <= 8 ? value : value.substring(0, 8);
    }

    private static String fallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
