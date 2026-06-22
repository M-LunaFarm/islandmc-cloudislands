package kr.lunaf.cloudislands.paper.command;

import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers;
import kr.lunaf.cloudislands.paper.session.PlayerLocaleCache;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

final class IslandCommandMessenger {
    private final Plugin plugin;
    private final MessageRenderer messages;
    private final PlayerLocaleCache locales;

    IslandCommandMessenger(Plugin plugin, MessageRenderer messages, PlayerLocaleCache locales) {
        this.plugin = plugin;
        this.messages = messages;
        this.locales = locales;
    }

    MessageRenderer messagesFor(Player player) {
        return messages == null || player == null ? messages : messages.forLocale(locales == null ? player.getLocale() : locales.locale(player));
    }

    String routeMessage(String key, String fallback, String... variables) {
        String rendered = messages == null ? "" : messages.plain(key, variables);
        return rendered.isBlank() ? fallback : rendered;
    }

    String routeMessage(Player player, String key, String fallback, String... variables) {
        MessageRenderer playerMessages = messagesFor(player);
        String rendered = playerMessages == null ? "" : playerMessages.plain(key, variables);
        return rendered.isBlank() ? fallback : rendered;
    }

    String playerCodeMessage(String code, String fallback) {
        return IslandCommandMessages.playerCodeMessage(code, fallback);
    }

    String playerMessage(String message) {
        return IslandCommandRuntimeSupport.playerMessage(message);
    }

    void message(Player player, String message) {
        PaperSchedulers.run(plugin, () -> player.sendMessage(playerMessage(message)));
    }
}
