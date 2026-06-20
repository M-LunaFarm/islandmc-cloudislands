package kr.lunaf.cloudislands.paper.gui;

import java.util.ArrayList;
import java.util.List;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.paper.application.view.PaperGuiViews;
import kr.lunaf.cloudislands.paper.application.view.PaperGuiViews.TemplateView;
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

public final class IslandCreateMenu implements Listener {
    private static final String MENU_ID = "island.create";
    private static final String TITLE_KEY = "create-menu-title";
    private static final String TITLE = "섬 템플릿 선택";
    private final MessageRenderer messages;

    public IslandCreateMenu() {
        this(null);
    }

    public IslandCreateMenu(MessageRenderer messages) {
        this.messages = messages;
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player) {
        open(plugin, client, player, null);
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player, MessageRenderer messages) {
        GuiSession session = GuiSessions.begin(player, MENU_ID);
        GuiStateMenus.openLoading(plugin, player, session, messages, message(messages, TITLE_KEY, TITLE));
        PaperGuiViews.templates(client)
            .thenAccept(templates -> openSync(plugin, player, session, templates, messages))
            .exceptionally(error -> {
                GuiStateMenus.openError(plugin, player, session, messages, message(messages, TITLE_KEY, TITLE), message(messages, "create-menu-load-failed", "섬 템플릿을 불러오지 못했습니다."), "island.create.open", "island.main.open");
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
        GuiActionRegistry.execute(player, actionId, GuiItems.data(event.getCurrentItem()), GuiClick.from(event));
    }

    private static void openSync(Plugin plugin, Player player, GuiSession session, List<TemplateView> templates, MessageRenderer messages) {
        GuiSessions.runIfCurrent(plugin, player, session, () -> {
            Inventory inventory = GuiInventories.create(MENU_ID, 27, message(messages, TITLE_KEY, TITLE));
            List<TemplateView> enabled = templates.stream().filter(TemplateView::enabled).limit(14).toList();
            if (enabled.isEmpty()) {
                enabled = List.of(new TemplateView("default", message(messages, "create-menu-default-template", "기본 섬"), true, ""));
            }
            int[] templateSlots = {9, 10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 23, 24, 25};
            for (int index = 0; index < enabled.size() && index < templateSlots.length; index++) {
                inventory.setItem(templateSlots[index], item(enabled.get(index), messages));
            }
            inventory.setItem(18, button(Material.COMPASS, message(messages, "create-menu-main-menu-name", "메인 메뉴"), message(messages, "create-menu-main-menu-command", "/섬 메뉴")));
            inventory.setItem(22, button(Material.CLOCK, message(messages, "create-menu-refresh-name", "템플릿 새로고침"), message(messages, "create-menu-refresh-command", "/섬 생성메뉴")));
            player.openInventory(inventory);
        });
    }

    private static ItemStack item(TemplateView template, MessageRenderer messages) {
        ItemStack item = GuiItems.action(Material.OAK_SAPLING, template.displayName().isBlank() ? template.id() : template.displayName(), "island.create", java.util.Map.of("templateId", template.id()), message(messages, "create-menu-click-to-create", "클릭하면 이 템플릿으로 섬을 생성합니다."));
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
        if (messages == null) {
            return fallback;
        }
        String rendered = messages.plain(key);
        return rendered.isBlank() ? fallback : rendered;
    }

    private static ItemStack button(Material material, String name, String... lore) {
        String actionId = material == Material.COMPASS ? "island.main.open" : "island.create.open";
        return GuiItems.action(material, name, actionId, lore);
    }

}
