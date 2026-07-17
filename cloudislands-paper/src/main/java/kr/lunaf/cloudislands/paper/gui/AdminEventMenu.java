package kr.lunaf.cloudislands.paper.gui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import kr.lunaf.cloudislands.coreclient.AdminEventStreamView;
import kr.lunaf.cloudislands.coreclient.AdminEventView;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

public final class AdminEventMenu implements Listener {
    private static final int EVENT_LIMIT = 450;
    private static final int MAX_VISIBLE_FIELDS = 6;
    private static final String TITLE_KEY = "admin-event-menu-title";
    private static final String TITLE = "Core 이벤트 스트림";
    private static final GuiMenuDefinition MENU = GuiMenuDefinition.bundled(
        "config-v2/ui/menus/admin-events.yml",
        new GuiMenuDefinition("admin.events", 6, TITLE_KEY, Map.ofEntries(
            Map.entry("entry", "admin.events.page"),
            Map.entry("page", "admin.events.page"),
            Map.entry("refresh", "admin.events.page"),
            Map.entry("close", "gui.close")
        ))
    );
    private static final String MENU_ID = MENU.id();
    private final GuiActionRegistry actions;

    public AdminEventMenu() {
        this(null);
    }

    public AdminEventMenu(MessageRenderer messages) {
        this(messages, new GuiActionRegistry(GuiActionExecutor.noop()));
    }

    public AdminEventMenu(MessageRenderer messages, GuiActionRegistry actions) {
        this.actions = actions == null ? new GuiActionRegistry(GuiActionExecutor.noop()) : actions;
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player, MessageRenderer messages) {
        open(plugin, client, player, messages, 0);
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player, MessageRenderer messages, int requestedPage) {
        GuiSession session = GuiSessions.begin(player, MENU_ID);
        GuiStateMenus.openLoading(plugin, player, session, messages,
            message(messages, "admin-event-menu-loading", "이벤트 스트림을 불러오는 중입니다."));
        client.adminEvents().list(EVENT_LIMIT)
            .thenAccept(stream -> openSync(plugin, player, session, stream, messages, requestedPage))
            .exceptionally(error -> {
                GuiStateMenus.openError(plugin, player, session, messages,
                    message(messages, TITLE_KEY, TITLE),
                    message(messages, "admin-event-menu-load-failed", "이벤트 스트림을 불러오지 못했습니다."),
                    "admin.events.page",
                    Map.of("page", Integer.toString(Math.max(0, requestedPage))),
                    "gui.close",
                    Map.of());
                return null;
            });
    }

    private static void openSync(Plugin plugin, Player player, GuiSession session, AdminEventStreamView stream,
                                 MessageRenderer messages, int requestedPage) {
        GuiSessions.runIfCurrent(plugin, player, session, () -> {
            List<AdminEventView> entries = newestFirst(stream == null ? List.of() : stream.events());
            List<Integer> slots = GuiMenuRenderer.slots(MENU, "_");
            int pageSize = Math.max(1, slots.size());
            int maxPage = Math.max(0, (entries.size() - 1) / pageSize);
            int page = Math.max(0, Math.min(requestedPage, maxPage));
            String title = message(messages, TITLE_KEY, TITLE) + " " + (page + 1) + "/" + (maxPage + 1)
                + " (" + entries.size() + ")";
            Inventory inventory = GuiMenuRenderer.render(MENU, session, messages, title,
                item -> !List.of("_", "E", "P", "N").contains(item.symbol()));
            int offset = page * pageSize;
            for (int index = 0; index < pageSize && offset + index < entries.size(); index++) {
                inventory.setItem(slots.get(index), eventItem(entries.get(offset + index), messages, page));
            }
            if (entries.isEmpty()) {
                GuiMenuRenderer.setSymbolItem(inventory, MENU, "E", messages, Map.of(), List.of());
            }
            if (page > 0) {
                setPageItem(inventory, "P", page - 1, messages, List.of());
            }
            if (page < maxPage) {
                setPageItem(inventory, "N", page + 1, messages, List.of());
            }
            long oldestSeq = stream == null ? 0L : stream.oldestSeq();
            long latestSeq = stream == null ? 0L : stream.latestSeq();
            setPageItem(inventory, "R", page, messages, List.of(
                message(messages, "admin-event-menu-range-prefix", "보존 시퀀스: ") + oldestSeq + " - " + latestSeq,
                message(messages, "admin-event-menu-count-prefix", "표시 이벤트: ") + entries.size(),
                message(messages, "admin-event-menu-refresh-action", "클릭: 새로고침")
            ));
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
        player.closeInventory();
        if (actionId.equals("gui.close")) {
            return;
        }
        actions.execute(player, GuiActions.from(actionId, GuiItems.data(event.getCurrentItem())).orElse(null), GuiClick.from(event));
    }

    static List<AdminEventView> newestFirst(List<AdminEventView> events) {
        if (events == null || events.isEmpty()) {
            return List.of();
        }
        return events.stream()
            .sorted(Comparator.comparingLong(AdminEventView::seq).reversed())
            .toList();
    }

    static ItemStack eventItem(AdminEventView event, MessageRenderer messages, int page) {
        return GuiItems.action(
            GuiMenuRenderer.material(MENU, "_", "REDSTONE_TORCH"),
            "#" + event.seq() + " " + safeLine(event.type(), 34),
            "admin.events.page",
            Map.of("page", Integer.toString(Math.max(0, page))),
            eventLore(event, messages).toArray(String[]::new)
        );
    }

    static List<String> eventLore(AdminEventView event, MessageRenderer messages) {
        List<String> lore = new ArrayList<>();
        lore.add(message(messages, "admin-event-menu-sequence-prefix", "시퀀스: ") + event.seq());
        lore.add(message(messages, "admin-event-menu-type-prefix", "유형: ") + safeLine(event.type(), 48));
        lore.add(message(messages, "admin-event-menu-time-prefix", "발생 시각: ") + safeLine(event.occurredAt(), 48));
        List<Map.Entry<String, String>> fields = event.fields().entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .toList();
        for (Map.Entry<String, String> field : fields.stream().limit(MAX_VISIBLE_FIELDS).toList()) {
            lore.add(safeLine(field.getKey(), 24) + " = " + safeLine(field.getValue(), 72));
        }
        if (fields.size() > MAX_VISIBLE_FIELDS) {
            lore.add(message(messages, "admin-event-menu-more-fields-prefix", "추가 필드: ")
                + (fields.size() - MAX_VISIBLE_FIELDS));
        }
        lore.add(message(messages, "admin-event-menu-refresh-action", "클릭: 새로고침"));
        return List.copyOf(lore);
    }

    private static void setPageItem(Inventory inventory, String symbol, int page, MessageRenderer messages, List<String> lore) {
        GuiMenuRenderer.setSymbolItem(inventory, MENU, symbol, messages,
            Map.of("page", Integer.toString(Math.max(0, page))), lore);
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
