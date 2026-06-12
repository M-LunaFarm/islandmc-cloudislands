package kr.lunaf.cloudislands.paper.session;

import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public final class PaperChatListener implements Listener {
    private final Component prefix;

    public PaperChatListener(String serviceName) {
        String name = serviceName == null || serviceName.isBlank() ? "CloudIslands" : serviceName;
        this.prefix = Component.text("[" + name + "] ");
    }

    @EventHandler
    public void onAsyncChat(AsyncChatEvent event) {
        event.renderer(ChatRenderer.viewerUnaware((source, sourceDisplayName, message) ->
            prefix.append(sourceDisplayName).append(Component.text(": ")).append(message)
        ));
    }
}
