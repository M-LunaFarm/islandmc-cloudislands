package kr.lunaf.cloudislands.paper.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
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

public final class AdminAddonFeatureMenu implements Listener {
    private static final String TITLE_KEY = "admin-addon-feature-menu-title";
    private static final String TITLE = "Addon 기능 관리";
    private static final GuiMenuDefinition MENU = GuiMenuDefinition.bundled(
        "config-v2/ui/menus/admin-addon-features.yml",
        new GuiMenuDefinition("admin.addons.features", 6, TITLE_KEY, Map.ofEntries(
            Map.entry("entry", "admin.addons.feature.toggle.prepare"),
            Map.entry("page", "admin.addons.features.page"),
            Map.entry("refresh", "admin.addons.features.page"),
            Map.entry("addons", "admin.addons.page"),
            Map.entry("dashboard", "admin.dashboard.open")
        ))
    );
    private static final String MENU_ID = MENU.id();
    private final GuiActionRegistry actions;

    public AdminAddonFeatureMenu() {
        this(null);
    }

    public AdminAddonFeatureMenu(MessageRenderer messages) {
        this(messages, new GuiActionRegistry(GuiActionExecutor.noop()));
    }

    public AdminAddonFeatureMenu(MessageRenderer messages, GuiActionRegistry actions) {
        this.actions = actions == null ? new GuiActionRegistry(GuiActionExecutor.noop()) : actions;
    }

    public static void open(Plugin plugin, IslandAddonService service, Player player, MessageRenderer messages,
                            String addonId, int requestedPage, int listPage) {
        GuiSession session = GuiSessions.begin(player, MENU_ID);
        GuiStateMenus.openLoading(plugin, player, session, messages,
            message(messages, "admin-addon-feature-menu-loading", "Addon 기능을 불러오는 중입니다."));
        service.get(addonId)
            .thenAccept(addon -> {
                if (addon.isEmpty()) {
                    GuiStateMenus.openError(plugin, player, session, messages,
                        message(messages, TITLE_KEY, TITLE),
                        message(messages, "admin-addon-feature-menu-not-found", "Addon을 찾지 못했습니다."),
                        "admin.addons.page",
                        Map.of("page", Integer.toString(Math.max(0, listPage))),
                        "admin.dashboard.open",
                        Map.of());
                    return;
                }
                openSync(plugin, player, session, addon.get(), messages, requestedPage, listPage);
            })
            .exceptionally(error -> {
                GuiStateMenus.openError(plugin, player, session, messages,
                    message(messages, TITLE_KEY, TITLE),
                    message(messages, "admin-addon-feature-menu-load-failed", "Addon 기능을 불러오지 못했습니다."),
                    "admin.addons.features.page",
                    Map.of(
                        "addonId", addonId,
                        "page", Integer.toString(Math.max(0, requestedPage)),
                        "listPage", Integer.toString(Math.max(0, listPage))
                    ),
                    "admin.addons.page",
                    Map.of("page", Integer.toString(Math.max(0, listPage))));
                return null;
            });
    }

    private static void openSync(Plugin plugin, Player player, GuiSession session, CloudIslandsAddonSnapshot addon,
                                 MessageRenderer messages, int requestedPage, int listPage) {
        GuiSessions.runIfCurrent(plugin, player, session, () -> {
            List<FeatureEntry> entries = featureEntries(addon);
            List<Integer> slots = GuiMenuRenderer.slots(MENU, "_");
            int pageSize = Math.max(1, slots.size());
            int maxPage = Math.max(0, (entries.size() - 1) / pageSize);
            int page = Math.max(0, Math.min(requestedPage, maxPage));
            String addonName = safeLine(addon.displayName().isBlank() ? addon.id() : addon.displayName(), 14);
            String title = message(messages, TITLE_KEY, TITLE) + " " + addonName + " " + (page + 1) + "/" + (maxPage + 1);
            Inventory inventory = GuiMenuRenderer.render(MENU, session, messages, title,
                item -> !List.of("_", "E", "P", "N").contains(item.symbol()));
            int offset = page * pageSize;
            for (int index = 0; index < pageSize && offset + index < entries.size(); index++) {
                inventory.setItem(slots.get(index), featureItem(addon, entries.get(offset + index), messages, page, listPage));
            }
            if (entries.isEmpty()) {
                GuiMenuRenderer.setSymbolItem(inventory, MENU, "E", messages, Map.of(), List.of());
            }
            if (page > 0) {
                setFeaturePageItem(inventory, "P", addon.id(), page - 1, listPage, messages, List.of());
            }
            if (page < maxPage) {
                setFeaturePageItem(inventory, "N", addon.id(), page + 1, listPage, messages, List.of());
            }
            setFeaturePageItem(inventory, "R", addon.id(), page, listPage, messages, List.of(
                message(messages, "admin-addon-feature-menu-addon-state-prefix", "Addon 상태: ")
                    + onOff(addon.enabled(), messages),
                message(messages, "admin-addon-feature-menu-refresh-action", "클릭: 새로고침")
            ));
            GuiMenuRenderer.setSymbolItem(inventory, MENU, "L", messages,
                Map.of("page", Integer.toString(Math.max(0, listPage))), List.of());
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
        if (actionId.equals("admin.addons.feature.toggle.prepare") && click != GuiClick.LEFT) {
            return;
        }
        if (!click.supported() || actionId.isBlank()) {
            return;
        }
        player.closeInventory();
        actions.execute(player, GuiActions.from(actionId, GuiItems.data(event.getCurrentItem())).orElse(null), click);
    }

    static List<FeatureEntry> featureEntries(CloudIslandsAddonSnapshot addon) {
        if (addon == null) {
            return List.of();
        }
        Set<String> keys = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        keys.addAll(addon.configuredFeatures().keySet());
        keys.addAll(addon.features().keySet());
        keys.addAll(addon.featureDependencies().keySet());
        addon.featureAliases().forEach((alias, canonical) -> keys.add(canonical));
        Set<String> canonicalKeys = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        keys.stream().map(addon::canonicalFeatureKey).filter(key -> !key.isBlank()).forEach(canonicalKeys::add);
        return canonicalKeys.stream()
            .map(key -> new FeatureEntry(
                key,
                addon.configuredFeatureEnabled(key, true),
                addon.acceptsRuntimeFeature(key, true),
                addon.featureDependencies().getOrDefault(key, "")
            ))
            .toList();
    }

    static List<String> featureLore(FeatureEntry feature, MessageRenderer messages) {
        List<String> lore = new ArrayList<>();
        lore.add(message(messages, "admin-addon-feature-menu-key-prefix", "기능: ") + safeLine(feature.key(), 48));
        lore.add(message(messages, "admin-addon-feature-menu-configured-prefix", "구성 상태: ")
            + onOff(feature.configured(), messages));
        lore.add(message(messages, "admin-addon-feature-menu-effective-prefix", "실효 상태: ")
            + onOff(feature.effective(), messages));
        if (!feature.dependency().isBlank()) {
            lore.add(message(messages, "admin-addon-feature-menu-dependency-prefix", "필요 기능: ")
                + safeLine(feature.dependency(), 48));
        }
        lore.add(message(messages, feature.configured()
                ? "admin-addon-feature-menu-disable-action"
                : "admin-addon-feature-menu-enable-action",
            feature.configured() ? "좌클릭: 비활성화 확인" : "좌클릭: 활성화 확인"));
        return List.copyOf(lore);
    }

    private static ItemStack featureItem(CloudIslandsAddonSnapshot addon, FeatureEntry feature,
                                         MessageRenderer messages, int page, int listPage) {
        String symbol = !feature.configured() ? "D" : feature.effective() ? "A" : "W";
        String fallback = !feature.configured() ? "GRAY_DYE" : feature.effective() ? "LIME_DYE" : "YELLOW_DYE";
        return GuiItems.action(
            GuiMenuRenderer.material(MENU, symbol, fallback),
            safeLine(feature.key(), 48),
            "admin.addons.feature.toggle.prepare",
            Map.of(
                "addonId", addon.id(),
                "feature", feature.key(),
                "enable", Boolean.toString(!feature.configured()),
                "page", Integer.toString(Math.max(0, page)),
                "listPage", Integer.toString(Math.max(0, listPage))
            ),
            featureLore(feature, messages).toArray(String[]::new)
        );
    }

    private static void setFeaturePageItem(Inventory inventory, String symbol, String addonId, int page, int listPage,
                                           MessageRenderer messages, List<String> lore) {
        GuiMenuRenderer.setSymbolItem(inventory, MENU, symbol, messages, Map.of(
            "addonId", addonId,
            "page", Integer.toString(Math.max(0, page)),
            "listPage", Integer.toString(Math.max(0, listPage))
        ), lore);
    }

    private static String onOff(boolean enabled, MessageRenderer messages) {
        return message(messages, enabled ? "admin-addon-menu-on" : "admin-addon-menu-off", enabled ? "ON" : "OFF");
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

    record FeatureEntry(String key, boolean configured, boolean effective, String dependency) {
        FeatureEntry {
            key = key == null ? "" : key.trim();
            dependency = dependency == null ? "" : dependency.trim();
        }
    }
}
