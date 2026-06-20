package kr.lunaf.cloudislands.paper.config;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.paper.AgentRole;
import kr.lunaf.cloudislands.storage.snapshot.SnapshotRetentionPolicy;

public record PaperRuntimeConfig(
    String serviceName,
    Node node,
    CoreApi coreApi,
    Redis redis,
    Security security,
    Routing routing,
    Protection protection,
    Generator generator,
    Messages messages,
    Storage storage,
    Worker worker,
    SnapshotRetentionPolicy snapshots,
    Health health,
    Heartbeat heartbeat,
    Gui gui
) {
    public PaperRuntimeConfig {
        serviceName = blankDefault(serviceName, "CloudIslands");
        node = node == null ? Node.defaults() : node;
        coreApi = coreApi == null ? CoreApi.defaults() : coreApi;
        redis = redis == null ? Redis.defaults() : redis;
        security = security == null ? Security.defaults() : security;
        routing = routing == null ? Routing.defaults() : routing;
        protection = protection == null ? Protection.defaults() : protection;
        generator = generator == null ? Generator.defaults() : generator;
        messages = messages == null ? Messages.defaults() : messages;
        storage = storage == null ? Storage.defaults() : storage;
        worker = worker == null ? Worker.defaults() : worker;
        snapshots = snapshots == null ? new SnapshotRetentionPolicy(24, 7, 4, 50, true, "SHA-256").normalized() : snapshots.normalized();
        health = health == null ? Health.defaults() : health;
        heartbeat = heartbeat == null ? Heartbeat.defaults() : heartbeat;
        gui = gui == null ? Gui.defaults() : gui;
    }

    public boolean guiEnabledForRole(AgentRole role) {
        return gui.enabledForRole(role);
    }

    public static PaperRuntimeConfig defaults() {
        return new PaperRuntimeConfig(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    private static String blankDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    public record Node(
        String id,
        String pool,
        String velocityServerName,
        AgentRole role,
        boolean rejectDefaultIdentity,
        List<String> supportedTemplates,
        String supportedTemplateFallback,
        String templateVersions,
        int maxActivationQueue,
        int hardPlayerCap,
        int reservedSlots,
        Integer softPlayerCap,
        int maxActiveIslands
    ) {
        public Node {
            id = blankDefault(id, "island-1");
            pool = blankDefault(pool, "island");
            velocityServerName = blankDefault(velocityServerName, id);
            role = role == null ? AgentRole.ISLAND_NODE : role;
            supportedTemplates = supportedTemplates == null ? List.of() : List.copyOf(supportedTemplates);
            supportedTemplateFallback = blankDefault(supportedTemplateFallback, "*");
            templateVersions = templateVersions == null ? "" : templateVersions;
            maxActivationQueue = Math.max(1, maxActivationQueue);
            hardPlayerCap = Math.max(1, hardPlayerCap);
            reservedSlots = Math.max(0, reservedSlots);
            softPlayerCap = softPlayerCap == null ? null : Math.max(1, softPlayerCap);
            maxActiveIslands = Math.max(1, maxActiveIslands);
        }

        public static Node defaults() {
            return new Node("island-1", "island", "island-1", AgentRole.ISLAND_NODE, true, List.of(), "*", "", 4, 110, 15, null, 600);
        }

        public String supportedTemplatesCsv() {
            String configured = String.join(",", supportedTemplates);
            return configured.isBlank() ? supportedTemplateFallback : configured;
        }

        public int effectiveSoftPlayerCap() {
            int reservedSoftCap = Math.max(1, hardPlayerCap - reservedSlots);
            return softPlayerCap == null ? reservedSoftCap : Math.max(1, Math.min(reservedSoftCap, softPlayerCap));
        }
    }

    public record CoreApi(String baseUrl, String token, String adminToken, Duration timeout) {
        public CoreApi {
            baseUrl = blankDefault(baseUrl, "https://core-api.internal:8443");
            token = token == null ? "" : token;
            adminToken = adminToken == null ? "" : adminToken;
            timeout = timeout == null || timeout.isZero() || timeout.isNegative() ? Duration.ofMillis(3000L) : timeout;
        }

        public static CoreApi defaults() {
            return new CoreApi("https://core-api.internal:8443", "", "", Duration.ofMillis(3000L));
        }
    }

    public record Redis(String uri, Duration timeout) {
        public Redis {
            uri = blankDefault(uri, "redis://redis.internal:6379");
            timeout = timeout == null || timeout.isZero() || timeout.isNegative() ? Duration.ofMillis(1000L) : timeout;
        }

        public static Redis defaults() {
            return new Redis("redis://redis.internal:6379", Duration.ofMillis(1000L));
        }
    }

    public record Security(
        boolean allowBungeeConnectPluginMessaging,
        boolean enforceRouteSession,
        boolean requireRouteSession,
        boolean requireVelocityForwarding,
        String forwardingSecret,
        List<String> proxySourceAllowlist,
        boolean requireProxySourceAllowlist
    ) {
        public Security {
            forwardingSecret = forwardingSecret == null ? "" : forwardingSecret;
            proxySourceAllowlist = proxySourceAllowlist == null ? List.of() : List.copyOf(proxySourceAllowlist);
        }

        public static Security defaults() {
            return new Security(false, true, true, true, "", List.of(), true);
        }
    }

    public record Routing(String fallbackServerName, int waitForActivationTimeoutSeconds, boolean hideNodeNames) {
        public Routing {
            fallbackServerName = blankDefault(fallbackServerName, "Lobby");
            waitForActivationTimeoutSeconds = Math.max(1, waitForActivationTimeoutSeconds);
        }

        public static Routing defaults() {
            return new Routing("Lobby", 20, true);
        }
    }

    public record Protection(long denyMessageCooldownMs, long cacheEventPollTicks, Map<IslandPermission, String> denyMessages) {
        public Protection {
            denyMessageCooldownMs = Math.max(0L, denyMessageCooldownMs);
            cacheEventPollTicks = Math.max(1L, cacheEventPollTicks);
            denyMessages = denyMessages == null ? Map.of() : Map.copyOf(denyMessages);
        }

        public static Protection defaults() {
            return new Protection(1000L, 100L, Map.of());
        }
    }

    public record Generator(String defaultKey) {
        public Generator {
            defaultKey = blankDefault(defaultKey, "default");
        }

        public static Generator defaults() {
            return new Generator("default");
        }
    }

    public record Messages(String locale, Map<String, String> translations, List<String> scoreboardLines) {
        public Messages {
            locale = blankDefault(locale, "ko_kr");
            translations = translations == null ? Map.of() : Map.copyOf(translations);
            scoreboardLines = scoreboardLines == null || scoreboardLines.isEmpty()
                ? List.of("플레이어: {player}", "접속: {online}명", "섬 이동: /섬", "방문: /섬 방문", "관리: /섬 설정")
                : List.copyOf(scoreboardLines);
        }

        public static Messages defaults() {
            return new Messages("ko_kr", Map.of(), List.of());
        }
    }

    public record Storage(StorageTarget primary, boolean fallbackEnabled, StorageTarget fallback) {
        public Storage {
            primary = primary == null ? StorageTarget.s3Defaults() : primary;
            fallback = fallback == null ? StorageTarget.localDefaults("islands-storage-fallback") : fallback;
        }

        public static Storage defaults() {
            return new Storage(StorageTarget.s3Defaults(), true, StorageTarget.localDefaults("islands-storage-fallback"));
        }
    }

    public record StorageTarget(
        String backend,
        String endpoint,
        String bucket,
        String region,
        String accessKey,
        String secretKey,
        String bearerToken,
        String localPath
    ) {
        public StorageTarget {
            backend = blankDefault(backend, "S3");
            endpoint = blankDefault(endpoint, "http://minio.internal:9000");
            bucket = blankDefault(bucket, "cloudislands");
            region = blankDefault(region, "us-east-1");
            accessKey = accessKey == null ? "" : accessKey;
            secretKey = secretKey == null ? "" : secretKey;
            bearerToken = bearerToken == null ? "" : bearerToken;
            localPath = blankDefault(localPath, "islands-storage");
        }

        public static StorageTarget s3Defaults() {
            return new StorageTarget("S3", "http://minio.internal:9000", "cloudislands", "us-east-1", "", "", "", "islands-storage");
        }

        public static StorageTarget localDefaults(String path) {
            return new StorageTarget("LOCAL_FILESYSTEM", "http://minio.internal:9000", "cloudislands", "us-east-1", "", "", "", path);
        }
    }

    public record Worker(
        String shardWorldPrefix,
        int shardCount,
        int cellSize,
        int activationPreloadRadius,
        int defaultIslandSize,
        long activationWorkerIntervalTicks,
        long periodicSaveSeconds,
        long saveOnEmptyAfterSeconds,
        long levelScanIntervalSeconds
    ) {
        public Worker {
            shardWorldPrefix = blankDefault(shardWorldPrefix, "ci_shard_");
            shardCount = Math.max(1, shardCount);
            cellSize = Math.max(1, cellSize);
            activationPreloadRadius = Math.max(0, activationPreloadRadius);
            defaultIslandSize = Math.max(1, defaultIslandSize);
            activationWorkerIntervalTicks = Math.max(1L, activationWorkerIntervalTicks);
            periodicSaveSeconds = Math.max(1L, periodicSaveSeconds);
            saveOnEmptyAfterSeconds = Math.max(1L, saveOnEmptyAfterSeconds);
            levelScanIntervalSeconds = Math.max(1L, levelScanIntervalSeconds);
        }

        public static Worker defaults() {
            return new Worker("ci_shard_", 16, 1024, 4, 300, 20L, 600L, 300L, 900L);
        }
    }

    public record Health(boolean enabled, String bindHost, int port) {
        public Health {
            bindHost = blankDefault(bindHost, "127.0.0.1");
            port = Math.max(1, port);
        }

        public static Health defaults() {
            return new Health(false, "127.0.0.1", 8787);
        }
    }

    public record Heartbeat(long intervalTicks) {
        public Heartbeat {
            intervalTicks = Math.max(1L, intervalTicks);
        }

        public static Heartbeat defaults() {
            return new Heartbeat(20L);
        }
    }

    public record Gui(boolean enabled, boolean islandNodeEnabled, boolean lobbyEnabled) {
        public static Gui defaults() {
            return new Gui(true, true, true);
        }

        public boolean enabledForRole(AgentRole role) {
            if (!enabled) {
                return false;
            }
            return role == AgentRole.ISLAND_NODE ? islandNodeEnabled : lobbyEnabled;
        }
    }
}
