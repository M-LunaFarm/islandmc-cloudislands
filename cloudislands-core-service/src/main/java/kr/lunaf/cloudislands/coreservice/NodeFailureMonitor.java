package kr.lunaf.cloudislands.coreservice;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import kr.lunaf.cloudislands.api.model.IslandRuntimeSnapshot;
import kr.lunaf.cloudislands.api.model.IslandState;
import kr.lunaf.cloudislands.coreservice.event.GlobalEventPublisher;
import kr.lunaf.cloudislands.coreservice.repository.IslandRuntimeRepository;

public final class NodeFailureMonitor {
    private static final Logger LOGGER = Logger.getLogger(NodeFailureMonitor.class.getName());
    private final NodeRegistry nodes;
    private final IslandRuntimeRepository runtimes;
    private final GlobalEventPublisher events;
    private final Duration heartbeatTimeout;
    private final ScheduledExecutorService executor;
    private final AtomicBoolean started = new AtomicBoolean(false);
    private volatile long lastFailureLogMillis;

    public NodeFailureMonitor(NodeRegistry nodes, IslandRuntimeRepository runtimes, GlobalEventPublisher events, Duration heartbeatTimeout) {
        this.nodes = nodes;
        this.runtimes = runtimes;
        this.events = events;
        this.heartbeatTimeout = heartbeatTimeout;
        this.executor = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "cloudislands-node-failure-monitor");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void start() {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        long delayMillis = Math.max(1000L, heartbeatTimeout.toMillis());
        executor.scheduleWithFixedDelay(this::sweep, delayMillis, delayMillis, TimeUnit.MILLISECONDS);
    }

    public void sweep() {
        try {
            runSweep();
        } catch (RuntimeException exception) {
            logSweepFailure(exception);
        }
    }

    private void runSweep() {
        List<String> downNodes = nodes.markStaleDown(heartbeatTimeout);
        for (String nodeId : downNodes) {
            List<IslandRuntimeSnapshot> affectedIslands = runtimes.listByNode(nodeId, Integer.MAX_VALUE).stream()
                .filter(NodeFailureMonitor::requiresRecovery)
                .toList();
            int affected = runtimes.markRecoveryRequiredForNode(nodeId);
            events.publish(kr.lunaf.cloudislands.common.event.CloudIslandEventType.NODE_STATE_CHANGED.name(), Map.of("nodeId", nodeId, "state", "DOWN", "recoveryRequired", Integer.toString(affected)));
            for (IslandRuntimeSnapshot runtime : affectedIslands) {
                events.publish(kr.lunaf.cloudislands.common.event.CloudIslandEventType.ISLAND_RECOVERY_REQUIRED.name(), Map.of(
                    "islandId", runtime.islandId().toString(),
                    "nodeId", nodeId,
                    "reason", "NODE_DOWN"
                ));
            }
        }
    }

    private static boolean requiresRecovery(IslandRuntimeSnapshot runtime) {
        return runtime.state() == IslandState.ACTIVE
            || runtime.state() == IslandState.ACTIVATING
            || runtime.state() == IslandState.SAVING
            || runtime.state() == IslandState.DEACTIVATING;
    }

    private void logSweepFailure(RuntimeException exception) {
        long now = System.currentTimeMillis();
        if (now - lastFailureLogMillis < 30_000L) {
            return;
        }
        lastFailureLogMillis = now;
        LOGGER.warning("CloudIslands node failure sweep failed: " + exception.getMessage());
    }
}
