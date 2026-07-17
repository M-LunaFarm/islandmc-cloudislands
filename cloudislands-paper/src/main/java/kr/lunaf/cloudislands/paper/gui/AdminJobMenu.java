package kr.lunaf.cloudislands.paper.gui;

import java.util.List;
import java.util.Map;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.coreclient.JobView;
import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

public final class AdminJobMenu implements Listener {
    private static final String TITLE_KEY = "admin-job-menu-title";
    private static final String TITLE = "섬 작업 관리";
    private static final GuiMenuDefinition MENU = GuiMenuDefinition.bundled(
        "config-v2/ui/menus/admin-jobs.yml",
        new GuiMenuDefinition("admin.jobs", 6, TITLE_KEY, Map.ofEntries(
            Map.entry("entry", "admin.jobs.retry"),
            Map.entry("page", "admin.jobs.page"),
            Map.entry("refresh", "admin.jobs.page"),
            Map.entry("close", "gui.close")
        ))
    );
    private static final String MENU_ID = MENU.id();
    private final MessageRenderer messages;
    private final GuiActionRegistry actions;

    public AdminJobMenu() {
        this(null);
    }

    public AdminJobMenu(MessageRenderer messages) {
        this(messages, new GuiActionRegistry(GuiActionExecutor.noop()));
    }

    public AdminJobMenu(MessageRenderer messages, GuiActionRegistry actions) {
        this.messages = messages;
        this.actions = actions == null ? new GuiActionRegistry(GuiActionExecutor.noop()) : actions;
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player, MessageRenderer messages) {
        open(plugin, client, player, messages, 0);
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player, MessageRenderer messages, int requestedPage) {
        GuiSession session = GuiSessions.begin(player, MENU_ID);
        GuiStateMenus.openLoading(plugin, player, session, messages,
            message(messages, "admin-job-menu-loading", "작업 목록을 불러오는 중입니다."));
        client.jobs().list()
            .thenAccept(jobs -> openSync(plugin, player, session, jobs, messages, requestedPage))
            .exceptionally(error -> {
                GuiStateMenus.openError(plugin, player, session, messages,
                    message(messages, TITLE_KEY, TITLE),
                    message(messages, "admin-job-menu-load-failed", "작업 목록을 불러오지 못했습니다."),
                    "admin.jobs.page",
                    Map.of("page", Integer.toString(Math.max(0, requestedPage))),
                    "gui.close",
                    Map.of());
                return null;
            });
    }

    private static void openSync(Plugin plugin, Player player, GuiSession session, List<JobView> jobs, MessageRenderer messages, int requestedPage) {
        GuiSessions.runIfCurrent(plugin, player, session, () -> {
            List<JobView> entries = jobs == null ? List.of() : List.copyOf(jobs);
            List<Integer> slots = GuiMenuRenderer.slots(MENU, "_");
            int pageSize = Math.max(1, slots.size());
            int maxPage = Math.max(0, (entries.size() - 1) / pageSize);
            int page = Math.max(0, Math.min(requestedPage, maxPage));
            String title = message(messages, TITLE_KEY, TITLE) + " " + (page + 1) + "/" + (maxPage + 1) + " (" + entries.size() + ")";
            Inventory inventory = GuiMenuRenderer.render(MENU, session, messages, title,
                item -> !List.of("_", "E", "P", "N").contains(item.symbol()));
            int offset = page * pageSize;
            for (int index = 0; index < pageSize && offset + index < entries.size(); index++) {
                inventory.setItem(slots.get(index), jobItem(entries.get(offset + index), messages, page));
            }
            if (entries.isEmpty()) {
                GuiMenuRenderer.setSymbolItem(inventory, MENU, "E", messages, Map.of(), List.of());
            }
            if (page > 0) {
                setPageItem(inventory, "P", page - 1, messages);
            }
            if (page < maxPage) {
                setPageItem(inventory, "N", page + 1, messages);
            }
            setPageItem(inventory, "R", page, messages);
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
        GuiClick click = GuiClick.from(event);
        if (actionId.equals("admin.jobs.retry")) {
            if (click == GuiClick.SHIFT_RIGHT) {
                actionId = "admin.jobs.cancel.prepare";
            } else if (click != GuiClick.LEFT) {
                return;
            }
        }
        player.closeInventory();
        actions.execute(player, GuiActions.from(actionId, GuiItems.data(event.getCurrentItem())).orElse(null), click);
    }

    static ItemStack jobItem(JobView job, MessageRenderer messages, int page) {
        return GuiItems.action(
            GuiMenuRenderer.material(MENU, "_", "PAPER"),
            jobTitle(job),
            "admin.jobs.retry",
            jobActionData(job, page),
            jobLore(job, messages).toArray(String[]::new)
        );
    }

    static Map<String, String> jobActionData(JobView job, int page) {
        return Map.of("jobId", job.id(), "page", Integer.toString(Math.max(0, page)));
    }

    static String jobTitle(JobView job) {
        return safeLine(job.type(), 30) + " #" + shortId(job.id());
    }

    static List<String> jobLore(JobView job, MessageRenderer messages) {
        return List.of(
            message(messages, "admin-job-menu-state-prefix", "상태: ") + safeLine(job.state(), 24),
            message(messages, "admin-job-menu-island-prefix", "섬: ") + shortId(job.islandId()),
            message(messages, "admin-job-menu-node-prefix", "대상 노드: ") + safeLine(job.targetNode(), 32),
            message(messages, "admin-job-menu-attempts-prefix", "시도 횟수: ") + job.attempts(),
            message(messages, "admin-job-menu-created-prefix", "생성: ") + safeLine(job.createdAt(), 48),
            message(messages, "admin-job-menu-error-prefix", "오류: ") + safeLine(job.error(), 80),
            message(messages, "admin-job-menu-left-action", "좌클릭: 작업 재시도"),
            message(messages, "admin-job-menu-shift-right-action", "Shift+우클릭: 작업 취소 확인")
        );
    }

    private static void setPageItem(Inventory inventory, String symbol, int page, MessageRenderer messages) {
        GuiMenuRenderer.setSymbolItem(inventory, MENU, symbol, messages,
            Map.of("page", Integer.toString(Math.max(0, page))), List.of());
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
