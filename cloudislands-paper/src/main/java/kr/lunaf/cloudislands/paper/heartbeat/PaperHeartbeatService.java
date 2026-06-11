package kr.lunaf.cloudislands.paper.heartbeat;

import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;
import kr.lunaf.cloudislands.api.model.NodeState;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.protocol.node.NodeHeartbeatRequest;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

public final class PaperHeartbeatService {
    private final Plugin plugin;
    private final CoreApiClient coreApiClient;
    private final String nodeId;
    private final String pool;
    private final String velocityServerName;
    private final String nodeVersion;
    private final String supportedTemplates;
    private final BooleanSupplier storageAvailable;
    private final IntSupplier softPlayerCap;
    private final IntSupplier hardPlayerCap;
    private final IntSupplier activeIslandCount;
    private final IntSupplier maxActiveIslands;
    private final IntSupplier activationQueue;
    private final IntSupplier maxActivationQueue;
    private final DoubleSupplier chunkLoadPressure;
    private final IntSupplier recentFailurePenalty;
    private BukkitTask task;

    public PaperHeartbeatService(Plugin plugin, CoreApiClient coreApiClient, String nodeId, String pool, String velocityServerName) {
        this(plugin, coreApiClient, nodeId, pool, velocityServerName, "", "*", () -> true);
    }

    public PaperHeartbeatService(Plugin plugin, CoreApiClient coreApiClient, String nodeId, String pool, String velocityServerName, BooleanSupplier storageAvailable) {
        this(plugin, coreApiClient, nodeId, pool, velocityServerName, "", "*", storageAvailable);
    }

    public PaperHeartbeatService(Plugin plugin, CoreApiClient coreApiClient, String nodeId, String pool, String velocityServerName, String nodeVersion, String supportedTemplates, BooleanSupplier storageAvailable) {
        this(plugin, coreApiClient, nodeId, pool, velocityServerName, nodeVersion, supportedTemplates, storageAvailable, () -> 90, () -> 110, () -> 0, () -> 600, () -> 0, () -> 20, () -> 0.0D, () -> 0);
    }

    public PaperHeartbeatService(Plugin plugin, CoreApiClient coreApiClient, String nodeId, String pool, String velocityServerName, String nodeVersion, String supportedTemplates, BooleanSupplier storageAvailable, IntSupplier softPlayerCap, IntSupplier hardPlayerCap, IntSupplier activeIslandCount, IntSupplier maxActiveIslands, IntSupplier activationQueue, IntSupplier maxActivationQueue, DoubleSupplier chunkLoadPressure, IntSupplier recentFailurePenalty) {
        this.plugin = plugin;
        this.coreApiClient = coreApiClient;
        this.nodeId = nodeId;
        this.pool = pool;
        this.velocityServerName = velocityServerName;
        this.nodeVersion = nodeVersion == null ? "" : nodeVersion;
        this.supportedTemplates = supportedTemplates == null || supportedTemplates.isBlank() ? "*" : supportedTemplates;
        this.storageAvailable = storageAvailable;
        this.softPlayerCap = softPlayerCap;
        this.hardPlayerCap = hardPlayerCap;
        this.activeIslandCount = activeIslandCount;
        this.maxActiveIslands = maxActiveIslands;
        this.activationQueue = activationQueue;
        this.maxActivationQueue = maxActivationQueue;
        this.chunkLoadPressure = chunkLoadPressure;
        this.recentFailurePenalty = recentFailurePenalty;
    }

    public void start(long intervalTicks) {
        stop();
        task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::publish, intervalTicks, intervalTicks);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void publish() {
        Runtime runtime = Runtime.getRuntime();
        long heapMax = runtime.maxMemory() / 1024L / 1024L;
        long heapUsed = (runtime.totalMemory() - runtime.freeMemory()) / 1024L / 1024L;
        int players = Bukkit.getOnlinePlayers().size();
        int activeIslands = Math.max(0, activeIslandCount.getAsInt());
        int softCap = Math.max(1, softPlayerCap.getAsInt());
        int hardCap = Math.max(softCap, hardPlayerCap.getAsInt());
        int maxActive = Math.max(1, maxActiveIslands.getAsInt());
        NodeHeartbeatRequest heartbeat = new NodeHeartbeatRequest(
            nodeId,
            pool,
            velocityServerName,
            nodeVersion,
            nodeState(players, softCap, hardCap, activeIslands, maxActive),
            players,
            softCap,
            hardCap,
            activeIslands,
            maxActive,
            currentMspt(),
            Math.max(0, activationQueue.getAsInt()),
            Math.max(1, maxActivationQueue.getAsInt()),
            Math.max(0.0D, chunkLoadPressure.getAsDouble()),
            heapUsed,
            heapMax,
            Math.max(0, recentFailurePenalty.getAsInt()),
            storageAvailable.getAsBoolean(),
            supportedTemplates
        );
        coreApiClient.publishHeartbeat(heartbeat);
    }

    private NodeState nodeState(int players, int softCap, int hardCap, int activeIslands, int maxActive) {
        if (players >= hardCap) {
            return NodeState.HARD_FULL;
        }
        if (players >= softCap || activeIslands >= maxActive) {
            return NodeState.SOFT_FULL;
        }
        return NodeState.READY;
    }

    private double currentMspt() {
        double[] recent = Bukkit.getServer().getTPS();
        if (recent.length == 0 || recent[0] <= 0.0D) {
            return 50.0D;
        }
        return Math.min(50.0D, 1000.0D / recent[0]);
    }
}
