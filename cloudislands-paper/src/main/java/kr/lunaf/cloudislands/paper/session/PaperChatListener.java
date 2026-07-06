package kr.lunaf.cloudislands.paper.session;

import io.papermc.paper.event.player.AsyncChatEvent;
import kr.lunaf.cloudislands.paper.AdminChatSpyRegistry;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public final class PaperChatListener implements Listener {
    private final MessageRenderer messages;
    private final PlayerLocaleCache locales;
    private final AdminChatSpyRegistry adminChatSpies;

    public PaperChatListener(MessageRenderer messages) {
        this(messages, null);
    }

    public PaperChatListener(MessageRenderer messages, PlayerLocaleCache locales) {
        this(messages, locales, null);
    }

    public PaperChatListener(MessageRenderer messages, PlayerLocaleCache locales, AdminChatSpyRegistry adminChatSpies) {
        this.messages = messages;
        this.locales = locales;
        this.adminChatSpies = adminChatSpies;
    }

    @EventHandler
    public void onAsyncChat(AsyncChatEvent event) {
        event.renderer((source, sourceDisplayName, message, viewer) ->
            chatLine(viewerLocale(viewer), sourceDisplayName, message)
        );
        sendAdminSpyLine(event);
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
        if (!(viewer instanceof Player player)) {
            return "";
        }
        return locales == null ? player.getLocale() : locales.locale(player);
    }

    private void sendAdminSpyLine(AsyncChatEvent event) {
        if (adminChatSpies == null) {
            return;
        }
        Player source = event.getPlayer();
        for (Audience viewer : event.viewers()) {
            if (!(viewer instanceof Player player) || player.getUniqueId().equals(source.getUniqueId())) {
                continue;
            }
            if (!adminChatSpies.enabled(player)) {
                continue;
            }
            if (!player.hasPermission("cloudislands.admin.spy")) {
                adminChatSpies.clear(player.getUniqueId());
                continue;
            }
            player.sendMessage(spyLine(viewerLocale(player), source.displayName(), event.message()));
        }
    }

    private Component spyLine(String locale, Component playerName, Component chatMessage) {
        String prefix = messages.plainForLocale(locale, "admin-chat-spy-prefix", "channel", "GLOBAL");
        if (prefix.isBlank()) {
            prefix = "[Spy:GLOBAL] ";
        }
        return Component.text(prefix)
            .append(playerName)
            .append(Component.text(": "))
            .append(chatMessage);
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
