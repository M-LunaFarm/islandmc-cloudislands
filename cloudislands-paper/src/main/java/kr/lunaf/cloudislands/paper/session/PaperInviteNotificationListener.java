package kr.lunaf.cloudislands.paper.session;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import kr.lunaf.cloudislands.api.model.IslandInviteSnapshot;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.paper.event.IslandInviteChangeEvent;
import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

/** Delivers durable Core invite events as actionable messages on the player's current Paper node. */
public final class PaperInviteNotificationListener implements Listener {
    private static final int MAX_JOIN_NOTIFICATIONS = 5;
    private static final long DEDUPLICATION_MILLIS = 5 * 60 * 1_000L;

    private final Plugin plugin;
    private final CoreApiClient client;
    private final MessageRenderer messages;
    private final PlayerLocaleCache locales;
    private final Map<UUID, Long> recentlyNotified = new ConcurrentHashMap<>();

    public PaperInviteNotificationListener(Plugin plugin, CoreApiClient client, MessageRenderer messages, PlayerLocaleCache locales) {
        this.plugin = java.util.Objects.requireNonNull(plugin, "plugin");
        this.client = java.util.Objects.requireNonNull(client, "client");
        this.messages = java.util.Objects.requireNonNull(messages, "messages");
        this.locales = locales;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        UUID playerUuid = event.getPlayer().getUniqueId();
        PaperSchedulers.runLater(plugin, () -> loadPending(playerUuid, null, true), 20L);
    }

    @EventHandler
    public void onInviteChanged(IslandInviteChangeEvent event) {
        UUID inviteId = event.inviteId();
        if (!"PENDING".equalsIgnoreCase(event.state())) {
            if (inviteId != null) {
                recentlyNotified.remove(inviteId);
            }
            return;
        }
        if (inviteId == null || event.targetUuid() == null) {
            return;
        }
        loadPending(event.targetUuid(), inviteId, false);
    }

    private void loadPending(UUID playerUuid, UUID expectedInviteId, boolean joinReminder) {
        client.members().inviteSnapshots(playerUuid)
            .thenAccept(invites -> PaperSchedulers.run(plugin, () -> deliver(playerUuid, pending(invites, expectedInviteId), joinReminder)))
            .exceptionally(error -> null);
    }

    private void deliver(UUID playerUuid, List<IslandInviteSnapshot> invites, boolean joinReminder) {
        Player player = plugin.getServer().getPlayer(playerUuid);
        if (player == null || !player.isOnline() || invites.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        recentlyNotified.entrySet().removeIf(entry -> entry.getValue() <= now);
        int eligible = 0;
        int delivered = 0;
        for (IslandInviteSnapshot invite : invites) {
            if (!markForDelivery(invite.inviteId(), now)) {
                continue;
            }
            eligible++;
            if (delivered >= MAX_JOIN_NOTIFICATIONS) {
                continue;
            }
            player.sendMessage(notification(messages, locale(player), invite));
            delivered++;
        }
        int remaining = eligible - delivered;
        if (joinReminder && remaining > 0) {
            player.sendMessage(moreNotification(messages, locale(player), remaining));
        }
    }

    private boolean markForDelivery(UUID inviteId, long now) {
        if (inviteId == null) {
            return false;
        }
        Long previous = recentlyNotified.putIfAbsent(inviteId, now + DEDUPLICATION_MILLIS);
        if (previous == null || previous <= now) {
            recentlyNotified.put(inviteId, now + DEDUPLICATION_MILLIS);
            return true;
        }
        return false;
    }

    static Component notification(MessageRenderer messages, String locale, IslandInviteSnapshot invite) {
        String inviteId = invite.inviteId().toString();
        String island = compact(invite.islandId());
        Component prefix = messages.componentForLocaleOrFallback(locale, "invite-notification", "새 섬 초대가 도착했습니다. 섬={island}", "island", island);
        return prefix
            .append(Component.space())
            .append(action(messages, locale, "invite-notification-accept", "[수락]", NamedTextColor.GREEN, "/is accept " + inviteId))
            .append(Component.space())
            .append(action(messages, locale, "invite-notification-decline", "[거절]", NamedTextColor.RED, "/is decline " + inviteId))
            .append(Component.space())
            .append(action(messages, locale, "invite-notification-view", "[목록]", NamedTextColor.AQUA, "/is invites"));
    }

    static Component moreNotification(MessageRenderer messages, String locale, int remaining) {
        Component prefix = messages.componentForLocaleOrFallback(locale, "invite-notification-more", "표시하지 않은 초대가 {count}개 더 있습니다.", "count", Integer.toString(Math.max(0, remaining)));
        return prefix.append(Component.space())
            .append(action(messages, locale, "invite-notification-view", "[목록]", NamedTextColor.AQUA, "/is invites"));
    }

    private static Component action(MessageRenderer messages, String locale, String key, String fallback, NamedTextColor color, String command) {
        String label = messages.plainForLocale(locale, key);
        if (label.isBlank()) {
            label = fallback;
        }
        String hover = messages.plainForLocale(locale, key + "-hover");
        Component component = Component.text(label, color, TextDecoration.BOLD).clickEvent(ClickEvent.runCommand(command));
        return hover.isBlank() ? component : component.hoverEvent(HoverEvent.showText(Component.text(hover, NamedTextColor.GRAY)));
    }

    private static List<IslandInviteSnapshot> pending(List<IslandInviteSnapshot> invites, UUID expectedInviteId) {
        Instant now = Instant.now();
        return (invites == null ? List.<IslandInviteSnapshot>of() : invites).stream()
            .filter(invite -> invite != null && "PENDING".equalsIgnoreCase(invite.state()))
            .filter(invite -> invite.expiresAt() == null || invite.expiresAt().isAfter(now))
            .filter(invite -> expectedInviteId == null || expectedInviteId.equals(invite.inviteId()))
            .sorted(java.util.Comparator.comparing(IslandInviteSnapshot::createdAt, java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())).thenComparing(IslandInviteSnapshot::inviteId))
            .toList();
    }

    private String locale(Player player) {
        return locales == null ? PlayerLocaleCache.clientLocale(player) : locales.locale(player);
    }

    private static String compact(UUID value) {
        if (value == null) {
            return "?";
        }
        String text = value.toString();
        return text.substring(0, Math.min(8, text.length()));
    }
}
