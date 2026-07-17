package kr.lunaf.cloudislands.paper.gui;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.coreclient.ReviewModerationView;
import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

public final class AdminReviewModerationMenu implements Listener {
    private static final String TITLE = "후기 신고 관리";
    private static final int MAX_ENTRIES = 36;
    private static final GuiMenuDefinition MENU = GuiMenuDefinition.bundled(
        "config-v2/ui/menus/admin-reviews.yml",
        new GuiMenuDefinition("admin.reviews", 6, "admin-review-menu-title", Map.of(
            "entry", "admin.reviews.moderate",
            "refresh", "admin.reviews.open",
            "close", "gui.close"
        ))
    );
    private static final String MENU_ID = MENU.id();
    private final MessageRenderer messages;
    private final GuiActionRegistry actions;

    public AdminReviewModerationMenu(MessageRenderer messages, GuiActionRegistry actions) {
        this.messages = messages;
        this.actions = actions == null ? new GuiActionRegistry(GuiActionExecutor.noop()) : actions;
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player, MessageRenderer messages, int requestedLimit) {
        int limit = Math.max(1, Math.min(requestedLimit, MAX_ENTRIES));
        GuiSession session = GuiSessions.begin(player, MENU_ID);
        GuiStateMenus.openLoading(plugin, player, session, messages, message(messages, "admin-review-menu-loading", "후기 신고 목록을 불러오는 중입니다."));
        client.navigationCommands().reviewModerationQueue(limit)
            .thenAccept(reviews -> openSync(plugin, player, session, reviews, messages, limit))
            .exceptionally(error -> {
                GuiStateMenus.openError(plugin, player, session,
                    messages,
                    message(messages, "admin-review-menu-title", TITLE),
                    message(messages, "admin-review-menu-load-failed", "후기 신고 목록을 불러오지 못했습니다."),
                    "admin.reviews.open",
                    Map.of("limit", Integer.toString(limit)),
                    "gui.close",
                    Map.of()
                );
                return null;
            });
    }

    private static void openSync(Plugin plugin, Player player, GuiSession session, List<ReviewModerationView> reviews, MessageRenderer messages, int limit) {
        GuiSessions.runIfCurrent(plugin, player, session, () -> {
            List<ReviewModerationView> entries = reviews == null ? List.of() : reviews;
            String title = message(messages, "admin-review-menu-title", TITLE) + " (" + entries.size() + ")";
            Inventory inventory = GuiMenuRenderer.render(MENU, session, messages, title, item -> !List.of("_", "E").contains(item.symbol()));
            List<Integer> slots = GuiMenuRenderer.slots(MENU, "_");
            for (int index = 0; index < entries.size() && index < slots.size(); index++) {
                inventory.setItem(slots.get(index), reviewItem(entries.get(index), messages));
            }
            if (entries.isEmpty()) {
                GuiMenuRenderer.setSymbolItem(inventory, MENU, "E", messages, Map.of(), List.of());
            }
            setRefreshLimit(inventory, messages, limit);
            player.openInventory(inventory);
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
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getView().getTopInventory().getSize()) {
            return;
        }
        String actionId = GuiItems.actionId(event.getCurrentItem());
        if (actionId.equals("gui.close")) {
            player.closeInventory();
            return;
        }
        Map<String, String> data = GuiItems.data(event.getCurrentItem());
        if (actionId.equals("admin.reviews.moderate")) {
            GuiClick click = GuiClick.from(event);
            if (!click.left() && !click.right()) {
                return;
            }
            LinkedHashMap<String, String> actionData = new LinkedHashMap<>(data);
            actionData.put("moderationState", click.right() ? "VISIBLE" : "HIDDEN");
            data = Map.copyOf(actionData);
        }
        player.closeInventory();
        actions.execute(player, GuiActions.from(actionId, data).orElse(null), GuiClick.from(event));
    }

    static ItemStack reviewItem(ReviewModerationView review, MessageRenderer messages) {
        List<String> lore = reviewLore(review, messages);
        return GuiItems.action(
            GuiMenuRenderer.material(MENU, "_", "WRITABLE_BOOK"),
            reviewTitle(review),
            "admin.reviews.moderate",
            reviewActionData(review),
            lore.toArray(String[]::new)
        );
    }

    static Map<String, String> reviewActionData(ReviewModerationView review) {
        return Map.of("islandId", review.islandId(), "reviewerUuid", review.reviewerUuid());
    }

    static String reviewTitle(ReviewModerationView review) {
        return display(review.islandName(), review.islandId()) + " / " + display(review.reviewerName(), review.reviewerUuid());
    }

    static List<String> reviewLore(ReviewModerationView review, MessageRenderer messages) {
        return List.of(
            message(messages, "admin-review-menu-state-prefix", "상태: ") + safeLine(review.moderationState(), 24),
            message(messages, "admin-review-menu-reports-prefix", "신고 수: ") + review.reportCount(),
            message(messages, "admin-review-menu-reason-prefix", "사유: ") + safeLine(review.reportReason(), 80),
            message(messages, "admin-review-menu-island-id-prefix", "섬 UUID: ") + review.islandId(),
            message(messages, "admin-review-menu-reviewer-id-prefix", "작성자 UUID: ") + review.reviewerUuid(),
            message(messages, "admin-review-menu-left-action", "좌클릭: 후기 숨김"),
            message(messages, "admin-review-menu-right-action", "우클릭: 후기 복구")
        );
    }

    private static void setRefreshLimit(Inventory inventory, MessageRenderer messages, int limit) {
        for (int slot : GuiMenuRenderer.slots(MENU, "R")) {
            MENU.item("R").ifPresent(item -> inventory.setItem(slot, GuiMenuRenderer.item(
                MENU,
                item,
                messages,
                Map.of("limit", Integer.toString(limit))
            )));
        }
    }

    private static String display(String name, String id) {
        return name == null || name.isBlank() ? shortId(id) : safeLine(name, 24);
    }

    private static String shortId(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.length() <= 8 ? value : value.substring(0, 8);
    }

    private static String safeLine(String value, int maxLength) {
        String normalized = value == null ? "" : value.replace('\n', ' ').replace('\r', ' ').trim();
        if (normalized.isBlank()) {
            return "-";
        }
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength - 3) + "...";
    }

    private static String message(MessageRenderer messages, String key, String fallback) {
        return GuiMenuRenderer.message(messages, key, fallback);
    }
}
