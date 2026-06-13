package kr.lunaf.cloudislands.paper.session;

import net.kyori.adventure.text.Component;
import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PaperBrandingListener implements Listener {
    private final MessageRenderer messages;

    public PaperBrandingListener(MessageRenderer messages) {
        this.messages = messages;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        event.getPlayer().sendPlayerListHeaderAndFooter(messages.component("tab-header"), messages.component("tab-footer"));
        event.joinMessage(messages.component("join-message", "player", event.getPlayer().getName()));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        event.quitMessage(messages.component("quit-message", "player", event.getPlayer().getName()));
    }
}
