package kr.lunaf.cloudislands.paper.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import kr.lunaf.cloudislands.coreclient.AdminAuditEntryView;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

public final class AdminAuditMenu implements Listener {
    private static final int AUDIT_LIMIT = 500;
    private static final int MAX_VISIBLE_PAYLOAD_FIELDS = 5;
    private static final String TITLE_KEY = "admin-audit-menu-title";
    private static final String TITLE = "관리자 감사 로그";
    private static final GuiMenuDefinition MENU = GuiMenuDefinition.bundled(
        "config-v2/ui/menus/admin-audit.yml",
        new GuiMenuDefinition("admin.audit", 6, TITLE_KEY, Map.ofEntries(
            Map.entry("entry", "admin.audit.page"),
            Map.entry("page", "admin.audit.page"),
            Map.entry("refresh", "admin.audit.page"),
            Map.entry("events", "admin.events.open"),
            Map.entry("close", "gui.close")
        ))
    );
    private static final String MENU_ID = MENU.id();
    private final GuiActionRegistry actions;

    public AdminAuditMenu() {
        this(null);
    }

    public AdminAuditMenu(MessageRenderer messages) {
        this(messages, new GuiActionRegistry(GuiActionExecutor.noop()));
    }

    public AdminAuditMenu(MessageRenderer messages, GuiActionRegistry actions) {
        this.actions = actions == null ? new GuiActionRegistry(GuiActionExecutor.noop()) : actions;
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player, MessageRenderer messages) {
        open(plugin, client, player, messages, 0);
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player, MessageRenderer messages, int requestedPage) {
        GuiSession session = GuiSessions.begin(player, MENU_ID);
        GuiStateMenus.openLoading(plugin, player, session, messages,
            message(messages, "admin-audit-menu-loading", "감사 로그를 불러오는 중입니다."));
        client.adminAudit().list(AUDIT_LIMIT)
            .thenAccept(entries -> openSync(plugin, player, session, entries, messages, requestedPage))
            .exceptionally(error -> {
                GuiStateMenus.openError(plugin, player, session, messages,
                    message(messages, TITLE_KEY, TITLE),
                    message(messages, "admin-audit-menu-load-failed", "감사 로그를 불러오지 못했습니다."),
                    "admin.audit.page",
                    Map.of("page", Integer.toString(Math.max(0, requestedPage))),
                    "gui.close",
                    Map.of());
                return null;
            });
    }

    private static void openSync(Plugin plugin, Player player, GuiSession session, List<AdminAuditEntryView> audit,
                                 MessageRenderer messages, int requestedPage) {
        GuiSessions.runIfCurrent(plugin, player, session, () -> {
            List<AdminAuditEntryView> entries = audit == null ? List.of() : List.copyOf(audit);
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
                inventory.setItem(slots.get(index), auditItem(entries.get(offset + index), messages, page));
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
            setPageItem(inventory, "R", page, messages, List.of(
                message(messages, "admin-audit-menu-count-prefix", "표시 로그: ") + entries.size(),
                message(messages, "admin-audit-menu-refresh-action", "클릭: 새로고침")
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

    static ItemStack auditItem(AdminAuditEntryView entry, MessageRenderer messages, int page) {
        return GuiItems.action(
            GuiMenuRenderer.material(MENU, "_", "WRITABLE_BOOK"),
            safeLine(entry.action(), 34) + " #" + shortId(entry.id()),
            "admin.audit.page",
            Map.of("page", Integer.toString(Math.max(0, page))),
            auditLore(entry, messages).toArray(String[]::new)
        );
    }

    static List<String> auditLore(AdminAuditEntryView entry, MessageRenderer messages) {
        List<String> lore = new ArrayList<>();
        lore.add(message(messages, "admin-audit-menu-action-prefix", "작업: ") + safeLine(entry.action(), 48));
        lore.add(message(messages, "admin-audit-menu-actor-prefix", "실행자: ")
            + safeLine(entry.actorType(), 24) + ":" + shortId(entry.actorUuid()));
        lore.add(message(messages, "admin-audit-menu-target-prefix", "대상: ")
            + safeLine(entry.targetType(), 24) + ":" + safeLine(entry.targetId(), 48));
        lore.add(message(messages, "admin-audit-menu-time-prefix", "기록 시각: ") + safeLine(entry.createdAt(), 48));
        List<Map.Entry<String, String>> payload = entry.payload().entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .toList();
        for (Map.Entry<String, String> field : payload.stream().limit(MAX_VISIBLE_PAYLOAD_FIELDS).toList()) {
            lore.add(safeLine(field.getKey(), 24) + " = " + safeLine(field.getValue(), 72));
        }
        if (payload.size() > MAX_VISIBLE_PAYLOAD_FIELDS) {
            lore.add(message(messages, "admin-audit-menu-more-fields-prefix", "추가 payload: ")
                + (payload.size() - MAX_VISIBLE_PAYLOAD_FIELDS));
        }
        lore.add(message(messages, "admin-audit-menu-refresh-action", "클릭: 새로고침"));
        return List.copyOf(lore);
    }

    private static void setPageItem(Inventory inventory, String symbol, int page, MessageRenderer messages, List<String> lore) {
        GuiMenuRenderer.setSymbolItem(inventory, MENU, symbol, messages,
            Map.of("page", Integer.toString(Math.max(0, page))), lore);
    }

    private static String shortId(String value) {
        if (value == null || value.isBlank()) {
            return "-";
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
