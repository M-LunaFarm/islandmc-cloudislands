package kr.lunaf.cloudislands.paper.gui;

import java.util.List;
import java.util.UUID;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
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

public final class IslandBankMenu implements Listener {
    private static final String TITLE_KEY = "bank-menu-title";
    private static final String TITLE = "섬 은행";
    private final MessageRenderer messages;

    public IslandBankMenu() {
        this(null);
    }

    public IslandBankMenu(MessageRenderer messages) {
        this.messages = messages;
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player, UUID islandId) {
        open(plugin, client, player, islandId, null);
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player, UUID islandId, MessageRenderer messages) {
        client.islandBank(islandId)
            .thenAccept(body -> openSync(plugin, player, text(body, "balance"), text(body, "updatedAt"), messages))
            .exceptionally(error -> {
                kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers.run(plugin, () -> player.sendMessage(message(messages, "bank-menu-load-failed", "섬 은행을 불러오지 못했습니다.")));
                return null;
            });
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!message(messages, TITLE_KEY, TITLE).equals(event.getView().getTitle())) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getCurrentItem() == null) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 27) {
            return;
        }
        player.closeInventory();
        if (slot == 13) {
            player.sendMessage(message(messages, "bank-menu-deposit-usage", "사용법: /섬 입금 <금액>"));
        } else if (slot == 10) {
            GuiActionRegistry.execute(player, "island.bank.deposit", java.util.Map.of("amount", "1000"), GuiClick.from(event));
        } else if (slot == 11) {
            GuiActionRegistry.execute(player, "island.bank.deposit", java.util.Map.of("amount", "10000"), GuiClick.from(event));
        } else if (slot == 17) {
            player.sendMessage(message(messages, "bank-menu-withdraw-usage", "사용법: /섬 출금 <금액>"));
        } else if (slot == 15) {
            GuiActionRegistry.execute(player, "island.bank.withdraw", java.util.Map.of("amount", "1000"), GuiClick.from(event));
        } else if (slot == 16) {
            GuiActionRegistry.execute(player, "island.bank.withdraw", java.util.Map.of("amount", "10000"), GuiClick.from(event));
        } else if (slot == 22) {
            GuiActionRegistry.execute(player, "island.bank.open", GuiClick.from(event));
        } else if (slot == 18) {
            GuiActionRegistry.execute(player, "island.main.open", GuiClick.from(event));
        } else if (slot == 26) {
            GuiActionRegistry.execute(player, "island.settings.open", GuiClick.from(event));
        }
    }

    private static void openSync(Plugin plugin, Player player, String balance, String updatedAt, MessageRenderer messages) {
        kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers.run(plugin, () -> {
            Inventory inventory = Bukkit.createInventory(null, 27, message(messages, TITLE_KEY, TITLE));
            inventory.setItem(4, item(Material.GOLD_BLOCK, message(messages, "bank-menu-balance-name", "잔액"), message(messages, "bank-menu-current-balance", "현재 잔액: ") + (balance.isBlank() ? "0" : balance), updatedAt.isBlank() ? message(messages, "bank-menu-no-update", "업데이트 정보 없음") : message(messages, "bank-menu-updated-at", "갱신 시각: ") + updatedAt));
            inventory.setItem(10, item(Material.EMERALD, message(messages, "bank-menu-deposit-1000-name", "1,000 입금"), message(messages, "bank-menu-deposit-1000-command", "/섬 입금 1000")));
            inventory.setItem(11, item(Material.EMERALD_BLOCK, message(messages, "bank-menu-deposit-10000-name", "10,000 입금"), message(messages, "bank-menu-deposit-10000-command", "/섬 입금 10000")));
            inventory.setItem(13, item(Material.PAPER, message(messages, "bank-menu-deposit-name", "입금"), message(messages, "bank-menu-deposit-usage", "사용법: /섬 입금 <금액>")));
            inventory.setItem(15, item(Material.REDSTONE, message(messages, "bank-menu-withdraw-1000-name", "1,000 출금"), message(messages, "bank-menu-withdraw-1000-command", "/섬 출금 1000")));
            inventory.setItem(16, item(Material.REDSTONE_BLOCK, message(messages, "bank-menu-withdraw-10000-name", "10,000 출금"), message(messages, "bank-menu-withdraw-10000-command", "/섬 출금 10000")));
            inventory.setItem(17, item(Material.PAPER, message(messages, "bank-menu-withdraw-name", "출금"), message(messages, "bank-menu-withdraw-usage", "사용법: /섬 출금 <금액>")));
            inventory.setItem(18, item(Material.COMPASS, message(messages, "bank-menu-main-menu-name", "메인 메뉴"), message(messages, "bank-menu-main-menu-command", "/섬 메뉴")));
            inventory.setItem(22, item(Material.CLOCK, message(messages, "bank-menu-refresh-name", "잔액 새로고침"), message(messages, "bank-menu-refresh-command", "/섬 은행")));
            inventory.setItem(26, item(Material.COMPARATOR, message(messages, "bank-menu-settings-name", "설정"), message(messages, "bank-menu-settings-command", "/섬 설정")));
            player.openInventory(inventory);
        });
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

    private static String text(String body, String key) {
        String needle = "\"" + key + "\":\"";
        int start = body == null ? -1 : body.indexOf(needle);
        if (start < 0) {
            return "";
        }
        start += needle.length();
        int end = body.indexOf('"', start);
        return end < start ? "" : body.substring(start, end).replace("\\\"", "\"").replace("\\\\", "\\");
    }
}
