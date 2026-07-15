package kr.lunaf.cloudislands.paper.session;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
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
            runIfStillOnline(event.getPlayer(), activePlayer -> sendTeamChat(activePlayer, message));
            return;
        }
        if (islandChatEnabled(event)) {
            boolean alreadyCancelled = event.isCancelled();
            isolateTeamChat(event);
            if (alreadyCancelled) {
                return;
            }
            String message = PlainTextComponentSerializer.plainText().serialize(event.message());
            runIfStillOnline(event.getPlayer(), activePlayer -> sendLocalChat(activePlayer, message));
            return;
        }
        event.renderer((source, sourceDisplayName, message, viewer) ->
            chatLine(viewerLocale(viewer), sourceDisplayName, message)
        );
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void enforceTeamChatIsolation(AsyncChatEvent event) {
        if (teamChatEnabled(event) || islandChatEnabled(event)) {
            isolateTeamChat(event);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAcceptedGlobalChat(AsyncChatEvent event) {
        if (!teamChatEnabled(event) && !islandChatEnabled(event)) {
            scheduleAdminSpyLine(event);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (teamChatModes != null) {
            teamChatModes.clear(event.getPlayer().getUniqueId());
        }
    }

    private void sendTeamChat(Player player, String message) {
        UUID playerUuid = player.getUniqueId();
        protection.islandAt(player.getLocation().getBlock()).ifPresentOrElse(islandId ->
            coreApiClient.communicationCommands().sendChat(islandId, playerUuid, "TEAM", message)
                .exceptionally(error -> {
                    deliverChatFailure(playerUuid, "팀 채팅을 전송하지 못했습니다.");
                    return null;
                }),
            () -> player.sendMessage(Component.text("섬 안에서만 팀 채팅 모드를 사용할 수 있습니다."))
        );
    }

    private void sendLocalChat(Player player, String message) {
        UUID playerUuid = player.getUniqueId();
        protection.islandAt(player.getLocation().getBlock()).ifPresentOrElse(islandId ->
            coreApiClient.communicationCommands().sendChat(islandId, playerUuid, "ISLAND", message)
                .exceptionally(error -> {
                    deliverChatFailure(playerUuid, "섬 채팅을 전송하지 못했습니다.");
                    return null;
                }),
            () -> player.sendMessage(Component.text("섬 안에서만 로컬 채팅 모드를 사용할 수 있습니다."))
        );
    }

    private void deliverChatFailure(UUID playerUuid, String message) {
        PaperSchedulers.run(plugin, () -> {
            Player activePlayer = plugin.getServer().getPlayer(playerUuid);
            if (activePlayer != null && activePlayer.isOnline()) {
                activePlayer.sendMessage(Component.text(message));
            }
        });
    }

    private boolean teamChatEnabled(AsyncChatEvent event) {
        return teamChatModes != null
            && teamChatModes.enabled(event.getPlayer().getUniqueId())
            && plugin != null
            && coreApiClient != null
            && protection != null;
    }

    private boolean islandChatEnabled(AsyncChatEvent event) {
        return teamChatModes != null
            && teamChatModes.islandEnabled(event.getPlayer().getUniqueId())
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

    private void scheduleAdminSpyLine(AsyncChatEvent event) {
        if (adminChatSpies == null || plugin == null) {
            return;
        }
        Player source = event.getPlayer();
        UUID sourceUuid = source.getUniqueId();
        Component sourceDisplayName = source.displayName();
        Component chatMessage = event.message();
        List<ViewerIdentity> viewers = event.viewers().stream()
            .filter(Player.class::isInstance)
            .map(Player.class::cast)
            .filter(player -> !player.getUniqueId().equals(sourceUuid))
            .map(player -> new ViewerIdentity(player.getUniqueId(), player))
            .toList();
        PaperSchedulers.run(plugin, () -> viewers.forEach(identity -> {
            Player player = plugin.getServer().getPlayer(identity.playerUuid());
            if (!ChatPlayerIdentityPolicy.isCurrent(identity.expectedPlayer(), player)) {
                return;
            }
            if (!adminChatSpies.enabled(player)) {
                return;
            }
            if (!player.hasPermission("cloudislands.admin.spy")) {
                adminChatSpies.clear(player.getUniqueId());
                return;
            }
            player.sendMessage(spyLine(viewerLocale(player), sourceDisplayName, chatMessage));
        }));
    }

    private void runIfStillOnline(Player expectedPlayer, Consumer<Player> action) {
        UUID playerUuid = expectedPlayer.getUniqueId();
        PaperSchedulers.run(plugin, () -> {
            Player activePlayer = plugin.getServer().getPlayer(playerUuid);
            if (ChatPlayerIdentityPolicy.isCurrent(expectedPlayer, activePlayer)) {
                action.accept(activePlayer);
            }
        });
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

    private record ViewerIdentity(UUID playerUuid, Player expectedPlayer) {
    }
}
