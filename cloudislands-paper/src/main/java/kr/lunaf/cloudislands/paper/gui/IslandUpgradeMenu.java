package kr.lunaf.cloudislands.paper.gui;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.paper.application.view.PaperGuiViews;
import kr.lunaf.cloudislands.paper.application.view.PaperGuiViews.UpgradeView;
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

public final class IslandUpgradeMenu implements Listener {
    private static final String TITLE = "섬 업그레이드";
    private static final String MENU_ID = "island.upgrades";
    private final MessageRenderer messages;
    private final GuiActionRegistry actions;

    public IslandUpgradeMenu() {
        this(null);
    }

    public IslandUpgradeMenu(MessageRenderer messages) {
        this(messages, new GuiActionRegistry(GuiActionExecutor.noop()));
    }

    public IslandUpgradeMenu(MessageRenderer messages, GuiActionRegistry actions) {
        this.messages = messages;
        this.actions = actions == null ? new GuiActionRegistry(GuiActionExecutor.noop()) : actions;
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player, UUID islandId) {
        open(plugin, client, player, islandId, null);
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player, UUID islandId, MessageRenderer messages) {
        GuiSession session = GuiSessions.begin(player, MENU_ID);
        GuiStateMenus.openLoading(plugin, player, session, messages, TITLE);
        PaperGuiViews.islandUpgrades(client, islandId)
            .thenAccept(upgrades -> openSync(plugin, player, session, upgrades, messages))
            .exceptionally(error -> {
                GuiStateMenus.openError(plugin, player, session, messages, TITLE, message(messages, "upgrade-menu-load-failed", "섬 업그레이드를 불러오지 못했습니다."), "island.upgrades.open", "island.settings.open");
                return null;
            });
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!GuiInventories.isMenu(event.getView().getTopInventory(), MENU_ID)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getCurrentItem() == null || !GuiItems.topInventoryClick(event)) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 54) {
            return;
        }
        player.closeInventory();
        if (slot == 45) {
            actions.execute(player, "island.bank.open", GuiClick.from(event));
            return;
        }
        if (slot == 49) {
            actions.execute(player, "island.upgrades.open", GuiClick.from(event));
            return;
        }
        if (slot == 53) {
            actions.execute(player, "island.settings.open", GuiClick.from(event));
            return;
        }
        String key = GuiItems.data(event.getCurrentItem()).getOrDefault("upgradeKey", "");
        if (key.isBlank()) {
            return;
        }
        actions.execute(player, "island.upgrade.purchase", java.util.Map.of("upgradeKey", key), GuiClick.from(event));
    }

    private static void openSync(Plugin plugin, Player player, GuiSession session, List<UpgradeView> upgrades, MessageRenderer messages) {
        GuiSessions.runIfCurrent(plugin, player, session, () -> {
            Inventory inventory = GuiInventories.create(MENU_ID, 54, TITLE);
            int slot = 0;
            for (UpgradeView upgrade : upgrades.stream().limit(45).toList()) {
                inventory.setItem(slot++, upgradeItem(upgrade, messages));
            }
            if (upgrades.isEmpty()) {
                inventory.setItem(22, item(Material.BARRIER, message(messages, "upgrade-menu-empty-title", "업그레이드 없음"), message(messages, "upgrade-menu-empty", "Core API에 등록된 섬 업그레이드가 없습니다.")));
            }
            inventory.setItem(45, item(Material.GOLD_BLOCK, message(messages, "upgrade-menu-bank-name", "섬 은행"), message(messages, "upgrade-menu-bank-command", "/섬 은행")));
            inventory.setItem(49, item(Material.CLOCK, message(messages, "upgrade-menu-refresh-name", "새로고침"), message(messages, "upgrade-menu-refresh-command", "/섬 업그레이드")));
            inventory.setItem(53, item(Material.COMPARATOR, message(messages, "upgrade-menu-settings-name", "설정"), message(messages, "upgrade-menu-settings-command", "/섬 설정")));
            player.openInventory(inventory);
        });
    }

    private static ItemStack upgradeItem(UpgradeView upgrade, MessageRenderer messages) {
        Material material = switch (upgrade.type()) {
            case "ISLAND_SIZE" -> Material.GRASS_BLOCK;
            case "MAX_MEMBERS" -> Material.NAME_TAG;
            case "MAX_WARPS" -> Material.ENDER_PEARL;
            case "HOPPER_LIMIT" -> Material.HOPPER;
            case "SPAWNER_LIMIT" -> Material.SPAWNER;
            case "GENERATOR_LEVEL" -> Material.COBBLESTONE;
            case "MOB_LIMIT" -> Material.ZOMBIE_HEAD;
            case "CROP_GROWTH" -> Material.WHEAT;
            case "FLY_ACCESS" -> Material.FEATHER;
            case "REDSTONE_LIMIT" -> Material.REDSTONE;
            case "BANK_LIMIT" -> Material.GOLD_INGOT;
            default -> Material.BEACON;
        };
        return GuiItems.action(material, upgrade.key(), "island.upgrade.purchase",
            Map.of("upgradeKey", upgrade.key()),
            message(messages, "upgrade-menu-type", "유형: ") + upgrade.type(),
            message(messages, "upgrade-menu-current-level", "현재 레벨: ") + upgrade.level(),
            message(messages, "upgrade-menu-click-to-buy", "클릭하면 다음 레벨 구매를 요청합니다."));
    }

    private static String message(MessageRenderer messages, String key, String fallback) {
        if (messages == null) {
            return fallback;
        }
        String rendered = messages.plain(key);
        return rendered.isBlank() ? fallback : rendered;
    }

    private static ItemStack item(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(List.of(lore));
            item.setItemMeta(meta);
        }
        return item;
    }

}
