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
import kr.lunaf.cloudislands.api.model.IslandSnapshotRecord;
import kr.lunaf.cloudislands.api.model.IslandState;
import kr.lunaf.cloudislands.common.event.CloudIslandEventType;
import kr.lunaf.cloudislands.coreservice.event.GlobalEventPublisher;
import kr.lunaf.cloudislands.coreservice.repository.IslandRepository;
import kr.lunaf.cloudislands.coreservice.repository.IslandRuntimeRepository;
import kr.lunaf.cloudislands.coreservice.session.RouteSessionStore;
import kr.lunaf.cloudislands.coreservice.snapshot.IslandSnapshotRepository;
import kr.lunaf.cloudislands.coreservice.ticket.RouteTicketStore;
import kr.lunaf.cloudislands.coreservice.workflow.IslandLifecycleWorkflow;

public final class NodeFailureMonitor {
    private static final Logger LOGGER = Logger.getLogger(NodeFailureMonitor.class.getName());
    private final NodeRegistry nodes;
    private final IslandRuntimeRepository runtimes;
    private final IslandRepository islands;
    private final GlobalEventPublisher events;
    private final RouteTicketStore tickets;
    private final RouteSessionStore sessions;
    private final IslandSnapshotRepository snapshots;
    private final IslandLifecycleWorkflow lifecycle;
    private final Duration heartbeatTimeout;
    private final ScheduledExecutorService executor;
    private final AtomicBoolean started = new AtomicBoolean(false);
    private volatile long lastFailureLogMillis;

    public NodeFailureMonitor(NodeRegistry nodes, IslandRuntimeRepository runtimes, IslandRepository islands, GlobalEventPublisher events, Duration heartbeatTimeout) {
        this(nodes, runtimes, islands, events, heartbeatTimeout, null, null, null, null);
    }

    public NodeFailureMonitor(NodeRegistry nodes, IslandRuntimeRepository runtimes, IslandRepository islands, GlobalEventPublisher events, Duration heartbeatTimeout, RouteTicketStore tickets, RouteSessionStore sessions) {
        this(nodes, runtimes, islands, events, heartbeatTimeout, tickets, sessions, null, null);
    }

    public NodeFailureMonitor(NodeRegistry nodes, IslandRuntimeRepository runtimes, IslandRepository islands, GlobalEventPublisher events, Duration heartbeatTimeout, RouteTicketStore tickets, RouteSessionStore sessions, IslandSnapshotRepository snapshots, IslandLifecycleWorkflow lifecycle) {
        this.nodes = nodes;
        this.runtimes = runtimes;
        this.islands = islands;
        this.events = events;
        this.tickets = tickets;
        this.sessions = sessions;
        this.snapshots = snapshots;
        this.lifecycle = lifecycle;
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
            int affected = markRecoveryRequiredForNode(nodeId);
            int recoveryQueued = recoverOrQuarantineNodeIslands(nodeId);
            int failedTickets = failRoutesForNode(nodeId);
            int clearedSessions = clearSessionsForNode(nodeId);
            events.publish(CloudIslandEventType.NODE_STATE_CHANGED.name(), Map.of(
                "nodeId", nodeId,
                "state", "DOWN",
                "recoveryRequired", Integer.toString(affected),
                "recoveryQueued", Integer.toString(recoveryQueued),
                "failedTickets", Integer.toString(failedTickets),
                "clearedSessions", Integer.toString(clearedSessions)
            ));
            if (failedTickets > 0 || clearedSessions > 0) {
                events.publish(CloudIslandEventType.ROUTE_TICKET_CLEARED.name(), Map.of(
                    "targetNode", nodeId,
                    "reason", "NODE_DOWN",
                    "clearedTickets", Integer.toString(failedTickets),
                    "clearedSessions", Integer.toString(clearedSessions)
                ));
            }
        }
    }

    public int markRecoveryRequiredForNode(String nodeId) {
        List<IslandRuntimeSnapshot> affectedIslands = runtimes.listByNode(nodeId, Integer.MAX_VALUE).stream()
            .filter(NodeFailureMonitor::requiresRecovery)
            .toList();
        int affected = runtimes.markRecoveryRequiredForNode(nodeId);
        for (IslandRuntimeSnapshot runtime : affectedIslands) {
            islands.setState(runtime.islandId(), IslandState.RECOVERY_REQUIRED);
            events.publish(CloudIslandEventType.ISLAND_RECOVERY_REQUIRED.name(), Map.of(
                "islandId", runtime.islandId().toString(),
                "nodeId", nodeId,
                "previousState", runtime.state().name(),
                "activeWorld", runtime.activeWorld() == null ? "" : runtime.activeWorld(),
                "cellX", runtime.cellX() == null ? "" : runtime.cellX().toString(),
                "cellZ", runtime.cellZ() == null ? "" : runtime.cellZ().toString(),
                "reason", "NODE_DOWN"
            ));
        }
        return affected;
    }

    public int recoverOrQuarantineNodeIslands(String nodeId) {
        if (snapshots == null || lifecycle == null) {
            return 0;
        }
        List<IslandRuntimeSnapshot> recoverable = runtimes.listByNode(nodeId, Integer.MAX_VALUE).stream()
            .filter(runtime -> runtime.state() == IslandState.RECOVERY_REQUIRED)
            .toList();
        int queued = 0;
        for (IslandRuntimeSnapshot runtime : recoverable) {
            List<IslandSnapshotRecord> latest = snapshots.list(runtime.islandId(), 1);
            if (latest.isEmpty()) {
                lifecycle.quarantine(runtime.islandId(), "MISSING_RECOVERY_SNAPSHOT");
                continue;
            }
            IslandSnapshotRecord snapshot = latest.get(0);
            IslandLifecycleWorkflow.Result result = lifecycle.restore(runtime.islandId(), snapshot.snapshotNo(), snapshot.storagePath());
            if (result.accepted()) {
                queued++;
                events.publish(CloudIslandEventType.ISLAND_RECOVERY_REQUIRED.name(), Map.of(
                    "islandId", runtime.islandId().toString(),
                    "nodeId", nodeId,
                    "reason", "RECOVERY_RESTORE_QUEUED",
                    "snapshotNo", Long.toString(snapshot.snapshotNo()),
                    "storagePath", snapshot.storagePath() == null ? "" : snapshot.storagePath(),
                    "code", result.code()
                ));
            } else if (!result.code().startsWith("NO_READY_NODE")) {
                lifecycle.quarantine(runtime.islandId(), "RECOVERY_RESTORE_REJECTED_" + result.code());
            }
        }
        return queued;
    }

    private int failRoutesForNode(String nodeId) {
        if (tickets == null) {
            return 0;
        }
        return tickets.markFailedForNode(nodeId, "NODE_DOWN").size();
    }

    private int clearSessionsForNode(String nodeId) {
        if (sessions == null) {
            return 0;
        }
        return sessions.clearForNode(nodeId);
    }

    private static boolean requiresRecovery(IslandRuntimeSnapshot runtime) {
        return runtime.state() == IslandState.ACTIVE
            || runtime.state() == IslandState.ACTIVATING
            || runtime.state() == IslandState.RESTORING
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
