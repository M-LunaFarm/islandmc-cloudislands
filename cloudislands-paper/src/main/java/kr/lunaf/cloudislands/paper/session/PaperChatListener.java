package kr.lunaf.cloudislands.paper.session;

import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public final class PaperChatListener implements Listener {
    private final MessageRenderer messages;

    public PaperChatListener(MessageRenderer messages) {
        this.messages = messages;
    }

    @EventHandler
    public void onAsyncChat(AsyncChatEvent event) {
        event.renderer(ChatRenderer.viewerUnaware((source, sourceDisplayName, message) ->
            messages.component("chat-prefix").append(sourceDisplayName).append(Component.text(": ")).append(message)
        ));
    }
}
