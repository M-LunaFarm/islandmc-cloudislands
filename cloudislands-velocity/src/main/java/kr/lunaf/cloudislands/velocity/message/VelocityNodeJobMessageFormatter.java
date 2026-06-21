package kr.lunaf.cloudislands.velocity.message;

import static kr.lunaf.cloudislands.velocity.message.VelocityJsonFields.arrayValue;
import static kr.lunaf.cloudislands.velocity.message.VelocityJsonFields.boolValue;
import static kr.lunaf.cloudislands.velocity.message.VelocityJsonFields.doubleValue;
import static kr.lunaf.cloudislands.velocity.message.VelocityJsonFields.jsonValue;
import static kr.lunaf.cloudislands.velocity.message.VelocityJsonFields.longValue;
import static kr.lunaf.cloudislands.velocity.message.VelocityJsonFields.matchingObjectEnd;
import static kr.lunaf.cloudislands.velocity.message.VelocityJsonFields.objectValue;

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

    public String appendLevelScanSummary(String body) {
        List<String> summaries = new ArrayList<>();
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

    public String nodeIslandList(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        String nodeId = jsonValue(body, "nodeId");
        long count = longValue(body, "count");
        String islands = arrayValue(body, "islands");
        if (islands.isBlank() || count == 0L) {
            return "노드 섬 현황" + routePrivacy.hiddenNodeLabel(nodeId) + ": 활성 섬 없음";
        }
        List<String> entries = new ArrayList<>();
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
                entries.add(islandId + "(" + routePrivacy.nodeIslandRuntimeSuffix(object) + ")");
            }
            index = objectEnd + 1;
        }
        return "노드 섬 현황" + routePrivacy.hiddenNodeLabel(nodeId) + ": " + (entries.isEmpty() ? "활성 섬 없음" : String.join(", ", entries));
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

    public String storageStatus(String body) {
        String nodes = arrayValue(body, "nodes");
        if (nodes.isBlank()) {
            return "Storage status: registered node 없음";
        }
        List<String> entries = new ArrayList<>();
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
                entries.add(routePrivacy.displayNodeName(nodeId, entries.size() + 1) + "=" + (available ? "OK" : "DOWN") + storageMetricSuffix(object));
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

    public String nodeListSummary(String body) {
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
        List<String> entries = new ArrayList<>();
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
            + poolSummarySuffix(body)
            + (entries.isEmpty() ? "" : " / " + String.join(" | ", entries));
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

    public String nodeActionSummary(String label, String nodeId, String body) {
        String displayNode = nodeId == null || nodeId.isBlank() ? "target-node" : nodeId;
        if (body == null || body.isBlank()) {
            return label + ": accepted" + routePrivacy.routeNodeSuffix(displayNode);
        }
        String code = jsonValue(body, "code");
        if (!code.isBlank()) {
            return label + ": " + (boolValue(body, "accepted") ? "accepted" : "rejected") + routePrivacy.routeNodeSuffix(displayNode) + " code=" + code;
        }
        return label + ": " + (boolValue(body, "accepted") ? "accepted" : "requested") + routePrivacy.routeNodeSuffix(displayNode);
    }

    public String nodeActionSummary(String label, String nodeId, AdminNodeActionView view) {
        if (view == null) {
            return nodeActionSummary(label, nodeId, "");
        }
        String displayNode = !view.nodeId().isBlank() ? view.nodeId() : nodeId;
        if (!view.code().isBlank()) {
            return label + ": " + (view.accepted() ? "accepted" : "rejected") + routePrivacy.routeNodeSuffix(displayNode) + " code=" + view.code();
        }
        return label + ": " + (view.accepted() ? "accepted" : "requested") + routePrivacy.routeNodeSuffix(displayNode);
    }

    public String nodeSweep(String body) {
        String nodes = arrayValue(body, "nodes");
        long recoveryRequired = longValue(body, "recoveryRequired");
        List<String> swept = new ArrayList<>();
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
            swept.add(routePrivacy.displayNodeName(nodeId, swept.size() + 1));
            index = valueEnd + 1;
        }
        return "Node sweep: nodes=" + (swept.isEmpty() ? "none" : String.join(",", swept)) + " recoveryRequired=" + recoveryRequired;
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

    public String jobList(String body) {
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
        List<String> entries = new ArrayList<>();
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

    public String jobAction(String action, String body) {
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

    private String storageMetricSuffix(String nodeObject) {
        String storage = objectValue(nodeObject, "storage");
        if (storage.isBlank()) {
            return "";
        }
        long failures = longValue(storage, "healthCheckFailures")
            + longValue(storage, "uploadFailures")
            + longValue(storage, "downloadFailures")
            + longValue(storage, "operationFailures");
        String backend = fallback(jsonValue(storage, "backend"), "unknown");
        boolean primaryDegraded = boolValue(storage, "primaryDegraded");
        return "(backend=" + backend
            + ", primaryDegraded=" + primaryDegraded
            + ", failures=" + failures
            + ", up=" + seconds(doubleValue(storage, "uploadSeconds")) + "s"
            + ", down=" + seconds(doubleValue(storage, "downloadSeconds")) + "s)";
    }

    private String poolSummarySuffix(String body) {
        String pools = arrayValue(body, "pools");
        if (pools.isBlank()) {
            return "";
        }
        List<String> entries = new ArrayList<>();
        int index = 0;
        while (index < pools.length()) {
            int objectStart = pools.indexOf('{', index);
            if (objectStart < 0) {
                break;
            }
            int objectEnd = matchingObjectEnd(pools, objectStart);
            if (objectEnd < 0) {
                break;
            }
            String object = pools.substring(objectStart, objectEnd + 1);
            String pool = fallback(jsonValue(object, "pool"), "island");
            entries.add(pool
                + " nodes=" + longValue(object, "healthyNodeCount") + "/" + longValue(object, "nodeCount")
                + " players=" + longValue(object, "players") + "/" + longValue(object, "softPlayerCap") + "/" + longValue(object, "hardPlayerCap")
                + " reserved=" + longValue(object, "reservedSlots")
                + " islands=" + longValue(object, "activeIslands") + "/" + longValue(object, "maxActiveIslands")
                + " queue=" + longValue(object, "activationQueue") + "/" + longValue(object, "maxActivationQueue"));
            index = objectEnd + 1;
        }
        return entries.isEmpty() ? "" : " / pools: " + String.join(" | ", entries);
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
            + ",fail:" + seconds(scorePartValue(breakdown, "recentFailurePressure", "recentFailurePenalty"));
    }

    private double scorePartValue(String breakdown, String primaryKey, String fallbackKey) {
        double primary = doubleValue(breakdown, primaryKey);
        if (primary != 0.0D || breakdown.contains("\"" + primaryKey + "\"")) {
            return primary;
        }
        return doubleValue(breakdown, fallbackKey);
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
            builder.append(routePrivacy.routeNodeSuffix(targetNode));
        }
        if (!error.isBlank()) {
            builder.append(" error=").append(error);
        }
        return builder.toString();
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
