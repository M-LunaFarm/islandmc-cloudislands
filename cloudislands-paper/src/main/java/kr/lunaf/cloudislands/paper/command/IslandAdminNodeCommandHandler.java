package kr.lunaf.cloudislands.paper.command;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.paper.gui.GuiAction;
import kr.lunaf.cloudislands.paper.gui.AdminNodeMenu;
import kr.lunaf.cloudislands.paper.gui.GuiClick;
import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

final class IslandAdminNodeCommandHandler {
    private final Plugin plugin;
    private final CoreApiClient coreApiClient;
    private final Runtime runtime;
    private final String configuredNodeId;

    IslandAdminNodeCommandHandler(Plugin plugin, CoreApiClient coreApiClient, String configuredNodeId, Runtime runtime) {
        this.plugin = plugin;
        this.coreApiClient = coreApiClient;
        this.configuredNodeId = configuredNodeId == null || configuredNodeId.isBlank() ? "island-1" : configuredNodeId;
        this.runtime = runtime;
    }

    boolean handleGuiAction(Player player, GuiAction action, GuiClick click) {
        if (action instanceof GuiAction.AdminNodeAction adminNode) {
            return handleAdminNodeAction(player, adminNode, click);
        }
        if (action instanceof GuiAction.AdminIslandPrompt) {
            runtime.message(player, runtime.routeMessage("admin-node-direct-required", "섬 UUID와 대상 노드 입력이 필요한 관리 작업입니다. 관리자 명령 도움말을 확인해주세요."));
            return true;
        }
        return false;
    }

    private boolean handleAdminNodeAction(Player player, GuiAction.AdminNodeAction action, GuiClick click) {
        return switch (action.type()) {
            case OPEN -> {
                openAdminNodeMenu(player, adminNodeId(action.data()));
                yield true;
            }
            case LIST -> {
                listAdminNodes(player);
                yield true;
            }
            case INFO -> {
                refreshAdminNodeInfo(player, adminNodeId(action.data()));
                yield true;
            }
            case ISLANDS -> {
                listAdminNodeIslands(player, adminNodeId(action.data()));
                yield true;
            }
            case DRAIN -> {
                drainAdminNode(player, adminNodeId(action.data()));
                yield true;
            }
            case UNDRAIN -> {
                undrainAdminNode(player, adminNodeId(action.data()));
                yield true;
            }
            case SWEEP -> {
                sweepAdminNode(player, adminNodeId(action.data()));
                yield true;
            }
            case KICKALL_PREPARE -> {
                openAdminNodeKickAllConfirmation(player, adminNodeId(action.data()));
                yield true;
            }
            case SHUTDOWN_SAFE_PREPARE -> {
                openAdminNodeShutdownConfirmation(player, adminNodeId(action.data()));
                yield true;
            }
            case KICKALL_CONFIRM -> {
                if (runtime.confirmationAccepted(player, action, click)) {
                    kickAllAdminNode(player, adminNodeId(action.data()), action.reason());
                }
                yield true;
            }
            case SHUTDOWN_SAFE_CONFIRM -> {
                if (runtime.confirmationAccepted(player, action, click)) {
                    shutdownAdminNodeSafely(player, adminNodeId(action.data()), action.reason());
                }
                yield true;
            }
        };
    }

    private String adminNodeId(Map<String, String> data) {
        String nodeId = data == null ? configuredNodeId : data.getOrDefault("nodeId", configuredNodeId);
        return nodeId == null || nodeId.isBlank() ? configuredNodeId : nodeId;
    }

    private void openAdminNodeMenu(Player player, String nodeId) {
        AdminNodeMenu.open(player, nodeId, runtime.messagesFor(player));
    }

    private void listAdminNodes(Player player) {
        coreApiClient.listNodes()
            .thenAccept(body -> runtime.message(player, runtime.routeMessage("admin-node-list-result-prefix", "노드 목록: ") + adminNodeBodySummary(body)))
            .exceptionally(error -> adminNodeFailure(player, "admin-node-list-failed", "노드 목록을 불러오지 못했습니다.", error));
    }

    private void refreshAdminNodeInfo(Player player, String nodeId) {
        coreApiClient.nodeInfo(nodeId)
            .thenAccept(body -> kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers.run(plugin, () -> AdminNodeMenu.open(player, nodeId, body, runtime.messagesFor(player))))
            .exceptionally(error -> adminNodeFailure(player, "admin-node-info-failed", "노드 정보를 불러오지 못했습니다.", error));
    }

    private void listAdminNodeIslands(Player player, String nodeId) {
        coreApiClient.nodeIslands(nodeId, 50)
            .thenAccept(body -> runtime.message(player, runtime.routeMessage("admin-node-islands-result-prefix", "노드 섬 현황: ") + adminNodeBodySummary(body)))
            .exceptionally(error -> adminNodeFailure(player, "admin-node-islands-failed", "노드 섬 현황을 불러오지 못했습니다.", error));
    }

    private void drainAdminNode(Player player, String nodeId) {
        runtime.mutate("admin.node.drain", () -> coreApiClient.drainNode(nodeId))
            .thenAccept(body -> runtime.message(player, runtime.actionResultMessage("Node drain", nodeId, body)))
            .exceptionally(error -> adminNodeFailure(player, "admin-node-action-failed", "Node drain 실패", error));
    }

    private void undrainAdminNode(Player player, String nodeId) {
        runtime.mutate("admin.node.undrain", () -> coreApiClient.undrainNode(nodeId))
            .thenAccept(body -> runtime.message(player, runtime.actionResultMessage("Node undrain", nodeId, body)))
            .exceptionally(error -> adminNodeFailure(player, "admin-node-action-failed", "Node undrain 실패", error));
    }

    private void sweepAdminNode(Player player, String nodeId) {
        runtime.mutate("admin.node.sweep", () -> coreApiClient.sweepNode(nodeId))
            .thenAccept(body -> runtime.message(player, runtime.actionResultMessage("Node sweep", nodeId, body)))
            .exceptionally(error -> adminNodeFailure(player, "admin-node-action-failed", "Node sweep 실패", error));
    }

    private void kickAllAdminNode(Player player, String nodeId, String reason) {
        runtime.mutateIdempotent("admin.node.kickall", () -> coreApiClient.kickAllNode(nodeId, reason))
            .thenAccept(body -> runtime.message(player, runtime.actionResultMessage("Node kickall", nodeId, body)))
            .exceptionally(error -> adminNodeFailure(player, "admin-node-danger-action-failed", "Node kickall 실패", error));
    }

    private void shutdownAdminNodeSafely(Player player, String nodeId, String reason) {
        runtime.mutateIdempotent("admin.node.shutdown-safe", () -> coreApiClient.shutdownNodeSafely(nodeId, reason))
            .thenAccept(body -> runtime.message(player, runtime.actionResultMessage("Node shutdown-safe", nodeId, body)))
            .exceptionally(error -> adminNodeFailure(player, "admin-node-danger-action-failed", "Node shutdown-safe 실패", error));
    }

    private void openAdminNodeKickAllConfirmation(Player player, String nodeId) {
        runtime.openConfirmation(player,
            runtime.routeMessage("admin-node-kickall-confirm-title", "노드 플레이어 이동 확인"),
            runtime.routeMessage("admin-node-kickall-confirm-description", "현재 노드의 접속자를 로비로 이동합니다."),
            Material.IRON_DOOR,
            runtime.routeMessage("admin-node-kickall-confirm-name", "로비 이동 실행"),
            "admin.node.kickall.confirm",
            Map.of("nodeId", nodeId, "reason", "admin-gui"),
            runtime.routeMessage("admin-node-kickall-confirm-lore", "클릭하면 Core에 노드 플레이어 이동을 요청합니다."),
            "admin.node.open");
    }

    private void openAdminNodeShutdownConfirmation(Player player, String nodeId) {
        runtime.openConfirmation(player,
            runtime.routeMessage("admin-node-shutdown-confirm-title", "노드 안전 종료 확인"),
            runtime.routeMessage("admin-node-shutdown-confirm-description", "Drain 후 접속자를 로비로 이동하고 안전 종료를 요청합니다."),
            Material.BELL,
            runtime.routeMessage("admin-node-shutdown-confirm-name", "안전 종료 실행"),
            "admin.node.shutdown-safe.confirm",
            Map.of("nodeId", nodeId, "reason", "admin-gui"),
            runtime.routeMessage("admin-node-shutdown-confirm-lore", "클릭하면 Core에 노드 안전 종료를 요청합니다."),
            "admin.node.open");
    }

    private Void adminNodeFailure(Player player, String key, String fallback, Throwable error) {
        runtime.message(player, runtime.routeMessage(key, fallback));
        return null;
    }

    private String adminNodeBodySummary(String body) {
        if (body == null || body.isBlank()) {
            return runtime.routeMessage("admin-node-empty-response", "응답 없음");
        }
        String code = text(body, "code");
        if (!code.isBlank()) {
            return "code=" + code;
        }
        String nodeId = text(body, "nodeId");
        if (!nodeId.isBlank()) {
            return "node=" + compactId(nodeId);
        }
        return body.length() > 180 ? body.substring(0, 180) + "..." : body;
    }

    private static String text(String json, String key) {
        String needle = "\"" + key + "\":\"";
        int start = json.indexOf(needle);
        if (start < 0) {
            return "";
        }
        int valueStart = start + needle.length();
        int end = json.indexOf('"', valueStart);
        return end < 0 ? "" : json.substring(valueStart, end);
    }

    private static String compactId(String value) {
        return value != null && value.length() == 36 && value.indexOf('-') > 0 ? value.substring(0, 8) : value;
    }

    interface Runtime {
        void message(Player player, String message);

        String routeMessage(String key, String fallback);

        String actionResultMessage(String label, String targetId, String body);

        <T> CompletableFuture<T> mutate(String auditAction, Supplier<CompletableFuture<T>> operation);

        <T> CompletableFuture<T> mutateIdempotent(String auditAction, Supplier<CompletableFuture<T>> operation);

        void openConfirmation(Player player, String title, String description, Material material, String confirmName, String confirmAction, Map<String, String> data, String confirmLore, String cancelAction);

        boolean confirmationAccepted(Player player, GuiAction action, GuiClick click);

        MessageRenderer messagesFor(Player player);
    }
}
