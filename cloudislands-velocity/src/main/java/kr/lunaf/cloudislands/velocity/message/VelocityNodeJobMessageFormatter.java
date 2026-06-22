package kr.lunaf.cloudislands.velocity.message;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import kr.lunaf.cloudislands.api.model.IslandNodeSnapshot;
import kr.lunaf.cloudislands.api.model.NodeLevelScanSnapshot;
import kr.lunaf.cloudislands.coreclient.AdminIslandRuntimeView;
import kr.lunaf.cloudislands.coreclient.AdminNodeActionView;
import kr.lunaf.cloudislands.coreclient.AdminStorageStatusView;
import kr.lunaf.cloudislands.coreclient.CoreGuiViews;
import kr.lunaf.cloudislands.coreclient.JobActionView;
import kr.lunaf.cloudislands.coreclient.JobRecoveryView;
import kr.lunaf.cloudislands.coreclient.JobView;

public final class VelocityNodeJobMessageFormatter {
    private final VelocityRoutePrivacyFormatter routePrivacy;

    public VelocityNodeJobMessageFormatter(VelocityRoutePrivacyFormatter routePrivacy) {
        this.routePrivacy = routePrivacy == null ? new VelocityRoutePrivacyFormatter(true) : routePrivacy;
    }

    public String appendLevelScanSummary(CoreGuiViews.NodeSummaryView view) {
        if (view == null) {
            return "";
        }
        return view.nodeId()
            + " " + (view.state().isBlank() ? "UNKNOWN" : view.state())
            + " pool=" + view.pool()
            + " players=" + view.players() + "/" + view.softPlayerCap() + "/" + view.hardPlayerCap()
            + " islands=" + view.activeIslands() + "/" + view.maxActiveIslands()
            + " queue=" + view.activationQueue() + "/" + view.maxActivationQueue()
            + " mspt=" + view.mspt();
    }

    public String nodeIslandList(List<AdminIslandRuntimeView> runtimes) {
        if (runtimes == null || runtimes.isEmpty()) {
            return "노드 섬 현황: 활성 섬 없음";
        }
        String nodeId = runtimes.stream()
            .map(AdminIslandRuntimeView::activeNode)
            .filter(value -> value != null && !value.isBlank())
            .findFirst()
            .orElse("");
        List<String> entries = new ArrayList<>();
        for (AdminIslandRuntimeView runtime : runtimes) {
            if (!runtime.islandId().isBlank()) {
                entries.add(runtime.islandId() + "(" + runtimeSuffix(runtime) + ")");
            }
        }
        return "노드 섬 현황" + routePrivacy.hiddenNodeLabel(nodeId) + ": " + (entries.isEmpty() ? "활성 섬 없음" : String.join(", ", entries));
    }

    public String storageStatus(AdminStorageStatusView view) {
        if (view == null || view.nodes().isEmpty()) {
            return "Storage status: registered node 없음";
        }
        List<String> entries = new ArrayList<>();
        int index = 0;
        for (AdminStorageStatusView.NodeView node : view.nodes()) {
            entries.add(routePrivacy.displayNodeName(node.nodeId(), ++index)
                + "=" + (node.storageAvailable() ? "OK" : "DOWN")
                + "(backend=" + fallback(node.backend(), "unknown")
                + ", primaryDegraded=" + node.primaryDegraded()
                + ", failures=" + node.totalFailures()
                + ", up=" + seconds(node.uploadSeconds()) + "s"
                + ", down=" + seconds(node.downloadSeconds()) + "s)");
        }
        return "Storage status: " + String.join(", ", entries) + " / unavailable=" + view.unavailableCount();
    }

    public String nodeListSummary(List<IslandNodeSnapshot> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return "Nodes: empty";
        }
        int starting = 0;
        int warming = 0;
        int ready = 0;
        int softFull = 0;
        int hardFull = 0;
        int draining = 0;
        int shuttingDown = 0;
        int down = 0;
        List<String> entries = new ArrayList<>();
        for (IslandNodeSnapshot node : nodes) {
            String state = node.state() == null ? "UNKNOWN" : node.state().name();
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
                entries.add(nodeSummary(node, entries.size() + 1));
            }
        }
        return "Nodes: total=" + nodes.size()
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

    public String nodeActionSummary(String label, String nodeId, AdminNodeActionView view) {
        if (view == null) {
            String displayNode = nodeId == null || nodeId.isBlank() ? "target-node" : nodeId;
            return label + ": accepted" + routePrivacy.routeNodeSuffix(displayNode);
        }
        String displayNode = !view.nodeId().isBlank() ? view.nodeId() : nodeId;
        if (!view.code().isBlank()) {
            return label + ": " + (view.accepted() ? "accepted" : "rejected") + routePrivacy.routeNodeSuffix(displayNode) + " code=" + view.code();
        }
        return label + ": " + (view.accepted() ? "accepted" : "requested") + routePrivacy.routeNodeSuffix(displayNode);
    }

    public String nodeSweep(AdminNodeActionView view) {
        if (view == null) {
            return "Node sweep: nodes=none recoveryRequired=0";
        }
        List<String> swept = new ArrayList<>();
        int index = 0;
        for (String nodeId : view.nodes()) {
            swept.add(routePrivacy.displayNodeName(nodeId, ++index));
        }
        return "Node sweep: nodes=" + (swept.isEmpty() ? "none" : String.join(",", swept)) + " recoveryRequired=" + view.recoveryRequired();
    }

    public String jobList(List<JobView> jobs) {
        if (jobs == null || jobs.isEmpty()) {
            return "Jobs: empty";
        }
        int pending = 0;
        int claimed = 0;
        int failed = 0;
        int done = 0;
        int other = 0;
        List<String> entries = new ArrayList<>();
        for (JobView job : jobs) {
            String state = job.state();
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
                entries.add(jobSummary(job));
            }
        }
        return "Jobs: total=" + jobs.size()
            + " pending=" + pending
            + " claimed=" + claimed
            + " failed=" + failed
            + " done=" + done
            + " other=" + other
            + (entries.isEmpty() ? "" : " / " + String.join(" | ", entries));
    }

    public String jobAction(String action, JobActionView view) {
        if (view == null) {
            return "Job " + action + ": no response";
        }
        if (!view.accepted() && !view.code().isBlank()) {
            return "Job " + action + ": failed code=" + view.code();
        }
        return "Job " + action + ": " + (view.accepted() ? "accepted" : "not applied");
    }

    public String jobRecovery(JobRecoveryView view) {
        if (view == null) {
            return "Job recover: recovered=0";
        }
        if (!view.accepted() && !view.code().isBlank()) {
            return "Job recover: failed code=" + view.code();
        }
        return "Job recover: recovered=" + (view.recovered().isBlank() ? "0" : view.recovered());
    }

    private String levelScanSummary(NodeLevelScanSnapshot scan) {
        if (scan == null) {
            return "";
        }
        StringBuilder summary = new StringBuilder("레벨 스캔=");
        summary.append(scan.running() ? "실행 중" : "대기");
        if (!scan.lastIsland().isBlank()) {
            summary.append(", 마지막 섬=").append(scan.lastIsland());
        }
        appendLongSummary(summary, "시작", scan.startedAt());
        appendLongSummary(summary, "완료", scan.finishedAt());
        appendLongSummary(summary, "실패", scan.failedAt());
        return summary.toString();
    }

    private String runtimeSuffix(AdminIslandRuntimeView runtime) {
        List<String> parts = new ArrayList<>();
        if (!runtime.state().isBlank()) {
            parts.add(runtime.state());
        }
        if (runtime.activeWorld() != null && !runtime.activeWorld().isBlank()) {
            parts.add("world=" + runtime.activeWorld());
        }
        if (runtime.hasCell()) {
            parts.add("cell=" + runtime.cellX() + "," + runtime.cellZ());
        }
        return String.join(" ", parts);
    }

    private String nodeSummary(IslandNodeSnapshot node, int displayIndex) {
        String displayNode = node.nodeId().isBlank() ? "node-" + displayIndex : node.nodeId();
        String allocationBlockReason = node.allocationBlockReason() == null ? "" : node.allocationBlockReason();
        return displayNode
            + " " + (node.state() == null ? "UNKNOWN" : node.state().name())
            + " players=" + node.players() + "/" + node.softPlayerCap() + "/" + node.hardPlayerCap() + " reserved=" + node.reservedSlots()
            + " islands=" + node.activeIslands() + "/" + node.maxActiveIslands()
            + " queue=" + node.activationQueue() + "/" + node.maxActivationQueue()
            + " mspt=" + seconds(node.mspt())
            + " score=" + seconds(node.score())
            + scoreParts(node)
            + " activation=" + (node.eligibleForNewActivation() ? "ok" : "blocked:" + (allocationBlockReason.isBlank() ? "UNKNOWN" : allocationBlockReason))
            + " storage=" + (node.storageAvailable() ? "ok" : "down")
            + " | " + levelScanSummary(node.levelScan());
    }

    private String jobSummary(JobView job) {
        String shortId = job.id().length() > 8 ? job.id().substring(0, 8) : job.id();
        StringBuilder builder = new StringBuilder(shortId.isBlank() ? "job" : shortId)
            .append(' ')
            .append(job.type().isBlank() ? "UNKNOWN" : job.type())
            .append(' ')
            .append(job.state().isBlank() ? "UNKNOWN" : job.state())
            .append(" attempts=")
            .append(job.attempts());
        if (!job.targetNode().isBlank()) {
            builder.append(routePrivacy.routeNodeSuffix(job.targetNode()));
        }
        if (!job.error().isBlank()) {
            builder.append(" error=").append(job.error());
        }
        return builder.toString();
    }

    private String scoreParts(IslandNodeSnapshot node) {
        if (node.scoreBreakdown() == null || node.scoreBreakdown().isEmpty()) {
            return "";
        }
        return " parts=p:" + seconds(scorePartValue(node, "playerPressure", "playerPressure"))
            + ",a:" + seconds(scorePartValue(node, "activeIslandPressure", "activeIslandPressure"))
            + ",m:" + seconds(scorePartValue(node, "msptPressure", "msptPressure"))
            + ",q:" + seconds(scorePartValue(node, "activationQueuePressure", "activationQueuePressure"))
            + ",mem:" + seconds(scorePartValue(node, "memoryPressure", "memoryPressure"))
            + ",fail:" + seconds(scorePartValue(node, "recentFailurePressure", "recentFailurePenalty"));
    }

    private double scorePartValue(IslandNodeSnapshot node, String primaryKey, String fallbackKey) {
        Double primary = node.scoreBreakdown().get(primaryKey);
        if (primary != null) {
            return primary;
        }
        return node.scoreBreakdown().getOrDefault(fallbackKey, 0.0D);
    }

    private void appendLongSummary(StringBuilder summary, String label, long value) {
        if (value > 0L) {
            summary.append(", ").append(label).append('=').append(value);
        }
    }

    private String seconds(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private String fallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
