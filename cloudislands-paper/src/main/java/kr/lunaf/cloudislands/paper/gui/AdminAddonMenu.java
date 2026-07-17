package kr.lunaf.cloudislands.paper.gui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import kr.lunaf.cloudislands.api.model.CloudIslandsAddonSnapshot;
import kr.lunaf.cloudislands.api.service.IslandAddonService;
import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

public final class AdminAddonMenu implements Listener {
    private static final String TITLE_KEY = "admin-addon-menu-title";
    private static final String TITLE = "Addon 관리";
    private static final GuiMenuDefinition MENU = GuiMenuDefinition.bundled(
        "config-v2/ui/menus/admin-addons.yml",
        new GuiMenuDefinition("admin.addons", 6, TITLE_KEY, Map.ofEntries(
            Map.entry("entry", "admin.addons.toggle.prepare"),
            Map.entry("page", "admin.addons.page"),
            Map.entry("refresh", "admin.addons.page"),
            Map.entry("dashboard", "admin.dashboard.open")
        ))
    );
    private static final String MENU_ID = MENU.id();
    private final GuiActionRegistry actions;

    public AdminAddonMenu() {
        this(null);
    }

    public AdminAddonMenu(MessageRenderer messages) {
        this(messages, new GuiActionRegistry(GuiActionExecutor.noop()));
    }

    public AdminAddonMenu(MessageRenderer messages, GuiActionRegistry actions) {
        this.actions = actions == null ? new GuiActionRegistry(GuiActionExecutor.noop()) : actions;
    }

    public static void open(Plugin plugin, IslandAddonService service, Player player, MessageRenderer messages) {
        open(plugin, service, player, messages, 0);
    }

    public static void open(Plugin plugin, IslandAddonService service, Player player, MessageRenderer messages, int requestedPage) {
        GuiSession session = GuiSessions.begin(player, MENU_ID);
        GuiStateMenus.openLoading(plugin, player, session, messages,
            message(messages, "admin-addon-menu-loading", "Addon 목록을 불러오는 중입니다."));
        service.list()
            .thenAccept(addons -> openSync(plugin, player, session, addons, messages, requestedPage))
            .exceptionally(error -> {
                GuiStateMenus.openError(plugin, player, session, messages,
                    message(messages, TITLE_KEY, TITLE),
                    message(messages, "admin-addon-menu-load-failed", "Addon 목록을 불러오지 못했습니다."),
                    "admin.addons.page",
                    Map.of("page", Integer.toString(Math.max(0, requestedPage))),
                    "admin.dashboard.open",
                    Map.of());
                return null;
            });
    }

    private static void openSync(Plugin plugin, Player player, GuiSession session, List<CloudIslandsAddonSnapshot> addons,
                                 MessageRenderer messages, int requestedPage) {
        GuiSessions.runIfCurrent(plugin, player, session, () -> {
            List<CloudIslandsAddonSnapshot> entries = sortedAddons(addons);
            int enabledCount = (int) entries.stream().filter(CloudIslandsAddonSnapshot::enabled).count();
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
                inventory.setItem(slots.get(index), addonItem(entries.get(offset + index), messages, page));
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
                message(messages, "admin-addon-menu-enabled-count-prefix", "활성 Addon: ") + enabledCount + "/" + entries.size(),
                message(messages, "admin-addon-menu-refresh-action", "클릭: 새로고침")
            ));
            player.openInventory(inventory);
        });
    }

    public static Material toggleConfirmationMaterial(boolean enable) {
        return GuiMenuRenderer.material(MENU, enable ? "A" : "D", enable ? "LIME_DYE" : "GRAY_DYE");
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
        if (actionId.equals("admin.addons.toggle.prepare") && click != GuiClick.LEFT) {
            return;
        }
        if (!click.supported() || actionId.isBlank()) {
            return;
        }
        player.closeInventory();
        actions.execute(player, GuiActions.from(actionId, GuiItems.data(event.getCurrentItem())).orElse(null), click);
    }

    static List<CloudIslandsAddonSnapshot> sortedAddons(List<CloudIslandsAddonSnapshot> addons) {
        if (addons == null || addons.isEmpty()) {
            return List.of();
        }
        return addons.stream()
            .sorted(Comparator.comparing(CloudIslandsAddonSnapshot::enabled).reversed()
                .thenComparing(addon -> addon.displayName().isBlank() ? addon.id() : addon.displayName(), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(CloudIslandsAddonSnapshot::id))
            .toList();
    }

    static List<String> addonLore(CloudIslandsAddonSnapshot addon, MessageRenderer messages) {
        List<String> lore = new ArrayList<>();
        lore.add(message(messages, "admin-addon-menu-id-prefix", "ID: ") + safeLine(addon.id(), 48));
        lore.add(message(messages, "admin-addon-menu-version-prefix", "버전: ") + safeLine(addon.version(), 32));
        lore.add(message(messages, "admin-addon-menu-state-prefix", "상태: ")
            + message(messages, addon.enabled() ? "admin-addon-menu-enabled" : "admin-addon-menu-disabled",
                addon.enabled() ? "활성" : "비활성"));
        lore.add(message(messages, "admin-addon-menu-configured-features-prefix", "구성 기능: ")
            + enabledFeatures(addon.configuredFeatures()) + "/" + addon.configuredFeatures().size());
        lore.add(message(messages, "admin-addon-menu-runtime-features-prefix", "실효 기능: ")
            + enabledFeatures(addon.features()) + "/" + addon.features().size());
        lore.add(message(messages, "admin-addon-menu-commands-prefix", "명령: ") + onOff(addon.commandsEnabled(), messages));
        lore.add(message(messages, "admin-addon-menu-gui-prefix", "GUI: ") + onOff(addon.guiEnabled(), messages));
        lore.add(message(messages, "admin-addon-menu-placeholders-prefix", "Placeholder: ") + onOff(addon.placeholdersEnabled(), messages));
        lore.add(message(messages, "admin-addon-menu-updated-at-prefix", "갱신: ") + safeLine(addon.updatedAt().toString(), 32));
        lore.add(message(messages, addon.enabled() ? "admin-addon-menu-disable-action" : "admin-addon-menu-enable-action",
            addon.enabled() ? "좌클릭: 비활성화 확인" : "좌클릭: 활성화 확인"));
        return List.copyOf(lore);
    }

    private static ItemStack addonItem(CloudIslandsAddonSnapshot addon, MessageRenderer messages, int page) {
        Material material = GuiMenuRenderer.material(MENU, addon.enabled() ? "A" : "D",
            addon.enabled() ? "LIME_DYE" : "GRAY_DYE");
        String displayName = addon.displayName().isBlank() ? addon.id() : addon.displayName();
        return GuiItems.action(material, displayName, "admin.addons.toggle.prepare", Map.of(
            "addonId", addon.id(),
            "enable", Boolean.toString(!addon.enabled()),
            "page", Integer.toString(Math.max(0, page))
        ), addonLore(addon, messages).toArray(String[]::new));
    }

    private static long enabledFeatures(Map<String, Boolean> features) {
        return features.values().stream().filter(Boolean.TRUE::equals).count();
    }

    private static String onOff(boolean enabled, MessageRenderer messages) {
        return message(messages, enabled ? "admin-addon-menu-on" : "admin-addon-menu-off", enabled ? "ON" : "OFF");
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
