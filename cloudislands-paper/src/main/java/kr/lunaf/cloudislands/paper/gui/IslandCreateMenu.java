package kr.lunaf.cloudislands.paper.gui;

import java.util.ArrayList;
import java.util.List;
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

public final class IslandCreateMenu implements Listener {
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
        client.listTemplates()
            .thenAccept(body -> openSync(plugin, player, templates(body), messages))
            .exceptionally(error -> {
                openSync(plugin, player, List.of(new Template("default", message(messages, "create-menu-default-template", "기본 섬"), true, "")), messages);
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
        ItemMeta meta = event.getCurrentItem().getItemMeta();
        if (meta == null || meta.getLore() == null) {
            return;
        }
        if (message(messages, "create-menu-refresh-name", "템플릿 새로고침").equals(meta.getDisplayName())) {
            player.closeInventory();
            player.performCommand("섬 생성메뉴");
            return;
        }
        if (message(messages, "create-menu-main-menu-name", "메인 메뉴").equals(meta.getDisplayName())) {
            player.closeInventory();
            player.performCommand("섬 메뉴");
            return;
        }
        String templateId = "";
        for (String line : meta.getLore()) {
            if (line.startsWith("templateId=")) {
                templateId = line.substring("templateId=".length());
                break;
            }
        }
        if (templateId.isBlank()) {
            return;
        }
        player.closeInventory();
        player.performCommand("섬 생성 " + templateId);
    }

    private static void openSync(Plugin plugin, Player player, List<Template> templates, MessageRenderer messages) {
        kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers.run(plugin, () -> {
            Inventory inventory = Bukkit.createInventory(null, 27, message(messages, TITLE_KEY, TITLE));
            List<Template> enabled = templates.stream().filter(Template::enabled).limit(14).toList();
            if (enabled.isEmpty()) {
                enabled = List.of(new Template("default", message(messages, "create-menu-default-template", "기본 섬"), true, ""));
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

    private static ItemStack item(Template template, MessageRenderer messages) {
        ItemStack item = new ItemStack(Material.OAK_SAPLING);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(template.displayName().isBlank() ? template.id() : template.displayName());
            List<String> lore = new ArrayList<>();
            lore.add("templateId=" + template.id());
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
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(List.of(lore));
            item.setItemMeta(meta);
        }
        return item;
    }

    private static List<Template> templates(String body) {
        List<Template> templates = new ArrayList<>();
        int index = 0;
        while (body != null && index < body.length()) {
            int objectStart = body.indexOf('{', index);
            if (objectStart < 0) {
                break;
            }
            int objectEnd = body.indexOf('}', objectStart);
            if (objectEnd < 0) {
                break;
            }
            String object = body.substring(objectStart, objectEnd + 1);
            String id = text(object, "id");
            if (!id.isBlank()) {
                templates.add(new Template(id, text(object, "displayName"), bool(object, "enabled", true), text(object, "minNodeVersion")));
            }
            index = objectEnd + 1;
        }
        return templates;
    }

    private static String text(String body, String key) {
        String needle = "\"" + key + "\":\"";
        int start = body.indexOf(needle);
        if (start < 0) {
            return "";
        }
        start += needle.length();
        int end = jsonStringEnd(body, start);
        if (end < start) {
            return "";
        }
        return unescape(body.substring(start, end));
    }

    private static boolean bool(String body, String key, boolean fallback) {
        String needle = "\"" + key + "\":";
        int start = body.indexOf(needle);
        if (start < 0) {
            return fallback;
        }
        start += needle.length();
        return body.startsWith("true", start) || (!body.startsWith("false", start) && fallback);
    }

    private static int jsonStringEnd(String body, int start) {
        boolean escaped = false;
        for (int i = start; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c == '"' && !escaped) {
                return i;
            }
            escaped = c == '\\' && !escaped;
            if (c != '\\') {
                escaped = false;
            }
        }
        return -1;
    }

    private static String unescape(String value) {
        return value.replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private record Template(String id, String displayName, boolean enabled, String minNodeVersion) {
    }
}
