package kr.lunaf.cloudislands.velocity;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import kr.lunaf.cloudislands.api.model.CreateIslandResult;
import kr.lunaf.cloudislands.api.model.IslandLocation;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.api.model.IslandRole;
import kr.lunaf.cloudislands.api.model.RouteTicket;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.protocol.session.PlayerRouteSession;
import kr.lunaf.cloudislands.velocity.message.VelocityMessages;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;

public final class VelocityRoutingController {
    private static final Pattern EVENT = Pattern.compile("\\{[^}]*\"seq\":(\\d+)[^}]*\"type\":\"([^\"]+)\"[^}]*\"fields\":\\{([^}]*)}[^}]*\"occurredAt\":\"([^\"]+)\"[^}]*}");
    private static final Pattern FIELD = Pattern.compile("\"([^\"]+)\":\"([^\"]*)\"");
    private static final int EVENT_BATCH_SIZE = 512;
    private static final long PLAYER_ROUTE_COOLDOWN_MILLIS = 1_500L;

    private final ProxyServer proxy;
    private final CoreApiClient coreApiClient;
    private final String fallbackServer;
    private final int routeWaitSeconds;
    private final String islandPool;
    private final int routeTicketTtlSeconds;
    private final boolean hideNodeNames;
    private final boolean useActionBar;
    private final boolean useBossBarLoading;
    private final VelocityMessages messages;
    private final AtomicLong routeAttempts = new AtomicLong();
    private final AtomicLong routeSuccesses = new AtomicLong();
    private final AtomicLong routeFailures = new AtomicLong();
    private final AtomicLong fallbackTransfers = new AtomicLong();
    private final Set<String> seenEvents = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> recentRouteRequests = new ConcurrentHashMap<>();
    private ScheduledTask eventPollTask;
    private long lastEventSequence;

    public VelocityRoutingController(ProxyServer proxy, CoreApiClient coreApiClient, String fallbackServer) {
        this(proxy, coreApiClient, fallbackServer, 20);
    }

    public VelocityRoutingController(ProxyServer proxy, CoreApiClient coreApiClient, String fallbackServer, int routeWaitSeconds) {
        this(proxy, coreApiClient, fallbackServer, routeWaitSeconds, true, true);
    }

    public VelocityRoutingController(ProxyServer proxy, CoreApiClient coreApiClient, String fallbackServer, int routeWaitSeconds, boolean useActionBar, boolean useBossBarLoading) {
        this(proxy, coreApiClient, fallbackServer, routeWaitSeconds, useActionBar, useBossBarLoading, true);
    }

    public VelocityRoutingController(ProxyServer proxy, CoreApiClient coreApiClient, String fallbackServer, int routeWaitSeconds, boolean useActionBar, boolean useBossBarLoading, boolean hideNodeNames) {
        this(proxy, coreApiClient, fallbackServer, routeWaitSeconds, useActionBar, useBossBarLoading, hideNodeNames, "island", 30);
    }

    public VelocityRoutingController(ProxyServer proxy, CoreApiClient coreApiClient, String fallbackServer, int routeWaitSeconds, boolean useActionBar, boolean useBossBarLoading, boolean hideNodeNames, String islandPool, int routeTicketTtlSeconds) {
        this(proxy, coreApiClient, fallbackServer, routeWaitSeconds, useActionBar, useBossBarLoading, hideNodeNames, islandPool, routeTicketTtlSeconds, VelocityMessages.defaults());
    }

    public VelocityRoutingController(ProxyServer proxy, CoreApiClient coreApiClient, String fallbackServer, int routeWaitSeconds, boolean useActionBar, boolean useBossBarLoading, boolean hideNodeNames, String islandPool, int routeTicketTtlSeconds, VelocityMessages messages) {
        this.proxy = proxy;
        this.coreApiClient = coreApiClient;
        this.fallbackServer = fallbackServer;
        this.routeWaitSeconds = Math.max(1, routeWaitSeconds);
        this.islandPool = islandPool == null || islandPool.isBlank() ? "island" : islandPool;
        this.routeTicketTtlSeconds = Math.max(1, routeTicketTtlSeconds);
        this.hideNodeNames = hideNodeNames;
        this.useActionBar = useActionBar;
        this.useBossBarLoading = useBossBarLoading;
        this.messages = messages == null ? VelocityMessages.defaults() : messages;
    }

    public void createIsland(Player player, String templateId) {
        if (!allowRouteRequest(player)) {
            return;
        }
        coreApiClient.createIsland(player.getUniqueId(), templateId).thenAccept(result -> {
            if (result == null || !result.accepted()) {
                String code = result == null ? "FAILED" : result.code();
                player.sendMessage(Component.text(messageForCreateFailure(code)));
                return;
            }
            actionBar(player, messages.text("island-create-starting"));
            if (result.ticket() != null) {
                route(player, result.ticket(), "섬으로 이동하지 못했습니다.");
            }
        }).exceptionally(error -> {
            player.sendMessage(messages.component("island-service-maintenance"));
            return null;
        });
    }

    public void deleteIsland(Player player, UUID islandId) {
        coreApiClient.deleteIsland(player.getUniqueId(), islandId).thenAccept(result -> {
            if (result != null && result.accepted()) {
                player.sendMessage(Component.text("섬을 삭제했습니다."));
                return;
            }
            player.sendMessage(Component.text("섬을 삭제할 수 없습니다."));
        }).exceptionally(error -> {
            player.sendMessage(Component.text("섬 삭제를 처리하지 못했습니다."));
            return null;
        });
    }

    public String statusSummary() {
        return "CloudIslands Velocity router online, fallback=" + fallbackServer
            + ", islandPool=" + islandPool
            + ", routeWaitSeconds=" + routeWaitSeconds
            + ", routeTicketTtlSeconds=" + routeTicketTtlSeconds
            + ", onlinePlayers=" + proxy.getPlayerCount()
            + ", actionbar=" + useActionBar
            + ", bossbarLoading=" + useBossBarLoading
            + ", hideNodeNames=" + hideNodeNames
            + ", routeAttempts=" + routeAttempts.get()
            + ", routeSuccesses=" + routeSuccesses.get()
            + ", routeFailures=" + routeFailures.get();
    }

    public String routingMetricsText() {
        return ""
            + "cloudislands_velocity_route_attempts_total " + routeAttempts.get() + "\n"
            + "cloudislands_velocity_route_success_total " + routeSuccesses.get() + "\n"
            + "cloudislands_velocity_route_failed_total " + routeFailures.get() + "\n"
            + "cloudislands_velocity_fallback_transfers_total " + fallbackTransfers.get() + "\n";
    }

    public void resetIsland(Player player, UUID islandId, String reason) {
        sendBodyResult(player, coreApiClient.resetIsland(islandId, player.getUniqueId(), reason).thenApply(body -> actionResultMessage("Island reset", islandId.toString(), body)), "섬 리셋을 요청하지 못했습니다.");
    }

    public void showMyIsland(Player player) {
        sendBodyResult(player, coreApiClient.islandInfoByOwner(player.getUniqueId()).thenApply(this::islandInfoMessage), "섬 정보를 불러오지 못했습니다.");
    }

    public void showIslandSettings(Player player, UUID islandId) {
        sendBodyResult(player, coreApiClient.islandInfo(islandId).thenApply(this::islandInfoMessage), "섬 설정을 불러오지 못했습니다.");
    }

    public void showIslandLevel(Player player, UUID islandId) {
        sendBodyResult(player, coreApiClient.islandInfo(islandId).thenApply(body -> islandStatMessage("섬 레벨", "level", body)), "섬 레벨을 불러오지 못했습니다.");
    }

    public void showIslandWorth(Player player, UUID islandId) {
        sendBodyResult(player, coreApiClient.islandInfo(islandId).thenApply(body -> islandStatMessage("섬 가치", "worth", body)), "섬 가치를 불러오지 못했습니다.");
    }

    public void showIslandSize(Player player, UUID islandId) {
        sendBodyResult(player, coreApiClient.islandInfo(islandId).thenApply(body -> islandStatMessage("섬 크기", "size", body)), "섬 크기를 불러오지 못했습니다.");
    }

    public void showIslandBorder(Player player, UUID islandId) {
        sendBodyResult(player, coreApiClient.islandInfo(islandId).thenApply(body -> islandStatMessage("섬 경계", "border", body)), "섬 경계를 불러오지 못했습니다.");
    }

    public void showBiome(Player player, UUID islandId) {
        sendBodyResult(player, coreApiClient.islandBiome(islandId).thenApply(this::biomeInfoMessage), "섬 바이옴을 불러오지 못했습니다.");
    }

    public void setBiome(Player player, UUID islandId, String biomeKey) {
        sendActionResult(player, coreApiClient.setIslandBiome(islandId, player.getUniqueId(), biomeKey), "섬 바이옴을 변경했습니다.", "섬 바이옴을 변경하지 못했습니다.");
    }

    public void routeHome(Player player) {
        routeHome(player, "default");
    }

    public void routeHome(Player player, String homeName) {
        if (!allowRouteRequest(player)) {
            return;
        }
        routeFuture(player, coreApiClient.createHomeTicket(player.getUniqueId(), homeName), "현재 섬 서비스 일부 기능이 점검 중입니다.");
    }

    public void routeVisit(Player player, UUID targetIslandId) {
        if (!allowRouteRequest(player)) {
            return;
        }
        routeFuture(player, coreApiClient.createVisitTicket(player.getUniqueId(), targetIslandId), "현재 섬 서비스가 혼잡합니다. 잠시 후 다시 시도해주세요.");
    }

    public void routeVisitOwner(Player player, UUID ownerUuid) {
        if (ownerUuid.equals(new UUID(0L, 0L))) {
            player.sendMessage(Component.text("방문할 플레이어를 찾을 수 없습니다."));
            return;
        }
        if (!allowRouteRequest(player)) {
            return;
        }
        routeFuture(player, coreApiClient.createVisitTicketForOwner(player.getUniqueId(), ownerUuid), "해당 섬에 방문할 수 없습니다.");
    }

    public void routeVisitName(Player player, String islandName) {
        if (islandName == null || islandName.isBlank()) {
            player.sendMessage(Component.text("방문할 섬 이름을 입력해주세요."));
            return;
        }
        if (!allowRouteRequest(player)) {
            return;
        }
        routeFuture(player, coreApiClient.createVisitTicket(player.getUniqueId(), islandName), "해당 섬에 방문할 수 없습니다.");
    }

    public void routeVisitNamedTarget(Player player, String targetName) {
        if (targetName == null || targetName.isBlank()) {
            player.sendMessage(Component.text("방문할 대상 이름을 입력해주세요."));
            return;
        }
        coreApiClient.playerInfoByName(targetName).thenAccept(body -> {
            UUID primaryIslandId = parseUuid(jsonValue(body, "primaryIslandId"));
            if (primaryIslandId.equals(new UUID(0L, 0L))) {
                routeVisitName(player, targetName);
                return;
            }
            UUID ownerUuid = parseUuid(jsonValue(body, "playerUuid"));
            if (ownerUuid.equals(new UUID(0L, 0L))) {
                routeVisit(player, primaryIslandId);
                return;
            }
            routeVisitOwner(player, ownerUuid);
        }).exceptionally(error -> {
            routeVisitName(player, targetName);
            return null;
        });
    }

    public void recordPlayerProfile(Player player) {
        coreApiClient.touchPlayerProfile(player.getUniqueId(), player.getUsername())
            .exceptionally(error -> null);
    }

    public void routePendingSession(Player player) {
        coreApiClient.findAnyRouteSession(player.getUniqueId()).thenAccept(session ->
            session.ifPresent(value -> connectPendingSession(player, value))
        ).exceptionally(error -> null);
    }

    private void connectPendingSession(Player player, PlayerRouteSession session) {
        String targetServerName = session.targetServerName() == null || session.targetServerName().isBlank() ? session.targetNode() : session.targetServerName();
        RegisteredServer server = findServer(targetServerName);
        if (server == null) {
            coreApiClient.clearRoute(session.playerUuid(), session.ticketId(), "PENDING_TARGET_NOT_FOUND").exceptionally(error -> null);
            fallback(player, "이전 섬 이동 경로를 찾을 수 없어 로비로 이동합니다.");
            return;
        }
        actionBar(player, "이전 섬 이동을 이어갑니다.");
        player.createConnectionRequest(server).connectWithIndication().thenAccept(success -> {
            if (!success) {
                coreApiClient.clearRoute(session.playerUuid(), session.ticketId(), "PENDING_CONNECT_FAILED").exceptionally(error -> null);
                fallback(player, "이전 섬 이동을 이어가지 못해 로비로 이동합니다.");
            }
        }).exceptionally(error -> {
            coreApiClient.clearRoute(session.playerUuid(), session.ticketId(), "PENDING_CONNECT_EXCEPTION").exceptionally(ignored -> null);
            fallback(player, "이전 섬 이동을 이어가지 못해 로비로 이동합니다.");
            return null;
        });
    }

    public void listMyIslands(Player player) {
        coreApiClient.listPlayerIslands(player.getUniqueId())
            .thenAccept(body -> player.sendMessage(Component.text(playerIslandListMessage(body))))
            .exceptionally(error -> {
                player.sendMessage(Component.text("내 섬 목록을 불러오지 못했습니다."));
                return null;
            });
    }

    private String playerIslandListMessage(String body) {
        java.util.List<String> entries = new java.util.ArrayList<>();
        int index = 0;
        while (body != null && index < body.length()) {
            int objectStart = body.indexOf('{', index);
            if (objectStart < 0) {
                break;
            }
            int objectEnd = body.indexOf('}', objectStart);
            if (objectEnd < 0) {
                break;
            }
            String object = body.substring(objectStart, objectEnd + 1);
            String islandId = islandText(object, "islandId");
            if (!islandId.isBlank()) {
                String name = islandText(object, "name");
                String role = islandText(object, "role");
                long level = islandNumber(object, "level");
                entries.add((name.isBlank() ? "이름 없는 섬" : name) + " (ID=" + shortId(islandId) + ", 역할=" + (role.isBlank() ? "MEMBER" : role) + ", 레벨=" + level + ")");
            }
            index = objectEnd + 1;
        }
        return entries.isEmpty() ? "속한 섬이 없습니다." : "내 섬 목록: " + String.join(" / ", entries);
    }

    private String islandText(String body, String key) {
        String needle = "\"" + key + "\":\"";
        int start = body == null ? -1 : body.indexOf(needle);
        if (start < 0) {
            return "";
        }
        start += needle.length();
        int end = body.indexOf('"', start);
        return end < start ? "" : body.substring(start, end).replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private long islandNumber(String body, String key) {
        String needle = "\"" + key + "\":";
        int start = body == null ? -1 : body.indexOf(needle);
        if (start < 0) {
            return 0L;
        }
        start += needle.length();
        int end = start;
        while (end < body.length() && Character.isDigit(body.charAt(end))) {
            end++;
        }
        try {
            return Long.parseLong(body.substring(start, end));
        } catch (NumberFormatException exception) {
            return 0L;
        }
    }

    public void routeRandomVisit(Player player) {
        if (!allowRouteRequest(player)) {
            return;
        }
        routeFuture(player, coreApiClient.createRandomVisitTicket(player.getUniqueId()), "방문 가능한 공개 섬을 찾지 못했습니다.");
    }

    public void listPublicIslands(Player player, int limit) {
        sendBodyResult(player, coreApiClient.listPublicIslands(Math.max(1, Math.min(limit, 100))).thenApply(this::publicIslandListMessage), "공개 섬 목록을 불러오지 못했습니다.");
    }

    public void routeWarp(Player player, UUID targetIslandId, String warpName) {
        if (!allowRouteRequest(player)) {
            return;
        }
        routeFuture(player, coreApiClient.createWarpTicket(player.getUniqueId(), targetIslandId, warpName), "해당 워프로 이동할 수 없습니다.");
    }

    public void listWarps(Player player, UUID islandId) {
        sendBodyResult(player, coreApiClient.listIslandWarps(islandId).thenApply(body -> warpListMessage("섬 워프", body)), "섬 워프를 불러오지 못했습니다.");
    }

    public void listPublicWarps(Player player) {
        sendBodyResult(player, coreApiClient.listPublicWarps(27).thenApply(body -> warpListMessage("공개 워프", body)), "공개 워프를 불러오지 못했습니다.");
    }

    public void setWarp(Player player, UUID islandId, String name, boolean publicAccess) {
        IslandLocation defaultLocation = new IslandLocation("ci_shard_001", 0.5D, 100.0D, 0.5D, 180.0F, 0.0F);
        sendActionResult(player, coreApiClient.setIslandWarp(islandId, player.getUniqueId(), name, defaultLocation, publicAccess), "섬 워프를 설정했습니다.", "섬 워프를 설정하지 못했습니다.");
    }

    public void deleteWarp(Player player, UUID islandId, String name) {
        sendActionResult(player, coreApiClient.deleteIslandWarp(islandId, player.getUniqueId(), name), "섬 워프를 삭제했습니다.", "섬 워프를 삭제하지 못했습니다.");
    }

    public void setWarpPublicAccess(Player player, UUID islandId, String name, boolean publicAccess) {
        sendActionResult(player, coreApiClient.setIslandWarpPublicAccess(islandId, player.getUniqueId(), name, publicAccess), publicAccess ? "섬 워프를 공개했습니다." : "섬 워프를 비공개로 변경했습니다.", "섬 워프 공개 상태를 변경하지 못했습니다.");
    }

    public void invite(Player player, UUID islandId, UUID targetUuid) {
        sendBodyResult(player, coreApiClient.createIslandInvite(islandId, player.getUniqueId(), targetUuid).thenApply(this::inviteCreateMessage), "초대를 생성하지 못했습니다.");
    }

    public void inviteTarget(Player player, UUID islandId, String target) {
        resolvePlayerUuid(target).thenAccept(targetUuid -> {
            if (targetUuid.equals(new UUID(0L, 0L))) {
                player.sendMessage(Component.text("초대할 플레이어를 찾지 못했습니다."));
                return;
            }
            invite(player, islandId, targetUuid);
        }).exceptionally(error -> {
            player.sendMessage(Component.text("초대할 플레이어를 찾지 못했습니다."));
            return null;
        });
    }

    public void listInvites(Player player) {
        coreApiClient.listPendingInvites(player.getUniqueId()).thenAccept(body -> player.sendMessage(Component.text(inviteListMessage(body)))).exceptionally(error -> {
            player.sendMessage(Component.text("초대 목록을 불러오지 못했습니다."));
            return null;
        });
    }

    public void acceptInvite(Player player, UUID inviteId) {
        sendInviteActionResult(player, coreApiClient.acceptIslandInviteResult(inviteId, player.getUniqueId()), "섬 초대를 수락했습니다.", "섬 초대를 수락하지 못했습니다.");
    }

    public void acceptInviteTarget(Player player, String target) {
        resolveInviteTarget(player, target).thenAccept(inviteId -> {
            if (inviteId.equals(new UUID(0L, 0L))) {
                player.sendMessage(Component.text("대상 초대를 찾지 못했습니다."));
                return;
            }
            acceptInvite(player, inviteId);
        }).exceptionally(error -> {
            player.sendMessage(Component.text("대상 초대를 찾지 못했습니다."));
            return null;
        });
    }

    public void declineInvite(Player player, UUID inviteId) {
        sendInviteActionResult(player, coreApiClient.declineIslandInviteResult(inviteId, player.getUniqueId()), "섬 초대를 거절했습니다.", "섬 초대를 거절하지 못했습니다.");
    }

    public void declineInviteTarget(Player player, String target) {
        resolveInviteTarget(player, target).thenAccept(inviteId -> {
            if (inviteId.equals(new UUID(0L, 0L))) {
                player.sendMessage(Component.text("대상 초대를 찾지 못했습니다."));
                return;
            }
            declineInvite(player, inviteId);
        }).exceptionally(error -> {
            player.sendMessage(Component.text("대상 초대를 찾지 못했습니다."));
            return null;
        });
    }

    public void listMembers(Player player, UUID islandId) {
        sendBodyResult(player, coreApiClient.listIslandMembers(islandId).thenApply(this::memberListMessage), "멤버 목록을 불러오지 못했습니다.");
    }

    public void setRole(Player player, UUID islandId, UUID targetUuid, IslandRole role) {
        sendActionResult(player, coreApiClient.setIslandMember(islandId, player.getUniqueId(), targetUuid, role), "섬 멤버 역할을 변경했습니다.", "섬 멤버 역할을 변경하지 못했습니다.");
    }

    public void setRoleTarget(Player player, UUID islandId, String target, IslandRole role) {
        resolvePlayerUuid(target).thenAccept(targetUuid -> {
            if (targetUuid.equals(new UUID(0L, 0L))) {
                player.sendMessage(Component.text("대상 플레이어를 찾지 못했습니다."));
                return;
            }
            setRole(player, islandId, targetUuid, role);
        }).exceptionally(error -> {
            player.sendMessage(Component.text("대상 플레이어를 찾지 못했습니다."));
            return null;
        });
    }

    public void transferOwnership(Player player, UUID islandId, UUID targetUuid) {
        sendActionResult(player, coreApiClient.transferIslandOwnership(islandId, player.getUniqueId(), targetUuid), "섬 소유권을 양도했습니다.", "섬 소유권을 양도하지 못했습니다.");
    }

    public void transferOwnershipTarget(Player player, UUID islandId, String target) {
        resolvePlayerUuid(target).thenAccept(targetUuid -> {
            if (targetUuid.equals(new UUID(0L, 0L))) {
                player.sendMessage(Component.text("대상 플레이어를 찾지 못했습니다."));
                return;
            }
            transferOwnership(player, islandId, targetUuid);
        }).exceptionally(error -> {
            player.sendMessage(Component.text("대상 플레이어를 찾지 못했습니다."));
            return null;
        });
    }

    public void kickMember(Player player, UUID islandId, UUID targetUuid) {
        sendActionResult(player, coreApiClient.removeIslandMember(islandId, player.getUniqueId(), targetUuid), "섬 멤버를 추방했습니다.", "섬 멤버를 추방하지 못했습니다.");
    }

    public void kickMemberTarget(Player player, UUID islandId, String target) {
        resolvePlayerUuid(target).thenAccept(targetUuid -> {
            if (targetUuid.equals(new UUID(0L, 0L))) {
                player.sendMessage(Component.text("대상 플레이어를 찾지 못했습니다."));
                return;
            }
            kickMember(player, islandId, targetUuid);
        }).exceptionally(error -> {
            player.sendMessage(Component.text("대상 플레이어를 찾지 못했습니다."));
            return null;
        });
    }

    public void banVisitor(Player player, UUID islandId, UUID targetUuid, String reason) {
        sendActionResult(player, coreApiClient.banIslandVisitor(islandId, player.getUniqueId(), targetUuid, reason), "방문자를 밴했습니다.", "방문자를 밴하지 못했습니다.");
    }

    public void banVisitorTarget(Player player, UUID islandId, String target, String reason) {
        resolvePlayerUuid(target).thenAccept(targetUuid -> {
            if (targetUuid.equals(new UUID(0L, 0L))) {
                player.sendMessage(Component.text("대상 플레이어를 찾지 못했습니다."));
                return;
            }
            banVisitor(player, islandId, targetUuid, reason);
        }).exceptionally(error -> {
            player.sendMessage(Component.text("대상 플레이어를 찾지 못했습니다."));
            return null;
        });
    }

    public void listBans(Player player, UUID islandId) {
        sendBodyResult(player, coreApiClient.listIslandBans(islandId).thenApply(this::banListMessage), "밴 목록을 불러오지 못했습니다.");
    }

    public void pardonVisitor(Player player, UUID islandId, UUID targetUuid) {
        sendActionResult(player, coreApiClient.pardonIslandVisitor(islandId, player.getUniqueId(), targetUuid), "방문자 밴을 해제했습니다.", "방문자 밴을 해제하지 못했습니다.");
    }

    public void pardonVisitorTarget(Player player, UUID islandId, String target) {
        resolvePlayerUuid(target).thenAccept(targetUuid -> {
            if (targetUuid.equals(new UUID(0L, 0L))) {
                player.sendMessage(Component.text("대상 플레이어를 찾지 못했습니다."));
                return;
            }
            pardonVisitor(player, islandId, targetUuid);
        }).exceptionally(error -> {
            player.sendMessage(Component.text("대상 플레이어를 찾지 못했습니다."));
            return null;
        });
    }

    public void kickVisitor(Player player, UUID islandId, UUID targetUuid) {
        coreApiClient.kickIslandVisitor(islandId, player.getUniqueId(), targetUuid).thenRun(() ->
            player.sendMessage(Component.text("방문자 추방을 요청했습니다."))
        ).exceptionally(error -> {
            player.sendMessage(Component.text("방문자를 추방할 권한이 없거나 처리하지 못했습니다."));
            return null;
        });
    }

    public void kickVisitorTarget(Player player, UUID islandId, String target) {
        resolvePlayerUuid(target).thenAccept(targetUuid -> {
            if (targetUuid.equals(new UUID(0L, 0L))) {
                player.sendMessage(Component.text("대상 플레이어를 찾지 못했습니다."));
                return;
            }
            kickVisitor(player, islandId, targetUuid);
        }).exceptionally(error -> {
            player.sendMessage(Component.text("대상 플레이어를 찾지 못했습니다."));
            return null;
        });
    }

    public void setPublicAccess(Player player, UUID islandId, boolean publicAccess) {
        sendActionResult(player, coreApiClient.setIslandPublicAccess(islandId, player.getUniqueId(), publicAccess), publicAccess ? "섬을 공개로 변경했습니다." : "섬을 비공개로 변경했습니다.", "섬 공개 상태를 변경하지 못했습니다.");
    }

    public void setIslandName(Player player, UUID islandId, String name) {
        if (name == null || name.isBlank()) {
            player.sendMessage(Component.text("새 섬 이름을 입력해주세요."));
            return;
        }
        withResolvedIsland(player, islandId, "이름을 변경할 섬을 찾지 못했습니다.", "섬 이름을 변경하지 못했습니다.",
            resolved -> sendActionResult(player, coreApiClient.setIslandName(resolved, player.getUniqueId(), name), "섬 이름을 변경했습니다.", "섬 이름을 변경하지 못했습니다."));
    }

    public void setFlyFlag(Player player, UUID islandId, boolean enabled) {
        sendActionResult(player, coreApiClient.setIslandFlag(islandId, player.getUniqueId(), kr.lunaf.cloudislands.api.model.IslandFlag.FLY, Boolean.toString(enabled)), enabled ? "섬 비행을 허용했습니다." : "섬 비행을 비활성화했습니다.", "섬 비행 설정을 변경하지 못했습니다.");
    }

    public void setBooleanFlag(Player player, UUID islandId, kr.lunaf.cloudislands.api.model.IslandFlag flag, boolean enabled, String label) {
        sendActionResult(player, coreApiClient.setIslandFlag(islandId, player.getUniqueId(), flag, Boolean.toString(enabled)), "섬 " + label + " 설정을 " + (enabled ? "켰습니다." : "껐습니다."), "섬 " + label + " 설정을 변경하지 못했습니다.");
    }

    public void listFlags(Player player, UUID islandId) {
        if (rejectExplicitIslandLookup(player, islandId)) {
            return;
        }
        withResolvedIsland(player, islandId, "플래그를 확인할 섬을 찾지 못했습니다.", "섬 플래그를 불러오지 못했습니다.",
            resolved -> sendBodyResult(player, coreApiClient.listIslandFlags(resolved).thenApply(this::flagListMessage), "섬 플래그를 불러오지 못했습니다."));
    }

    public void listHomes(Player player, UUID islandId) {
        sendBodyResult(player, coreApiClient.listIslandHomes(islandId).thenApply(this::homeListMessage), "섬 홈을 불러오지 못했습니다.");
    }

    public void setHome(Player player, UUID islandId, String name) {
        IslandLocation defaultHome = new IslandLocation("ci_shard_001", 0.5D, 100.0D, 0.5D, 180.0F, 0.0F);
        sendActionResult(player, coreApiClient.setIslandHome(islandId, player.getUniqueId(), name, defaultHome), "섬 홈을 설정했습니다.", "섬 홈을 설정하지 못했습니다.");
    }

    public void setLocked(Player player, UUID islandId, boolean locked) {
        sendActionResult(player, coreApiClient.setIslandLocked(islandId, player.getUniqueId(), locked), locked ? "섬을 잠금 상태로 변경했습니다." : "섬 잠금을 해제했습니다.", "섬 잠금 상태를 변경하지 못했습니다.");
    }

    public void listPermissions(Player player, UUID islandId) {
        if (rejectExplicitIslandLookup(player, islandId)) {
            return;
        }
        withResolvedIsland(player, islandId, "권한을 확인할 섬을 찾지 못했습니다.", "섬 권한을 불러오지 못했습니다.",
            resolved -> sendBodyResult(player, coreApiClient.listIslandPermissions(resolved).thenApply(this::permissionListMessage), "섬 권한을 불러오지 못했습니다."));
    }

    public void setPermission(Player player, UUID islandId, IslandRole role, IslandPermission permission, boolean allowed) {
        sendActionResult(player, coreApiClient.setIslandPermission(islandId, player.getUniqueId(), role, permission, allowed), "섬 권한을 변경했습니다.", "섬 권한을 변경하지 못했습니다.");
    }

    public void listRoles(Player player, UUID islandId) {
        if (rejectExplicitIslandLookup(player, islandId)) {
            return;
        }
        withResolvedIsland(player, islandId, "역할을 확인할 섬을 찾지 못했습니다.", "섬 역할을 불러오지 못했습니다.",
            resolved -> sendBodyResult(player, coreApiClient.listIslandRoles(resolved).thenApply(this::roleListMessage), "섬 역할을 불러오지 못했습니다."));
    }

    public void upsertRole(Player player, UUID islandId, IslandRole role, int weight, String displayName) {
        sendBodyResult(player, coreApiClient.upsertIslandRole(islandId, player.getUniqueId(), role, weight, displayName).thenApply(body -> "섬 역할 저장 완료: " + jsonValue(body, "role") + " weight=" + longValue(body, "weight") + " name=" + jsonValue(body, "displayName")), "섬 역할을 저장하지 못했습니다.");
    }

    public void resetRole(Player player, UUID islandId, IslandRole role) {
        sendBodyResult(player, coreApiClient.resetIslandRole(islandId, player.getUniqueId(), role).thenApply(body -> "섬 역할 초기화 완료: " + jsonValue(body, "role")), "섬 역할을 초기화하지 못했습니다.");
    }

    public void listIslandLogs(Player player, UUID islandId) {
        if (rejectExplicitIslandLookup(player, islandId)) {
            return;
        }
        withResolvedIsland(player, islandId, "로그를 확인할 섬을 찾지 못했습니다.", "섬 로그를 불러오지 못했습니다.",
            resolved -> sendBodyResult(player, coreApiClient.listIslandLogs(resolved, 30).thenApply(this::islandLogListMessage), "섬 로그를 불러오지 못했습니다."));
    }

    public void showBank(Player player, UUID islandId) {
        if (rejectExplicitIslandLookup(player, islandId)) {
            return;
        }
        withResolvedIsland(player, islandId, "은행을 확인할 섬을 찾지 못했습니다.", "섬 은행을 불러오지 못했습니다.",
            resolved -> sendBodyResult(player, coreApiClient.islandBank(resolved).thenApply(this::bankInfoMessage), "섬 은행을 불러오지 못했습니다."));
    }

    public void depositBank(Player player, UUID islandId, String amount) {
        player.sendMessage(Component.text("섬 은행 입금은 경제 플러그인 연동이 필요한 작업이라 Paper Agent에서만 처리합니다."));
    }

    public void withdrawBank(Player player, UUID islandId, String amount) {
        player.sendMessage(Component.text("섬 은행 출금은 경제 플러그인 연동이 필요한 작업이라 Paper Agent에서만 처리합니다."));
    }

    public void showLevelRanking(Player player) {
        showLevelRanking(player, 10);
    }

    public void showLevelRanking(Player player, int limit) {
        sendBodyResult(player, coreApiClient.topIslandsByLevel(Math.max(1, Math.min(limit, 100))).thenApply(body -> rankingListMessage("섬 랭킹", body)), "랭킹을 불러오지 못했습니다.");
    }

    public void showWorthRanking(Player player) {
        showWorthRanking(player, 10);
    }

    public void showWorthRanking(Player player, int limit) {
        sendBodyResult(player, coreApiClient.topIslandsByWorth(Math.max(1, Math.min(limit, 100))).thenApply(body -> rankingListMessage("가치 랭킹", body)), "가치 랭킹을 불러오지 못했습니다.");
    }

    public void recalculateLevel(Player player, UUID islandId) {
        withResolvedIsland(player, islandId, "레벨을 계산할 섬을 찾지 못했습니다.", "레벨 계산을 시작하지 못했습니다.",
            resolved -> sendBodyResult(player, coreApiClient.recalculateIslandLevel(resolved, player.getUniqueId()).thenApply(this::levelRecalculationMessage), "레벨 계산을 시작하지 못했습니다."));
    }

    public void listUpgradeRules(Player player) {
        sendBodyResult(player, coreApiClient.listUpgradeRules().thenApply(this::upgradeRulesMessage), "업그레이드 목록을 불러오지 못했습니다.");
    }

    public void listUpgrades(Player player, UUID islandId) {
        if (rejectExplicitIslandLookup(player, islandId)) {
            return;
        }
        withResolvedIsland(player, islandId, "업그레이드를 확인할 섬을 찾지 못했습니다.", "섬 업그레이드를 불러오지 못했습니다.",
            resolved -> sendBodyResult(player, coreApiClient.listIslandUpgrades(resolved).thenApply(this::upgradeListMessage), "섬 업그레이드를 불러오지 못했습니다."));
    }

    public void showGenerator(Player player, UUID islandId) {
        if (rejectExplicitIslandLookup(player, islandId)) {
            return;
        }
        withResolvedIsland(player, islandId, "생성기를 확인할 섬을 찾지 못했습니다.", "섬 생성기를 불러오지 못했습니다.",
            resolved -> sendBodyResult(player, coreApiClient.listIslandUpgrades(resolved).thenApply(this::generatorInfoMessage), "섬 생성기를 불러오지 못했습니다."));
    }

    public void purchaseUpgrade(Player player, UUID islandId, String upgradeKey) {
        withResolvedIsland(player, islandId, "업그레이드할 섬을 찾지 못했습니다.", "업그레이드에 실패했습니다.",
            resolved -> sendBodyResult(player, coreApiClient.purchaseIslandUpgrade(resolved, player.getUniqueId(), upgradeKey).thenApply(this::upgradePurchaseMessage), "업그레이드에 실패했습니다."));
    }

    public void listMissions(Player player, UUID islandId) {
        if (rejectExplicitIslandLookup(player, islandId)) {
            return;
        }
        withResolvedIsland(player, islandId, "미션을 확인할 섬을 찾지 못했습니다.", "미션 목록을 불러오지 못했습니다.",
            resolved -> sendBodyResult(player, coreApiClient.listIslandMissions(resolved, "MISSION").thenApply(body -> missionListMessage("섬 미션", body)), "미션 목록을 불러오지 못했습니다."));
    }

    public void listChallenges(Player player, UUID islandId) {
        if (rejectExplicitIslandLookup(player, islandId)) {
            return;
        }
        withResolvedIsland(player, islandId, "챌린지를 확인할 섬을 찾지 못했습니다.", "챌린지 목록을 불러오지 못했습니다.",
            resolved -> sendBodyResult(player, coreApiClient.listIslandMissions(resolved, "CHALLENGE").thenApply(body -> missionListMessage("섬 챌린지", body)), "챌린지 목록을 불러오지 못했습니다."));
    }

    public void completeMission(Player player, UUID islandId, String missionKey) {
        withResolvedIsland(player, islandId, "미션을 완료할 섬을 찾지 못했습니다.", "미션을 완료하지 못했습니다.",
            resolved -> sendBodyResult(player, coreApiClient.completeIslandMission(resolved, player.getUniqueId(), missionKey, "MISSION").thenApply(body -> missionResultMessage("섬 미션", body)), "미션을 완료하지 못했습니다."));
    }

    public void completeChallenge(Player player, UUID islandId, String missionKey) {
        withResolvedIsland(player, islandId, "챌린지를 완료할 섬을 찾지 못했습니다.", "챌린지를 완료하지 못했습니다.",
            resolved -> sendBodyResult(player, coreApiClient.completeIslandMission(resolved, player.getUniqueId(), missionKey, "CHALLENGE").thenApply(body -> missionResultMessage("섬 챌린지", body)), "챌린지를 완료하지 못했습니다."));
    }

    public void listLimits(Player player, UUID islandId) {
        if (rejectExplicitIslandLookup(player, islandId)) {
            return;
        }
        withResolvedIsland(player, islandId, "제한을 확인할 섬을 찾지 못했습니다.", "섬 제한을 불러오지 못했습니다.",
            resolved -> sendBodyResult(player, coreApiClient.listIslandLimits(resolved).thenApply(this::limitListMessage), "섬 제한을 불러오지 못했습니다."));
    }

    public void setLimit(Player player, UUID islandId, String limitKey, long value) {
        withResolvedIsland(player, islandId, "제한을 변경할 섬을 찾지 못했습니다.", "섬 제한을 변경하지 못했습니다.",
            resolved -> sendBodyResult(player, coreApiClient.setIslandLimit(resolved, player.getUniqueId(), limitKey, value).thenApply(this::limitResultMessage), "섬 제한을 변경하지 못했습니다."));
    }

    public void sendIslandChat(Player player, UUID islandId, String channel, String message) {
        if (message == null || message.isBlank()) {
            player.sendMessage(Component.text("보낼 메시지를 입력해주세요."));
            return;
        }
        String label = channel.equalsIgnoreCase("TEAM") ? "팀 채팅" : "섬 채팅";
        String stripped = message.strip();
        withResolvedIsland(player, islandId, "채팅을 보낼 섬을 찾지 못했습니다.", label + "을 전송하지 못했습니다.",
            resolved -> sendIslandChatResolved(player, resolved, channel, stripped, label));
    }

    private void sendIslandChatResolved(Player player, UUID islandId, String channel, String message, String label) {
        sendBodyResult(player, coreApiClient.sendIslandChat(islandId, player.getUniqueId(), channel, message).thenApply(body -> chatResultMessage(label, body)), label + "을 전송하지 못했습니다.");
    }

    private boolean rejectExplicitIslandLookup(Player player, UUID islandId) {
        if (islandId == null || islandId.equals(new UUID(0L, 0L))) {
            return false;
        }
        player.sendMessage(Component.text("플레이어 명령에서는 섬 UUID 직접 조회를 사용할 수 없습니다."));
        return true;
    }

    private void withResolvedIsland(Player player, UUID islandId, String missingMessage, String failureMessage, Consumer<UUID> action) {
        UUID emptyIslandId = new UUID(0L, 0L);
        if (islandId != null && !islandId.equals(emptyIslandId)) {
            action.accept(islandId);
            return;
        }
        coreApiClient.islandInfoByOwner(player.getUniqueId()).thenAccept(body -> {
            UUID resolvedIslandId = parseUuid(jsonValue(body, "islandId"));
            if (resolvedIslandId.equals(emptyIslandId)) {
                player.sendMessage(Component.text(missingMessage));
                return;
            }
            action.accept(resolvedIslandId);
        }).exceptionally(error -> {
            player.sendMessage(Component.text(failureMessage));
            return null;
        });
    }

    public void listSnapshots(Player player, UUID islandId) {
        withResolvedIsland(player, islandId, "스냅샷을 확인할 섬을 찾지 못했습니다.", "스냅샷 목록을 불러오지 못했습니다.",
            resolved -> sendBodyResult(player, coreApiClient.listIslandSnapshots(resolved, 20).thenApply(this::snapshotListMessage), "스냅샷 목록을 불러오지 못했습니다."));
    }

    public void snapshot(Player player, UUID islandId, String reason) {
        withResolvedIsland(player, islandId, "스냅샷을 만들 섬을 찾지 못했습니다.", "섬 스냅샷을 요청하지 못했습니다.",
            resolved -> sendActionResult(player, coreApiClient.requestIslandSnapshot(resolved, reason), "섬 스냅샷을 요청했습니다.", "섬 스냅샷을 요청하지 못했습니다."));
    }

    public void restore(Player player, UUID islandId, long snapshotNo) {
        withResolvedIsland(player, islandId, "복원할 섬을 찾지 못했습니다.", "섬 복원을 요청하지 못했습니다.",
            resolved -> sendActionResult(player, coreApiClient.restoreIslandSnapshot(resolved, snapshotNo), "섬 복원을 요청했습니다.", "섬 복원을 요청하지 못했습니다."));
    }

    public void listJobs(Player player) {
        sendBodyResult(player, coreApiClient.listJobs().thenApply(this::jobListMessage), "작업 목록을 불러오지 못했습니다.");
    }

    public void retryJob(Player player, UUID jobId) {
        sendBodyResult(player, coreApiClient.retryJob(jobId).thenApply(body -> jobActionMessage("retry", body)), "작업 재시도를 요청하지 못했습니다.");
    }

    public void cancelJob(Player player, UUID jobId) {
        sendBodyResult(player, coreApiClient.cancelJob(jobId).thenApply(body -> jobActionMessage("cancel", body)), "작업 취소를 요청하지 못했습니다.");
    }

    public void recoverJobs(Player player, String nodeId, long minIdleMillis, int maxJobs) {
        sendBodyResult(player, coreApiClient.recoverJobs(nodeId, minIdleMillis, maxJobs).thenApply(body -> jobActionMessage("recover", body)), "작업 복구를 요청하지 못했습니다.");
    }

    public void listNodes(Player player) {
        sendBodyResult(player, coreApiClient.listNodes().thenApply(this::nodeListSummaryMessage), "노드 목록을 불러오지 못했습니다.");
    }

    public void nodeInfo(Player player, String nodeId) {
        sendBodyResult(player, coreApiClient.nodeInfo(nodeId).thenApply(this::appendLevelScanSummary), "노드 정보를 불러오지 못했습니다.");
    }

    public void nodeIslands(Player player, String nodeId, int limit) {
        sendBodyResult(player, coreApiClient.nodeIslands(nodeId, Math.max(1, Math.min(limit, 200))).thenApply(this::nodeIslandListMessage), "노드 섬 현황을 불러오지 못했습니다.");
    }

    public void drainNode(Player player, String nodeId) {
        sendBodyResult(player, coreApiClient.drainNode(nodeId).thenApply(body -> nodeActionSummaryMessage("Node drain", nodeId, body)), "노드 drain을 요청하지 못했습니다.");
    }

    public void undrainNode(Player player, String nodeId) {
        sendBodyResult(player, coreApiClient.undrainNode(nodeId).thenApply(body -> nodeActionSummaryMessage("Node undrain", nodeId, body)), "노드 undrain을 요청하지 못했습니다.");
    }

    public void sweepNode(Player player, String nodeId) {
        sendBodyResult(player, coreApiClient.sweepNode(nodeId).thenApply(this::nodeSweepMessage), "노드 장애 스윕을 요청하지 못했습니다.");
    }

    public void kickAllNode(Player player, String nodeId, String reason) {
        coreApiClient.kickAllNode(nodeId, reason).thenAccept(body -> {
            int moved = moveNodePlayersToFallback(nodeId);
            player.sendMessage(Component.text(nodeActionSummaryMessage("Node kickall", nodeId, body) + " lobbyMoved=" + moved));
        }).exceptionally(error -> {
            player.sendMessage(Component.text("노드 kickall을 요청하지 못했습니다."));
            return null;
        });
    }

    public void shutdownSafeNode(Player player, String nodeId, String reason) {
        coreApiClient.shutdownNodeSafely(nodeId, reason).thenAccept(body -> {
            int moved = moveNodePlayersToFallback(nodeId);
            player.sendMessage(Component.text(nodeActionSummaryMessage("Node shutdown-safe", nodeId, body) + " lobbyMoved=" + moved));
        }).exceptionally(error -> {
            player.sendMessage(Component.text("노드 shutdown-safe를 요청하지 못했습니다."));
            return null;
        });
    }

    public void activateIsland(Player player, UUID islandId) {
        sendBodyResult(player, coreApiClient.activateIsland(islandId).thenApply(body -> actionResultMessage("Island activate", islandId.toString(), body)), "섬 활성화를 요청하지 못했습니다.");
    }

    public void activateIslandTarget(Player player, String target) {
        adminIslandTarget(player, target, islandId -> activateIsland(player, islandId));
    }

    public void deactivateIsland(Player player, UUID islandId) {
        sendBodyResult(player, coreApiClient.deactivateIsland(islandId).thenApply(body -> actionResultMessage("Island deactivate", islandId.toString(), body)), "섬 비활성화를 요청하지 못했습니다.");
    }

    public void deactivateIslandTarget(Player player, String target) {
        adminIslandTarget(player, target, islandId -> deactivateIsland(player, islandId));
    }

    public void migrateIsland(Player player, UUID islandId, String targetNode) {
        sendBodyResult(player, coreApiClient.migrateIsland(islandId, targetNode).thenApply(body -> actionResultMessage("Island migrate", islandId.toString(), body)), "섬 마이그레이션을 요청하지 못했습니다.");
    }

    public void migrateIslandTarget(Player player, String target, String targetNode) {
        adminIslandTarget(player, target, islandId -> migrateIsland(player, islandId, targetNode));
    }

    public void quarantineIsland(Player player, UUID islandId, String reason) {
        sendBodyResult(player, coreApiClient.quarantineIsland(islandId, reason).thenApply(body -> actionResultMessage("Island quarantine", islandId.toString(), body)), "섬 격리를 요청하지 못했습니다.");
    }

    public void quarantineIslandTarget(Player player, String target, String reason) {
        adminIslandTarget(player, target, islandId -> quarantineIsland(player, islandId, reason));
    }

    public void adminIslandInfo(Player player, UUID lookupUuid) {
        sendBodyResult(player, coreApiClient.adminIslandInfo(lookupUuid).thenApply(this::islandInfoMessage), "섬 정보를 불러오지 못했습니다.");
    }

    public void adminIslandInfoTarget(Player player, String target) {
        UUID parsed = parseUuid(target);
        if (!parsed.equals(new UUID(0L, 0L))) {
            adminIslandInfo(player, parsed);
            return;
        }
        sendBodyResult(player, coreApiClient.islandInfoByName(target).thenApply(this::islandInfoMessage), "섬 정보를 불러오지 못했습니다.");
    }

    public void adminIslandWhere(Player player, UUID islandId) {
        sendBodyResult(player, coreApiClient.adminIslandWhere(islandId).thenApply(this::runtimeInfoMessage), "섬 위치 정보를 불러오지 못했습니다.");
    }

    public void adminIslandWhereTarget(Player player, String target) {
        adminIslandTarget(player, target, islandId -> adminIslandWhere(player, islandId));
    }

    public void adminTeleportIsland(Player player, UUID islandId) {
        routeFuture(player, coreApiClient.adminIslandTeleport(player.getUniqueId(), islandId), "섬으로 이동하지 못했습니다.");
    }

    public void adminTeleportIslandTarget(Player player, String target) {
        adminIslandTarget(player, target, islandId -> adminTeleportIsland(player, islandId));
    }

    public void adminDeleteIsland(Player player, UUID islandId) {
        sendBodyResult(player, coreApiClient.adminDeleteIsland(islandId).thenApply(body -> actionResultMessage("Island delete", islandId.toString(), body)), "섬 삭제를 요청하지 못했습니다.");
    }

    public void adminDeleteIslandTarget(Player player, String target) {
        adminIslandTarget(player, target, islandId -> adminDeleteIsland(player, islandId));
    }

    public void repairIsland(Player player, UUID islandId, String reason) {
        sendBodyResult(player, coreApiClient.repairIsland(islandId, reason).thenApply(body -> actionResultMessage("Island repair", islandId.toString(), body)), "섬 복구를 요청하지 못했습니다.");
    }

    public void repairIslandTarget(Player player, String target, String reason) {
        adminIslandTarget(player, target, islandId -> repairIsland(player, islandId, reason));
    }

    public void listSnapshotsTarget(Player player, String target) {
        adminIslandTarget(player, target, islandId -> listSnapshots(player, islandId));
    }

    public void snapshotTarget(Player player, String target, String reason) {
        adminIslandTarget(player, target, islandId -> snapshot(player, islandId, reason));
    }

    public void restoreTarget(Player player, String target, long snapshotNo) {
        adminIslandTarget(player, target, islandId -> restore(player, islandId, snapshotNo));
    }

    public void debugRoutes(Player player, UUID playerUuid) {
        sendBodyResult(player, coreApiClient.debugRoutes(playerUuid).thenApply(this::routeDebugMessage), "라우트 정보를 불러오지 못했습니다.");
    }

    public void debugRoutesTarget(Player player, String target) {
        resolvePlayerUuid(target).thenAccept(playerUuid -> {
            if (playerUuid.equals(new UUID(0L, 0L))) {
                player.sendMessage(Component.text("플레이어를 찾지 못했습니다."));
                return;
            }
            debugRoutes(player, playerUuid);
        }).exceptionally(error -> {
            player.sendMessage(Component.text("플레이어를 찾지 못했습니다."));
            return null;
        });
    }

    public void routeTicket(Player player, UUID ticketId) {
        sendBodyResult(player, coreApiClient.routeTicket(ticketId).thenApply(this::routeTicketMessage), "티켓 정보를 불러오지 못했습니다.");
    }

    public void routeTicketTarget(Player player, String target) {
        UUID ticketId = parseUuid(target);
        if (!ticketId.equals(new UUID(0L, 0L))) {
            routeTicket(player, ticketId);
            return;
        }
        resolvePlayerUuid(target).thenAccept(playerUuid -> {
            if (playerUuid.equals(new UUID(0L, 0L))) {
                player.sendMessage(Component.text("플레이어를 찾지 못했습니다."));
                return;
            }
            sendBodyResult(player, coreApiClient.routeTicketForPlayer(playerUuid).thenApply(this::routeTicketMessage), "티켓 정보를 불러오지 못했습니다.");
        }).exceptionally(error -> {
            player.sendMessage(Component.text("플레이어를 찾지 못했습니다."));
            return null;
        });
    }

    public void clearRoute(Player player, UUID playerUuid, UUID ticketId) {
        sendBodyResult(player, coreApiClient.clearRoute(playerUuid, ticketId).thenApply(this::routeClearMessage), "라우트 정리를 요청하지 못했습니다.");
    }

    public void clearRouteTarget(Player player, String target, UUID ticketId) {
        resolvePlayerUuid(target).thenAccept(playerUuid -> {
            if (playerUuid.equals(new UUID(0L, 0L))) {
                player.sendMessage(Component.text("플레이어를 찾지 못했습니다."));
                return;
            }
            clearRoute(player, playerUuid, ticketId);
        }).exceptionally(error -> {
            player.sendMessage(Component.text("플레이어를 찾지 못했습니다."));
            return null;
        });
    }

    public void clearCache(Player player) {
        sendBodyResult(player, coreApiClient.clearCache().thenApply(body -> maintenanceMessage("Cache clear", body)), "캐시 정리를 요청하지 못했습니다.");
    }

    public void startEventPolling(Object plugin) {
        stopEventPolling();
        eventPollTask = proxy.getScheduler()
            .buildTask(plugin, this::pollCoreEvents)
            .repeat(Duration.ofSeconds(2L))
            .schedule();
    }

    public void stopEventPolling() {
        if (eventPollTask != null) {
            eventPollTask.cancel();
            eventPollTask = null;
        }
    }

    public void listEvents(Player player) {
        sendBodyResult(player, coreApiClient.listEvents().thenApply(this::eventListMessage), "이벤트 목록을 불러오지 못했습니다.");
    }

    public void listAuditLogs(Player player) {
        sendBodyResult(player, coreApiClient.listAuditLogs().thenApply(this::auditListMessage), "감사 로그를 불러오지 못했습니다.");
    }

    public void metrics(Player player) {
        sendBodyResult(player, coreApiClient.metrics().thenApply(this::metricsMessage), "Core metrics를 불러오지 못했습니다.");
    }

    public void coreConfig(Player player) {
        sendBodyResult(player, coreApiClient.coreConfig().thenApply(this::coreConfigMessage), "Core config를 불러오지 못했습니다.");
    }

    public void storageStatus(Player player) {
        sendBodyResult(player, coreApiClient.storageStatus().thenApply(this::storageStatusMessage), "Storage 상태를 불러오지 못했습니다.");
    }

    public void listBlockValues(Player player) {
        sendBodyResult(player, coreApiClient.listBlockValues().thenApply(this::blockValueListMessage), "블록 가치 목록을 불러오지 못했습니다.");
    }

    public void setBlockValue(Player player, String materialKey, String worth, long levelPoints, long limit) {
        sendBodyResult(player, coreApiClient.setBlockValueResult(player.getUniqueId(), materialKey, worth, levelPoints, limit).thenApply(body -> actionResultMessage("Block value set", materialKey, body)), "블록 가치를 변경하지 못했습니다.");
    }

    public void reload(Player player) {
        sendBodyResult(player, coreApiClient.reload().thenApply(body -> maintenanceMessage("Core reload", body)), "reload를 요청하지 못했습니다.");
    }

    public void migrateSuperiorSkyblock2(Player player, String action, String path) {
        sendBodyResult(player, coreApiClient.migrateSuperiorSkyblock2(action, path).thenApply(this::migrationMessage), "마이그레이션 명령을 실행하지 못했습니다.");
    }

    private String migrationMessage(String body) {
        if (body == null || body.isBlank()) {
            return "Migration: no response";
        }
        String code = jsonValue(body, "code");
        if (!code.isBlank()) {
            return "Migration: failed code=" + code;
        }
        String state = jsonValue(body, "state");
        String path = jsonValue(body, "path");
        String manifestPath = jsonValue(body, "manifestPath");
        String reportPath = jsonValue(body, "reportPath");
        String approvalToken = jsonValue(body, "approvalToken");
        String issues = arrayValue(body, "issues");
        long manifests = longValue(body, "manifests");
        long importedIslands = longValue(body, "importedIslands");
        long removedIslands = longValue(body, "removedIslands");
        StringBuilder builder = new StringBuilder("Migration: state=")
            .append(state.isBlank() ? "UNKNOWN" : state)
            .append(" manifests=")
            .append(manifests);
        if (!path.isBlank()) {
            builder.append(" path=").append(path);
        }
        if (!manifestPath.isBlank()) {
            builder.append(" manifest=").append(manifestPath);
        }
        if (!reportPath.isBlank()) {
            builder.append(" report=").append(reportPath);
        }
        if (!approvalToken.isBlank()) {
            builder.append(" approval=").append(approvalToken);
        }
        if (body.contains("\"canImport\"")) {
            builder.append(" canImport=").append(boolValue(body, "canImport"));
        }
        if (body.contains("\"imported\"")) {
            builder.append(" imported=").append(boolValue(body, "imported"))
                .append(" islands=")
                .append(importedIslands);
        }
        if (body.contains("\"passed\"")) {
            builder.append(" passed=").append(boolValue(body, "passed"))
                .append(" expected=")
                .append(longValue(body, "expected"));
        }
        if (body.contains("\"rolledBack\"")) {
            builder.append(" rolledBack=").append(boolValue(body, "rolledBack"))
                .append(" removed=")
                .append(removedIslands);
        }
        if (body.contains("\"extractedBundles\"")) {
            builder.append(" extracted=")
                .append(longValue(body, "extractedBundles"))
                .append(" files=")
                .append(longValue(body, "extractedFiles"))
                .append(" bytes=")
                .append(longValue(body, "extractedBytes"));
        }
        if (body.contains("\"members\"")) {
            builder.append(" members=").append(longValue(body, "members"))
                .append(" homes=").append(longValue(body, "homes"))
                .append(" warps=").append(longValue(body, "warps"))
                .append(" perms=").append(longValue(body, "permissions"));
        }
        if (body.contains("\"blockingIssues\"")) {
            builder.append(" blocking=").append(longValue(body, "blockingIssues"))
                .append(" warnings=").append(longValue(body, "warningIssues"));
        }
        builder.append(migrationIssuesSuffix(issues));
        return builder.toString();
    }

    private String migrationIssuesSuffix(String issues) {
        if (issues.isBlank()) {
            return " issues=0";
        }
        int total = 0;
        int blocking = 0;
        java.util.List<String> samples = new java.util.ArrayList<>();
        int index = 0;
        while (index < issues.length()) {
            int objectStart = issues.indexOf('{', index);
            if (objectStart < 0) {
                break;
            }
            int objectEnd = matchingObjectEnd(issues, objectStart);
            if (objectEnd < 0) {
                break;
            }
            String object = issues.substring(objectStart, objectEnd + 1);
            total++;
            boolean blocked = boolValue(object, "blocking");
            if (blocked) {
                blocking++;
            }
            if (samples.size() < 5) {
                String issueCode = jsonValue(object, "code");
                samples.add((issueCode.isBlank() ? "UNKNOWN" : issueCode) + (blocked ? "(blocking)" : ""));
            }
            index = objectEnd + 1;
        }
        return " issues=" + total
            + " blocking=" + blocking
            + (samples.isEmpty() ? "" : " [" + String.join(", ", samples) + "]");
    }

    private String actionResultMessage(String label, String targetId, String body) {
        if (body == null || body.isBlank()) {
            return label + ": accepted target=" + compactTarget(targetId);
        }
        String code = jsonValue(body, "code");
        boolean accepted = body.contains("\"accepted\"") ? boolValue(body, "accepted") : !body.contains("\"accepted\":false");
        StringBuilder builder = new StringBuilder(label)
            .append(": ")
            .append(accepted ? "accepted" : "rejected")
            .append(" target=")
            .append(compactTarget(targetId));
        if (!code.isBlank()) {
            builder.append(" code=").append(code);
            String detail = adminCodeDetail(code);
            if (!detail.isBlank()) {
                builder.append(" detail=").append(detail);
            }
        }
        String islandId = jsonValue(body, "islandId");
        if (!islandId.isBlank() && !islandId.equals(targetId)) {
            builder.append(" 섬=").append(shortId(islandId));
        }
        String materialKey = jsonValue(body, "materialKey");
        if (!materialKey.isBlank()) {
            builder.append(" material=").append(materialKey);
        }
        String worth = jsonValue(body, "worth");
        if (!worth.isBlank()) {
            builder.append(" worth=").append(worth);
        }
        if (body.contains("\"snapshotNo\"")) {
            builder.append(" snapshot=").append(longValue(body, "snapshotNo"));
        }
        return builder.toString();
    }

    private String adminCodeDetail(String code) {
        if (code == null || code.isBlank()) {
            return "";
        }
        if (code.startsWith("NO_READY_NODE")) {
            return "no-ready-node";
        }
        if (code.startsWith("TARGET_NODE")) {
            return "target-node-blocked";
        }
        if (code.startsWith("ACTIVE_NODE")) {
            return "active-node-blocked";
        }
        return switch (code) {
            case "ACTIVATION_LOCKED" -> "activation-in-progress";
            case "VISITOR_SOFT_FULL" -> "visitor-denied-soft-full";
            case "CREATE_LOCKED" -> "player-create-lock-held";
            case "NODE_UNAVAILABLE" -> "node-unavailable";
            default -> "";
        };
    }

    private String inviteCreateMessage(String body) {
        String code = jsonValue(body, "code");
        if (!code.isBlank()) {
            return "초대: 실패 사유=" + code;
        }
        return "초대: 생성됨 invite=" + shortId(jsonValue(body, "inviteId"))
            + " 섬=" + shortId(jsonValue(body, "islandId"))
            + " target=" + shortId(jsonValue(body, "targetUuid"))
            + " state=" + jsonValue(body, "state");
    }

    private String chatResultMessage(String label, String body) {
        String code = jsonValue(body, "code");
        if (!code.isBlank()) {
            return label + ": 실패 사유=" + code;
        }
        return label + ": 전송 완료 채널=" + jsonValue(body, "channel");
    }

    private String compactTarget(String targetId) {
        return targetId != null && targetId.length() == 36 && targetId.indexOf('-') > 0 ? shortId(targetId) : targetId;
    }

    private String islandInfoMessage(String body) {
        String code = jsonValue(body, "code");
        if (!code.isBlank()) {
            return "섬 정보: 실패 사유=" + code;
        }
        String islandId = jsonValue(body, "islandId");
        String ownerUuid = jsonValue(body, "ownerUuid");
        String name = jsonValue(body, "name");
        String state = jsonValue(body, "state");
        return "섬 정보: ID=" + shortId(islandId)
            + " 소유자=" + shortId(ownerUuid)
            + (name.isBlank() ? "" : " 이름=" + name)
            + " 상태=" + (state.isBlank() ? "UNKNOWN" : state)
            + " 크기=" + longValue(body, "size")
            + " 레벨=" + longValue(body, "level")
            + " 가치=" + jsonValue(body, "worth")
            + " 공개=" + boolValue(body, "publicAccess");
    }

    private String islandStatMessage(String label, String field, String body) {
        String code = jsonValue(body, "code");
        if (!code.isBlank()) {
            return label + ": 실패 사유=" + code;
        }
        String islandId = jsonValue(body, "islandId");
        String value = field.equals("worth") ? jsonValue(body, field) : Long.toString(longValue(body, field));
        return label + ": 섬=" + shortId(islandId) + " 값=" + value;
    }

    private String biomeInfoMessage(String body) {
        String code = jsonValue(body, "code");
        if (!code.isBlank()) {
            return "섬 바이옴: 실패 사유=" + code;
        }
        return "섬 바이옴: 섬=" + shortId(jsonValue(body, "islandId"))
            + " 바이옴=" + jsonValue(body, "biomeKey")
            + " 변경자=" + shortId(jsonValue(body, "updatedBy"));
    }

    private String runtimeInfoMessage(String body) {
        String code = jsonValue(body, "code");
        if (!code.isBlank()) {
            return "Island runtime: failed code=" + code;
        }
        String islandId = jsonValue(body, "islandId");
        String state = jsonValue(body, "state");
        String activeNode = jsonValue(body, "activeNode");
        String activeWorld = jsonValue(body, "activeWorld");
        return "Island runtime: 섬=" + shortId(islandId)
            + " state=" + (state.isBlank() ? "UNKNOWN" : state)
            + (activeNode.isBlank() ? "" : " node=" + activeNode)
            + (activeWorld.isBlank() ? "" : " world=" + activeWorld)
            + (body.contains("\"cellX\":null") || body.contains("\"cellZ\":null") ? "" : " cell=" + longValue(body, "cellX") + "," + longValue(body, "cellZ"))
            + " fence=" + longValue(body, "fencingToken");
    }

    private String playerInfoMessage(String body) {
        String code = jsonValue(body, "code");
        if (!code.isBlank()) {
            return "플레이어 정보: 실패 사유=" + code;
        }
        String playerUuid = jsonValue(body, "playerUuid");
        String lastName = jsonValue(body, "lastName");
        String islandId = jsonValue(body, "primaryIslandId");
        return "플레이어 정보: ID=" + shortId(playerUuid)
            + (lastName.isBlank() ? "" : " 이름=" + lastName)
            + (islandId.isBlank() ? " 섬=없음" : " 섬=" + shortId(islandId));
    }

    private String rankingListMessage(String label, String body) {
        String rankings = arrayValue(body, "rankings");
        if (rankings.isBlank()) {
            return label + ": 기록이 없습니다.";
        }
        java.util.List<String> entries = new java.util.ArrayList<>();
        int total = 0;
        int index = 0;
        while (index < rankings.length()) {
            int objectStart = rankings.indexOf('{', index);
            if (objectStart < 0) {
                break;
            }
            int objectEnd = matchingObjectEnd(rankings, objectStart);
            if (objectEnd < 0) {
                break;
            }
            total++;
            if (entries.size() < 10) {
                String object = rankings.substring(objectStart, objectEnd + 1);
                String islandId = jsonValue(object, "islandId");
                String name = jsonValue(object, "name");
                entries.add("#" + total
                    + " " + (name.isBlank() ? "이름 없는 섬" : name)
                    + " (ID=" + shortId(islandId)
                    + ", 레벨=" + longValue(object, "level")
                    + ", 가치=" + jsonValue(object, "worth") + ")");
            }
            index = objectEnd + 1;
        }
        return label + ": 전체 " + total + "개" + (entries.isEmpty() ? "" : " / " + String.join(" | ", entries));
    }

    private String blockValueListMessage(String body) {
        String values = arrayValue(body, "values");
        if (values.isBlank()) {
            return "Block values: empty";
        }
        java.util.List<String> entries = new java.util.ArrayList<>();
        int total = 0;
        int index = 0;
        while (index < values.length()) {
            int objectStart = values.indexOf('{', index);
            if (objectStart < 0) {
                break;
            }
            int objectEnd = matchingObjectEnd(values, objectStart);
            if (objectEnd < 0) {
                break;
            }
            total++;
            if (entries.size() < 10) {
                String object = values.substring(objectStart, objectEnd + 1);
                entries.add(jsonValue(object, "materialKey")
                    + " worth=" + jsonValue(object, "worth")
                    + " level=" + longValue(object, "levelPoints")
                    + " limit=" + longValue(object, "limit"));
            }
            index = objectEnd + 1;
        }
        return "Block values: total=" + total + (entries.isEmpty() ? "" : " / " + String.join(" | ", entries));
    }

    private String templateListMessage(String body) {
        String templates = arrayValue(body, "templates");
        if (templates.isBlank()) {
            return "섬 템플릿: 없음";
        }
        java.util.List<String> entries = new java.util.ArrayList<>();
        int total = 0;
        int enabled = 0;
        int index = 0;
        while (index < templates.length()) {
            int objectStart = templates.indexOf('{', index);
            if (objectStart < 0) {
                break;
            }
            int objectEnd = matchingObjectEnd(templates, objectStart);
            if (objectEnd < 0) {
                break;
            }
            String object = templates.substring(objectStart, objectEnd + 1);
            total++;
            if (boolValue(object, "enabled")) {
                enabled++;
            }
            if (entries.size() < 10) {
                String minNodeVersion = jsonValue(object, "minNodeVersion");
                entries.add(jsonValue(object, "id")
                    + " " + (boolValue(object, "enabled") ? "사용 가능" : "비활성")
                    + (minNodeVersion.isBlank() ? "" : " 최소버전=" + minNodeVersion));
            }
            index = objectEnd + 1;
        }
        return "섬 템플릿: 전체 " + total + "개, 사용 가능 " + enabled + "개" + (entries.isEmpty() ? "" : " / " + String.join(" | ", entries));
    }

    private String warpListMessage(String label, String body) {
        return namedObjectListMessage(label, body, "warps", object -> jsonValue(object, "name")
            + (boolValue(object, "publicAccess") ? "(공개)" : "")
            + " 섬=" + shortId(jsonValue(object, "islandId"))
            + " 위치=" + seconds(doubleValue(object, "localX")) + "," + seconds(doubleValue(object, "localY")) + "," + seconds(doubleValue(object, "localZ")));
    }

    private String homeListMessage(String body) {
        return namedObjectListMessage("섬 홈", body, "homes", object -> jsonValue(object, "name")
            + " 위치=" + seconds(doubleValue(object, "localX")) + "," + seconds(doubleValue(object, "localY")) + "," + seconds(doubleValue(object, "localZ")));
    }

    private String memberListMessage(String body) {
        return namedObjectListMessage("섬 멤버", body, "members", object -> shortId(jsonValue(object, "playerUuid"))
            + " 역할=" + jsonValue(object, "role"));
    }

    private String banListMessage(String body) {
        return namedObjectListMessage("섬 밴", body, "bans", object -> shortId(jsonValue(object, "bannedUuid"))
            + " 사유=" + fallback(jsonValue(object, "reason"), "-"));
    }

    private String permissionListMessage(String body) {
        return namedObjectListMessage("섬 권한", body, "rules", object -> jsonValue(object, "role")
            + ":" + jsonValue(object, "permission")
            + "=" + (boolValue(object, "allowed") ? "허용" : "거부"));
    }

    private String roleListMessage(String body) {
        return namedObjectListMessage("섬 역할", body, "roles", object -> jsonValue(object, "role")
            + " weight=" + longValue(object, "weight")
            + " name=" + fallback(jsonValue(object, "displayName"), "-"));
    }

    private String islandLogListMessage(String body) {
        return namedObjectListMessage("섬 로그", body, "logs", object -> fallback(jsonValue(object, "action"), "UNKNOWN")
            + " 처리자=" + shortId(jsonValue(object, "actorUuid"))
            + " 시각=" + jsonValue(object, "createdAt"));
    }

    private String bankInfoMessage(String body) {
        String code = jsonValue(body, "code");
        if (!code.isBlank()) {
            return "섬 은행: 실패 사유=" + code;
        }
        return "섬 은행: 섬=" + shortId(jsonValue(body, "islandId"))
            + " balance=" + jsonValue(body, "balance");
    }

    private String bankActionMessage(String label, String body) {
        String code = jsonValue(body, "code");
        String bank = objectValue(body, "bank");
        if (bank.isBlank()) {
            bank = body;
        }
        boolean accepted = !body.contains("\"accepted\":false");
        return label + ": " + (accepted ? "접수됨" : "거부됨")
            + (code.isBlank() ? "" : " 사유=" + code)
            + " 섬=" + shortId(jsonValue(bank, "islandId"))
            + " 잔액=" + jsonValue(bank, "balance");
    }

    private String levelRecalculationMessage(String body) {
        String code = jsonValue(body, "code");
        if (!code.isBlank()) {
            return "레벨 계산: 실패 사유=" + code;
        }
        return "레벨 계산: 섬=" + shortId(jsonValue(body, "islandId"))
            + " level=" + longValue(body, "level")
            + " worth=" + jsonValue(body, "worth");
    }

    private String upgradeListMessage(String body) {
        return namedObjectListMessage("섬 업그레이드", body, "upgrades", object -> jsonValue(object, "upgradeKey")
            + " 레벨=" + longValue(object, "level")
            + " 유형=" + jsonValue(object, "type"));
    }

    private String generatorInfoMessage(String body) {
        String generatorKey = "default";
        long level = 1L;
        int index = 0;
        while (body != null && index < body.length()) {
            int objectStart = body.indexOf('{', index);
            if (objectStart < 0) {
                break;
            }
            int objectEnd = body.indexOf('}', objectStart);
            if (objectEnd < 0) {
                break;
            }
            String object = body.substring(objectStart, objectEnd + 1);
            String upgradeKey = jsonValue(object, "upgradeKey");
            String normalized = upgradeKey.toLowerCase(java.util.Locale.ROOT);
            if (normalized.equals("generator") || normalized.startsWith("generator:")) {
                long currentLevel = Math.max(1L, longValue(object, "level"));
                String currentKey = jsonValue(object, "generatorKey");
                if (currentKey.isBlank()) {
                    int separator = upgradeKey.indexOf(':');
                    currentKey = separator < 0 ? "default" : upgradeKey.substring(separator + 1);
                }
                if (currentLevel > level || (currentLevel == level && generatorKey.equals("default") && !currentKey.equalsIgnoreCase("default"))) {
                    level = currentLevel;
                    generatorKey = currentKey.isBlank() ? "default" : currentKey;
                }
            }
            index = objectEnd + 1;
        }
        return "섬 생성기: key=" + generatorKey + " level=" + level + " / 업그레이드: /섬 업그레이드구매 generator";
    }

    private String upgradePurchaseMessage(String body) {
        String code = jsonValue(body, "code");
        String upgrade = objectValue(body, "upgrade");
        boolean accepted = boolValue(body, "accepted");
        return "업그레이드 구매: " + (accepted ? "접수됨" : "거부됨")
            + (code.isBlank() ? "" : " 사유=" + code)
            + " 비용=" + jsonValue(body, "cost")
            + (upgrade.isBlank() ? "" : " 업그레이드=" + jsonValue(upgrade, "upgradeKey") + " 레벨=" + longValue(upgrade, "level"));
    }

    private String missionListMessage(String label, String body) {
        return namedObjectListMessage(label, body, "missions", object -> jsonValue(object, "missionKey")
            + " " + longValue(object, "progress") + "/" + longValue(object, "goal")
            + " 완료=" + boolValue(object, "completed"));
    }

    private String missionResultMessage(String label, String body) {
        String code = jsonValue(body, "code");
        if (!code.isBlank()) {
            return label + ": 실패 사유=" + code;
        }
        return label + ": 완료 키=" + jsonValue(body, "missionKey")
            + " 보상=" + jsonValue(body, "reward");
    }

    private String limitListMessage(String body) {
        return namedObjectListMessage("섬 제한", body, "limits", object -> jsonValue(object, "limitKey")
            + " 값=" + longValue(object, "value"));
    }

    private String limitResultMessage(String body) {
        String code = jsonValue(body, "code");
        if (!code.isBlank()) {
            return "섬 제한 변경: 실패 사유=" + code;
        }
        return "섬 제한 변경: " + jsonValue(body, "limitKey")
            + "=" + longValue(body, "value")
            + " 섬=" + shortId(jsonValue(body, "islandId"));
    }

    private String flagListMessage(String body) {
        String flags = objectValue(body, "flags");
        if (flags.isBlank()) {
            return "섬 플래그: 없음";
        }
        java.util.List<String> entries = new java.util.ArrayList<>();
        int total = 0;
        int index = 0;
        while (index < flags.length()) {
            int keyStart = flags.indexOf('"', index);
            if (keyStart < 0) {
                break;
            }
            int keyEnd = flags.indexOf('"', keyStart + 1);
            if (keyEnd < 0) {
                break;
            }
            int valueStart = flags.indexOf('"', keyEnd + 1);
            if (valueStart < 0) {
                break;
            }
            int valueEnd = flags.indexOf('"', valueStart + 1);
            if (valueEnd < 0) {
                break;
            }
            total++;
            if (entries.size() < 12) {
                entries.add(flags.substring(keyStart + 1, keyEnd) + "=" + flags.substring(valueStart + 1, valueEnd));
            }
            index = valueEnd + 1;
        }
        return "섬 플래그: 전체 " + total + "개" + (entries.isEmpty() ? "" : " / " + String.join(" | ", entries));
    }

    private String namedObjectListMessage(String label, String body, String arrayField, java.util.function.Function<String, String> formatter) {
        String array = arrayValue(body, arrayField);
        if (array.isBlank()) {
            return label + ": empty";
        }
        java.util.List<String> entries = new java.util.ArrayList<>();
        int total = 0;
        int index = 0;
        while (index < array.length()) {
            int objectStart = array.indexOf('{', index);
            if (objectStart < 0) {
                break;
            }
            int objectEnd = matchingObjectEnd(array, objectStart);
            if (objectEnd < 0) {
                break;
            }
            total++;
            if (entries.size() < 10) {
                entries.add(formatter.apply(array.substring(objectStart, objectEnd + 1)));
            }
            index = objectEnd + 1;
        }
        return label + ": 전체 " + total + "개" + (entries.isEmpty() ? "" : " / " + String.join(" | ", entries));
    }

    private String fallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String upgradeRulesMessage(String body) {
        String rules = arrayValue(body, "rules");
        if (rules.isBlank()) {
            return "업그레이드 규칙: 없음";
        }
        java.util.List<String> entries = new java.util.ArrayList<>();
        int total = 0;
        int index = 0;
        while (index < rules.length()) {
            int objectStart = rules.indexOf('{', index);
            if (objectStart < 0) {
                break;
            }
            int objectEnd = matchingObjectEnd(rules, objectStart);
            if (objectEnd < 0) {
                break;
            }
            total++;
            if (entries.size() < 10) {
                String object = rules.substring(objectStart, objectEnd + 1);
                entries.add(jsonValue(object, "upgradeKey")
                    + " 유형=" + jsonValue(object, "type")
                    + " 최대=" + longValue(object, "maxLevel")
                    + " 기본비용=" + jsonValue(object, "baseCost"));
            }
            index = objectEnd + 1;
        }
        return "업그레이드 규칙: 전체 " + total + "개" + (entries.isEmpty() ? "" : " / " + String.join(" | ", entries));
    }

    private String maintenanceMessage(String label, String body) {
        String code = jsonValue(body, "code");
        if (!code.isBlank()) {
            return label + ": failed code=" + code;
        }
        return label + ": accepted sessions=" + longValue(body, "clearedSessions") + " tickets=" + longValue(body, "clearedTickets");
    }

    private String metricsMessage(String body) {
        if (body == null || body.isBlank()) {
            return "Core metrics: empty";
        }
        int samples = 0;
        java.util.List<String> names = new java.util.ArrayList<>();
        for (String line : body.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isBlank() || trimmed.startsWith("#")) {
                continue;
            }
            samples++;
            if (names.size() < 6) {
                int brace = trimmed.indexOf('{');
                int space = trimmed.indexOf(' ');
                int end = brace > 0 ? brace : space > 0 ? space : trimmed.length();
                String name = trimmed.substring(0, end);
                if (!names.contains(name)) {
                    names.add(name);
                }
            }
        }
        return "Core metrics: samples=" + samples + (names.isEmpty() ? "" : " / " + String.join(", ", names));
    }

    private String coreConfigMessage(String body) {
        String code = jsonValue(body, "code");
        if (!code.isBlank()) {
            return "Core config: failed code=" + code;
        }
        return "Core config: repo=" + jsonValue(body, "repositoryMode")
            + " jobs=" + jsonValue(body, "jobQueueMode")
            + " events=" + jsonValue(body, "eventBusMode")
            + " storage=" + jsonValue(body, "storageType")
            + " pool=" + jsonValue(body, "islandPool")
            + " dbPool=" + longValue(body, "databasePoolSize")
            + " softFull=" + jsonValue(body, "softFullPolicy")
            + " hardFull=" + jsonValue(body, "hardFullPolicy")
            + " migration=" + jsonValue(body, "migrationPolicy")
            + " ticketTtl=" + longValue(body, "routeTicketTtlSeconds") + "s"
            + " prepTtl=" + longValue(body, "routePreparingTicketTtlSeconds") + "s"
            + " mtls=" + boolValue(body, "requireMtls")
            + " ipAllowlist=" + boolValue(body, "ipAllowlistEnabled");
    }

    public void playerInfo(Player player, UUID playerUuid) {
        sendBodyResult(player, coreApiClient.playerInfo(playerUuid).thenApply(this::playerInfoMessage), "플레이어 정보를 불러오지 못했습니다.");
    }

    public void playerInfoTarget(Player player, String target) {
        resolvePlayerUuid(target).thenAccept(playerUuid -> {
            if (playerUuid.equals(new UUID(0L, 0L))) {
                player.sendMessage(Component.text("대상 플레이어를 찾지 못했습니다."));
                return;
            }
            playerInfo(player, playerUuid);
        }).exceptionally(error -> {
            player.sendMessage(Component.text("대상 플레이어를 찾지 못했습니다."));
            return null;
        });
    }

    public void setPlayerIsland(Player player, UUID playerUuid, UUID islandId) {
        sendBodyResult(player, coreApiClient.setPlayerIsland(playerUuid, islandId).thenApply(body -> actionResultMessage("Player setisland", playerUuid.toString(), body)), "플레이어 섬을 설정하지 못했습니다.");
    }

    public void setPlayerIslandTarget(Player player, String target, UUID islandId) {
        resolvePlayerUuid(target).thenAccept(playerUuid -> {
            if (playerUuid.equals(new UUID(0L, 0L))) {
                player.sendMessage(Component.text("대상 플레이어를 찾지 못했습니다."));
                return;
            }
            setPlayerIsland(player, playerUuid, islandId);
        }).exceptionally(error -> {
            player.sendMessage(Component.text("대상 플레이어를 찾지 못했습니다."));
            return null;
        });
    }

    public void clearPlayerIsland(Player player, UUID playerUuid) {
        sendBodyResult(player, coreApiClient.clearPlayerIsland(playerUuid).thenApply(body -> actionResultMessage("Player clearisland", playerUuid.toString(), body)), "플레이어 섬을 해제하지 못했습니다.");
    }

    public void clearPlayerIslandTarget(Player player, String target) {
        resolvePlayerUuid(target).thenAccept(playerUuid -> {
            if (playerUuid.equals(new UUID(0L, 0L))) {
                player.sendMessage(Component.text("대상 플레이어를 찾지 못했습니다."));
                return;
            }
            clearPlayerIsland(player, playerUuid);
        }).exceptionally(error -> {
            player.sendMessage(Component.text("대상 플레이어를 찾지 못했습니다."));
            return null;
        });
    }

    public void listTemplates(Player player) {
        sendBodyResult(player, coreApiClient.listTemplates().thenApply(this::templateListMessage), "섬 템플릿 목록을 불러오지 못했습니다.");
    }

    public void upsertTemplate(Player player, String templateId, String displayName, boolean enabled, String minNodeVersion) {
        sendBodyResult(player, coreApiClient.upsertTemplate(templateId, displayName, enabled, minNodeVersion).thenApply(body -> actionResultMessage("Template upsert", templateId, body)), "섬 템플릿을 저장하지 못했습니다.");
    }

    public void enableTemplate(Player player, String templateId) {
        sendBodyResult(player, coreApiClient.enableTemplate(templateId).thenApply(body -> actionResultMessage("Template enable", templateId, body)), "섬 템플릿을 활성화하지 못했습니다.");
    }

    public void disableTemplate(Player player, String templateId) {
        sendBodyResult(player, coreApiClient.disableTemplate(templateId).thenApply(body -> actionResultMessage("Template disable", templateId, body)), "섬 템플릿을 비활성화하지 못했습니다.");
    }

    private void sendPlayerPayload(Player player, String body, String emptyMessage, String successMessage) {
        player.sendMessage(Component.text(playerPayloadMessage(body, emptyMessage, successMessage)));
    }

    private void sendPlayerPayloadFuture(Player player, CompletableFuture<String> future, String emptyMessage, String successMessage) {
        future.thenAccept(body -> sendPlayerPayload(player, body, emptyMessage, successMessage)).exceptionally(error -> {
            player.sendMessage(Component.text(emptyMessage));
            return null;
        });
    }

    private void sendActionResult(Player player, CompletableFuture<Void> future, String successMessage, String failureMessage) {
        future.thenRun(() -> player.sendMessage(Component.text(successMessage))).exceptionally(error -> {
            player.sendMessage(Component.text(failureMessage));
            return null;
        });
    }

    private void sendBodyResult(Player player, CompletableFuture<String> future, String emptyMessage) {
        future.thenAccept(body -> player.sendMessage(Component.text(body == null || body.isBlank() ? emptyMessage : body))).exceptionally(error -> {
            player.sendMessage(Component.text(emptyMessage));
            return null;
        });
    }

    private void pollCoreEvents() {
        coreApiClient.listEventsSince(lastEventSequence, EVENT_BATCH_SIZE).thenAccept(body -> {
            long oldestSequence = longValue(body, "oldestSeq");
            long latestSequence = longValue(body, "latestSeq");
            if (latestSequence > 0L && latestSequence < lastEventSequence) {
                lastEventSequence = 0L;
                seenEvents.clear();
            }
            if (lastEventSequence > 0L && oldestSequence > lastEventSequence + 1L) {
                lastEventSequence = oldestSequence - 1L;
                seenEvents.clear();
            }
            Matcher matcher = EVENT.matcher(body == null ? "" : body);
            while (matcher.find()) {
                long sequence = parseLong(matcher.group(1));
                lastEventSequence = Math.max(lastEventSequence, sequence);
                String type = matcher.group(2);
                Map<String, String> fields = fields(matcher.group(3));
                String key = eventKey(type, fields, matcher.group(4));
                if (seenEvents.add(key)) {
                    handleCoreEvent(type, fields);
                }
            }
            if (seenEvents.size() > 2048) {
                seenEvents.clear();
            }
        }).exceptionally(error -> null);
    }

    private void handleCoreEvent(String type, Map<String, String> fields) {
        if (!type.equals("NODE_STATE_CHANGED")) {
            return;
        }
        String state = fields.getOrDefault("state", "");
        String operation = fields.getOrDefault("operation", "");
        if (!state.equals("KICKALL") && !state.equals("SHUTDOWN_SAFE") && !operation.equals("SHUTDOWN_SAFE")) {
            return;
        }
        String nodeId = fields.getOrDefault("nodeId", "");
        if (nodeId.isBlank() || nodeId.equals("*")) {
            return;
        }
        moveNodePlayersToFallback(nodeId);
    }

    private String eventKey(String type, Map<String, String> fields, String occurredAt) {
        String identity = firstPresent(fields, "nodeId", "islandId", "ticketId", "jobId", "playerUuid");
        if (identity.isBlank()) {
            identity = fields.toString();
        }
        return type + "@" + occurredAt + "@" + identity + "@" + fields.getOrDefault("state", "");
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

    private Map<String, String> fields(String raw) {
        Map<String, String> result = new java.util.HashMap<>();
        Matcher matcher = FIELD.matcher(raw == null ? "" : raw);
        while (matcher.find()) {
            result.put(matcher.group(1), matcher.group(2));
        }
        return result;
    }

    private String appendLevelScanSummary(String body) {
        java.util.List<String> summaries = new java.util.ArrayList<>();
        String activation = activationAllocationSummary(body);
        if (!activation.isBlank()) {
            summaries.add(activation);
        }
        String levelScan = levelScanSummary(body);
        if (!levelScan.isBlank()) {
            summaries.add(levelScan);
        }
        if (summaries.isEmpty()) {
            return body;
        }
        return (body == null || body.isBlank() ? "" : body + " | ") + String.join(" | ", summaries);
    }

    private String activationAllocationSummary(String body) {
        if (body == null || body.isBlank() || !body.contains("\"eligibleForNewActivation\"")) {
            return "";
        }
        boolean eligible = boolValue(body, "eligibleForNewActivation");
        String reason = jsonValue(body, "allocationBlockReason");
        return "활성화 배정=" + (eligible ? "가능" : "차단(" + (reason.isBlank() ? "UNKNOWN" : reason) + ")");
    }

    private String levelScanSummary(String body) {
        String scan = objectValue(body, "levelScan");
        if (scan.isBlank()) {
            return "";
        }
        StringBuilder summary = new StringBuilder("레벨 스캔=");
        summary.append(boolValue(scan, "running") ? "실행 중" : "대기");
        String lastIsland = jsonValue(scan, "lastIsland");
        if (!lastIsland.isBlank()) {
            summary.append(", 마지막 섬=").append(lastIsland);
        }
        appendLongSummary(summary, "시작", longValue(scan, "startedAt"));
        appendLongSummary(summary, "완료", longValue(scan, "finishedAt"));
        appendLongSummary(summary, "실패", longValue(scan, "failedAt"));
        return summary.toString();
    }

    private String nodeIslandSummary(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        String id = jsonValue(body, "id");
        String server = jsonValue(body, "server");
        String state = jsonValue(body, "state");
        long active = longValue(body, "activeIslands");
        long max = longValue(body, "maxActiveIslands");
        StringBuilder summary = new StringBuilder("노드 섬 현황");
        if (!id.isBlank()) {
            summary.append(' ').append(id);
        }
        summary.append(": 활성 섬 ").append(active);
        if (max > 0L) {
            summary.append('/').append(max);
        }
        if (!state.isBlank()) {
            summary.append(", 상태=").append(state);
        }
        if (!server.isBlank()) {
            summary.append(", 서버=").append(server);
        }
        return summary.toString();
    }

    private String nodeIslandListMessage(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        String nodeId = jsonValue(body, "nodeId");
        long count = longValue(body, "count");
        String islands = arrayValue(body, "islands");
        if (islands.isBlank() || count == 0L) {
            return "노드 섬 현황" + hiddenNodeLabel(nodeId) + ": 활성 섬 없음";
        }
        java.util.List<String> entries = new java.util.ArrayList<>();
        int index = 0;
        while (index < islands.length()) {
            int objectStart = islands.indexOf('{', index);
            if (objectStart < 0) {
                break;
            }
            int objectEnd = matchingObjectEnd(islands, objectStart);
            if (objectEnd < 0) {
                break;
            }
            String object = islands.substring(objectStart, objectEnd + 1);
            String islandId = jsonValue(object, "islandId");
            if (!islandId.isBlank()) {
                entries.add(islandId + "(" + nodeIslandRuntimeSuffix(object) + ")");
            }
            index = objectEnd + 1;
        }
        return "노드 섬 현황" + hiddenNodeLabel(nodeId) + ": " + (entries.isEmpty() ? "활성 섬 없음" : String.join(", ", entries));
    }

    private String storageStatusMessage(String body) {
        String nodes = arrayValue(body, "nodes");
        if (nodes.isBlank()) {
            return "Storage status: registered node 없음";
        }
        java.util.List<String> entries = new java.util.ArrayList<>();
        int unavailable = 0;
        int index = 0;
        while (index < nodes.length()) {
            int objectStart = nodes.indexOf('{', index);
            if (objectStart < 0) {
                break;
            }
            int objectEnd = matchingObjectEnd(nodes, objectStart);
            if (objectEnd < 0) {
                break;
            }
            String object = nodes.substring(objectStart, objectEnd + 1);
            String nodeId = jsonValue(object, "nodeId");
            boolean available = boolValue(object, "storageAvailable");
            if (!nodeId.isBlank()) {
                entries.add(nodeId + "=" + (available ? "OK" : "DOWN") + storageMetricSuffix(object));
                if (!available) {
                    unavailable++;
                }
            }
            index = objectEnd + 1;
        }
        return entries.isEmpty()
            ? "Storage status: registered node 없음"
            : "Storage status: " + String.join(", ", entries) + " / unavailable=" + unavailable;
    }

    private String storageMetricSuffix(String nodeObject) {
        String storage = objectValue(nodeObject, "storage");
        if (storage.isBlank()) {
            return "";
        }
        long failures = longValue(storage, "healthCheckFailures")
            + longValue(storage, "uploadFailures")
            + longValue(storage, "downloadFailures")
            + longValue(storage, "operationFailures");
        return "(failures=" + failures
            + ", up=" + seconds(doubleValue(storage, "uploadSeconds")) + "s"
            + ", down=" + seconds(doubleValue(storage, "downloadSeconds")) + "s)";
    }

    private String nodeListSummaryMessage(String body) {
        String nodes = arrayValue(body, "nodes");
        if (nodes.isBlank()) {
            return "Nodes: empty";
        }
        int total = 0;
        int starting = 0;
        int warming = 0;
        int ready = 0;
        int softFull = 0;
        int hardFull = 0;
        int draining = 0;
        int shuttingDown = 0;
        int down = 0;
        java.util.List<String> entries = new java.util.ArrayList<>();
        int index = 0;
        while (index < nodes.length()) {
            int objectStart = nodes.indexOf('{', index);
            if (objectStart < 0) {
                break;
            }
            int objectEnd = matchingObjectEnd(nodes, objectStart);
            if (objectEnd < 0) {
                break;
            }
            String object = nodes.substring(objectStart, objectEnd + 1);
            String state = jsonValue(object, "state");
            total++;
            if (state.equalsIgnoreCase("STARTING")) {
                starting++;
            } else if (state.equalsIgnoreCase("WARMING")) {
                warming++;
            } else if (state.equalsIgnoreCase("READY")) {
                ready++;
            } else if (state.equalsIgnoreCase("SOFT_FULL")) {
                softFull++;
            } else if (state.equalsIgnoreCase("HARD_FULL")) {
                hardFull++;
            } else if (state.equalsIgnoreCase("DRAINING")) {
                draining++;
            } else if (state.equalsIgnoreCase("SHUTTING_DOWN")) {
                shuttingDown++;
            } else if (state.equalsIgnoreCase("DOWN")) {
                down++;
            }
            if (entries.size() < 10) {
                entries.add(nodeSummary(object, entries.size() + 1));
            }
            index = objectEnd + 1;
        }
        return "Nodes: total=" + total
            + " starting=" + starting
            + " warming=" + warming
            + " ready=" + ready
            + " softFull=" + softFull
            + " hardFull=" + hardFull
            + " draining=" + draining
            + " shuttingDown=" + shuttingDown
            + " down=" + down
            + (entries.isEmpty() ? "" : " / " + String.join(" | ", entries));
    }

    private String nodeSummary(String object, int displayIndex) {
        String id = jsonValue(object, "id");
        String state = jsonValue(object, "state");
        long players = longValue(object, "players");
        long softCap = longValue(object, "softPlayerCap");
        long hardCap = longValue(object, "hardPlayerCap");
        long reservedSlots = longValue(object, "reservedSlots");
        long activeIslands = longValue(object, "activeIslands");
        long maxActiveIslands = longValue(object, "maxActiveIslands");
        long activationQueue = longValue(object, "activationQueue");
        long maxActivationQueue = longValue(object, "maxActivationQueue");
        boolean activationEligible = boolValue(object, "eligibleForNewActivation");
        String allocationBlockReason = jsonValue(object, "allocationBlockReason");
        String displayNode = id.isBlank() ? "node-" + displayIndex : id;
        return displayNode
            + " " + (state.isBlank() ? "UNKNOWN" : state)
            + " players=" + players + "/" + softCap + "/" + hardCap + " reserved=" + reservedSlots
            + " islands=" + activeIslands + "/" + maxActiveIslands
            + " queue=" + activationQueue + "/" + maxActivationQueue
            + " mspt=" + seconds(doubleValue(object, "mspt"))
            + " score=" + seconds(doubleValue(object, "score"))
            + scoreParts(object)
            + " activation=" + (activationEligible ? "ok" : "blocked:" + (allocationBlockReason.isBlank() ? "UNKNOWN" : allocationBlockReason))
            + " storage=" + (boolValue(object, "storageAvailable") ? "ok" : "down");
    }

    private String scoreParts(String nodeObject) {
        String breakdown = objectValue(nodeObject, "scoreBreakdown");
        if (breakdown.isBlank()) {
            return "";
        }
        return " parts=p:" + seconds(doubleValue(breakdown, "playerPressure"))
            + ",a:" + seconds(doubleValue(breakdown, "activeIslandPressure"))
            + ",m:" + seconds(doubleValue(breakdown, "msptPressure"))
            + ",q:" + seconds(doubleValue(breakdown, "activationQueuePressure"))
            + ",mem:" + seconds(doubleValue(breakdown, "memoryPressure"))
            + ",fail:" + seconds(doubleValue(breakdown, "recentFailurePenalty"));
    }

    private String nodeActionSummaryMessage(String label, String nodeId, String body) {
        String displayNode = nodeId == null || nodeId.isBlank() ? "target-node" : nodeId;
        if (body == null || body.isBlank()) {
            return label + ": accepted node=" + displayNode;
        }
        String code = jsonValue(body, "code");
        if (!code.isBlank()) {
            return label + ": " + (boolValue(body, "accepted") ? "accepted" : "rejected") + " node=" + displayNode + " code=" + code;
        }
        return label + ": " + (boolValue(body, "accepted") ? "accepted" : "requested") + " node=" + displayNode;
    }

    private String nodeSweepMessage(String body) {
        String nodes = arrayValue(body, "nodes");
        long recoveryRequired = longValue(body, "recoveryRequired");
        java.util.List<String> swept = new java.util.ArrayList<>();
        int index = 0;
        while (index < nodes.length()) {
            int valueStart = nodes.indexOf('"', index);
            if (valueStart < 0) {
                break;
            }
            int valueEnd = nodes.indexOf('"', valueStart + 1);
            if (valueEnd < 0) {
                break;
            }
            String nodeId = nodes.substring(valueStart + 1, valueEnd);
            swept.add(nodeId.isBlank() ? "node-" + (swept.size() + 1) : nodeId);
            index = valueEnd + 1;
        }
        return "Node sweep: nodes=" + (swept.isEmpty() ? "none" : String.join(",", swept)) + " recoveryRequired=" + recoveryRequired;
    }

    private String jobListMessage(String body) {
        String jobs = arrayValue(body, "jobs");
        if (jobs.isBlank()) {
            return "Jobs: empty";
        }
        int pending = 0;
        int claimed = 0;
        int failed = 0;
        int done = 0;
        int other = 0;
        int total = 0;
        java.util.List<String> entries = new java.util.ArrayList<>();
        int index = 0;
        while (index < jobs.length()) {
            int objectStart = jobs.indexOf('{', index);
            if (objectStart < 0) {
                break;
            }
            int objectEnd = matchingObjectEnd(jobs, objectStart);
            if (objectEnd < 0) {
                break;
            }
            String object = jobs.substring(objectStart, objectEnd + 1);
            String state = jsonValue(object, "state");
            total++;
            if (state.equalsIgnoreCase("PENDING")) {
                pending++;
            } else if (state.equalsIgnoreCase("CLAIMED")) {
                claimed++;
            } else if (state.equalsIgnoreCase("FAILED")) {
                failed++;
            } else if (state.equalsIgnoreCase("DONE") || state.equalsIgnoreCase("COMPLETED")) {
                done++;
            } else {
                other++;
            }
            if (entries.size() < 10) {
                entries.add(jobSummary(object));
            }
            index = objectEnd + 1;
        }
        return "Jobs: total=" + total
            + " pending=" + pending
            + " claimed=" + claimed
            + " failed=" + failed
            + " done=" + done
            + " other=" + other
            + (entries.isEmpty() ? "" : " / " + String.join(" | ", entries));
    }

    private String jobSummary(String object) {
        String id = jsonValue(object, "id");
        String type = jsonValue(object, "type");
        String state = jsonValue(object, "state");
        String targetNode = jsonValue(object, "targetNode");
        long attempts = longValue(object, "attempts");
        String error = jsonValue(object, "error");
        String shortId = id.length() > 8 ? id.substring(0, 8) : id;
        StringBuilder builder = new StringBuilder(shortId.isBlank() ? "job" : shortId)
            .append(' ')
            .append(type.isBlank() ? "UNKNOWN" : type)
            .append(' ')
            .append(state.isBlank() ? "UNKNOWN" : state)
            .append(" attempts=")
            .append(attempts);
        if (!targetNode.isBlank()) {
            builder.append(" node=").append(targetNode);
        }
        if (!error.isBlank()) {
            builder.append(" error=").append(error);
        }
        return builder.toString();
    }

    private String jobActionMessage(String action, String body) {
        if (body == null || body.isBlank()) {
            return "Job " + action + ": no response";
        }
        String code = jsonValue(body, "code");
        if (!code.isBlank()) {
            return "Job " + action + ": failed code=" + code;
        }
        if (body.contains("\"recovered\"")) {
            String recoveredText = jsonValue(body, "recovered");
            long recoveredNumber = longValue(body, "recovered");
            return "Job recover: recovered=" + (recoveredText.isBlank() ? Long.toString(recoveredNumber) : recoveredText);
        }
        return "Job " + action + ": " + (boolValue(body, "ok") ? "accepted" : "not applied");
    }

    private String eventListMessage(String body) {
        String events = arrayValue(body, "events");
        if (events.isBlank()) {
            return "Events: empty";
        }
        java.util.List<String> entries = new java.util.ArrayList<>();
        int index = 0;
        while (index < events.length() && entries.size() < 10) {
            int objectStart = events.indexOf('{', index);
            if (objectStart < 0) {
                break;
            }
            int objectEnd = matchingObjectEnd(events, objectStart);
            if (objectEnd < 0) {
                break;
            }
            String object = events.substring(objectStart, objectEnd + 1);
            String type = jsonValue(object, "type");
            String occurredAt = jsonValue(object, "occurredAt");
            String fields = objectValue(object, "fields");
            String islandId = jsonValue(fields, "islandId");
            String ticketId = jsonValue(fields, "ticketId");
            String playerUuid = jsonValue(fields, "playerUuid");
            String action = jsonValue(fields, "action");
            String reason = jsonValue(fields, "reason");
            String requestedNode = jsonValue(fields, "requestedNode");
            String clearedSession = jsonValue(fields, "clearedSession");
            String clearedTicket = jsonValue(fields, "clearedTicket");
            String nodeId = jsonValue(fields, "nodeId");
            if (nodeId.isBlank()) {
                nodeId = jsonValue(fields, "targetNode");
            }
            entries.add((type.isBlank() ? "UNKNOWN_EVENT" : type)
                + (islandId.isBlank() ? "" : " 섬=" + islandId)
                + (ticketId.isBlank() ? "" : " ticket=" + shortId(ticketId))
                + (playerUuid.isBlank() ? "" : " player=" + shortId(playerUuid))
                + (action.isBlank() ? "" : " action=" + action)
                + (reason.isBlank() ? "" : " reason=" + reason)
                + (requestedNode.isBlank() ? "" : " requestedNode=" + requestedNode)
                + (clearedSession.isBlank() ? "" : " session=" + clearedSession)
                + (clearedTicket.isBlank() ? "" : " ticketCleared=" + clearedTicket)
                + (nodeId.isBlank() ? "" : " node=" + nodeId)
                + (occurredAt.isBlank() ? "" : " at=" + occurredAt));
            index = objectEnd + 1;
        }
        return entries.isEmpty() ? "Events: empty" : "Events: " + String.join(" | ", entries);
    }

    private String auditListMessage(String body) {
        String audit = arrayValue(body, "audit");
        if (audit.isBlank()) {
            return "Audit: empty";
        }
        java.util.List<String> entries = new java.util.ArrayList<>();
        int index = 0;
        while (index < audit.length() && entries.size() < 10) {
            int objectStart = audit.indexOf('{', index);
            if (objectStart < 0) {
                break;
            }
            int objectEnd = matchingObjectEnd(audit, objectStart);
            if (objectEnd < 0) {
                break;
            }
            String object = audit.substring(objectStart, objectEnd + 1);
            String action = jsonValue(object, "action");
            String actorType = jsonValue(object, "actorType");
            String targetType = jsonValue(object, "targetType");
            String targetId = jsonValue(object, "targetId");
            String createdAt = jsonValue(object, "createdAt");
            entries.add((action.isBlank() ? "UNKNOWN_ACTION" : action)
                + (targetType.isBlank() && targetId.isBlank() ? "" : " target=" + targetType + ":" + targetId)
                + (actorType.isBlank() ? "" : " actor=" + actorType)
                + (createdAt.isBlank() ? "" : " at=" + createdAt));
            index = objectEnd + 1;
        }
        return entries.isEmpty() ? "Audit: empty" : "Audit: " + String.join(" | ", entries);
    }

    private String routeDebugMessage(String body) {
        String sessions = arrayValue(body, "sessions");
        String tickets = arrayValue(body, "tickets");
        java.util.List<String> sessionEntries = new java.util.ArrayList<>();
        java.util.List<String> ticketEntries = new java.util.ArrayList<>();
        collectSessionSummaries(sessions, sessionEntries, 5);
        collectTicketSummaries(tickets, ticketEntries, 5);
        return "Routes: sessions=" + countObjects(sessions)
            + (sessionEntries.isEmpty() ? "" : " [" + String.join(" | ", sessionEntries) + "]")
            + " tickets=" + countObjects(tickets)
            + (ticketEntries.isEmpty() ? "" : " [" + String.join(" | ", ticketEntries) + "]");
    }

    private String routeTicketMessage(String body) {
        if (body == null || body.isBlank()) {
            return "Route ticket: not found";
        }
        String code = jsonValue(body, "code");
        if (!code.isBlank()) {
            return "Route ticket: failed code=" + code;
        }
        return "Route ticket: " + ticketSummary(body);
    }

    private String routeClearMessage(String body) {
        if (body == null || body.isBlank()) {
            return "Route clear: no response";
        }
        String reason = jsonValue(body, "reason");
        return "Route clear: session=" + boolValue(body, "clearedSession") + " ticket=" + boolValue(body, "clearedTicket") + (reason.isBlank() ? "" : " reason=" + reason);
    }

    private String snapshotListMessage(String body) {
        String snapshots = arrayValue(body, "snapshots");
        if (snapshots.isBlank()) {
            return "섬 스냅샷이 없습니다.";
        }
        java.util.List<String> entries = new java.util.ArrayList<>();
        int index = 0;
        while (index < snapshots.length() && entries.size() < 20) {
            int objectStart = snapshots.indexOf('{', index);
            if (objectStart < 0) {
                break;
            }
            int objectEnd = matchingObjectEnd(snapshots, objectStart);
            if (objectEnd < 0) {
                break;
            }
            String object = snapshots.substring(objectStart, objectEnd + 1);
            long snapshotNo = longValue(object, "snapshotNo");
            if (snapshotNo > 0L) {
                String reason = jsonValue(object, "reason");
                long sizeBytes = longValue(object, "sizeBytes");
                String createdAt = jsonValue(object, "createdAt");
                entries.add("#" + snapshotNo
                    + (reason.isBlank() ? "" : " 사유=" + reason)
                    + " 크기=" + sizeBytes
                    + (createdAt.isBlank() ? "" : " 생성=" + createdAt));
            }
            index = objectEnd + 1;
        }
        return entries.isEmpty() ? "섬 스냅샷이 없습니다." : "섬 스냅샷: " + String.join(" | ", entries);
    }

    private void collectSessionSummaries(String sessions, java.util.List<String> entries, int limit) {
        int index = 0;
        while (index < sessions.length() && entries.size() < limit) {
            int objectStart = sessions.indexOf('{', index);
            if (objectStart < 0) {
                break;
            }
            int objectEnd = matchingObjectEnd(sessions, objectStart);
            if (objectEnd < 0) {
                break;
            }
            String object = sessions.substring(objectStart, objectEnd + 1);
            String playerUuid = jsonValue(object, "playerUuid");
            String ticketId = jsonValue(object, "ticketId");
            String nodeId = jsonValue(object, "targetNode");
            String serverName = jsonValue(object, "targetServerName");
            String expiresAt = jsonValue(object, "expiresAt");
            entries.add(shortId(playerUuid)
                + " ticket=" + shortId(ticketId)
                + routeNodeSuffix(nodeId)
                + routeServerSuffix(serverName)
                + (expiresAt.isBlank() ? "" : " expires=" + expiresAt));
            index = objectEnd + 1;
        }
    }

    private void collectTicketSummaries(String tickets, java.util.List<String> entries, int limit) {
        int index = 0;
        while (index < tickets.length() && entries.size() < limit) {
            int objectStart = tickets.indexOf('{', index);
            if (objectStart < 0) {
                break;
            }
            int objectEnd = matchingObjectEnd(tickets, objectStart);
            if (objectEnd < 0) {
                break;
            }
            entries.add(ticketSummary(tickets.substring(objectStart, objectEnd + 1)));
            index = objectEnd + 1;
        }
    }

    private String ticketSummary(String object) {
        String ticketId = jsonValue(object, "ticketId");
        String action = jsonValue(object, "action");
        String state = jsonValue(object, "state");
        String islandId = jsonValue(object, "islandId");
        String nodeId = jsonValue(object, "targetNode");
        return shortId(ticketId)
            + " " + (action.isBlank() ? "UNKNOWN" : action)
            + " " + (state.isBlank() ? "UNKNOWN" : state)
            + (islandId.isBlank() ? "" : " 섬=" + shortId(islandId))
            + routeNodeSuffix(nodeId);
    }

    private String shortId(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        return value.length() > 8 ? value.substring(0, 8) : value;
    }

    private int countObjects(String array) {
        int count = 0;
        int index = 0;
        while (index < array.length()) {
            int objectStart = array.indexOf('{', index);
            if (objectStart < 0) {
                break;
            }
            int objectEnd = matchingObjectEnd(array, objectStart);
            if (objectEnd < 0) {
                break;
            }
            count++;
            index = objectEnd + 1;
        }
        return count;
    }

    private String publicIslandListMessage(String body) {
        if (body == null || body.isBlank()) {
            return "공개 섬이 없습니다.";
        }
        String islands = arrayValue(body, "islands");
        if (islands.isBlank()) {
            return "공개 섬이 없습니다.";
        }
        java.util.List<String> entries = new java.util.ArrayList<>();
        int index = 0;
        while (index < islands.length() && entries.size() < 20) {
            int objectStart = islands.indexOf('{', index);
            if (objectStart < 0) {
                break;
            }
            int objectEnd = matchingObjectEnd(islands, objectStart);
            if (objectEnd < 0) {
                break;
            }
            String object = islands.substring(objectStart, objectEnd + 1);
            String islandId = jsonValue(object, "islandId");
            if (!islandId.isBlank()) {
                String name = jsonValue(object, "name");
                long level = longValue(object, "level");
                String worth = jsonValue(object, "worth");
                entries.add((entries.size() + 1) + ". " + (name.isBlank() ? "이름 없는 섬" : name) + " (ID=" + shortId(islandId) + ", 레벨=" + level + ", 가치=" + (worth.isBlank() ? "0" : worth) + ")");
            }
            index = objectEnd + 1;
        }
        return entries.isEmpty() ? "공개 섬이 없습니다." : "공개 섬: " + String.join(" | ", entries);
    }

    private String hiddenNodeLabel(String nodeId) {
        if (hideNodeNames) {
            return nodeId == null || nodeId.isBlank() ? "" : " node-hidden";
        }
        return nodeId == null || nodeId.isBlank() ? "" : " " + nodeId;
    }

    private String routeNodeSuffix(String nodeId) {
        if (nodeId == null || nodeId.isBlank()) {
            return "";
        }
        return hideNodeNames ? " node=node-hidden" : " node=" + nodeId;
    }

    private String routeServerSuffix(String serverName) {
        if (serverName == null || serverName.isBlank()) {
            return "";
        }
        return hideNodeNames ? " server=server-hidden" : " server=" + serverName;
    }

    private String nodeIslandRuntimeSuffix(String object) {
        java.util.List<String> parts = new java.util.ArrayList<>();
        String state = jsonValue(object, "state");
        if (!state.isBlank()) {
            parts.add(state);
        }
        String world = jsonValue(object, "activeWorld");
        if (!world.isBlank()) {
            parts.add("world=" + world);
        }
        if (!object.contains("\"cellX\":null") && !object.contains("\"cellZ\":null")) {
            parts.add("cell=" + longValue(object, "cellX") + "," + longValue(object, "cellZ"));
        }
        return String.join(" ", parts);
    }

    private String arrayValue(String body, String field) {
        String needle = "\"" + field + "\":[";
        int start = body == null ? -1 : body.indexOf(needle);
        if (start < 0) {
            return "";
        }
        start += needle.length() - 1;
        int depth = 0;
        for (int i = start; i < body.length(); i++) {
            char current = body.charAt(i);
            if (current == '[') {
                depth++;
            } else if (current == ']') {
                depth--;
                if (depth == 0) {
                    return body.substring(start, i + 1);
                }
            }
        }
        return "";
    }

    private int matchingObjectEnd(String value, int objectStart) {
        int depth = 0;
        for (int i = objectStart; i < value.length(); i++) {
            char current = value.charAt(i);
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

    private void appendLongSummary(StringBuilder summary, String label, long value) {
        if (value > 0L) {
            summary.append(", ").append(label).append('=').append(value);
        }
    }

    private String objectValue(String body, String field) {
        String needle = "\"" + field + "\":{";
        int start = body == null ? -1 : body.indexOf(needle);
        if (start < 0) {
            return "";
        }
        start += needle.length() - 1;
        int depth = 0;
        for (int i = start; i < body.length(); i++) {
            char current = body.charAt(i);
            if (current == '{') {
                depth++;
            } else if (current == '}') {
                depth--;
                if (depth == 0) {
                    return body.substring(start, i + 1);
                }
            }
        }
        return "";
    }

    private boolean boolValue(String body, String field) {
        String needle = "\"" + field + "\":";
        int start = body == null ? -1 : body.indexOf(needle);
        if (start < 0) {
            return false;
        }
        start += needle.length();
        while (start < body.length() && Character.isWhitespace(body.charAt(start))) {
            start++;
        }
        return body.startsWith("true", start);
    }

    private long longValue(String body, String field) {
        String needle = "\"" + field + "\":";
        int start = body == null ? -1 : body.indexOf(needle);
        if (start < 0) {
            return 0L;
        }
        start += needle.length();
        int end = start;
        while (end < body.length() && (body.charAt(end) == '-' || Character.isDigit(body.charAt(end)))) {
            end++;
        }
        if (end == start) {
            return 0L;
        }
        try {
            return Long.parseLong(body.substring(start, end));
        } catch (NumberFormatException exception) {
            return 0L;
        }
    }

    private long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            return 0L;
        }
    }

    private double doubleValue(String body, String field) {
        String needle = "\"" + field + "\":";
        int start = body == null ? -1 : body.indexOf(needle);
        if (start < 0) {
            return 0.0D;
        }
        start += needle.length();
        int end = start;
        while (end < body.length() && (body.charAt(end) == '-' || body.charAt(end) == '+' || body.charAt(end) == '.' || Character.isDigit(body.charAt(end)))) {
            end++;
        }
        try {
            return Double.parseDouble(body.substring(start, end));
        } catch (RuntimeException ignored) {
            return 0.0D;
        }
    }

    private String seconds(double value) {
        return String.format(java.util.Locale.ROOT, "%.3f", value);
    }

    private void sendInviteActionResult(Player player, CompletableFuture<String> future, String successMessage, String failureMessage) {
        future.thenAccept(body -> {
            if (body == null || body.isBlank() || body.contains("\"error\"") || body.contains("\"accepted\":false")) {
                player.sendMessage(Component.text(failureMessage));
                return;
            }
            player.sendMessage(Component.text(successMessage));
        }).exceptionally(error -> {
            player.sendMessage(Component.text(failureMessage));
            return null;
        });
    }

    private CompletableFuture<UUID> resolveInviteTarget(Player player, String target) {
        if (target == null || target.isBlank()) {
            return CompletableFuture.completedFuture(new UUID(0L, 0L));
        }
        UUID parsed = parseUuid(target);
        if (!parsed.equals(new UUID(0L, 0L))) {
            return coreApiClient.listPendingInvites(player.getUniqueId()).thenApply(body -> {
                UUID inviteId = findInviteId(body, parsed);
                return inviteId.equals(new UUID(0L, 0L)) ? parsed : inviteId;
            });
        }
        Optional<Player> online = proxy.getPlayer(target);
        if (online.isPresent()) {
            return coreApiClient.listPendingInvites(player.getUniqueId()).thenApply(body -> findInviteId(body, online.get().getUniqueId()));
        }
        return coreApiClient.playerInfoByName(target)
            .handle((body, error) -> error == null ? parseUuid(jsonValue(body, "playerUuid")) : new UUID(0L, 0L))
            .thenCompose(playerUuid -> {
                if (playerUuid.equals(new UUID(0L, 0L))) {
                    return resolveInviteIslandName(player, target);
                }
                return coreApiClient.listPendingInvites(player.getUniqueId()).thenCompose(invites -> {
                    UUID inviteId = findInviteId(invites, playerUuid);
                    return inviteId.equals(new UUID(0L, 0L)) ? resolveInviteIslandName(player, target) : CompletableFuture.completedFuture(inviteId);
                });
            });
    }

    private CompletableFuture<UUID> resolveInviteIslandName(Player player, String islandName) {
        return coreApiClient.islandInfoByName(islandName)
            .thenCompose(body -> coreApiClient.listPendingInvites(player.getUniqueId()).thenApply(invites -> findInviteId(invites, parseUuid(jsonValue(body, "islandId")))));
    }

    private CompletableFuture<UUID> resolvePlayerUuid(String target) {
        if (target == null || target.isBlank()) {
            return CompletableFuture.completedFuture(new UUID(0L, 0L));
        }
        UUID parsed = parseUuid(target);
        if (!parsed.equals(new UUID(0L, 0L))) {
            return CompletableFuture.completedFuture(parsed);
        }
        Optional<Player> online = proxy.getPlayer(target);
        if (online.isPresent()) {
            return CompletableFuture.completedFuture(online.get().getUniqueId());
        }
        return coreApiClient.playerInfoByName(target).thenApply(body -> parseUuid(jsonValue(body, "playerUuid")));
    }

    private void adminIslandTarget(Player player, String target, Consumer<UUID> action) {
        resolveIslandId(target).thenAccept(islandId -> {
            if (islandId.equals(new UUID(0L, 0L))) {
                player.sendMessage(Component.text("섬을 찾지 못했습니다."));
                return;
            }
            action.accept(islandId);
        }).exceptionally(error -> {
            player.sendMessage(Component.text("섬을 찾지 못했습니다."));
            return null;
        });
    }

    private CompletableFuture<UUID> resolveIslandId(String target) {
        if (target == null || target.isBlank()) {
            return CompletableFuture.completedFuture(new UUID(0L, 0L));
        }
        UUID parsed = parseUuid(target);
        if (!parsed.equals(new UUID(0L, 0L))) {
            return CompletableFuture.completedFuture(parsed);
        }
        return coreApiClient.islandInfoByName(target).thenApply(body -> parseUuid(jsonValue(body, "islandId")));
    }

    private UUID findInviteId(String body, UUID targetUuid) {
        if (body == null || targetUuid == null || targetUuid.equals(new UUID(0L, 0L))) {
            return new UUID(0L, 0L);
        }
        int index = 0;
        while (index < body.length()) {
            int objectStart = body.indexOf('{', index);
            if (objectStart < 0) {
                break;
            }
            int objectEnd = body.indexOf('}', objectStart);
            if (objectEnd < 0) {
                break;
            }
            String object = body.substring(objectStart, objectEnd + 1);
            UUID inviteId = parseUuid(jsonValue(object, "inviteId"));
            if (targetUuid.equals(inviteId) || targetUuid.equals(parseUuid(jsonValue(object, "islandId"))) || targetUuid.equals(parseUuid(jsonValue(object, "inviterUuid")))) {
                return inviteId;
            }
            index = objectEnd + 1;
        }
        return new UUID(0L, 0L);
    }

    private String inviteListMessage(String body) {
        java.util.List<String> entries = new java.util.ArrayList<>();
        int index = 0;
        while (body != null && index < body.length()) {
            int objectStart = body.indexOf('{', index);
            if (objectStart < 0) {
                break;
            }
            int objectEnd = body.indexOf('}', objectStart);
            if (objectEnd < 0) {
                break;
            }
            String object = body.substring(objectStart, objectEnd + 1);
            String inviteId = jsonValue(object, "inviteId");
            if (!inviteId.isBlank()) {
                String islandId = jsonValue(object, "islandId");
                String inviterUuid = jsonValue(object, "inviterUuid");
                entries.add(shortId(inviteId) + (islandId.isBlank() ? "" : " 섬=" + shortId(islandId)) + (inviterUuid.isBlank() ? "" : " 초대한사람=" + shortId(inviterUuid)));
            }
            index = objectEnd + 1;
        }
        return entries.isEmpty() ? "대기 중인 섬 초대가 없습니다." : "섬 초대: " + String.join(", ", entries);
    }

    private String playerPayloadMessage(String body, String emptyMessage, String successMessage) {
        if (body == null || body.isBlank()) {
            return emptyMessage;
        }
        String trimmed = body.trim();
        if (trimmed.startsWith("{\"error\"")) {
            String code = jsonValue(trimmed, "code");
            return playerErrorMessage(code, emptyMessage);
        }
        if (trimmed.startsWith("{") && trimmed.contains("\"accepted\":false")) {
            String code = jsonValue(trimmed, "code");
            return playerErrorMessage(code, emptyMessage);
        }
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return successMessage;
        }
        return trimmed;
    }

    private String playerErrorMessage(String code, String fallback) {
        if (code == null || code.isBlank()) {
            return fallback;
        }
        if (code.startsWith("NO_READY_NODE") || code.startsWith("TARGET_NODE") || code.startsWith("ACTIVE_NODE")) {
            return "현재 섬 서비스가 혼잡합니다. 잠시 후 다시 시도해주세요.";
        }
        return switch (code) {
            case "ALREADY_HAS_ISLAND" -> "이미 섬을 보유하고 있습니다.";
            case "TEMPLATE_UNAVAILABLE" -> "사용할 수 없는 섬 템플릿입니다.";
            case "PLAYER_NOT_FOUND" -> "플레이어를 찾을 수 없습니다.";
            case "ISLAND_NOT_FOUND" -> "섬을 찾을 수 없습니다.";
            case "ISLAND_PRIVATE" -> "해당 섬은 비공개 상태입니다.";
            case "ISLAND_LOCKED" -> "해당 섬은 현재 잠겨 있습니다.";
            case "VISITOR_BANNED" -> "해당 섬에 방문할 수 없습니다.";
            case "VISITOR_SOFT_FULL" -> "해당 섬은 지금 멤버 입장 슬롯을 우선 사용 중입니다. 잠시 후 다시 시도해주세요.";
            case "ACTIVATION_LOCKED" -> "섬을 준비하는 중입니다. 잠시 후 다시 시도해주세요.";
            case "NODE_UNAVAILABLE" -> "현재 섬 서비스가 혼잡합니다. 잠시 후 다시 시도해주세요.";
            case "TARGET_OFFLINE_NO_ISLAND" -> "대상 플레이어의 섬을 찾을 수 없습니다.";
            case "PUBLIC_ISLAND_NOT_FOUND" -> "방문 가능한 공개 섬을 찾지 못했습니다.";
            case "WARP_NOT_FOUND" -> "해당 워프를 찾을 수 없습니다.";
            case "WARP_PRIVATE" -> "해당 워프는 공개 상태가 아닙니다.";
            case "WARP_LIMIT" -> "섬 워프 한도에 도달했습니다.";
            case "ISLAND_MIGRATING" -> "섬 서버를 최적화하는 중입니다. 잠시 후 자동으로 이동됩니다.";
            case "ISLAND_LOADING_FAILED" -> "섬을 준비하지 못했습니다. 잠시 후 다시 시도해주세요.";
            case "JOB_QUEUE_UNAVAILABLE", "RECOVERY_UNAVAILABLE" -> "현재 섬 서비스 일부 기능이 점검 중입니다.";
            case "ROUTE_TICKET_NOT_FOUND", "ROUTE_ROUTE_NOT_FOUND" -> "섬 이동 세션이 만료되었습니다. 다시 시도해주세요.";
            case "ISLAND_PERMISSION_DENIED" -> "섬 권한이 없습니다.";
            case "MEMBER_LIMIT" -> "섬 멤버 한도에 도달했습니다.";
            case "BANK_LIMIT" -> "섬 은행 한도에 도달했습니다.";
            case "INVALID_AMOUNT" -> "올바른 금액을 입력해주세요.";
            case "INSUFFICIENT_FUNDS" -> "잔액이 부족합니다.";
            case "UNKNOWN_UPGRADE" -> "알 수 없는 업그레이드입니다.";
            case "MAX_LEVEL" -> "이미 최대 업그레이드 레벨입니다.";
            case "INVITE_UNAVAILABLE" -> "사용할 수 없는 초대입니다.";
            case "OWNERSHIP_TRANSFER_DENIED" -> "섬 소유권을 양도할 수 없습니다.";
            case "UNAUTHORIZED", "ADMIN_PERMISSION_DENIED" -> "이 명령을 사용할 권한이 없습니다.";
            case "RATE_LIMITED" -> "요청이 너무 많습니다. 잠시 후 다시 시도해주세요.";
            default -> fallback;
        };
    }

    private UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (RuntimeException ignored) {
            return new UUID(0L, 0L);
        }
    }

    private String jsonValue(String json, String key) {
        String needle = "\"" + key + "\":\"";
        int start = json.indexOf(needle);
        if (start < 0) {
            return "";
        }
        start += needle.length();
        int end = json.indexOf('"', start);
        return end < 0 ? "" : json.substring(start, end);
    }

    private void route(Player player, RouteTicket ticket, String failureMessage) {
        routeAttempts.incrementAndGet();
        if (ticket == null) {
            fallback(player, failureMessage);
            return;
        }
        if (ticket.state().name().equals("PREPARING")) {
            String target = routeTargetName(ticket);
            actionBar(player, target + "을 준비하는 중입니다.");
            BossBar bossBar = BossBar.bossBar(Component.text(target + " 로딩 중"), 0.2F, BossBar.Color.BLUE, BossBar.Overlay.PROGRESS);
            showBossBar(player, bossBar);
            waitForReadyTicket(player, ticket, failureMessage, bossBar, 0);
            return;
        }
        publishAndConnect(player, ticket);
    }

    private void routeFuture(Player player, CompletableFuture<RouteTicket> ticketFuture, String failureMessage) {
        ticketFuture.thenAccept(ticket -> route(player, ticket, failureMessage)).exceptionally(error -> {
            fallback(player, failureMessage);
            return null;
        });
    }

    private boolean allowRouteRequest(Player player) {
        long now = System.currentTimeMillis();
        UUID playerUuid = player.getUniqueId();
        Long previous = recentRouteRequests.put(playerUuid, now);
        if (previous == null || now - previous >= PLAYER_ROUTE_COOLDOWN_MILLIS) {
            return true;
        }
        recentRouteRequests.put(playerUuid, previous);
        player.sendMessage(Component.text("섬 이동 요청이 너무 빠릅니다. 잠시 후 다시 시도해주세요."));
        return false;
    }

    private void waitForReadyTicket(Player player, RouteTicket ticket, String failureMessage, BossBar bossBar, int attempt) {
        int progress = Math.min(95, 20 + attempt);
        bossBar.progress(progress / 100.0F);
        String target = routeTargetName(ticket);
        bossBar.name(Component.text(target + " 로딩 중 " + progress + "%"));
        actionBar(player, target + "을 준비하는 중입니다... " + progress + "%");
        coreApiClient.routeTicketStatus(ticket.ticketId(), ticket.playerUuid(), ticket.nonce()).thenAccept(status -> {
            Optional<RouteTicket> ready = status.filter(value -> value.state().name().equals("READY"));
            if (ready.isPresent()) {
                bossBar.progress(1.0F);
                String readyTarget = routeTargetName(ready.get());
                bossBar.name(Component.text("잠시 후 " + readyTarget + "으로 이동합니다."));
                actionBar(player, "잠시 후 " + readyTarget + "으로 이동합니다.");
                hideBossBar(player, bossBar);
                publishAndConnect(player, ready.get());
                return;
            }
            if (attempt >= routeWaitSeconds) {
                hideBossBar(player, bossBar);
                clearFailedRoute(ticket, "ROUTE_READY_TIMEOUT");
                fallback(player, failureMessage);
                return;
            }
            CompletableFuture.delayedExecutor(1, TimeUnit.SECONDS).execute(() -> waitForReadyTicket(player, ticket, failureMessage, bossBar, attempt + 1));
        }).exceptionally(error -> {
            hideBossBar(player, bossBar);
            clearFailedRoute(ticket, "ROUTE_STATUS_FAILED");
            fallback(player, failureMessage);
            return null;
        });
    }

    private void actionBar(Player player, String message) {
        if (useActionBar) {
            player.sendActionBar(Component.text(message));
        }
    }

    private void showBossBar(Player player, BossBar bossBar) {
        if (useBossBarLoading) {
            player.showBossBar(bossBar);
        }
    }

    private void hideBossBar(Player player, BossBar bossBar) {
        if (useBossBarLoading) {
            player.hideBossBar(bossBar);
        }
    }

    private void publishAndConnect(Player player, RouteTicket ticket) {
        coreApiClient.publishRouteSession(ticket).thenRun(() -> {
            String targetServerName = ticket.payload().getOrDefault("targetServerName", ticket.targetNode());
            connectWithTicket(player, ticket, targetServerName);
        }).exceptionally(error -> {
            clearFailedRoute(ticket, "SESSION_PUBLISH_FAILED");
            fallback(player, "섬 이동 정보를 준비하지 못했습니다. 로비로 이동합니다.");
            return null;
        });
    }

    private void connectWithTicket(Player player, RouteTicket ticket, String targetServerName) {
        RegisteredServer server = findServer(targetServerName);
        if (server == null) {
            clearFailedRoute(ticket, "TARGET_SERVER_NOT_FOUND");
            fallback(player, "섬 이동 경로를 찾을 수 없습니다.");
            return;
        }
        connect(player, ticket, server);
    }

    private void connect(Player player, RouteTicket ticket, RegisteredServer server) {
        player.createConnectionRequest(server).connectWithIndication().thenAccept(success -> {
            if (!success) {
                clearFailedRoute(ticket, "CONNECT_FAILED");
                fallback(player, "섬으로 이동하지 못했습니다. 로비로 이동합니다.");
                return;
            }
            routeSuccesses.incrementAndGet();
            actionBar(player, arrivalMessage(ticket));
        }).exceptionally(error -> {
            clearFailedRoute(ticket, "CONNECT_EXCEPTION");
            fallback(player, "섬으로 이동하지 못했습니다. 로비로 이동합니다.");
            return null;
        });
    }

    private void clearFailedRoute(RouteTicket ticket, String reason) {
        if (ticket == null) {
            return;
        }
        coreApiClient.clearRoute(ticket.playerUuid(), ticket.ticketId(), reason == null || reason.isBlank() ? "ROUTE_FAILED" : reason).exceptionally(error -> null);
    }

    private void fallback(Player player, String message) {
        routeFailures.incrementAndGet();
        player.sendMessage(Component.text(message));
        proxy.getServer(fallbackServer).ifPresent(server -> player.createConnectionRequest(server).connectWithIndication().thenAccept(success -> {
            if (success) {
                fallbackTransfers.incrementAndGet();
            }
        }));
    }

    private String routeTargetName(RouteTicket ticket) {
        if (ticket == null || ticket.action() == null) {
            return "섬";
        }
        return switch (ticket.action().name().toUpperCase(Locale.ROOT)) {
            case "HOME" -> "내 섬";
            case "VISIT" -> "방문할 섬";
            case "WARP" -> "섬 워프";
            case "ADMIN_TELEPORT" -> "관리 대상 섬";
            case "RETURN_AFTER_MIGRATION" -> "이전하던 섬";
            default -> "섬";
        };
    }

    private String arrivalMessage(RouteTicket ticket) {
        if (ticket == null || ticket.action() == null) {
            return "섬에 도착했습니다.";
        }
        return switch (ticket.action().name().toUpperCase(Locale.ROOT)) {
            case "HOME" -> "내 섬에 도착했습니다.";
            case "VISIT" -> "방문한 섬에 도착했습니다.";
            case "WARP" -> "섬 워프에 도착했습니다.";
            case "ADMIN_TELEPORT" -> "관리 대상 섬에 도착했습니다.";
            case "RETURN_AFTER_MIGRATION" -> "섬 이전이 끝났습니다.";
            default -> "섬에 도착했습니다.";
        };
    }

    private int moveNodePlayersToFallback(String nodeId) {
        RegisteredServer target = findServer(nodeId);
        RegisteredServer fallback = findServer(fallbackServer);
        if (target == null || fallback == null) {
            return 0;
        }
        java.util.List<Player> players = java.util.List.copyOf(target.getPlayersConnected());
        for (Player connected : players) {
            connected.sendMessage(Component.text("섬 점검으로 로비로 이동합니다."));
            connected.createConnectionRequest(fallback).connectWithIndication();
        }
        return players.size();
    }

    private RegisteredServer findServer(String name) {
        return proxy.getServer(name).or(() -> proxy.getServer(nodeIdToServerName(name))).orElse(null);
    }

    private String nodeIdToServerName(String nodeId) {
        if (nodeId == null || nodeId.isBlank()) {
            return "";
        }
        String[] parts = nodeId.split("-");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append('-');
            }
            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                builder.append(part.substring(1));
            }
        }
        return builder.toString();
    }

    private String messageForCreateFailure(String code) {
        if (code != null && code.startsWith("NO_READY_NODE")) {
            return messages.text("island-create-node-unavailable");
        }
        return switch (code) {
            case "ALREADY_HAS_ISLAND" -> messages.text("island-create-already-has-island");
            case "TEMPLATE_UNAVAILABLE" -> messages.text("island-create-template-unavailable");
            case "CREATE_LOCKED" -> messages.text("island-create-locked");
            case "NODE_UNAVAILABLE" -> messages.text("island-create-node-unavailable");
            case "JOB_QUEUE_UNAVAILABLE", "RECOVERY_UNAVAILABLE" -> messages.text("island-service-maintenance");
            default -> messages.text("island-create-failed");
        };
    }
}
