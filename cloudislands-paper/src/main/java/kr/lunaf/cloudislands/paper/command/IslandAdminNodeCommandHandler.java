package kr.lunaf.cloudislands.paper.command;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.paper.application.IslandAdminNodeUseCase;
import kr.lunaf.cloudislands.paper.application.IslandAdminNodeUseCase.AdminNodeActionResult;
import kr.lunaf.cloudislands.paper.application.IslandAdminNodeUseCase.AdminNodeSummary;
import kr.lunaf.cloudislands.paper.gui.GuiAction;
import kr.lunaf.cloudislands.paper.gui.AdminNodeMenu;
import kr.lunaf.cloudislands.paper.gui.GuiClick;
import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

final class IslandAdminNodeCommandHandler {
    private final Plugin plugin;
    private final IslandAdminNodeUseCase adminNodeUseCase;
    private final Runtime runtime;
    private final String configuredNodeId;

    IslandAdminNodeCommandHandler(Plugin plugin, CoreApiClient coreApiClient, String configuredNodeId, Runtime runtime) {
        this.plugin = plugin;
        this.adminNodeUseCase = new IslandAdminNodeUseCase(coreApiClient);
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
                openAdminNodeMenu(player, adminNodeId(action));
                yield true;
            }
            case LIST -> {
                listAdminNodes(player);
                yield true;
            }
            case INFO -> {
                refreshAdminNodeInfo(player, adminNodeId(action));
                yield true;
            }
            case ISLANDS -> {
                listAdminNodeIslands(player, adminNodeId(action));
                yield true;
            }
            case DRAIN -> {
                drainAdminNode(player, adminNodeId(action));
                yield true;
            }
            case UNDRAIN -> {
                undrainAdminNode(player, adminNodeId(action));
                yield true;
            }
            case SWEEP -> {
                sweepAdminNode(player, adminNodeId(action));
                yield true;
            }
            case KICKALL_PREPARE -> {
                openAdminNodeKickAllConfirmation(player, adminNodeId(action));
                yield true;
            }
            case SHUTDOWN_SAFE_PREPARE -> {
                openAdminNodeShutdownConfirmation(player, adminNodeId(action));
                yield true;
            }
            case KICKALL_CONFIRM -> {
                if (runtime.confirmationAccepted(player, action, click)) {
                    kickAllAdminNode(player, adminNodeId(action), action.reason());
                }
                yield true;
            }
            case SHUTDOWN_SAFE_CONFIRM -> {
                if (runtime.confirmationAccepted(player, action, click)) {
                    shutdownAdminNodeSafely(player, adminNodeId(action), action.reason());
                }
                yield true;
            }
        };
    }

    private String adminNodeId(GuiAction.AdminNodeAction action) {
        String nodeId = action == null ? configuredNodeId : action.nodeId();
        return nodeId == null || nodeId.isBlank() ? configuredNodeId : nodeId;
    }

    private void openAdminNodeMenu(Player player, String nodeId) {
        AdminNodeMenu.open(player, nodeId, runtime.messagesFor(player));
    }

    private void listAdminNodes(Player player) {
        adminNodeUseCase.listNodesSummary()
            .thenAccept(summary -> runtime.message(player, runtime.routeMessage("admin-node-list-result-prefix", "노드 목록: ") + adminNodeBodySummary(summary)))
            .exceptionally(error -> adminNodeFailure(player, "admin-node-list-failed", "노드 목록을 불러오지 못했습니다.", error));
    }

    private void refreshAdminNodeInfo(Player player, String nodeId) {
        adminNodeUseCase.nodeInfoView(nodeId)
            .thenAccept(summary -> kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers.run(plugin, () -> AdminNodeMenu.open(player, nodeId, summary, runtime.messagesFor(player))))
            .exceptionally(error -> adminNodeFailure(player, "admin-node-info-failed", "노드 정보를 불러오지 못했습니다.", error));
    }

    private void listAdminNodeIslands(Player player, String nodeId) {
        adminNodeUseCase.nodeIslandsSummary(nodeId, 50)
            .thenAccept(summary -> runtime.message(player, runtime.routeMessage("admin-node-islands-result-prefix", "노드 섬 현황: ") + adminNodeBodySummary(summary)))
            .exceptionally(error -> adminNodeFailure(player, "admin-node-islands-failed", "노드 섬 현황을 불러오지 못했습니다.", error));
    }

    private void drainAdminNode(Player player, String nodeId) {
        adminNodeUseCase.drainAction(nodeId, runtime::mutate)
            .thenAccept(result -> runtime.message(player, adminNodeActionMessage("Node drain", nodeId, result)))
            .exceptionally(error -> adminNodeFailure(player, "admin-node-action-failed", "Node drain 실패", error));
    }

    private void undrainAdminNode(Player player, String nodeId) {
        adminNodeUseCase.undrainAction(nodeId, runtime::mutate)
            .thenAccept(result -> runtime.message(player, adminNodeActionMessage("Node undrain", nodeId, result)))
            .exceptionally(error -> adminNodeFailure(player, "admin-node-action-failed", "Node undrain 실패", error));
    }

    private void sweepAdminNode(Player player, String nodeId) {
        adminNodeUseCase.sweepAction(nodeId, runtime::mutate)
            .thenAccept(result -> runtime.message(player, adminNodeActionMessage("Node sweep", nodeId, result)))
            .exceptionally(error -> adminNodeFailure(player, "admin-node-action-failed", "Node sweep 실패", error));
    }

    private void kickAllAdminNode(Player player, String nodeId, String reason) {
        adminNodeUseCase.kickAllAction(nodeId, reason, runtime::mutateIdempotent)
            .thenAccept(result -> runtime.message(player, adminNodeActionMessage("Node kickall", nodeId, result)))
            .exceptionally(error -> adminNodeFailure(player, "admin-node-danger-action-failed", "Node kickall 실패", error));
    }

    private void shutdownAdminNodeSafely(Player player, String nodeId, String reason) {
        adminNodeUseCase.shutdownSafelyAction(nodeId, reason, runtime::mutateIdempotent)
            .thenAccept(result -> runtime.message(player, adminNodeActionMessage("Node shutdown-safe", nodeId, result)))
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

    private String adminNodeBodySummary(AdminNodeSummary summary) {
        if (summary == null || summary.text().isBlank()) {
            return runtime.routeMessage("admin-node-empty-response", "응답 없음");
        }
        return summary.text();
    }

    private static String adminNodeActionMessage(String label, String nodeId, AdminNodeActionResult result) {
        StringBuilder builder = new StringBuilder(label)
            .append(result.accepted() ? " 완료" : " 실패");
        String target = result.nodeId().isBlank() ? nodeId : result.nodeId();
        if (target != null && !target.isBlank()) {
            builder.append(": 대상=").append(target);
        }
        if (!result.operation().isBlank()) {
            builder.append(" 작업=").append(result.operation());
        }
        if (!result.accepted() && !result.code().isBlank()) {
            builder.append(" 사유=").append(result.code());
        }
        return builder.toString();
    }

    interface Runtime {
        void message(Player player, String message);

        String routeMessage(String key, String fallback);

        <T> CompletableFuture<T> mutate(String auditAction, Supplier<CompletableFuture<T>> operation);

        <T> CompletableFuture<T> mutateIdempotent(String auditAction, Supplier<CompletableFuture<T>> operation);

        void openConfirmation(Player player, String title, String description, Material material, String confirmName, String confirmAction, Map<String, String> data, String confirmLore, String cancelAction);

        boolean confirmationAccepted(Player player, GuiAction action, GuiClick click);

        MessageRenderer messagesFor(Player player);
    }
}
