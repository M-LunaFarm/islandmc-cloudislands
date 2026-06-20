package kr.lunaf.cloudislands.paper.session;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public final class PaperChatListener implements Listener {
    private final MessageRenderer messages;

    public PaperChatListener(MessageRenderer messages) {
        this.messages = messages;
    }

    @EventHandler
    public void onAsyncChat(AsyncChatEvent event) {
        event.renderer((source, sourceDisplayName, message, viewer) ->
            chatLine(viewerLocale(viewer), sourceDisplayName, message)
        );
    }

    private Component chatLine(String locale, Component playerName, Component chatMessage) {
        String format = messages.plainForLocale(locale, "chat-format", "prefix", messages.plainForLocale(locale, "chat-prefix"));
        if (format.isBlank()) {
            format = "{prefix}{player}: {message}";
        }
        Component line = Component.empty();
        int index = 0;
        boolean messageInserted = false;
        while (index < format.length()) {
            int playerToken = format.indexOf("{player}", index);
            int messageToken = format.indexOf("{message}", index);
            int nextToken = nextToken(playerToken, messageToken);
            if (nextToken < 0) {
                line = line.append(Component.text(format.substring(index)));
                break;
            }
            if (nextToken > index) {
                line = line.append(Component.text(format.substring(index, nextToken)));
            }
            if (nextToken == playerToken) {
                line = line.append(playerName);
                index = nextToken + "{player}".length();
            } else {
                line = line.append(chatMessage);
                messageInserted = true;
                index = nextToken + "{message}".length();
            }
        }
        return messageInserted ? line : line.append(Component.text(" ")).append(chatMessage);
    }

    private String viewerLocale(Audience viewer) {
        return viewer instanceof Player player ? player.getLocale() : "";
    }

    private int nextToken(int playerToken, int messageToken) {
        if (playerToken < 0) {
            return messageToken;
        }
        if (messageToken < 0) {
            return playerToken;
        }
        return Math.min(playerToken, messageToken);
    }
}
