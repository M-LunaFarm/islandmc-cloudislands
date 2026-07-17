package kr.lunaf.cloudislands.paper.session;

import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;
import kr.lunaf.cloudislands.paper.PlayerConnectionSession;
import kr.lunaf.cloudislands.paper.RouteTicketConsumer;
import kr.lunaf.cloudislands.paper.activation.ActiveIslandRegistry;
import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

public final class DirectLocalJoinRecoveryListener implements Listener {
    private static final String MARKER_NAME = "direct_local_last_island";

    private final Plugin plugin;
    private final ActiveIslandRegistry activeIslands;
    private final RouteTicketConsumer routeTickets;
    private final String fallbackWorld;
    private final MessageRenderer messages;
    private final PlayerLocaleCache locales;
    private final NamespacedKey lastIslandKey;
    private final Logger logger;

    public DirectLocalJoinRecoveryListener(
        Plugin plugin,
        ActiveIslandRegistry activeIslands,
        RouteTicketConsumer routeTickets,
        String fallbackWorld,
        MessageRenderer messages,
        PlayerLocaleCache locales
    ) {
        this.plugin = plugin;
        this.activeIslands = activeIslands;
        this.routeTickets = routeTickets;
        this.fallbackWorld = fallbackWorld == null || fallbackWorld.isBlank() ? "world" : fallbackWorld.trim();
        this.messages = messages;
        this.locales = locales;
        this.lastIslandKey = new NamespacedKey(plugin, MARKER_NAME);
        this.logger = plugin.getLogger();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Optional<UUID> lastIslandId = lastIslandId(player);
        if (lastIslandId.isEmpty()) {
            return;
        }
        PlayerConnectionSession session = PlayerConnectionSession.capture(player);
        PaperSchedulers.runLater(plugin, () -> recoverIfStale(session, lastIslandId.get()), 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        rememberDestination(event.getPlayer(), event.getTo());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        rememberDestination(event.getPlayer(), event.getPlayer().getLocation());
    }

    private void recoverIfStale(PlayerConnectionSession session, UUID expectedIslandId) {
        Player player = plugin.getServer().getPlayer(session.playerUuid());
        if (!session.isCurrent(player)) {
            return;
        }
        Location current = player.getLocation();
        if (!DirectLocalJoinRecoveryPolicy.requiresFallback(
            expectedIslandId,
            current.getWorld().getName(),
            current.getX(),
            current.getZ(),
            activeIslands.snapshot()
        )) {
            return;
        }
        player.setFallDistance(0.0F);
        routeTickets.teleportToWorldSpawn(session, fallbackWorld).whenComplete((teleported, error) ->
            PaperSchedulers.run(plugin, () -> finishRecovery(session, expectedIslandId, teleported, error))
        );
    }

    private void finishRecovery(PlayerConnectionSession session, UUID expectedIslandId, Boolean teleported, Throwable error) {
        Player player = plugin.getServer().getPlayer(session.playerUuid());
        if (!session.isCurrent(player)) {
            return;
        }
        if (error != null || !Boolean.TRUE.equals(teleported)) {
            logger.warning("Failed to recover stale direct-local island login for " + session.playerUuid()
                + " from island " + expectedIslandId + " to fallback world " + fallbackWorld);
            player.sendActionBar(message(player, "direct-local-recovery-failed", "안전한 로비 위치로 복구하지 못했습니다. 관리자에게 문의해주세요."));
            return;
        }
        clearLastIsland(player);
        player.setFallDistance(0.0F);
        player.sendActionBar(message(player, "direct-local-recovery-success", "이전 섬 월드가 닫혀 있어 로비로 안전하게 이동했습니다. /섬 홈으로 다시 입장해주세요."));
        logger.info("Recovered stale direct-local island login for " + session.playerUuid()
            + " from island " + expectedIslandId + " to fallback world " + fallbackWorld);
    }

    private void rememberDestination(Player player, Location location) {
        if (player == null || location == null || location.getWorld() == null) {
            return;
        }
        DirectLocalJoinRecoveryPolicy.islandAt(
            location.getWorld().getName(),
            location.getX(),
            location.getZ(),
            activeIslands.snapshot()
        ).ifPresentOrElse(
            active -> player.getPersistentDataContainer().set(lastIslandKey, PersistentDataType.STRING, active.islandId().toString()),
            () -> clearLastIsland(player)
        );
    }

    private Optional<UUID> lastIslandId(Player player) {
        String encoded = player.getPersistentDataContainer().get(lastIslandKey, PersistentDataType.STRING);
        if (encoded == null || encoded.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(encoded));
        } catch (IllegalArgumentException invalid) {
            clearLastIsland(player);
            return Optional.empty();
        }
    }

    private void clearLastIsland(Player player) {
        player.getPersistentDataContainer().remove(lastIslandKey);
    }

    private Component message(Player player, String key, String fallback) {
        if (messages == null) {
            return Component.text(fallback);
        }
        String locale = locales == null ? PlayerLocaleCache.clientLocale(player) : locales.locale(player);
        return messages.componentForLocaleOrFallback(locale, key, fallback);
    }
}
