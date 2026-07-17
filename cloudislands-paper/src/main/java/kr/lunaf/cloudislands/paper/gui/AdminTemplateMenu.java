package kr.lunaf.cloudislands.paper.gui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.coreclient.TemplateView;
import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

public final class AdminTemplateMenu implements Listener {
    private static final String TITLE_KEY = "admin-template-menu-title";
    private static final String TITLE = "섬 템플릿 관리";
    private static final GuiMenuDefinition MENU = GuiMenuDefinition.bundled(
        "config-v2/ui/menus/admin-templates.yml",
        new GuiMenuDefinition("admin.templates", 6, TITLE_KEY, Map.ofEntries(
            Map.entry("entry", "admin.templates.toggle.prepare"),
            Map.entry("page", "admin.templates.page"),
            Map.entry("refresh", "admin.templates.page"),
            Map.entry("dashboard", "admin.dashboard.open"),
            Map.entry("close", "gui.close")
        ))
    );
    private static final String MENU_ID = MENU.id();
    private final GuiActionRegistry actions;

    public AdminTemplateMenu() {
        this(null);
    }

    public AdminTemplateMenu(MessageRenderer messages) {
        this(messages, new GuiActionRegistry(GuiActionExecutor.noop()));
    }

    public AdminTemplateMenu(MessageRenderer messages, GuiActionRegistry actions) {
        this.actions = actions == null ? new GuiActionRegistry(GuiActionExecutor.noop()) : actions;
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player, MessageRenderer messages) {
        open(plugin, client, player, messages, 0);
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player, MessageRenderer messages, int requestedPage) {
        GuiSession session = GuiSessions.begin(player, MENU_ID);
        GuiStateMenus.openLoading(plugin, player, session, messages,
            message(messages, "admin-template-menu-loading", "템플릿 목록을 불러오는 중입니다."));
        client.templates().list()
            .thenAccept(templates -> openSync(plugin, player, session, templates, messages, requestedPage))
            .exceptionally(error -> {
                GuiStateMenus.openError(plugin, player, session, messages,
                    message(messages, TITLE_KEY, TITLE),
                    message(messages, "admin-template-menu-load-failed", "템플릿 목록을 불러오지 못했습니다."),
                    "admin.templates.page",
                    Map.of("page", Integer.toString(Math.max(0, requestedPage))),
                    "admin.dashboard.open",
                    Map.of());
                return null;
            });
    }

    private static void openSync(Plugin plugin, Player player, GuiSession session, List<TemplateView> templates,
                                 MessageRenderer messages, int requestedPage) {
        GuiSessions.runIfCurrent(plugin, player, session, () -> {
            List<TemplateView> entries = sortedTemplates(templates);
            int enabledCount = (int) entries.stream().filter(TemplateView::enabled).count();
            List<Integer> slots = GuiMenuRenderer.slots(MENU, "_");
            int pageSize = Math.max(1, slots.size());
            int maxPage = Math.max(0, (entries.size() - 1) / pageSize);
            int page = Math.max(0, Math.min(requestedPage, maxPage));
            String title = message(messages, TITLE_KEY, TITLE) + " " + (page + 1) + "/" + (maxPage + 1)
                + " (" + enabledCount + "/" + entries.size() + ")";
            Inventory inventory = GuiMenuRenderer.render(MENU, session, messages, title,
                item -> !List.of("_", "E", "P", "N").contains(item.symbol()));
            int offset = page * pageSize;
            for (int index = 0; index < pageSize && offset + index < entries.size(); index++) {
                inventory.setItem(slots.get(index), templateItem(entries.get(offset + index), messages, page, enabledCount));
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
                message(messages, "admin-template-menu-enabled-count-prefix", "활성 템플릿: ") + enabledCount + "/" + entries.size(),
                message(messages, "admin-template-menu-refresh-action", "클릭: 새로고침")
            ));
            player.openInventory(inventory);
        });
    }

    public static Material toggleConfirmationMaterial() {
        return GuiMenuRenderer.material(MENU, "_", "STRUCTURE_BLOCK");
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
        GuiClick click = GuiClick.from(event);
        String actionId = GuiItems.actionId(event.getCurrentItem());
        if (actionId.equals("admin.templates.toggle.prepare") && click != GuiClick.LEFT) {
            return;
        }
        if (!click.supported() || actionId.isBlank()) {
            return;
        }
        player.closeInventory();
        if (actionId.equals("gui.close")) {
            return;
        }
        actions.execute(player, GuiActions.from(actionId, GuiItems.data(event.getCurrentItem())).orElse(null), click);
    }

    static List<TemplateView> sortedTemplates(List<TemplateView> templates) {
        if (templates == null || templates.isEmpty()) {
            return List.of();
        }
        return templates.stream()
            .sorted(Comparator.comparingInt(TemplateView::sortOrder)
                .thenComparing(template -> template.displayName().isBlank() ? template.id() : template.displayName(), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(TemplateView::id))
            .toList();
    }

    static ItemStack templateItem(TemplateView template, MessageRenderer messages, int page, int enabledCount) {
        String displayName = template.displayName().isBlank() ? template.id() : template.displayName();
        return GuiItems.action(
            templateMaterial(template),
            displayName,
            "admin.templates.toggle.prepare",
            Map.of(
                "templateId", template.id(),
                "enable", Boolean.toString(!template.enabled()),
                "page", Integer.toString(Math.max(0, page)),
                "enabledCount", Integer.toString(Math.max(0, enabledCount))
            ),
            templateLore(template, messages).toArray(String[]::new)
        );
    }

    static List<String> templateLore(TemplateView template, MessageRenderer messages) {
        List<String> lore = new ArrayList<>();
        lore.add(message(messages, "admin-template-menu-id-prefix", "ID: ") + safeLine(template.id(), 48));
        lore.add(message(messages, "admin-template-menu-state-prefix", "상태: ")
            + message(messages, template.enabled() ? "admin-template-menu-enabled" : "admin-template-menu-disabled",
                template.enabled() ? "활성" : "비활성"));
        lore.add(message(messages, "admin-template-menu-category-prefix", "카테고리: ") + safeLine(template.category(), 32));
        lore.add(message(messages, "admin-template-menu-schema-prefix", "스키마/크기: ")
            + template.schemaVersion() + "/" + template.defaultIslandSize());
        lore.add(message(messages, "admin-template-menu-version-prefix", "최소 노드 버전: ") + safeLine(template.minNodeVersion(), 32));
        lore.add(message(messages, "admin-template-menu-permission-prefix", "필요 권한: ") + safeLine(template.requiredPermission(), 48));
        lore.add(message(messages, "admin-template-menu-cost-prefix", "생성 비용: ") + safeLine(template.creationCost(), 24));
        lore.add(message(messages, "admin-template-menu-bundle-prefix", "번들: ")
            + message(messages, template.bundleStoragePath().isBlank() ? "admin-template-menu-bundle-missing" : "admin-template-menu-bundle-ready",
                template.bundleStoragePath().isBlank() ? "없음" : "준비됨"));
        lore.add(message(messages, "admin-template-menu-sort-prefix", "정렬 순서: ") + template.sortOrder());
        lore.add(message(messages, template.enabled() ? "admin-template-menu-disable-action" : "admin-template-menu-enable-action",
            template.enabled() ? "좌클릭: 비활성화 확인" : "좌클릭: 활성화 확인"));
        return List.copyOf(lore);
    }

    private static Material templateMaterial(TemplateView template) {
        Material material = Material.matchMaterial(template.iconMaterial());
        return material == null || !material.isItem() ? GuiMenuRenderer.material(MENU, "_", "STRUCTURE_BLOCK") : material;
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
