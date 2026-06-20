package kr.lunaf.cloudislands.paper.gui;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class GuiSessions {
    private static final AtomicLong REVISIONS = new AtomicLong();
    private static final ConcurrentMap<UUID, GuiSession> CURRENT = new ConcurrentHashMap<>();

    private GuiSessions() {
    }

    public static GuiSession begin(Player player, String menuId) {
        GuiSession session = new GuiSession(UUID.randomUUID(), player.getUniqueId(), menuId, REVISIONS.incrementAndGet());
        CURRENT.put(player.getUniqueId(), session);
        return session;
    }

    public static boolean isCurrent(Player player, GuiSession session) {
        return player != null && session != null && session.equals(CURRENT.get(player.getUniqueId()));
    }

    public static void runIfCurrent(Plugin plugin, Player player, GuiSession session, Runnable action) {
        kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers.run(plugin, () -> {
            if (isCurrent(player, session)) {
                action.run();
            }
        });
    }

    public static void invalidate(Player player) {
        if (player != null) {
            CURRENT.remove(player.getUniqueId());
        }
    }

    public static void invalidate(Player player, UUID sessionId) {
        if (player == null || sessionId == null) {
            return;
        }
        CURRENT.computeIfPresent(player.getUniqueId(), (_uuid, session) -> session.sessionId().equals(sessionId) ? null : session);
    }
}
