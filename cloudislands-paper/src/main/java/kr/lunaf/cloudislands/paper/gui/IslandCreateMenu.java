package kr.lunaf.cloudislands.paper.gui;

import java.util.ArrayList;
import java.util.List;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.paper.application.view.PaperGuiViews;
import kr.lunaf.cloudislands.paper.application.view.PaperGuiViews.TemplateView;
import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

public final class IslandCreateMenu implements Listener {
    private static final String TITLE_KEY = "create-menu-title";
    private static final String TITLE = "섬 템플릿 선택";
    private static final GuiMenuDefinition MENU = GuiMenuDefinition.bundled(
        "config-v2/ui/menus/create.yml",
        new GuiMenuDefinition("island.create", 3, TITLE_KEY, java.util.Map.of(
            "open", "island.create.open",
            "create", "island.create",
            "back", "island.main.open"
        ))
    );
    private static final String MENU_ID = MENU.id();
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
        GuiSession session = GuiSessions.begin(player, MENU_ID);
        GuiStateMenus.openLoading(plugin, player, session, messages, message(messages, MENU.titleKey(), TITLE));
        PaperGuiViews.templates(client)
            .thenAccept(templates -> openSync(plugin, player, session, templates, messages))
            .exceptionally(error -> {
                GuiStateMenus.openError(plugin, player, session, messages, message(messages, MENU.titleKey(), TITLE), message(messages, "create-menu-load-failed", "섬 템플릿을 불러오지 못했습니다."), "island.create.open", "island.main.open");
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

    private static void openSync(Plugin plugin, Player player, GuiSession session, List<TemplateView> templates, MessageRenderer messages) {
        GuiSessions.runIfCurrent(plugin, player, session, () -> {
            Inventory inventory = GuiMenuRenderer.render(MENU, session, messages, TITLE, item -> true);
            List<TemplateView> enabled = templates.stream().filter(TemplateView::enabled).limit(14).toList();
            if (enabled.isEmpty()) {
                enabled = List.of(new TemplateView("default", message(messages, "create-menu-default-template", "기본 섬"), true, ""));
            }
            List<Integer> templateSlots = GuiMenuRenderer.slots(MENU, "_");
            for (int index = 0; index < enabled.size() && index < templateSlots.size(); index++) {
                inventory.setItem(templateSlots.get(index), item(enabled.get(index), messages));
            }
            player.openInventory(inventory);
        });
    }

    private static ItemStack item(TemplateView template, MessageRenderer messages) {
        ItemStack item = GuiItems.action(GuiMenuRenderer.material(MENU, "_", "OAK_SAPLING"), template.displayName().isBlank() ? template.id() : template.displayName(), "island.create", java.util.Map.of("templateId", template.id()), message(messages, "create-menu-click-to-create", "클릭하면 이 템플릿으로 섬을 생성합니다."));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            List<String> lore = new ArrayList<>();
            if (!template.minNodeVersion().isBlank()) {
                lore.add(message(messages, "create-menu-required-version", "필요 플랫폼 버전: ") + template.minNodeVersion());
            }
            lore.add(message(messages, "create-menu-click-to-create", "클릭하면 이 템플릿으로 섬을 생성합니다."));
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static String message(MessageRenderer messages, String key, String fallback) {
        return GuiMenuRenderer.message(messages, key, fallback);
    }

}
