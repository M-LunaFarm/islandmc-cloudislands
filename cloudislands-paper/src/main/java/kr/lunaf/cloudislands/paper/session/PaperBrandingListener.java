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

    public PaperBrandingListener(Plugin plugin, MessageRenderer messages) {
        this.plugin = plugin;
        this.messages = messages;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        applyTabList(event.getPlayer());
        refreshTabList();
        event.joinMessage(messages.component("join-message", "player", event.getPlayer().getName()));
        String brand = messages.plain("server-brand");
        if (!brand.isBlank()) {
            kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers.runLater(plugin, () ->
                event.getPlayer().sendPluginMessage(plugin, "minecraft:brand", brandPayload(brand)), 10L);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        event.quitMessage(messages.component("quit-message", "player", event.getPlayer().getName()));
        kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers.run(plugin, this::refreshTabList);
    }

    private void refreshTabList() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            applyTabList(player);
        }
    }

    private void applyTabList(Player player) {
        player.sendPlayerListHeaderAndFooter(messages.component("tab-header"), messages.component("tab-footer"));
        Component playerName = messages.component("tab-player-name", "player", player.getName());
        player.playerListName(playerName);
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
