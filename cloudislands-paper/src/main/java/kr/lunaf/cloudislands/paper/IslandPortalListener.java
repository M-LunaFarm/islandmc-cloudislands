package kr.lunaf.cloudislands.paper;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import kr.lunaf.cloudislands.paper.session.PlayerLocaleCache;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class IslandPortalListener implements Listener {
    public static final String POLICY = "single-world-island-regions-deny-cross-dimension-portals-use-cloudislands-route-commands";
    private static final int PORTAL_RETRY_COOLDOWN_TICKS = 100;
    private static final long MESSAGE_COOLDOWN_MILLIS = 1_500L;

    private final ProtectionController protection;
    private final MessageRenderer messages;
    private final Map<UUID, Long> lastMessages = new ConcurrentHashMap<>();
    private final AtomicLong blockedPlayerPortals = new AtomicLong();
    private final AtomicLong blockedEntityPortals = new AtomicLong();

    public IslandPortalListener(ProtectionController protection, MessageRenderer messages) {
        if (protection == null) {
            throw new IllegalArgumentException("protection is required");
        }
        this.protection = protection;
        this.messages = messages;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerPortal(PlayerPortalEvent event) {
        if (!blocks(event.getFrom())) {
            return;
        }
        event.setCanCreatePortal(false);
        event.setCancelled(true);
        blockPlayer(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityPortal(EntityPortalEvent event) {
        if (!blocks(event.getFrom())) {
            return;
        }
        event.setCanCreatePortal(false);
        event.setCancelled(true);
        Entity entity = event.getEntity();
        if (entity instanceof Player player) {
            blockPlayer(player);
            return;
        }
        entity.setPortalCooldown(PORTAL_RETRY_COOLDOWN_TICKS);
        blockedEntityPortals.incrementAndGet();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastMessages.remove(event.getPlayer().getUniqueId());
    }

    boolean blocks(Location location) {
        return location != null && blocks(location.getWorld().getName(), location.getBlockX(), location.getBlockZ());
    }

    boolean blocks(String worldName, int blockX, int blockZ) {
        return protection.regionAt(worldName, blockX, blockZ).isPresent();
    }

    private void blockPlayer(Player player) {
        player.setPortalCooldown(PORTAL_RETRY_COOLDOWN_TICKS);
        blockedPlayerPortals.incrementAndGet();
        long now = System.currentTimeMillis();
        Long previous = lastMessages.put(player.getUniqueId(), now);
        if (previous != null && now - previous < MESSAGE_COOLDOWN_MILLIS) {
            return;
        }
        player.sendActionBar(component(player));
    }

    private Component component(Player player) {
        String fallback = "섬 월드에서는 차원 포털을 사용할 수 없습니다. 섬 홈 또는 워프 명령을 사용해주세요.";
        if (messages == null) {
            return Component.text(fallback);
        }
        return messages.componentForLocaleOrFallback(
            PlayerLocaleCache.clientLocale(player),
            "portal-cross-dimension-denied",
            fallback
        );
    }

    public long blockedPlayerPortals() {
        return blockedPlayerPortals.get();
    }

    public long blockedEntityPortals() {
        return blockedEntityPortals.get();
    }

    public String policy() {
        return POLICY;
    }
}
