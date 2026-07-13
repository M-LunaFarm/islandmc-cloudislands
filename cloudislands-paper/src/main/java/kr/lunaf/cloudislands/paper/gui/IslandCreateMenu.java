package kr.lunaf.cloudislands.paper.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.paper.application.view.PaperGuiViews;
import kr.lunaf.cloudislands.paper.application.view.PaperGuiViews.TemplateView;
import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

public final class IslandCreateMenu implements Listener {
    private static final String TITLE_KEY = "create-menu-title";
    private static final String TITLE = "섬 템플릿 선택";
    private static final GuiMenuDefinition MENU = GuiMenuDefinition.bundled(
        "config-v2/ui/menus/create.yml",
        new GuiMenuDefinition("island.create", 3, TITLE_KEY, java.util.Map.of(
            "open", "island.create.open",
            "create", "island.create.prepare",
            "page", "island.create.page",
            "back", "island.main.open"
        ))
    );
    private static final String MENU_ID = MENU.id();
    private static final GuiMenuDefinition CONFIRM_MENU = new GuiMenuDefinition(
        "island.create.confirm",
        3,
        "create-confirm-menu-title",
        List.of(
            ".........",
            "...C.B...",
            "........."
        ),
        Map.of(
            "C", new GuiMenuDefinition.MenuItem("C", "EMERALD_BLOCK", "create-confirm-name", "섬 생성 확인", "", "", List.of(), List.of(), Map.of(), "confirm"),
            "B", new GuiMenuDefinition.MenuItem("B", "OAK_DOOR", "create-confirm-back-name", "템플릿 다시 선택", "", "", List.of(), List.of(), Map.of(), "back")
        ),
        Map.of(
            "confirm", "island.create",
            "back", "island.create.open"
        )
    );
    private static final String CONFIRM_MENU_ID = CONFIRM_MENU.id();
    private final MessageRenderer messages;
    private final GuiActionRegistry actions;

    public IslandCreateMenu() {
        this(null);
    }

    public IslandCreateMenu(MessageRenderer messages) {
        this(messages, new GuiActionRegistry(GuiActionExecutor.noop()));
    }

    public IslandCreateMenu(MessageRenderer messages, GuiActionRegistry actions) {
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
        PaperGuiViews.templates(client)
            .thenAccept(templates -> openSync(plugin, player, session, templates, messages, page))
            .exceptionally(error -> {
                GuiStateMenus.openError(plugin, player, session, messages, message(messages, MENU.titleKey(), TITLE), message(messages, "create-menu-load-failed", "섬 템플릿을 불러오지 못했습니다."), "island.create.open", "island.main.open");
                return null;
            });
    }

    public static void openConfirm(Plugin plugin, CoreApiClient client, Player player, String templateId, MessageRenderer messages) {
        String normalizedTemplateId = templateId == null || templateId.isBlank() ? "default" : templateId.trim();
        GuiSession session = GuiSessions.begin(player, CONFIRM_MENU_ID);
        GuiStateMenus.openLoading(plugin, player, session, messages, message(messages, "create-confirm-menu-title", "섬 생성 확인"));
        PaperGuiViews.templates(client)
            .thenAccept(templates -> PaperSchedulers.run(plugin, () -> {
                if (!GuiSessions.isCurrent(player, session)) {
                    return;
                }
                TemplateView template = templates.stream()
                    .filter(candidate -> candidate.id().equalsIgnoreCase(normalizedTemplateId))
                    .findFirst()
                    .orElse(null);
                if (template == null || !template.enabled()) {
                    GuiStateMenus.openError(plugin, player, session, messages, message(messages, "create-confirm-menu-title", "섬 생성 확인"), message(messages, "create-confirm-template-unavailable", "사용할 수 없는 템플릿입니다."), "island.create.open", "island.create.open");
                    return;
                }
                if (!canUse(player, template)) {
                    GuiStateMenus.openError(plugin, player, session, messages, message(messages, "create-confirm-menu-title", "섬 생성 확인"), message(messages, "create-menu-locked", "이 템플릿을 사용할 권한이 없습니다."), "island.create.open", "island.create.open");
                    return;
                }
                openConfirmSync(plugin, player, session, template, messages);
            }))
            .exceptionally(error -> {
                GuiStateMenus.openError(plugin, player, session, messages, message(messages, "create-confirm-menu-title", "섬 생성 확인"), message(messages, "create-menu-load-failed", "섬 템플릿을 불러오지 못했습니다."), "island.create.open", "island.create.open");
                return null;
            });
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!GuiItems.menuClick(event, MENU_ID) && !GuiItems.menuClick(event, CONFIRM_MENU_ID)) {
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

    private static void openSync(Plugin plugin, Player player, GuiSession session, List<TemplateView> templates, MessageRenderer messages, int requestedPage) {
        GuiSessions.runIfCurrent(plugin, player, session, () -> {
            List<TemplateView> enabled = templates.stream()
                .filter(TemplateView::enabled)
                .toList();
            if (enabled.isEmpty()) {
                enabled = List.of(new TemplateView("default", message(messages, "create-menu-default-template", "기본 섬"), true, ""));
            }
            List<Integer> templateSlots = GuiMenuRenderer.slots(MENU, "_");
            int pageSize = Math.max(1, templateSlots.size());
            int maxPage = Math.max(0, (enabled.size() - 1) / pageSize);
            int page = Math.max(0, Math.min(requestedPage, maxPage));
            String title = message(messages, MENU.titleKey(), TITLE) + " " + (page + 1) + "/" + (maxPage + 1);
            Inventory inventory = GuiMenuRenderer.render(MENU, session, messages, title, item -> !List.of("W", "N").contains(item.symbol()));
            int offset = page * pageSize;
            for (int index = 0; index < pageSize && offset + index < enabled.size(); index++) {
                TemplateView template = enabled.get(offset + index);
                inventory.setItem(templateSlots.get(index), item(template, messages, canUse(player, template)));
            }
            if (page > 0) {
                setPageItem(inventory, "W", page - 1, messages);
            }
            if (page < maxPage) {
                setPageItem(inventory, "N", page + 1, messages);
            }
            player.openInventory(inventory);
        });
    }

    private static void openConfirmSync(Plugin plugin, Player player, GuiSession session, TemplateView template, MessageRenderer messages) {
        GuiSessions.runIfCurrent(plugin, player, session, () -> {
            Inventory inventory = GuiMenuRenderer.render(CONFIRM_MENU, session, messages, "섬 생성 확인", item -> true);
            List<String> lore = templateLore(template, messages);
            lore.add(message(messages, "create-confirm-click", "클릭하면 이 템플릿으로 섬 생성을 요청합니다."));
            CONFIRM_MENU.item("C")
                .ifPresent(item -> GuiMenuRenderer.slots(CONFIRM_MENU, "C").forEach(slot -> inventory.setItem(slot, GuiMenuRenderer.item(CONFIRM_MENU, item, messages, template.displayName().isBlank() ? template.id() : template.displayName(), Map.of("templateId", template.id()), lore))));
            player.openInventory(inventory);
        });
    }

    private static ItemStack item(TemplateView template, MessageRenderer messages, boolean allowed) {
        String displayName = template.displayName().isBlank() ? template.id() : template.displayName();
        List<String> lore = templateLore(template, messages);
        if (allowed) {
            lore.add(message(messages, "create-menu-click-to-create", "클릭하면 이 템플릿으로 섬을 생성합니다."));
            return MENU.item("_")
                .map(item -> GuiMenuRenderer.item(MENU, item, messages, displayName, Map.of("templateId", template.id()), lore))
                .orElseThrow(() -> new IllegalStateException("Missing create menu template item symbol _"));
        }
        lore.add(message(messages, "create-menu-locked", "이 템플릿을 사용할 권한이 없습니다."));
        return GuiItems.action(
            Material.BARRIER,
            displayName,
            "island.create.locked",
            Map.of("templateId", template.id(), "requiredPermission", template.requiredPermission()),
            lore.toArray(String[]::new)
        );
    }

    private static List<String> templateLore(TemplateView template, MessageRenderer messages) {
        List<String> lore = new ArrayList<>();
        if (!template.description().isBlank()) {
            lore.add(template.description());
        }
        if (!template.category().isBlank()) {
            lore.add(message(messages, "create-menu-category", "카테고리: ") + template.category());
        }
        lore.add(message(messages, "create-menu-size", "섬 크기: ") + template.defaultIslandSize());
        if (!template.creationCost().isBlank() && !"0".equals(template.creationCost())) {
            lore.add(message(messages, "create-menu-cost", "생성 비용: ") + template.creationCost());
        }
        if (!template.requiredPermission().isBlank()) {
            lore.add(message(messages, "create-menu-required-permission", "필요 권한: ") + template.requiredPermission());
        }
        if (!template.minNodeVersion().isBlank()) {
            lore.add(message(messages, "create-menu-required-version", "필요 플랫폼 버전: ") + template.minNodeVersion());
        }
        if (!template.bundleStoragePath().isBlank()) {
            lore.add(message(messages, "create-menu-bundle-ready", "번들: 준비됨"));
        }
        return lore;
    }

    private static boolean canUse(Player player, TemplateView template) {
        return template.requiredPermission().isBlank() || player.hasPermission(template.requiredPermission());
    }

    private static void setPageItem(Inventory inventory, String symbol, int page, MessageRenderer messages) {
        String key = symbol.equals("W") ? "create-menu-previous-page" : "create-menu-next-page";
        String fallback = symbol.equals("W") ? "이전 페이지" : "다음 페이지";
        GuiMenuRenderer.setSymbolItem(inventory, MENU, symbol, messages, Map.of("page", Integer.toString(page)), List.of(message(messages, key, fallback)));
    }

    private static String message(MessageRenderer messages, String key, String fallback) {
        return GuiMenuRenderer.message(messages, key, fallback);
    }

}
