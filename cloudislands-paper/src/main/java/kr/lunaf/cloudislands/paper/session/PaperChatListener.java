package kr.lunaf.cloudislands.paper.session;

import io.papermc.paper.event.player.AsyncChatEvent;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.paper.AdminChatSpyRegistry;
import kr.lunaf.cloudislands.paper.ProtectionController;
import kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

public final class PaperChatListener implements Listener {
    private final MessageRenderer messages;
    private final PlayerLocaleCache locales;
    private final AdminChatSpyRegistry adminChatSpies;
    private final Plugin plugin;
    private final CoreApiClient coreApiClient;
    private final ProtectionController protection;
    private final TeamChatModeRegistry teamChatModes;

    public PaperChatListener(MessageRenderer messages) {
        this(messages, null);
    }

    public PaperChatListener(MessageRenderer messages, PlayerLocaleCache locales) {
        this(messages, locales, null);
    }

    public PaperChatListener(MessageRenderer messages, PlayerLocaleCache locales, AdminChatSpyRegistry adminChatSpies) {
        this(null, null, null, messages, locales, adminChatSpies, null);
    }

    public PaperChatListener(Plugin plugin, CoreApiClient coreApiClient, ProtectionController protection, MessageRenderer messages, PlayerLocaleCache locales, AdminChatSpyRegistry adminChatSpies, TeamChatModeRegistry teamChatModes) {
        this.plugin = plugin;
        this.coreApiClient = coreApiClient;
        this.protection = protection;
        this.messages = messages;
        this.locales = locales;
        this.adminChatSpies = adminChatSpies;
        this.teamChatModes = teamChatModes;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onAsyncChat(AsyncChatEvent event) {
        if (teamChatEnabled(event)) {
            boolean alreadyCancelled = event.isCancelled();
            isolateTeamChat(event);
            if (alreadyCancelled) {
                return;
            }
            String message = PlainTextComponentSerializer.plainText().serialize(event.message());
            PaperSchedulers.run(plugin, () -> sendTeamChat(event.getPlayer(), message));
            return;
        }
        event.renderer((source, sourceDisplayName, message, viewer) ->
            chatLine(viewerLocale(viewer), sourceDisplayName, message)
        );
        sendAdminSpyLine(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void enforceTeamChatIsolation(AsyncChatEvent event) {
        if (teamChatEnabled(event)) {
            isolateTeamChat(event);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (teamChatModes != null) {
            teamChatModes.clear(event.getPlayer().getUniqueId());
        }
    }

    private void sendTeamChat(Player player, String message) {
        protection.islandAt(player.getLocation().getBlock()).ifPresentOrElse(islandId ->
            coreApiClient.communicationCommands().sendChat(islandId, player.getUniqueId(), "TEAM", message)
                .exceptionally(error -> {
                    player.sendMessage(Component.text("팀 채팅을 전송하지 못했습니다."));
                    return null;
                }),
            () -> player.sendMessage(Component.text("섬 안에서만 팀 채팅 모드를 사용할 수 있습니다."))
        );
    }

    private boolean teamChatEnabled(AsyncChatEvent event) {
        return teamChatModes != null
            && teamChatModes.enabled(event.getPlayer().getUniqueId())
            && plugin != null
            && coreApiClient != null
            && protection != null;
    }

    private static void isolateTeamChat(AsyncChatEvent event) {
        event.setCancelled(true);
        event.viewers().clear();
        event.renderer((_source, _sourceDisplayName, _message, _viewer) -> Component.empty());
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
        return locales == null ? PlayerLocaleCache.clientLocale(player) : locales.locale(player);
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
