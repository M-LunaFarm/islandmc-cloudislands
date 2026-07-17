package kr.lunaf.cloudislands.paper.gui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import kr.lunaf.cloudislands.coreclient.AdminMetricsSummaryView;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

public final class AdminMetricsMenu implements Listener {
    private static final List<String> FOCUS_METRICS = List.of(
        "cloudislands_node_active_islands",
        "cloudislands_jobs_pending",
        "cloudislands_node_heartbeat_age_seconds",
        "cloudislands_redis_latency_seconds",
        "cloudislands_database_query_seconds",
        "cloudislands_storage_upload_seconds",
        "cloudislands_storage_download_seconds",
        "cloudislands_island_activation_seconds",
        "cloudislands_permission_cache_hit_ratio",
        "cloudislands_core_security_rejects_total",
        "cloudislands_route_ticket_failed_total",
        "cloudislands_jobs_retry_total"
    );
    private static final String TITLE_KEY = "admin-metrics-menu-title";
    private static final String TITLE = "Core 메트릭";
    private static final GuiMenuDefinition MENU = GuiMenuDefinition.bundled(
        "config-v2/ui/menus/admin-metrics.yml",
        new GuiMenuDefinition("admin.metrics", 6, TITLE_KEY, Map.ofEntries(
            Map.entry("entry", "admin.metrics.page"),
            Map.entry("page", "admin.metrics.page"),
            Map.entry("refresh", "admin.metrics.page"),
            Map.entry("events", "admin.events.open"),
            Map.entry("audit", "admin.audit.open"),
            Map.entry("close", "gui.close")
        ))
    );
    private static final String MENU_ID = MENU.id();
    private final GuiActionRegistry actions;

    public AdminMetricsMenu() {
        this(null);
    }

    public AdminMetricsMenu(MessageRenderer messages) {
        this(messages, new GuiActionRegistry(GuiActionExecutor.noop()));
    }

    public AdminMetricsMenu(MessageRenderer messages, GuiActionRegistry actions) {
        this.actions = actions == null ? new GuiActionRegistry(GuiActionExecutor.noop()) : actions;
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player, MessageRenderer messages) {
        open(plugin, client, player, messages, 0);
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player, MessageRenderer messages, int requestedPage) {
        GuiSession session = GuiSessions.begin(player, MENU_ID);
        GuiStateMenus.openLoading(plugin, player, session, messages,
            message(messages, "admin-metrics-menu-loading", "Core 메트릭을 불러오는 중입니다."));
        client.adminMetrics().summary()
            .thenAccept(summary -> openSync(plugin, player, session, summary, messages, requestedPage))
            .exceptionally(error -> {
                GuiStateMenus.openError(plugin, player, session, messages,
                    message(messages, TITLE_KEY, TITLE),
                    message(messages, "admin-metrics-menu-load-failed", "Core 메트릭을 불러오지 못했습니다."),
                    "admin.metrics.page",
                    Map.of("page", Integer.toString(Math.max(0, requestedPage))),
                    "gui.close",
                    Map.of());
                return null;
            });
    }

    private static void openSync(Plugin plugin, Player player, GuiSession session, AdminMetricsSummaryView summary,
                                 MessageRenderer messages, int requestedPage) {
        GuiSessions.runIfCurrent(plugin, player, session, () -> {
            Map<String, Double> values = summary == null ? Map.of() : summary.latestValues();
            List<String> entries = orderedMetrics(values);
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
                String metricName = entries.get(offset + index);
                inventory.setItem(slots.get(index), metricItem(metricName, values.get(metricName), messages, page));
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
                message(messages, "admin-metrics-menu-samples-prefix", "전체 샘플: ") + (summary == null ? 0L : summary.samples()),
                message(messages, "admin-metrics-menu-count-prefix", "집계 메트릭: ") + entries.size(),
                message(messages, "admin-metrics-menu-refresh-action", "클릭: 새로고침")
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

    static List<String> orderedMetrics(Map<String, Double> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.keySet().stream()
            .sorted(Comparator.comparingInt(AdminMetricsMenu::focusIndex).thenComparing(String::compareTo))
            .toList();
    }

    static ItemStack metricItem(String metricName, Double value, MessageRenderer messages, int page) {
        return GuiItems.action(
            GuiMenuRenderer.material(MENU, "_", "COMPARATOR"),
            displayName(metricName),
            "admin.metrics.page",
            Map.of("page", Integer.toString(Math.max(0, page))),
            metricLore(metricName, value, messages).toArray(String[]::new)
        );
    }

    static List<String> metricLore(String metricName, Double value, MessageRenderer messages) {
        return List.of(
            message(messages, "admin-metrics-menu-name-prefix", "메트릭: ") + safeLine(metricName, 72),
            message(messages, "admin-metrics-menu-value-prefix", "최신 값: ") + formatValue(value),
            message(messages, "admin-metrics-menu-unit-prefix", "단위: ") + unit(metricName),
            message(messages, "admin-metrics-menu-refresh-action", "클릭: 새로고침")
        );
    }

    private static int focusIndex(String metricName) {
        int index = FOCUS_METRICS.indexOf(metricName);
        return index < 0 ? Integer.MAX_VALUE : index;
    }

    private static String displayName(String metricName) {
        String normalized = metricName == null ? "" : metricName.trim();
        if (normalized.startsWith("cloudislands_")) {
            normalized = normalized.substring("cloudislands_".length());
        }
        return safeLine(normalized.replace('_', ' '), 40);
    }

    private static String formatValue(Double value) {
        if (value == null || !Double.isFinite(value)) {
            return "-";
        }
        if (Math.rint(value) == value) {
            return Long.toString(value.longValue());
        }
        String formatted = String.format(Locale.ROOT, "%.6f", value);
        return formatted.replaceFirst("0+$", "").replaceFirst("\\.$", "");
    }

    private static String unit(String metricName) {
        String name = metricName == null ? "" : metricName;
        if (name.endsWith("_seconds")) {
            return "seconds";
        }
        if (name.endsWith("_bytes")) {
            return "bytes";
        }
        if (name.endsWith("_ratio")) {
            return "ratio";
        }
        if (name.endsWith("_total")) {
            return "counter";
        }
        return "value";
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
