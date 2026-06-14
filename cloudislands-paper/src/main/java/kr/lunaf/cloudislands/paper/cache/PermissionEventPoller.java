package kr.lunaf.cloudislands.paper.cache;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import kr.lunaf.cloudislands.api.model.RouteTicket;
import kr.lunaf.cloudislands.api.model.RouteTicketState;
import kr.lunaf.cloudislands.common.protection.IslandRegion;
import kr.lunaf.cloudislands.common.event.CacheInvalidationPlan;
import kr.lunaf.cloudislands.common.event.CloudIslandEventType;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.paper.event.CoreCacheClearEvent;
import kr.lunaf.cloudislands.paper.event.CoreReloadEvent;
import kr.lunaf.cloudislands.paper.event.CloudIslandsGlobalEvent;
import kr.lunaf.cloudislands.paper.event.IslandAccessChangeEvent;
import kr.lunaf.cloudislands.paper.event.IslandActivationRequestEvent;
import kr.lunaf.cloudislands.paper.event.IslandActivatedEvent;
import kr.lunaf.cloudislands.paper.event.IslandBankChangeEvent;
import kr.lunaf.cloudislands.paper.event.IslandBiomeChangeEvent;
import kr.lunaf.cloudislands.paper.event.IslandCreatedEvent;
import kr.lunaf.cloudislands.paper.event.IslandDeactivatedEvent;
import kr.lunaf.cloudislands.paper.event.IslandDeactivationRequestEvent;
import kr.lunaf.cloudislands.paper.event.IslandDeleteRequestEvent;
import kr.lunaf.cloudislands.paper.event.IslandDeletedEvent;
import kr.lunaf.cloudislands.paper.event.IslandFlagChangeEvent;
import kr.lunaf.cloudislands.paper.event.IslandHomeChangeEvent;
import kr.lunaf.cloudislands.paper.event.IslandInviteChangeEvent;
import kr.lunaf.cloudislands.paper.event.IslandLevelRecalculateEvent;
import kr.lunaf.cloudislands.paper.event.IslandLimitChangeEvent;
import kr.lunaf.cloudislands.paper.event.IslandMemberChangedEvent;
import kr.lunaf.cloudislands.paper.event.IslandMemberJoinEvent;
import kr.lunaf.cloudislands.paper.event.IslandMemberLeaveEvent;
import kr.lunaf.cloudislands.paper.event.IslandMigratedEvent;
import kr.lunaf.cloudislands.paper.event.IslandMigrationEvent;
import kr.lunaf.cloudislands.paper.event.IslandMissionCompleteEvent;
import kr.lunaf.cloudislands.paper.event.IslandMissionProgressEvent;
import kr.lunaf.cloudislands.paper.event.IslandPermissionChangeEvent;
import kr.lunaf.cloudislands.paper.event.IslandRecoveryRequiredEvent;
import kr.lunaf.cloudislands.paper.event.IslandRepairedEvent;
import kr.lunaf.cloudislands.paper.event.IslandRenamedEvent;
import kr.lunaf.cloudislands.paper.event.IslandResetEvent;
import kr.lunaf.cloudislands.paper.event.IslandRestoreRequestEvent;
import kr.lunaf.cloudislands.paper.event.IslandRestoredEvent;
import kr.lunaf.cloudislands.paper.event.IslandRoleCatalogChangeEvent;
import kr.lunaf.cloudislands.paper.event.IslandRoleChangeEvent;
import kr.lunaf.cloudislands.paper.event.IslandRuntimeChangeEvent;
import kr.lunaf.cloudislands.paper.event.IslandSnapshotCreateEvent;
import kr.lunaf.cloudislands.paper.event.IslandSnapshotRequestEvent;
import kr.lunaf.cloudislands.paper.event.IslandTemplateChangeEvent;
import kr.lunaf.cloudislands.paper.event.IslandUpgradeEvent;
import kr.lunaf.cloudislands.paper.event.IslandVisitorBanChangeEvent;
import kr.lunaf.cloudislands.paper.event.IslandVisitorKickEvent;
import kr.lunaf.cloudislands.paper.event.IslandWarpChangeEvent;
import kr.lunaf.cloudislands.paper.event.IslandWarpCreateEvent;
import kr.lunaf.cloudislands.paper.event.IslandWarpDeleteEvent;
import kr.lunaf.cloudislands.paper.event.IslandWorthChangeEvent;
import kr.lunaf.cloudislands.paper.event.NodeStateChangeEvent;
import kr.lunaf.cloudislands.paper.event.NodeStateChangedEvent;
import kr.lunaf.cloudislands.paper.event.RouteSessionPublishedEvent;
import kr.lunaf.cloudislands.paper.event.RouteTicketConsumedEvent;
import kr.lunaf.cloudislands.paper.event.RouteTicketClearedEvent;
import kr.lunaf.cloudislands.paper.event.RouteTicketCreatedEvent;
import kr.lunaf.cloudislands.paper.event.RouteTicketFailedEvent;
import kr.lunaf.cloudislands.paper.generator.CropGrowthLevelCache;
import kr.lunaf.cloudislands.paper.generator.GeneratorLevelCache;
import kr.lunaf.cloudislands.paper.limit.IslandLimitCache;
import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import kr.lunaf.cloudislands.paper.ProtectionController;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

public final class PermissionEventPoller {
    private static final int MAX_SEEN_EVENTS = 8192;
    private static final int EVENT_BATCH_SIZE = 512;
    private static final int MAX_BATCHES_PER_POLL = 8;
    private final Plugin plugin;
    private final CoreApiClient client;
    private final PermissionCacheSyncService permissionSync;
    private final GeneratorLevelCache generatorLevels;
    private final CropGrowthLevelCache cropGrowthLevels;
    private final IslandLimitCache limits;
    private final ProtectionController protection;
    private final MessageRenderer messages;
    private final String nodeId;
    private final String fallbackServerName;
    private final Set<String> seen = ConcurrentHashMap.newKeySet();
    private final Deque<String> seenOrder = new ArrayDeque<>();
    private long lastEventSequence;
    private BukkitTask task;
    private final AtomicLong chatBroadcasts = new AtomicLong();
    private final AtomicLong chatDeliveries = new AtomicLong();
    private final AtomicLong chatNoRecipientBroadcasts = new AtomicLong();

    public PermissionEventPoller(Plugin plugin, CoreApiClient client, PermissionCacheSyncService permissionSync, GeneratorLevelCache generatorLevels, CropGrowthLevelCache cropGrowthLevels, IslandLimitCache limits, ProtectionController protection, String nodeId, String fallbackServerName) {
        this(plugin, client, permissionSync, generatorLevels, cropGrowthLevels, limits, protection, nodeId, fallbackServerName, null);
    }

    public PermissionEventPoller(Plugin plugin, CoreApiClient client, PermissionCacheSyncService permissionSync, GeneratorLevelCache generatorLevels, CropGrowthLevelCache cropGrowthLevels, IslandLimitCache limits, ProtectionController protection, String nodeId, String fallbackServerName, MessageRenderer messages) {
        this.plugin = plugin;
        this.client = client;
        this.permissionSync = permissionSync;
        this.generatorLevels = generatorLevels;
        this.cropGrowthLevels = cropGrowthLevels;
        this.limits = limits;
        this.protection = protection;
        this.messages = messages;
        this.nodeId = nodeId;
        this.fallbackServerName = fallbackServerName == null || fallbackServerName.isBlank() ? "Lobby" : fallbackServerName;
    }

    public void start(long intervalTicks) {
        stop();
        task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::poll, intervalTicks, intervalTicks);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public long chatBroadcasts() {
        return chatBroadcasts.get();
    }

    public long chatDeliveries() {
        return chatDeliveries.get();
    }

    public long chatNoRecipientBroadcasts() {
        return chatNoRecipientBroadcasts.get();
    }

    private void poll() {
        try {
            for (int batch = 0; batch < MAX_BATCHES_PER_POLL; batch++) {
                String json = client.listEventsSince(lastEventSequence, EVENT_BATCH_SIZE).join();
                long oldestSequence = longField(json, "oldestSeq");
                long latestSequence = longField(json, "latestSeq");
                if (latestSequence > 0L && latestSequence < lastEventSequence) {
                    lastEventSequence = 0L;
                    clearSeen();
                    json = client.listEventsSince(lastEventSequence, EVENT_BATCH_SIZE).join();
                    oldestSequence = longField(json, "oldestSeq");
                }
                if (lastEventSequence > 0L && oldestSequence > lastEventSequence + 1L) {
                    invalidateLocalCaches();
                    clearSeen();
                    lastEventSequence = oldestSequence - 1L;
                    json = client.listEventsSince(lastEventSequence, EVENT_BATCH_SIZE).join();
                }
                java.util.List<ParsedEvent> batchEvents = events(json);
                for (ParsedEvent event : batchEvents) {
                    handleEvent(event);
                }
                if (batchEvents.size() < EVENT_BATCH_SIZE) {
                    break;
                }
            }
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Failed to poll permission cache events: " + exception.getMessage());
        }
    }

    private void handleEvent(ParsedEvent event) {
        lastEventSequence = Math.max(lastEventSequence, event.sequence());
        String type = event.type();
        Map<String, String> fields = fields(event.fields());
        String key = eventKey(type, fields, event.occurredAt());
        if (!markSeen(key)) {
            return;
        }
        publishLocalEvents(event.sequence(), type, fields, event.occurredAt());
        handleMigrationLockState(type, fields);
        handleMigrationNotice(type, fields);
        handleIslandMutationEvacuation(type, fields);
        if (handlesNodeOperation(type, fields)) {
            return;
        }
        if (handlesVisitorRemoval(type, fields)) {
            return;
        }
        if (handlesIslandChat(type, fields)) {
            return;
        }
        if (affectsPermissions(type, fields)) {
            UUID islandId = islandId(fields);
            if (islandId != null) {
                permissionSync.sync(islandId);
            } else if (isGlobalCacheEvent(type)) {
                permissionSync.invalidateAll();
            }
        }
        if (affectsGenerator(type, fields)) {
            UUID islandId = islandId(fields);
            if (islandId != null) {
                generatorLevels.invalidate(islandId);
            } else if (isGlobalCacheEvent(type)) {
                generatorLevels.invalidateAll();
            }
        }
        if (affectsCrop(type, fields)) {
            UUID islandId = islandId(fields);
            if (islandId != null) {
                cropGrowthLevels.invalidate(islandId);
            } else if (isGlobalCacheEvent(type)) {
                cropGrowthLevels.invalidateAll();
            }
        }
        if (affectsLimits(type, fields)) {
            UUID islandId = islandId(fields);
            if (islandId != null) {
                limits.invalidate(islandId);
            } else if (isGlobalCacheEvent(type)) {
                limits.invalidateAll();
            }
        }
    }

    private synchronized boolean markSeen(String key) {
        if (!seen.add(key)) {
            return false;
        }
        seenOrder.addLast(key);
        while (seenOrder.size() > MAX_SEEN_EVENTS) {
            String oldest = seenOrder.removeFirst();
            seen.remove(oldest);
        }
        return true;
    }

    private synchronized void clearSeen() {
        seen.clear();
        seenOrder.clear();
    }

    private void invalidateLocalCaches() {
        permissionSync.invalidateAll();
        generatorLevels.invalidateAll();
        cropGrowthLevels.invalidateAll();
        limits.invalidateAll();
    }

    private String eventKey(String type, Map<String, String> fields, String occurredAt) {
        String identity = firstPresent(fields, "islandId", "ticketId", "jobId", "inviteId", "nodeId", "playerUuid");
        if (identity.isBlank()) {
            identity = fields.toString();
        }
        return type + "@" + occurredAt + "@" + identity;
    }

    private String firstPresent(Map<String, String> fields, String... keys) {
        for (String key : keys) {
            String value = fields.getOrDefault(key, "");
            if (!value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private boolean handlesNodeOperation(String type, Map<String, String> fields) {
        String targetNode = fields.getOrDefault("nodeId", "");
        if (!targetNode.equals(nodeId)) {
            return false;
        }
        String reason = fields.getOrDefault("reason", "admin-request");
        String state = fields.getOrDefault("state", "");
        String operation = fields.getOrDefault("operation", "");
        if ((type.equals(CloudIslandEventType.NODE_STATE_CHANGED.name()) && state.equals("KICKALL")) || type.equals("NODE_KICKALL")) {
            Bukkit.getScheduler().runTask(plugin, () -> kickPlayers(reason));
            return true;
        }
        if ((type.equals(CloudIslandEventType.NODE_STATE_CHANGED.name()) && (state.equals("SHUTDOWN_SAFE") || operation.equals("SHUTDOWN_SAFE"))) || type.equals("NODE_SHUTDOWN_SAFE")) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                kickPlayers(reason);
                Bukkit.shutdown();
            });
            return true;
        }
        return false;
    }

    private boolean handlesIslandChat(String type, Map<String, String> fields) {
        if (!type.equals(CloudIslandEventType.ISLAND_CHAT_SENT.name())) {
            return false;
        }
        String islandIdValue = fields.getOrDefault("islandId", "");
        String actorUuidValue = fields.getOrDefault("actorUuid", "");
        String message = fields.getOrDefault("message", "");
        if (islandIdValue.isBlank() || actorUuidValue.isBlank() || message.isBlank()) {
            return true;
        }
        UUID islandId = UUID.fromString(islandIdValue);
        String channel = fields.getOrDefault("channel", "ISLAND");
        String actorName = fields.getOrDefault("actorName", actorUuidValue);
        String recipients = fields.getOrDefault("recipients", "");
        Bukkit.getScheduler().runTask(plugin, () -> broadcastIslandChat(islandId, actorName, channel, message, recipients));
        return true;
    }

    private void broadcastIslandChat(UUID islandId, String actorName, String channel, String chatMessage, String recipients) {
        boolean teamChannel = channel.equalsIgnoreCase("TEAM");
        String normalizedChannel = teamChannel ? "팀" : "섬";
        String message = chatMessageLine(teamChannel, normalizedChannel, actorName, chatMessage);
        int deliveries = 0;
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (teamChannel) {
                if (teamRecipient(recipients, online.getUniqueId()) || (recipients.isBlank() && protection.memberOrTrusted(islandId, online.getUniqueId()))) {
                    online.sendMessage(message);
                    deliveries++;
                }
                continue;
            }
            UUID currentIslandId = protection.islandAt(online.getLocation().getBlock()).orElse(null);
            if (islandId.equals(currentIslandId)) {
                online.sendMessage(message);
                deliveries++;
            }
        }
        chatBroadcasts.incrementAndGet();
        chatDeliveries.addAndGet(deliveries);
        if (deliveries == 0) {
            chatNoRecipientBroadcasts.incrementAndGet();
        }
    }

    private String chatMessageLine(boolean teamChannel, String channelName, String actorName, String chatMessage) {
        String key = teamChannel ? "team-chat-format" : "island-chat-format";
        String fallback = "[" + channelName + "] " + actorName + ": " + chatMessage;
        if (messages == null) {
            return fallback;
        }
        String rendered = messages.plain(key, "channel", channelName, "actor", actorName, "message", chatMessage);
        return rendered.isBlank() ? fallback : rendered;
    }

    private void handleMigrationLockState(String type, Map<String, String> fields) {
        UUID islandId = islandId(fields);
        if (islandId == null) {
            return;
        }
        if (type.equals(CloudIslandEventType.ISLAND_MIGRATE_REQUESTED.name())) {
            protection.markMigrating(islandId);
            return;
        }
        if (type.equals(CloudIslandEventType.ISLAND_MIGRATED.name())
            || type.equals(CloudIslandEventType.ISLAND_DELETED.name())) {
            protection.clearMigrating(islandId);
            return;
        }
        if (type.equals(CloudIslandEventType.ISLAND_DEACTIVATED.name())) {
            if (!fields.getOrDefault("phase", "").equals("MIGRATION_SOURCE_SAVED")) {
                protection.clearMigrating(islandId);
            }
            return;
        }
        if (type.equals(CloudIslandEventType.ISLAND_RUNTIME_CHANGED.name())) {
            String state = fields.getOrDefault("state", "");
            if (!state.equals("DEACTIVATING")) {
                protection.clearMigrating(islandId);
            }
        }
    }

    private void handleMigrationNotice(String type, Map<String, String> fields) {
        if (!type.equals(CloudIslandEventType.ISLAND_MIGRATE_REQUESTED.name())) {
            return;
        }
        String islandIdValue = fields.getOrDefault("islandId", "");
        if (islandIdValue.isBlank()) {
            return;
        }
        UUID islandId;
        try {
            islandId = UUID.fromString(islandIdValue);
        } catch (IllegalArgumentException exception) {
            return;
        }
        String targetNode = fields.getOrDefault("targetNode", "");
        Bukkit.getScheduler().runTask(plugin, () -> notifyMigratingIslandPlayers(islandId, targetNode));
    }

    private void notifyMigratingIslandPlayers(UUID islandId, String targetNode) {
        String primary = message("migration-notice-primary", "섬 서버를 최적화하는 중입니다...");
        String secondary = message("migration-notice-secondary", "잠시 후 자동으로 이동됩니다.");
        for (Player player : Bukkit.getOnlinePlayers()) {
            Location location = player.getLocation();
            IslandRegion region = protection.regionAt(location.getBlock()).orElse(null);
            if (region == null || !islandId.equals(region.islandId())) {
                continue;
            }
            BossBar bossBar = BossBar.bossBar(Component.text(primary + " " + secondary), 1.0F, BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS);
            player.sendMessage(primary);
            player.sendMessage(secondary);
            player.sendActionBar(Component.text(secondary));
            player.showBossBar(bossBar);
            Bukkit.getScheduler().runTaskLater(plugin, () -> player.hideBossBar(bossBar), 160L);
            if (!targetNode.isBlank()) {
                createMigrationReturnTicket(player, islandId, targetNode, region, location);
            }
        }
    }

    private void createMigrationReturnTicket(Player player, UUID islandId, String targetNode, IslandRegion region, Location location) {
        double localX = location.getX() - region.originX();
        double localZ = location.getZ() - region.originZ();
        client.createMigrationReturnTicket(player.getUniqueId(), islandId, targetNode, localX, location.getY(), localZ, location.getYaw(), location.getPitch())
            .thenAccept(ticket -> waitMigrationReturnTicket(player.getUniqueId(), ticket, 0))
            .exceptionally(error -> {
                Bukkit.getScheduler().runTask(plugin, () -> player.sendActionBar(Component.text(message("migration-return-register-failed", "섬 이동 준비를 등록하지 못했습니다."))));
                return null;
            });
    }

    private void waitMigrationReturnTicket(UUID playerUuid, RouteTicket ticket, int attempt) {
        if (ticket.state() == RouteTicketState.READY) {
            publishMigrationReturnSession(playerUuid, ticket);
            return;
        }
        if (ticket.state() == RouteTicketState.FAILED || ticket.state() == RouteTicketState.EXPIRED || attempt >= 180) {
            Bukkit.getScheduler().runTask(plugin, () -> migrationReturnFailed(playerUuid));
            return;
        }
        CompletableFuture.delayedExecutor(1, TimeUnit.SECONDS).execute(() ->
            client.routeTicketStatus(ticket.ticketId(), ticket.playerUuid(), ticket.nonce()).thenAccept(status -> {
                if (status.isPresent()) {
                    waitMigrationReturnTicket(playerUuid, status.get(), attempt + 1);
                } else {
                    Bukkit.getScheduler().runTask(plugin, () -> migrationReturnFailed(playerUuid));
                }
            }).exceptionally(error -> {
                if (attempt < 180) {
                    waitMigrationReturnTicket(playerUuid, ticket, attempt + 1);
                } else {
                    Bukkit.getScheduler().runTask(plugin, () -> migrationReturnFailed(playerUuid));
                }
                return null;
            })
        );
    }

    private void publishMigrationReturnSession(UUID playerUuid, RouteTicket ticket) {
        client.publishRouteSession(ticket).thenRun(() ->
            Bukkit.getScheduler().runTask(plugin, () -> connectMigratingPlayer(playerUuid, ticket))
        ).exceptionally(error -> {
            Bukkit.getScheduler().runTask(plugin, () -> migrationReturnFailed(playerUuid));
            return null;
        });
    }

    private void connectMigratingPlayer(UUID playerUuid, RouteTicket ticket) {
        Player player = Bukkit.getPlayer(playerUuid);
        if (player == null) {
            return;
        }
        player.sendActionBar(Component.text(message("migration-return-start", "최적화된 섬 서버로 이동합니다.")));
        if (!canUseBungeeConnect()) {
            migrationReturnFailed(playerUuid);
            return;
        }
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeUTF("Connect");
            output.writeUTF(ticket.payload().getOrDefault("targetServerName", ticket.targetNode()));
            player.sendPluginMessage(plugin, "BungeeCord", bytes.toByteArray());
        } catch (IOException | RuntimeException exception) {
            plugin.getLogger().warning("Failed to move migrating player to target node: " + exception.getMessage());
            migrationReturnFailed(playerUuid);
        }
    }

    private void migrationReturnFailed(UUID playerUuid) {
        Player player = Bukkit.getPlayer(playerUuid);
        if (player != null) {
            player.sendActionBar(Component.text(message("migration-return-not-ready", "섬 서버 이동 준비가 완료되지 않았습니다. 잠시 후 /섬 홈을 사용해주세요.")));
        }
    }

    private String message(String key, String fallback) {
        if (messages == null) {
            return fallback;
        }
        String rendered = messages.plain(key);
        return rendered.isBlank() ? fallback : rendered;
    }

    private boolean teamRecipient(String recipients, UUID playerUuid) {
        if (recipients == null || recipients.isBlank()) {
            return false;
        }
        String target = playerUuid.toString();
        for (String recipient : recipients.split(",")) {
            if (recipient.trim().equalsIgnoreCase(target)) {
                return true;
            }
        }
        return false;
    }

    private void handleIslandMutationEvacuation(String type, Map<String, String> fields) {
        if (!type.equals(CloudIslandEventType.ISLAND_RESTORE_REQUESTED.name())
            && !type.equals(CloudIslandEventType.ISLAND_RESET_REQUESTED.name())
            && !type.equals(CloudIslandEventType.ISLAND_DELETE_REQUESTED.name())) {
            return;
        }
        UUID islandId = islandId(fields);
        if (islandId == null) {
            return;
        }
        String targetNode = fields.getOrDefault("targetNode", "");
        if (!targetNode.isBlank() && !targetNode.equals(nodeId)) {
            return;
        }
        String reason = switch (type) {
            case "ISLAND_RESTORE_REQUESTED" -> message("island-restore-evacuate", "섬 복원을 위해 로비로 이동합니다.");
            case "ISLAND_RESET_REQUESTED" -> message("island-reset-evacuate", "섬 리셋을 위해 로비로 이동합니다.");
            case "ISLAND_DELETE_REQUESTED" -> message("island-delete-evacuate", "섬 삭제를 위해 로비로 이동합니다.");
            default -> message("island-operation-evacuate", "섬 작업을 위해 로비로 이동합니다.");
        };
        Bukkit.getScheduler().runTask(plugin, () -> moveIslandPlayersToFallback(islandId, reason));
    }

    private void moveIslandPlayersToFallback(UUID islandId, String reason) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID currentIslandId = protection.islandAt(player.getLocation().getBlock()).orElse(null);
            if (!islandId.equals(currentIslandId)) {
                continue;
            }
            player.sendMessage(reason);
            if (!canUseBungeeConnect()) {
                continue;
            }
            try {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                DataOutputStream output = new DataOutputStream(bytes);
                output.writeUTF("Connect");
                output.writeUTF(fallbackServerName);
                player.sendPluginMessage(plugin, "BungeeCord", bytes.toByteArray());
            } catch (IOException | RuntimeException exception) {
                plugin.getLogger().warning("Failed to move island player to fallback: " + exception.getMessage());
            }
        }
    }

    private boolean handlesVisitorRemoval(String type, Map<String, String> fields) {
        if (!type.equals(CloudIslandEventType.ISLAND_VISITOR_KICKED.name())
            && !type.equals(CloudIslandEventType.ISLAND_VISITOR_BAN_CHANGED.name())) {
            return false;
        }
        if (type.equals(CloudIslandEventType.ISLAND_VISITOR_BAN_CHANGED.name())
            && !Boolean.parseBoolean(fields.getOrDefault("banned", "false"))) {
            return false;
        }
        String islandIdValue = fields.getOrDefault("islandId", "");
        String playerUuidValue = fields.getOrDefault("playerUuid", "");
        if (islandIdValue.isBlank() || playerUuidValue.isBlank()) {
            return true;
        }
        UUID islandId = UUID.fromString(islandIdValue);
        UUID playerUuid = UUID.fromString(playerUuidValue);
        Bukkit.getScheduler().runTask(plugin, () -> moveVisitorToFallback(islandId, playerUuid));
        return true;
    }

    private void moveVisitorToFallback(UUID islandId, UUID playerUuid) {
        Player target = Bukkit.getPlayer(playerUuid);
        if (target == null) {
            return;
        }
        UUID currentIslandId = protection.islandAt(target.getLocation().getBlock()).orElse(null);
        if (!islandId.equals(currentIslandId)) {
            return;
        }
        target.sendMessage("섬에서 추방되어 로비로 이동합니다.");
        if (!canUseBungeeConnect()) {
            return;
        }
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeUTF("Connect");
            output.writeUTF(fallbackServerName);
            target.sendPluginMessage(plugin, "BungeeCord", bytes.toByteArray());
        } catch (IOException | RuntimeException exception) {
            plugin.getLogger().warning("Failed to move kicked visitor to fallback: " + exception.getMessage());
        }
    }

    private boolean canUseBungeeConnect() {
        return plugin.getServer().getMessenger().isOutgoingChannelRegistered(plugin, "BungeeCord");
    }

    private void kickPlayers(String reason) {
        String message = reason == null || reason.isBlank()
            ? "섬 점검으로 로비로 이동합니다."
            : "섬 점검으로 로비로 이동합니다. 사유: " + reason;
        for (org.bukkit.entity.Player player : Bukkit.getOnlinePlayers()) {
            player.kickPlayer(message);
        }
    }

    private void publishLocalEvents(long sequence, String type, Map<String, String> fields, String occurredAt) {
        Bukkit.getPluginManager().callEvent(new CloudIslandsGlobalEvent(sequence, type, fields, occurredAt));
        if (type.equals(CloudIslandEventType.ROUTE_TICKET_CREATED.name())) {
            Bukkit.getPluginManager().callEvent(new RouteTicketCreatedEvent(
                uuidField(fields, "ticketId"),
                uuidField(fields, "islandId"),
                uuidField(fields, "playerUuid"),
                fields.getOrDefault("action", ""),
                fields.getOrDefault("targetNode", ""),
                fields.getOrDefault("state", ""),
                fields));
            return;
        }
        if (type.equals(CloudIslandEventType.ROUTE_SESSION_PUBLISHED.name())) {
            Bukkit.getPluginManager().callEvent(new RouteSessionPublishedEvent(
                uuidField(fields, "ticketId"),
                uuidField(fields, "islandId"),
                uuidField(fields, "playerUuid"),
                fields.getOrDefault("action", ""),
                fields.getOrDefault("targetNode", ""),
                fields));
            return;
        }
        if (type.equals(CloudIslandEventType.ROUTE_TICKET_CONSUMED.name())) {
            Bukkit.getPluginManager().callEvent(new RouteTicketConsumedEvent(
                uuidField(fields, "ticketId"),
                uuidField(fields, "islandId"),
                uuidField(fields, "playerUuid"),
                fields.getOrDefault("action", ""),
                fields.getOrDefault("targetNode", ""),
                fields));
            return;
        }
        if (type.equals(CloudIslandEventType.ROUTE_TICKET_FAILED.name())) {
            Bukkit.getPluginManager().callEvent(new RouteTicketFailedEvent(
                uuidField(fields, "ticketId"),
                uuidField(fields, "islandId"),
                uuidField(fields, "playerUuid"),
                fields.getOrDefault("action", ""),
                fields.getOrDefault("targetNode", ""),
                fields.getOrDefault("reason", ""),
                fields));
            return;
        }
        if (type.equals(CloudIslandEventType.ROUTE_TICKET_CLEARED.name())) {
            Bukkit.getPluginManager().callEvent(new RouteTicketClearedEvent(
                uuidField(fields, "ticketId"),
                uuidField(fields, "playerUuid"),
                fields.getOrDefault("reason", ""),
                Boolean.TRUE.equals(booleanField(fields, "clearedSession")),
                Boolean.TRUE.equals(booleanField(fields, "clearedTicket")),
                fields));
            return;
        }
        if (type.equals(CloudIslandEventType.ISLAND_TEMPLATE_CHANGED.name())) {
            Bukkit.getPluginManager().callEvent(new IslandTemplateChangeEvent(
                fields.getOrDefault("templateId", ""),
                booleanField(fields, "enabled"),
                fields.getOrDefault("operation", ""),
                fields.getOrDefault("minNodeVersion", ""),
                fields));
            return;
        }
        if (type.equals(CloudIslandEventType.NODE_STATE_CHANGED.name())) {
            Bukkit.getPluginManager().callEvent(new NodeStateChangedEvent(
                fields.getOrDefault("nodeId", ""),
                fields.getOrDefault("state", ""),
                fields.getOrDefault("operation", ""),
                fields.getOrDefault("reason", ""),
                intField(fields, "recoveryRequired"),
                fields));
            Bukkit.getPluginManager().callEvent(new NodeStateChangeEvent(
                fields.getOrDefault("nodeId", ""),
                fields.getOrDefault("state", ""),
                fields.getOrDefault("operation", ""),
                fields.getOrDefault("reason", ""),
                intField(fields, "recoveryRequired"),
                fields));
            return;
        }
        if (type.equals(CloudIslandEventType.CORE_CACHE_CLEARED.name())) {
            Bukkit.getPluginManager().callEvent(new CoreCacheClearEvent(
                fields.getOrDefault("scope", ""),
                intField(fields, "sessions"),
                intField(fields, "tickets"),
                intField(fields, "redisKeys"),
                fields));
            return;
        }
        if (type.equals(CloudIslandEventType.CORE_RELOADED.name())) {
            Bukkit.getPluginManager().callEvent(new CoreReloadEvent(
                intField(fields, "clearedSessions"),
                intField(fields, "clearedTickets"),
                intField(fields, "clearedRedisKeys"),
                fields));
            return;
        }
        UUID islandId = islandId(fields);
        if (islandId == null) {
            return;
        }
        if (type.equals(CloudIslandEventType.ISLAND_UPGRADE.name())) {
            Bukkit.getPluginManager().callEvent(new IslandUpgradeEvent(islandId, fields.getOrDefault("upgradeKey", ""), intField(fields, "level"), fields));
        } else if (type.equals(CloudIslandEventType.ISLAND_CREATED.name())) {
            Bukkit.getPluginManager().callEvent(new IslandCreatedEvent(islandId, uuidField(fields, "ownerUuid"), fields.getOrDefault("targetNode", ""), fields));
        } else if (type.equals(CloudIslandEventType.ISLAND_ACTIVATED.name())) {
            Bukkit.getPluginManager().callEvent(new IslandActivatedEvent(islandId, firstPresent(fields, "nodeId", "targetNode"), intField(fields, "readyTickets"), fields));
        } else if (type.equals(CloudIslandEventType.ISLAND_DEACTIVATED.name())) {
            Bukkit.getPluginManager().callEvent(new IslandDeactivatedEvent(islandId, fields));
        } else if (type.equals(CloudIslandEventType.ISLAND_ACTIVATE_REQUESTED.name())) {
            Bukkit.getPluginManager().callEvent(new IslandActivationRequestEvent(islandId, fields.getOrDefault("state", ""), fields.getOrDefault("targetNode", ""), fields));
        } else if (type.equals(CloudIslandEventType.ISLAND_DEACTIVATE_REQUESTED.name())) {
            Bukkit.getPluginManager().callEvent(new IslandDeactivationRequestEvent(islandId, fields.getOrDefault("state", ""), fields));
        } else if (type.equals(CloudIslandEventType.ISLAND_MIGRATE_REQUESTED.name())) {
            Bukkit.getPluginManager().callEvent(new IslandMigrationEvent(islandId, true, fields.getOrDefault("targetNode", ""), fields.getOrDefault("phase", ""), fields.getOrDefault("worldName", ""), longField(fields, "fencingToken"), fields));
        } else if (type.equals(CloudIslandEventType.ISLAND_MIGRATED.name())) {
            Bukkit.getPluginManager().callEvent(new IslandMigrationEvent(islandId, false, fields.getOrDefault("targetNode", ""), fields.getOrDefault("phase", ""), fields.getOrDefault("worldName", ""), longField(fields, "fencingToken"), fields));
            Bukkit.getPluginManager().callEvent(new IslandMigratedEvent(islandId, firstPresent(fields, "fromNode", "sourceNode", "activeNode"), firstPresent(fields, "targetNode", "nodeId"), fields.getOrDefault("worldName", ""), longField(fields, "fencingToken"), fields));
        } else if (type.equals(CloudIslandEventType.ISLAND_RESTORE_REQUESTED.name())) {
            Bukkit.getPluginManager().callEvent(new IslandRestoreRequestEvent(islandId, fields.getOrDefault("state", ""), fields.getOrDefault("targetNode", ""), longField(fields, "snapshotNo"), fields));
        } else if (type.equals(CloudIslandEventType.ISLAND_DELETE_REQUESTED.name())) {
            Bukkit.getPluginManager().callEvent(new IslandDeleteRequestEvent(islandId, fields.getOrDefault("targetNode", ""), fields.getOrDefault("reason", ""), fields));
        } else if (type.equals(CloudIslandEventType.ISLAND_DELETED.name())) {
            Bukkit.getPluginManager().callEvent(new IslandDeletedEvent(islandId, longField(fields, "snapshotNo"), fields));
        } else if (type.equals(CloudIslandEventType.ISLAND_RUNTIME_CHANGED.name())) {
            Bukkit.getPluginManager().callEvent(new IslandRuntimeChangeEvent(islandId, fields.getOrDefault("state", ""), fields.getOrDefault("targetNode", ""), fields.getOrDefault("reason", ""), fields.getOrDefault("error", ""), fields));
        } else if (type.equals(CloudIslandEventType.ISLAND_RECOVERY_REQUIRED.name())) {
            Bukkit.getPluginManager().callEvent(new IslandRecoveryRequiredEvent(islandId, firstPresent(fields, "nodeId", "activeNode", "targetNode"), fields.getOrDefault("reason", ""), fields));
        } else if (type.equals(CloudIslandEventType.ISLAND_REPAIRED.name())) {
            Bukkit.getPluginManager().callEvent(new IslandRepairedEvent(islandId, fields.getOrDefault("reason", ""), fields));
        } else if (type.equals(CloudIslandEventType.ISLAND_RESTORED.name())) {
            Bukkit.getPluginManager().callEvent(new IslandRestoredEvent(islandId, longField(fields, "snapshotNo"), fields.getOrDefault("state", ""), fields));
        } else if (type.equals(CloudIslandEventType.ISLAND_RESET_REQUESTED.name()) || type.equals(CloudIslandEventType.ISLAND_RESET.name())) {
            Bukkit.getPluginManager().callEvent(new IslandResetEvent(islandId, type.equals(CloudIslandEventType.ISLAND_RESET_REQUESTED.name()), fields.getOrDefault("state", ""), fields.getOrDefault("targetNode", ""), fields.getOrDefault("reason", ""), fields));
        } else if (type.equals(CloudIslandEventType.ISLAND_ACCESS_CHANGED.name())) {
            Bukkit.getPluginManager().callEvent(new IslandAccessChangeEvent(islandId, booleanField(fields, "publicAccess"), booleanField(fields, "locked"), fields));
        } else if (type.equals(CloudIslandEventType.ISLAND_RENAMED.name())) {
            Bukkit.getPluginManager().callEvent(new IslandRenamedEvent(islandId, uuidField(fields, "actorUuid"), fields.getOrDefault("name", ""), fields));
        } else if (type.equals(CloudIslandEventType.ISLAND_FLAG_CHANGED.name())) {
            Bukkit.getPluginManager().callEvent(new IslandFlagChangeEvent(islandId, firstPresent(fields, "flag", "flagKey"), fields.getOrDefault("value", ""), fields));
        } else if (type.equals(CloudIslandEventType.ISLAND_PERMISSION_CHANGED.name())) {
            Bukkit.getPluginManager().callEvent(new IslandPermissionChangeEvent(islandId, fields.getOrDefault("role", ""), fields.getOrDefault("permission", ""), booleanField(fields, "allowed"), fields));
        } else if (type.equals(CloudIslandEventType.ISLAND_ROLE_CHANGED.name())) {
            Bukkit.getPluginManager().callEvent(new IslandRoleCatalogChangeEvent(islandId, fields.getOrDefault("role", ""), fields.getOrDefault("operation", ""), fields));
        } else if (type.equals(CloudIslandEventType.ISLAND_INVITE_CHANGED.name())) {
            Bukkit.getPluginManager().callEvent(new IslandInviteChangeEvent(islandId, uuidField(fields, "inviteId"), uuidField(fields, "playerUuid"), uuidField(fields, "targetUuid"), fields.getOrDefault("state", ""), booleanField(fields, "accepted"), booleanField(fields, "declined"), fields));
        } else if (type.equals(CloudIslandEventType.ISLAND_HOME_CHANGED.name())) {
            Bukkit.getPluginManager().callEvent(new IslandHomeChangeEvent(islandId, fields.getOrDefault("name", ""), fields));
        } else if (type.equals(CloudIslandEventType.ISLAND_BANK_CHANGED.name())) {
            Bukkit.getPluginManager().callEvent(new IslandBankChangeEvent(islandId, uuidField(fields, "actorUuid"), fields.getOrDefault("operation", ""), fields.getOrDefault("amount", ""), fields.getOrDefault("balance", ""), fields));
        } else if (type.equals(CloudIslandEventType.ISLAND_VISITOR_BAN_CHANGED.name())) {
            Bukkit.getPluginManager().callEvent(new IslandVisitorBanChangeEvent(islandId, uuidField(fields, "playerUuid"), Boolean.TRUE.equals(booleanField(fields, "banned")), fields));
        } else if (type.equals(CloudIslandEventType.ISLAND_VISITOR_KICKED.name())) {
            Bukkit.getPluginManager().callEvent(new IslandVisitorKickEvent(islandId, uuidField(fields, "playerUuid"), uuidField(fields, "actorUuid"), fields));
        } else if (type.equals(CloudIslandEventType.ISLAND_LIMIT_CHANGED.name())) {
            Bukkit.getPluginManager().callEvent(new IslandLimitChangeEvent(islandId, fields.getOrDefault("limitKey", ""), longField(fields, "value"), fields));
        } else if (type.equals(CloudIslandEventType.ISLAND_MISSION_PROGRESS.name())) {
            Bukkit.getPluginManager().callEvent(new IslandMissionProgressEvent(islandId, fields.getOrDefault("missionKey", ""), fields.getOrDefault("kind", ""), longField(fields, "progress"), longField(fields, "goal"), longField(fields, "amount"), Boolean.TRUE.equals(booleanField(fields, "completed")), fields));
        } else if (type.equals(CloudIslandEventType.ISLAND_MISSION_COMPLETED.name())) {
            Bukkit.getPluginManager().callEvent(new IslandMissionCompleteEvent(islandId, fields.getOrDefault("missionKey", ""), fields.getOrDefault("kind", ""), fields));
        } else if (type.equals(CloudIslandEventType.ISLAND_SNAPSHOT_REQUESTED.name())) {
            Bukkit.getPluginManager().callEvent(new IslandSnapshotRequestEvent(islandId, fields.getOrDefault("reason", ""), fields));
        } else if (type.equals(CloudIslandEventType.ISLAND_SNAPSHOT_CREATED.name())) {
            Bukkit.getPluginManager().callEvent(new IslandSnapshotCreateEvent(islandId, longField(fields, "snapshotNo"), fields.getOrDefault("reason", ""), fields));
        } else if (type.equals(CloudIslandEventType.ISLAND_BIOME_CHANGED.name())) {
            Bukkit.getPluginManager().callEvent(new IslandBiomeChangeEvent(islandId, fields.getOrDefault("biomeKey", ""), fields));
        } else if (type.equals(CloudIslandEventType.ISLAND_WARP_CHANGED.name())) {
            publishWarpEvent(islandId, fields);
        } else if (type.equals(CloudIslandEventType.ISLAND_MEMBER_CHANGED.name())) {
            publishMemberEvent(islandId, fields);
        } else if (type.equals(CloudIslandEventType.ISLAND_OWNERSHIP_CHANGED.name())) {
            UUID playerUuid = uuidField(fields, "newOwnerUuid", "ownerUuid", "playerUuid");
            if (playerUuid != null) {
                Bukkit.getPluginManager().callEvent(new IslandRoleChangeEvent(islandId, playerUuid, fields.getOrDefault("oldRole", ""), "OWNER", fields));
            }
        } else if (type.equals(CloudIslandEventType.ISLAND_LEVEL_UPDATED.name()) || type.equals(CloudIslandEventType.ISLAND_BLOCKS_CHANGED.name())) {
            if (fields.containsKey("level")) {
                Bukkit.getPluginManager().callEvent(new IslandLevelRecalculateEvent(islandId, longField(fields, "level"), fields));
            }
            if (fields.containsKey("worth")) {
                Bukkit.getPluginManager().callEvent(new IslandWorthChangeEvent(islandId, fields.getOrDefault("worth", ""), fields));
            }
        }
    }

    private void publishWarpEvent(UUID islandId, Map<String, String> fields) {
        String warpName = firstPresent(fields, "warpName", "name");
        String action = fields.getOrDefault("action", fields.getOrDefault("operation", ""));
        String normalized = action.toUpperCase(java.util.Locale.ROOT);
        Bukkit.getPluginManager().callEvent(new IslandWarpChangeEvent(islandId, warpName, action, fields));
        if (normalized.equals("WARP_CREATE") || normalized.equals("WARP_SET") || normalized.equals("SET") || normalized.equals("CREATE") || normalized.equals("CREATED")) {
            Bukkit.getPluginManager().callEvent(new IslandWarpCreateEvent(islandId, warpName, fields));
        } else if (normalized.equals("WARP_DELETE") || normalized.equals("WARP_DELETED") || normalized.equals("DELETE") || normalized.equals("DELETED") || normalized.equals("REMOVE") || normalized.equals("REMOVED")) {
            Bukkit.getPluginManager().callEvent(new IslandWarpDeleteEvent(islandId, warpName, fields));
        }
    }

    private void publishMemberEvent(UUID islandId, Map<String, String> fields) {
        UUID playerUuid = uuidField(fields, "playerUuid", "memberUuid", "targetUuid");
        if (playerUuid == null) {
            return;
        }
        String action = fields.getOrDefault("action", fields.getOrDefault("operation", "")).toUpperCase(java.util.Locale.ROOT);
        String oldRole = fields.getOrDefault("oldRole", "");
        String newRole = firstPresent(fields, "newRole", "role");
        Bukkit.getPluginManager().callEvent(new IslandMemberChangedEvent(islandId, playerUuid, action, oldRole, newRole, fields));
        if (action.equals("REMOVE") || action.equals("REMOVED") || action.equals("LEAVE") || action.equals("LEFT") || action.equals("KICK") || action.equals("KICKED")) {
            Bukkit.getPluginManager().callEvent(new IslandMemberLeaveEvent(islandId, playerUuid, fields));
            return;
        }
        if (!oldRole.isBlank() || !newRole.isBlank()) {
            Bukkit.getPluginManager().callEvent(new IslandRoleChangeEvent(islandId, playerUuid, oldRole, newRole, fields));
        }
        Bukkit.getPluginManager().callEvent(new IslandMemberJoinEvent(islandId, playerUuid, newRole, fields));
    }

    private UUID islandId(Map<String, String> fields) {
        String islandId = fields.get("islandId");
        if (islandId == null || islandId.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(islandId);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private int intField(Map<String, String> fields, String key) {
        String value = fields.get(key);
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private long longField(Map<String, String> fields, String key) {
        String value = fields.get(key);
        if (value == null || value.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private Boolean booleanField(Map<String, String> fields, String key) {
        String value = fields.get(key);
        if (value == null || value.isBlank()) {
            return null;
        }
        if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("허용") || value.equals("1")) {
            return true;
        }
        if (value.equalsIgnoreCase("false") || value.equalsIgnoreCase("거부") || value.equals("0")) {
            return false;
        }
        return null;
    }

    private UUID uuidField(Map<String, String> fields, String... keys) {
        String value = firstPresent(fields, keys);
        if (value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private boolean affectsPermissions(String type, Map<String, String> fields) {
        if (targetsInclude(fields, CacheInvalidationPlan.CacheTarget.PERMISSIONS)) {
            return true;
        }
        try {
            return CacheInvalidationPlan.targetsFor(CloudIslandEventType.valueOf(type)).contains(CacheInvalidationPlan.CacheTarget.PERMISSIONS);
        } catch (IllegalArgumentException ignored) {
            return type.equals("ISLAND_PERMISSION_SET")
                || type.equals("ISLAND_MEMBER_SET")
                || type.equals("ISLAND_MEMBER_REMOVE")
                || type.equals("ISLAND_OWNERSHIP_TRANSFER")
                || type.equals("ISLAND_VISITOR_BAN")
                || type.equals("ISLAND_VISITOR_PARDON");
        }
    }

    private boolean isGlobalCacheEvent(String type) {
        return type.equals(CloudIslandEventType.CORE_CACHE_CLEARED.name())
            || type.equals(CloudIslandEventType.CORE_RELOADED.name());
    }

    private boolean affectsGenerator(String type, Map<String, String> fields) {
        if (targetsInclude(fields, CacheInvalidationPlan.CacheTarget.GENERATOR)) {
            return true;
        }
        try {
            if (!CacheInvalidationPlan.targetsFor(CloudIslandEventType.valueOf(type)).contains(CacheInvalidationPlan.CacheTarget.GENERATOR)) {
                return false;
            }
            return !type.equals(CloudIslandEventType.ISLAND_UPGRADE.name()) || isGeneratorUpgrade(fields.getOrDefault("upgradeKey", ""));
        } catch (IllegalArgumentException ignored) {
            return type.equals("ISLAND_UPGRADE") && isGeneratorUpgrade(fields.getOrDefault("upgradeKey", ""));
        }
    }

    private boolean isGeneratorUpgrade(String upgradeKey) {
        String normalized = upgradeKey.toLowerCase(java.util.Locale.ROOT);
        return normalized.equals("generator") || normalized.startsWith("generator:");
    }

    private boolean affectsCrop(String type, Map<String, String> fields) {
        if (targetsInclude(fields, CacheInvalidationPlan.CacheTarget.CROP)) {
            return true;
        }
        try {
            if (!CacheInvalidationPlan.targetsFor(CloudIslandEventType.valueOf(type)).contains(CacheInvalidationPlan.CacheTarget.CROP)) {
                return false;
            }
            return !type.equals(CloudIslandEventType.ISLAND_UPGRADE.name()) || fields.getOrDefault("upgradeKey", "").equalsIgnoreCase("crop");
        } catch (IllegalArgumentException ignored) {
            return type.equals("ISLAND_UPGRADE") && fields.getOrDefault("upgradeKey", "").equalsIgnoreCase("crop");
        }
    }

    private boolean affectsLimits(String type, Map<String, String> fields) {
        if (targetsInclude(fields, CacheInvalidationPlan.CacheTarget.LIMITS)) {
            return true;
        }
        try {
            return CacheInvalidationPlan.targetsFor(CloudIslandEventType.valueOf(type)).contains(CacheInvalidationPlan.CacheTarget.LIMITS);
        } catch (IllegalArgumentException ignored) {
            return type.equals("ISLAND_LIMIT_SET");
        }
    }

    private boolean targetsInclude(Map<String, String> fields, CacheInvalidationPlan.CacheTarget target) {
        String cacheTargets = fields.getOrDefault("cacheTargets", "");
        for (String value : cacheTargets.split(",")) {
            if (value.trim().equals(target.name())) {
                return true;
            }
        }
        return false;
    }

    private Map<String, String> fields(String raw) {
        Map<String, String> result = new java.util.HashMap<>();
        String source = raw == null ? "" : raw;
        int index = 0;
        while (index < source.length()) {
            int keyStart = source.indexOf('"', index);
            if (keyStart < 0) {
                break;
            }
            int keyEnd = jsonStringEnd(source, keyStart + 1);
            if (keyEnd < 0) {
                break;
            }
            int colon = source.indexOf(':', keyEnd + 1);
            int valueStart = colon < 0 ? -1 : source.indexOf('"', colon + 1);
            if (valueStart < 0) {
                break;
            }
            int valueEnd = jsonStringEnd(source, valueStart + 1);
            if (valueEnd < 0) {
                break;
            }
            result.put(unescape(source.substring(keyStart + 1, keyEnd)), unescape(source.substring(valueStart + 1, valueEnd)));
            index = valueEnd + 1;
        }
        return result;
    }

    private java.util.List<ParsedEvent> events(String json) {
        java.util.List<ParsedEvent> result = new java.util.ArrayList<>();
        String source = json == null ? "" : json;
        int arrayStart = source.indexOf("\"events\":[");
        if (arrayStart < 0) {
            return result;
        }
        int index = source.indexOf('{', arrayStart);
        while (index >= 0 && index < source.length()) {
            int objectEnd = matchingObjectEnd(source, index);
            if (objectEnd < 0) {
                break;
            }
            String object = source.substring(index, objectEnd + 1);
            String type = textField(object, "type");
            String fields = objectField(object, "fields");
            String occurredAt = textField(object, "occurredAt");
            long sequence = longField(object, "seq");
            if (!type.isBlank() && !occurredAt.isBlank()) {
                result.add(new ParsedEvent(sequence, type, fields, occurredAt));
            }
            index = source.indexOf('{', objectEnd + 1);
        }
        return result;
    }

    private long longField(String object, String key) {
        String needle = "\"" + key + "\":";
        int start = object.indexOf(needle);
        if (start < 0) {
            return 0L;
        }
        int valueStart = start + needle.length();
        int valueEnd = valueStart;
        while (valueEnd < object.length() && Character.isDigit(object.charAt(valueEnd))) {
            valueEnd++;
        }
        try {
            return Long.parseLong(object.substring(valueStart, valueEnd));
        } catch (RuntimeException ignored) {
            return 0L;
        }
    }

    private int matchingObjectEnd(String source, int objectStart) {
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = objectStart; i < source.length(); i++) {
            char current = source.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (current == '\\') {
                escaped = inString;
                continue;
            }
            if (current == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (current == '{') {
                depth++;
            } else if (current == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private String textField(String object, String key) {
        String needle = "\"" + key + "\":\"";
        int start = object.indexOf(needle);
        if (start < 0) {
            return "";
        }
        int valueStart = start + needle.length();
        int valueEnd = jsonStringEnd(object, valueStart);
        return valueEnd < 0 ? "" : unescape(object.substring(valueStart, valueEnd));
    }

    private String objectField(String object, String key) {
        String needle = "\"" + key + "\":";
        int start = object.indexOf(needle);
        if (start < 0) {
            return "";
        }
        int objectStart = object.indexOf('{', start + needle.length());
        if (objectStart < 0) {
            return "";
        }
        int objectEnd = matchingObjectEnd(object, objectStart);
        return objectEnd < 0 ? "" : object.substring(objectStart + 1, objectEnd);
    }

    private int jsonStringEnd(String source, int start) {
        boolean escaped = false;
        for (int i = start; i < source.length(); i++) {
            char current = source.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (current == '\\') {
                escaped = true;
                continue;
            }
            if (current == '"') {
                return i;
            }
        }
        return -1;
    }

    private String unescape(String value) {
        StringBuilder builder = new StringBuilder();
        boolean escaped = false;
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (escaped) {
                builder.append(current);
                escaped = false;
                continue;
            }
            if (current == '\\') {
                escaped = true;
                continue;
            }
            builder.append(current);
        }
        if (escaped) {
            builder.append('\\');
        }
        return builder.toString();
    }

    private record ParsedEvent(long sequence, String type, String fields, String occurredAt) {}
}
