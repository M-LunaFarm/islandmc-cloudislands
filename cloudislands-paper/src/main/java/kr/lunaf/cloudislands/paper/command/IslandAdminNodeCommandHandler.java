package kr.lunaf.cloudislands.paper.command;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import kr.lunaf.cloudislands.coreclient.AdminRouteClearView;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.coreclient.JobActionView;
import kr.lunaf.cloudislands.paper.application.IslandAdminNodeUseCase;
import kr.lunaf.cloudislands.paper.application.IslandAdminNodeUseCase.AdminNodeActionResult;
import kr.lunaf.cloudislands.paper.application.IslandAdminNodeUseCase.AdminNodeSummary;
import kr.lunaf.cloudislands.paper.gui.AdminNodeMenu;
import kr.lunaf.cloudislands.paper.gui.AdminJobMenu;
import kr.lunaf.cloudislands.paper.gui.AdminMigrationMenu;
import kr.lunaf.cloudislands.paper.gui.AdminNodeListMenu;
import kr.lunaf.cloudislands.paper.gui.AdminReviewModerationMenu;
import kr.lunaf.cloudislands.paper.gui.AdminRouteMenu;
import kr.lunaf.cloudislands.paper.gui.AdminStorageMenu;
import kr.lunaf.cloudislands.paper.gui.ConfirmationTokenPolicy;
import kr.lunaf.cloudislands.paper.gui.GuiAction;
import kr.lunaf.cloudislands.paper.gui.GuiClick;
import kr.lunaf.cloudislands.paper.gui.GuiSession;
import kr.lunaf.cloudislands.paper.gui.GuiSessions;
import kr.lunaf.cloudislands.paper.gui.GuiStateMenus;
import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

final class IslandAdminNodeCommandHandler {
    private final Plugin plugin;
    private final CoreApiClient coreApiClient;
    private final IslandAdminNodeUseCase adminNodeUseCase;
    private final Runtime runtime;
    private final String configuredNodeId;

    IslandAdminNodeCommandHandler(Plugin plugin, CoreApiClient coreApiClient, String configuredNodeId, Runtime runtime) {
        this.plugin = plugin;
        this.coreApiClient = coreApiClient;
        this.adminNodeUseCase = new IslandAdminNodeUseCase(coreApiClient);
        this.configuredNodeId = configuredNodeId == null || configuredNodeId.isBlank() ? "island-1" : configuredNodeId;
        this.runtime = runtime;
    }

    boolean handleGuiAction(Player player, GuiAction action, GuiClick click) {
        if (action instanceof GuiAction.AdminNodePage page) {
            openAdminNodeList(player, page.page());
            return true;
        }
        if (action instanceof GuiAction.AdminRoutePage page) {
            openRouteMenu(player, page.page());
            return true;
        }
        if (action instanceof GuiAction.AdminRouteClear clear) {
            if (clear.type() == GuiAction.AdminRouteClearType.PREPARE) {
                openRouteClearConfirmation(player, clear.playerUuid(), clear.ticketId(), clear.page());
            } else if (runtime.confirmationAccepted(player, action, click)) {
                clearRoute(player, clear.playerUuid(), clear.ticketId(), clear.page());
            }
            return true;
        }
        if (action instanceof GuiAction.AdminJobPage page) {
            openJobMenu(player, page.page());
            return true;
        }
        if (action instanceof GuiAction.AdminJobRetry retry) {
            retryJob(player, retry.jobId(), retry.page());
            return true;
        }
        if (action instanceof GuiAction.AdminJobCancel cancel) {
            if (cancel.type() == GuiAction.AdminJobCancelType.PREPARE) {
                openJobCancelConfirmation(player, cancel.jobId(), cancel.page());
            } else if (runtime.confirmationAccepted(player, action, click)) {
                cancelJob(player, cancel.jobId(), cancel.page());
            }
            return true;
        }
        if (action instanceof GuiAction.AdminReviewModeration moderation) {
            moderateReview(player, moderation);
            return true;
        }
        if (action instanceof GuiAction.AdminReviewOpen open) {
            openReviewModerationMenu(player, open.limit());
            return true;
        }
        if (action instanceof GuiAction.AdminNodeAction adminNode) {
            return handleAdminNodeAction(player, adminNode, click);
        }
        if (action instanceof GuiAction.AdminIslandPrompt) {
            runtime.message(player, runtime.routeMessage("admin-node-direct-required", "섬 UUID와 대상 노드 입력이 필요한 관리 작업입니다. 관리자 명령 도움말을 확인해주세요."));
            return true;
        }
        if (action instanceof GuiAction.AdminMenuAction adminMenu) {
            return handleAdminMenuAction(player, adminMenu);
        }
        return false;
    }

    private boolean handleAdminMenuAction(Player player, GuiAction.AdminMenuAction action) {
        UUID playerUuid = player.getUniqueId();
        return switch (action.type()) {
            case JOBS_OPEN, JOBS_LIST -> {
                openJobMenu(player, 0);
                yield true;
            }
            case JOBS_RETRY_PROMPT -> {
                prompt(player, "/ciadmin jobs retry <jobId>");
                yield true;
            }
            case JOBS_CANCEL_PROMPT -> {
                prompt(player, "/ciadmin jobs cancel <jobId>");
                yield true;
            }
            case ROUTE_OPEN, ROUTE_DEBUG -> {
                openRouteMenu(player, 0);
                yield true;
            }
            case ROUTE_CLEAR_PROMPT -> {
                prompt(player, "/ciadmin route clear <playerUuid|playerName> [ticketUuid]");
                yield true;
            }
            case STORAGE_OPEN -> {
                AdminStorageMenu.open(player, runtime.messagesFor(player));
                yield true;
            }
            case STORAGE_STATUS -> {
                coreApiClient.adminStorage().status()
                    .thenAccept(status -> deliverMessage(playerUuid, "Storage: nodes=" + status.nodes().size() + " unavailable=" + status.unavailableCount()))
                    .exceptionally(error -> adminNodeFailure(playerUuid, "admin-storage-status-failed", "스토리지 상태를 불러오지 못했습니다.", error));
                yield true;
            }
            case STORAGE_VERIFY_PROMPT -> {
                prompt(player, "/ciadmin storage verify <islandUuid|islandName>");
                yield true;
            }
            case MIGRATION_OPEN, MIGRATION_WIZARD -> {
                AdminMigrationMenu.open(player, runtime.messagesFor(player));
                yield true;
            }
            case MIGRATION_SCAN -> {
                runtime.mutateIdempotent("admin.migration.superiorskyblock2.scan", () -> coreApiClient.migrations().migrateSuperiorSkyblock2("scan", ""))
                    .thenAccept(snapshot -> deliverMessage(playerUuid, migrationSummary("Migration scan", snapshot.state(), snapshot.manifests(), snapshot.blockingIssues(), snapshot.warningIssues())))
                    .exceptionally(error -> adminNodeFailure(playerUuid, "admin-migration-scan-failed", "마이그레이션 스캔을 실행하지 못했습니다.", error));
                yield true;
            }
            case MIGRATION_DRYRUN -> {
                runtime.mutateIdempotent("admin.migration.superiorskyblock2.dryrun", () -> coreApiClient.migrations().migrateSuperiorSkyblock2("dryrun", ""))
                    .thenAccept(snapshot -> deliverMessage(playerUuid, migrationSummary("Migration dry-run", snapshot.state(), snapshot.manifests(), snapshot.blockingIssues(), snapshot.warningIssues())))
                    .exceptionally(error -> adminNodeFailure(playerUuid, "admin-migration-dryrun-failed", "마이그레이션 dry-run을 실행하지 못했습니다.", error));
                yield true;
            }
            case MIGRATION_VERIFY -> {
                runtime.mutateIdempotent("admin.migration.superiorskyblock2.verify", () -> coreApiClient.migrations().migrateSuperiorSkyblock2("verify", ""))
                    .thenAccept(snapshot -> deliverMessage(playerUuid, "Migration verify: state=" + snapshot.state() + " passed=" + snapshot.passed() + " expected=" + snapshot.expected()))
                    .exceptionally(error -> adminNodeFailure(playerUuid, "admin-migration-verify-failed", "마이그레이션 검증을 실행하지 못했습니다.", error));
                yield true;
            }
            case MIGRATION_IMPORT_PROMPT -> {
                prompt(player, "/ciadmin migrate-superiorskyblock2 import <approvalToken>");
                yield true;
            }
            case MIGRATION_APPROVE_PROMPT -> {
                prompt(player, "/ciadmin migrate-superiorskyblock2 approve <approvalToken>");
                yield true;
            }
            case MIGRATION_ROLLBACK_PLAN -> {
                runtime.mutateIdempotent("admin.migration.superiorskyblock2.rollback-plan", () -> coreApiClient.migrations().migrateSuperiorSkyblock2("rollback-plan", ""))
                    .thenAccept(snapshot -> deliverMessage(playerUuid, "Migration rollback-plan: state=" + snapshot.state() + " available=" + snapshot.rollbackPlanAvailable()))
                    .exceptionally(error -> adminNodeFailure(playerUuid, "admin-migration-rollback-plan-failed", "마이그레이션 롤백 계획을 불러오지 못했습니다.", error));
                yield true;
            }
            case MIGRATION_ROLLBACK_PROMPT -> {
                prompt(player, "/ciadmin migrate-superiorskyblock2 rollback");
                yield true;
            }
        };
    }

    private void prompt(Player player, String command) {
        runtime.message(player, runtime.routeMessage("admin-menu-command-required", "관리 명령 입력 필요: ") + command);
    }

    private void openRouteMenu(Player player, int page) {
        if (!routeManagementAllowed(player)) {
            runtime.message(player, runtime.routeMessage("admin-route-menu-permission-denied", "라우트 관리 권한이 없습니다."));
            return;
        }
        AdminRouteMenu.open(plugin, coreApiClient, player, runtime.messagesFor(player), page);
    }

    private void openRouteClearConfirmation(Player player, UUID playerUuid, UUID ticketId, int page) {
        if (!routeManagementAllowed(player)) {
            runtime.message(player, runtime.routeMessage("admin-route-menu-permission-denied", "라우트 관리 권한이 없습니다."));
            return;
        }
        runtime.openConfirmation(player,
            runtime.routeMessage("admin-route-menu-clear-confirm-title", "라우트 정리 확인"),
            runtime.routeMessage("admin-route-menu-clear-confirm-description", "선택한 플레이어의 라우트 세션과 티켓을 정리합니다."),
            AdminRouteMenu.clearConfirmationMaterial(),
            runtime.routeMessage("admin-route-menu-clear-confirm-name", "라우트 정리"),
            ConfirmationTokenPolicy.ADMIN_ROUTE_CLEAR_CONFIRM_ACTION,
            Map.of(
                "playerUuid", playerUuid.toString(),
                "ticketId", ticketId.toString(),
                "page", Integer.toString(Math.max(0, page))
            ),
            runtime.routeMessage("admin-route-menu-clear-confirm-lore", "클릭하면 Core에 라우트 정리를 요청합니다."),
            "admin.route.page");
    }

    private void clearRoute(Player player, UUID playerUuid, UUID ticketId, int page) {
        if (!routeManagementAllowed(player)) {
            runtime.message(player, runtime.routeMessage("admin-route-menu-permission-denied", "라우트 관리 권한이 없습니다."));
            return;
        }
        MessageRenderer messages = runtime.messagesFor(player);
        GuiSession session = GuiSessions.begin(player, "admin.route.clear");
        GuiStateMenus.openSaving(plugin, player, session, messages,
            runtime.routeMessage("admin-route-menu-clearing", "라우트를 정리하는 중입니다."));
        CompletableFuture<AdminRouteClearView> mutation = runtime.mutate("admin.route.clear", () -> coreApiClient.adminRoutes().clear(playerUuid, ticketId, "ADMIN_GUI"));
        mutation
            .thenAccept(result -> GuiSessions.runIfCurrent(plugin, player, session, () -> {
                runtime.message(player, runtime.routeMessage("admin-route-menu-cleared-prefix", "라우트 정리 완료: ")
                    + "session=" + result.clearedSession() + " ticket=" + result.clearedTicket()
                    + (result.reason().isBlank() ? "" : " reason=" + result.reason()));
                AdminRouteMenu.open(plugin, coreApiClient, player, messages, page);
            }))
            .exceptionally(error -> {
                GuiStateMenus.openError(plugin, player, session, messages,
                    runtime.routeMessage("admin-route-menu-title", "섬 라우트 관리"),
                    runtime.routeMessage("admin-route-menu-clear-failed", "라우트를 정리하지 못했습니다."),
                    "admin.route.page",
                    Map.of("page", Integer.toString(Math.max(0, page))),
                    "gui.close",
                    Map.of());
                return null;
            });
    }

    private static boolean routeManagementAllowed(Player player) {
        return player.hasPermission("cloudislands.admin") || player.hasPermission("cloudislands.admin.route");
    }

    private void openJobMenu(Player player, int page) {
        if (!jobManagementAllowed(player)) {
            runtime.message(player, runtime.routeMessage("admin-job-menu-permission-denied", "작업 관리 권한이 없습니다."));
            return;
        }
        AdminJobMenu.open(plugin, coreApiClient, player, runtime.messagesFor(player), page);
    }

    private void openJobCancelConfirmation(Player player, UUID jobId, int page) {
        if (!jobManagementAllowed(player)) {
            runtime.message(player, runtime.routeMessage("admin-job-menu-permission-denied", "작업 관리 권한이 없습니다."));
            return;
        }
        runtime.openConfirmation(player,
            runtime.routeMessage("admin-job-menu-cancel-confirm-title", "작업 취소 확인"),
            runtime.routeMessage("admin-job-menu-cancel-confirm-description", "선택한 작업을 취소합니다. 실행 중인 작업은 Core가 거부할 수 있습니다."),
            Material.BARRIER,
            runtime.routeMessage("admin-job-menu-cancel-confirm-name", "작업 취소"),
            ConfirmationTokenPolicy.ADMIN_JOB_CANCEL_CONFIRM_ACTION,
            Map.of("jobId", jobId.toString(), "page", Integer.toString(Math.max(0, page))),
            runtime.routeMessage("admin-job-menu-cancel-confirm-lore", "클릭하면 Core에 작업 취소를 요청합니다."),
            "admin.jobs.page");
    }

    private void retryJob(Player player, UUID jobId, int page) {
        mutateJob(player, jobId, page, false);
    }

    private void cancelJob(Player player, UUID jobId, int page) {
        mutateJob(player, jobId, page, true);
    }

    private void mutateJob(Player player, UUID jobId, int page, boolean cancel) {
        if (!jobManagementAllowed(player)) {
            runtime.message(player, runtime.routeMessage("admin-job-menu-permission-denied", "작업 관리 권한이 없습니다."));
            return;
        }
        MessageRenderer messages = runtime.messagesFor(player);
        String operation = cancel ? "cancel" : "retry";
        GuiSession session = GuiSessions.begin(player, "admin.jobs.mutate");
        GuiStateMenus.openSaving(plugin, player, session, messages,
            runtime.routeMessage(cancel ? "admin-job-menu-canceling" : "admin-job-menu-retrying",
                cancel ? "작업을 취소하는 중입니다." : "작업을 재시도하는 중입니다."));
        CompletableFuture<JobActionView> mutation = cancel
            ? runtime.mutate("admin.job.cancel", () -> coreApiClient.jobCommands().cancel(jobId))
            : runtime.mutate("admin.job.retry", () -> coreApiClient.jobCommands().retry(jobId));
        mutation
            .thenAccept(result -> GuiSessions.runIfCurrent(plugin, player, session, () -> {
                runtime.message(player, runtime.routeMessage("admin-job-menu-updated-prefix", "작업 상태 변경 완료: ")
                    + operation + "/" + (result.code().isBlank() ? Boolean.toString(result.accepted()) : result.code()));
                AdminJobMenu.open(plugin, coreApiClient, player, messages, page);
            }))
            .exceptionally(error -> {
                GuiStateMenus.openError(plugin, player, session, messages,
                    runtime.routeMessage("admin-job-menu-title", "섬 작업 관리"),
                    runtime.routeMessage("admin-job-menu-update-failed", "작업 상태를 변경하지 못했습니다."),
                    "admin.jobs.page",
                    Map.of("page", Integer.toString(Math.max(0, page))),
                    "gui.close",
                    Map.of());
                return null;
            });
    }

    private static boolean jobManagementAllowed(Player player) {
        return player.hasPermission("cloudislands.admin") || player.hasPermission("cloudislands.admin.jobs");
    }

    private void openReviewModerationMenu(Player player, int limit) {
        if (!reviewModerationAllowed(player)) {
            runtime.message(player, runtime.routeMessage("admin-review-menu-permission-denied", "후기 신고 관리 권한이 없습니다."));
            return;
        }
        AdminReviewModerationMenu.open(plugin, coreApiClient, player, runtime.messagesFor(player), limit);
    }

    private void moderateReview(Player player, GuiAction.AdminReviewModeration action) {
        if (!reviewModerationAllowed(player)) {
            runtime.message(player, runtime.routeMessage("admin-review-menu-permission-denied", "후기 신고 관리 권한이 없습니다."));
            return;
        }
        MessageRenderer messages = runtime.messagesFor(player);
        GuiSession session = GuiSessions.begin(player, "admin.reviews.mutate");
        GuiStateMenus.openSaving(plugin, player, session, messages,
            runtime.routeMessage("admin-review-menu-saving", "후기 상태를 변경하는 중입니다."));
        runtime.mutateIdempotent(
                "admin.review.moderate",
                () -> coreApiClient.navigationCommands().moderateReview(
                    action.islandId(),
                    action.reviewerUuid(),
                    player.getUniqueId(),
                    action.moderationState(),
                    "admin-review-gui"
                )
            )
            .thenAccept(result -> GuiSessions.runIfCurrent(plugin, player, session, () -> {
                runtime.message(player, runtime.routeMessage("admin-review-menu-updated-prefix", "후기 상태 변경 완료: ") + result.moderationState());
                AdminReviewModerationMenu.open(plugin, coreApiClient, player, messages, 36);
            }))
            .exceptionally(error -> {
                GuiStateMenus.openError(
                    plugin,
                    player,
                    session,
                    messages,
                    runtime.routeMessage("admin-review-menu-title", "후기 신고 관리"),
                    runtime.routeMessage("admin-review-menu-update-failed", "후기 상태를 변경하지 못했습니다."),
                    "admin.reviews.open",
                    "gui.close"
                );
                return null;
            });
    }

    private static boolean reviewModerationAllowed(Player player) {
        return player.hasPermission("cloudislands.admin") || player.hasPermission("cloudislands.admin.island");
    }

    private static String migrationSummary(String label, String state, int manifests, int blockingIssues, int warningIssues) {
        return label + ": state=" + state + " manifests=" + manifests + " blockingIssues=" + blockingIssues + " warningIssues=" + warningIssues;
    }

    private boolean handleAdminNodeAction(Player player, GuiAction.AdminNodeAction action, GuiClick click) {
        if (!nodeManagementAllowed(player)) {
            runtime.message(player, runtime.routeMessage("admin-node-menu-permission-denied", "노드 관리 권한이 없습니다."));
            return true;
        }
        return switch (action.type()) {
            case OPEN -> {
                refreshAdminNodeInfo(player, adminNodeId(action));
                yield true;
            }
            case LIST -> {
                openAdminNodeList(player, 0);
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

    private void openAdminNodeList(Player player, int page) {
        if (!nodeManagementAllowed(player)) {
            runtime.message(player, runtime.routeMessage("admin-node-menu-permission-denied", "노드 관리 권한이 없습니다."));
            return;
        }
        AdminNodeListMenu.open(plugin, coreApiClient, player, runtime.messagesFor(player), page);
    }

    private void refreshAdminNodeInfo(Player player, String nodeId) {
        MessageRenderer messages = runtime.messagesFor(player);
        GuiSession session = GuiSessions.begin(player, "admin.node.refresh");
        GuiStateMenus.openLoading(plugin, player, session, messages,
            runtime.routeMessage("admin-node-info-loading", "노드 정보를 불러오는 중입니다."));
        adminNodeUseCase.nodeInfoView(nodeId)
            .thenAccept(summary -> GuiSessions.runIfCurrent(plugin, player, session,
                () -> AdminNodeMenu.open(player, session, nodeId, summary, messages)))
            .exceptionally(error -> {
                GuiStateMenus.openError(plugin, player, session, messages,
                    runtime.routeMessage("admin-node-info-title", "노드 정보"),
                    runtime.routeMessage("admin-node-info-failed", "노드 정보를 불러오지 못했습니다."),
                    "admin.node.info", Map.of("nodeId", nodeId), "admin.node.open", Map.of("nodeId", nodeId));
                return null;
            });
    }

    private void listAdminNodeIslands(Player player, String nodeId) {
        UUID playerUuid = player.getUniqueId();
        adminNodeUseCase.nodeIslandsSummary(nodeId, 50)
            .thenAccept(summary -> deliverMessage(playerUuid, runtime.routeMessage("admin-node-islands-result-prefix", "노드 섬 현황: ") + adminNodeBodySummary(summary)))
            .exceptionally(error -> adminNodeFailure(playerUuid, "admin-node-islands-failed", "노드 섬 현황을 불러오지 못했습니다.", error));
    }

    private void drainAdminNode(Player player, String nodeId) {
        UUID playerUuid = player.getUniqueId();
        adminNodeUseCase.drainAction(nodeId, runtime::mutate)
            .thenAccept(result -> deliverMessage(playerUuid, adminNodeActionMessage("Node drain", nodeId, result)))
            .exceptionally(error -> adminNodeFailure(playerUuid, "admin-node-action-failed", "Node drain 실패", error));
    }

    private void undrainAdminNode(Player player, String nodeId) {
        UUID playerUuid = player.getUniqueId();
        adminNodeUseCase.undrainAction(nodeId, runtime::mutate)
            .thenAccept(result -> deliverMessage(playerUuid, adminNodeActionMessage("Node undrain", nodeId, result)))
            .exceptionally(error -> adminNodeFailure(playerUuid, "admin-node-action-failed", "Node undrain 실패", error));
    }

    private void sweepAdminNode(Player player, String nodeId) {
        UUID playerUuid = player.getUniqueId();
        adminNodeUseCase.sweepAction(nodeId, runtime::mutate)
            .thenAccept(result -> deliverMessage(playerUuid, adminNodeActionMessage("Node sweep", nodeId, result)))
            .exceptionally(error -> adminNodeFailure(playerUuid, "admin-node-action-failed", "Node sweep 실패", error));
    }

    private void kickAllAdminNode(Player player, String nodeId, String reason) {
        UUID playerUuid = player.getUniqueId();
        adminNodeUseCase.kickAllAction(nodeId, reason, runtime::mutateIdempotent)
            .thenAccept(result -> deliverMessage(playerUuid, adminNodeActionMessage("Node kickall", nodeId, result)))
            .exceptionally(error -> adminNodeFailure(playerUuid, "admin-node-danger-action-failed", "Node kickall 실패", error));
    }

    private void shutdownAdminNodeSafely(Player player, String nodeId, String reason) {
        UUID playerUuid = player.getUniqueId();
        adminNodeUseCase.shutdownSafelyAction(nodeId, reason, runtime::mutateIdempotent)
            .thenAccept(result -> deliverMessage(playerUuid, adminNodeActionMessage("Node shutdown-safe", nodeId, result)))
            .exceptionally(error -> adminNodeFailure(playerUuid, "admin-node-danger-action-failed", "Node shutdown-safe 실패", error));
    }

    private void openAdminNodeKickAllConfirmation(Player player, String nodeId) {
        runtime.openConfirmation(player,
            runtime.routeMessage("admin-node-kickall-confirm-title", "노드 플레이어 이동 확인"),
            runtime.routeMessage("admin-node-kickall-confirm-description", "현재 노드의 접속자를 로비로 이동합니다."),
            AdminNodeMenu.kickAllConfirmationMaterial(),
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
            AdminNodeMenu.shutdownConfirmationMaterial(),
            runtime.routeMessage("admin-node-shutdown-confirm-name", "안전 종료 실행"),
            "admin.node.shutdown-safe.confirm",
            Map.of("nodeId", nodeId, "reason", "admin-gui"),
            runtime.routeMessage("admin-node-shutdown-confirm-lore", "클릭하면 Core에 노드 안전 종료를 요청합니다."),
            "admin.node.open");
    }

    private Void adminNodeFailure(UUID playerUuid, String key, String fallback, Throwable error) {
        deliverMessage(playerUuid, runtime.routeMessage(key, fallback));
        return null;
    }

    private void deliverMessage(UUID playerUuid, String message) {
        PaperOnlinePlayer.run(plugin, playerUuid, activePlayer -> runtime.message(activePlayer, message));
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

    private static boolean nodeManagementAllowed(Player player) {
        return player.hasPermission("cloudislands.admin") || player.hasPermission("cloudislands.admin.node");
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
