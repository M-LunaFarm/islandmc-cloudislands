package kr.lunaf.cloudislands.paper.session;

import java.nio.charset.StandardCharsets;
import net.kyori.adventure.text.Component;
import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

public final class PaperBrandingListener implements Listener {
    private final Plugin plugin;
    private final MessageRenderer messages;
    private final PlayerLocaleCache locales;

    public PaperBrandingListener(Plugin plugin, MessageRenderer messages) {
        this(plugin, messages, null);
    }

    public PaperBrandingListener(Plugin plugin, MessageRenderer messages, PlayerLocaleCache locales) {
        this.plugin = plugin;
        this.messages = messages;
        this.locales = locales;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        applyTabList(player);
        refreshTabList();
        event.joinMessage(messages.componentForLocale(locale(player), "join-message", "player", player.getName()));
        String brand = messages.plainForLocale(locale(player), "server-brand");
        if (!brand.isBlank()) {
            kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers.runLater(plugin, () ->
                player.sendPluginMessage(plugin, "minecraft:brand", brandPayload(brand)), 10L);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        event.quitMessage(messages.componentForLocale(locale(player), "quit-message", "player", player.getName()));
        kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers.run(plugin, this::refreshTabList);
    }

    private void refreshTabList() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            applyTabList(player);
        }
    }

    private void applyTabList(Player player) {
        String locale = locale(player);
        player.sendPlayerListHeaderAndFooter(messages.componentForLocale(locale, "tab-header"), messages.componentForLocale(locale, "tab-footer"));
        Component playerName = messages.componentForLocale(locale, "tab-player-name", "player", player.getName());
        player.playerListName(playerName);
    }

    private String locale(Player player) {
        return locales == null ? player.getLocale() : locales.locale(player);
    }

    private byte[] brandPayload(String brand) {
        byte[] value = brand.getBytes(StandardCharsets.UTF_8);
        byte[] length = varInt(value.length);
        byte[] payload = new byte[length.length + value.length];
        System.arraycopy(length, 0, payload, 0, length.length);
        System.arraycopy(value, 0, payload, length.length, value.length);
        return payload;
    }

    private byte[] varInt(int value) {
        byte[] buffer = new byte[5];
        int index = 0;
        int current = value;
        do {
            byte part = (byte) (current & 0x7F);
            current >>>= 7;
            if (current != 0) {
                part |= 0x80;
            }
            buffer[index++] = part;
        } while (current != 0 && index < buffer.length);
        byte[] result = new byte[index];
        System.arraycopy(buffer, 0, result, 0, index);
        return result;
    }
}
