package kr.lunaf.cloudislands.paper.command;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import kr.lunaf.cloudislands.api.economy.EconomyBridge;
import kr.lunaf.cloudislands.api.economy.EconomyProviderState;
import kr.lunaf.cloudislands.api.model.CreateIslandResult;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.coreclient.TemplateView;
import kr.lunaf.cloudislands.paper.application.IslandCreationUseCase;
import kr.lunaf.cloudislands.paper.application.IslandCreationUseCase.IslandActionResult;
import kr.lunaf.cloudislands.paper.gui.GuiAction;
import kr.lunaf.cloudislands.paper.gui.DangerousGuiActionPolicy;
import kr.lunaf.cloudislands.paper.gui.GuiClick;
import kr.lunaf.cloudislands.paper.gui.GuiStateMenus;
import kr.lunaf.cloudislands.paper.gui.IslandCreateMenu;
import kr.lunaf.cloudislands.paper.gui.IslandDangerMenu;
import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

final class IslandLifecycleCommandHandler {
    private final Plugin plugin;
    private final CoreApiClient coreApiClient;
    private final EconomyBridge economyBridge;
    private final IslandCreationUseCase creationUseCase;
    private final Runtime runtime;
    private final PendingIslandCreationOperations pendingCreations = new PendingIslandCreationOperations();

    IslandLifecycleCommandHandler(Plugin plugin, CoreApiClient coreApiClient, Runtime runtime) {
        this(plugin, coreApiClient, null, runtime);
    }

    IslandLifecycleCommandHandler(Plugin plugin, CoreApiClient coreApiClient, EconomyBridge economyBridge, Runtime runtime) {
        this.plugin = plugin;
        this.coreApiClient = coreApiClient;
        this.economyBridge = economyBridge;
        this.creationUseCase = new IslandCreationUseCase(coreApiClient);
        this.runtime = runtime;
    }

    boolean handleCommand(Player player, String subcommand, String[] args) {
        if (subcommand.equals("create-menu") || subcommand.equals("templates") || subcommand.equals("생성메뉴") || subcommand.equals("템플릿")) {
            IslandCreateMenu.open(plugin, coreApiClient, player, runtime.messagesFor(player));
            return true;
        }
        if (subcommand.equals("create") || subcommand.equals("생성")) {
            createIsland(player, args.length > 1 ? args[1] : "default");
            return true;
        }
        if (subcommand.equals("delete") || subcommand.equals("disband") || subcommand.equals("삭제")) {
            if (args.length > 1 && args[1].equalsIgnoreCase("confirm")) {
                deleteIsland(player);
            } else {
                IslandDangerMenu.open(player, runtime.messagesFor(player));
            }
            return true;
        }
        if (subcommand.equals("reset") || subcommand.equals("리셋")) {
            if (args.length > 1 && args[1].equalsIgnoreCase("confirm")) {
                resetIsland(player, args.length > 2 ? joined(args, 2) : "player-reset");
            } else {
                IslandDangerMenu.open(player, runtime.messagesFor(player));
            }
            return true;
        }
        if (subcommand.equals("danger") || subcommand.equals("위험작업")) {
            IslandDangerMenu.open(player, runtime.messagesFor(player));
            return true;
        }
        return false;
    }

    boolean handleGuiAction(Player player, GuiAction action, GuiClick click) {
        if (action instanceof GuiAction.IslandCreate islandCreate) {
            createIsland(player, islandCreate.templateId());
            return true;
        }
        if (action instanceof GuiAction.IslandCreatePrepare createPrepare) {
            IslandCreateMenu.openConfirm(plugin, coreApiClient, player, createPrepare.templateId(), runtime.messagesFor(player));
            return true;
        }
        if (action instanceof GuiAction.IslandCreateLocked locked) {
            runtime.message(player, runtime.routeMessage("create-menu-locked", "이 템플릿을 사용할 권한이 없습니다.") + (locked.requiredPermission().isBlank() ? "" : " " + locked.requiredPermission()));
            return true;
        }
        if (action instanceof GuiAction.TemplatePage page) {
            IslandCreateMenu.open(plugin, coreApiClient, player, runtime.messagesFor(player), page.page());
            return true;
        }
        if (action instanceof GuiAction.DangerResetConfirm resetConfirm) {
            if (dangerConfirmed(player, resetConfirm.operation(), resetConfirm.token(), click, DangerousGuiActionPolicy.RESET_OPERATION, DangerousGuiActionPolicy.RESET_TOKEN)) {
                resetIsland(player, resetConfirm.reason());
            }
            return true;
        }
        if (action instanceof GuiAction.DangerDeleteConfirm deleteConfirm) {
            if (dangerConfirmed(player, deleteConfirm.operation(), deleteConfirm.token(), click, DangerousGuiActionPolicy.DELETE_OPERATION, DangerousGuiActionPolicy.DELETE_TOKEN)) {
                deleteIsland(player);
            }
            return true;
        }
        if (action instanceof GuiAction.NoPayload noPayload) {
            return switch (noPayload.type()) {
                case CREATE_OPEN -> {
                    IslandCreateMenu.open(plugin, coreApiClient, player, runtime.messagesFor(player));
                    yield true;
                }
                case DANGER_OPEN -> {
                    IslandDangerMenu.open(player, runtime.messagesFor(player));
                    yield true;
                }
                case DANGER_RESET_PREPARE -> {
                    IslandDangerMenu.openResetConfirm(player, runtime.messagesFor(player));
                    yield true;
                }
                case DANGER_DELETE_PREPARE -> {
                    IslandDangerMenu.openDeleteConfirm(player, runtime.messagesFor(player));
                    yield true;
                }
                default -> false;
            };
        }
        return false;
    }

    private boolean dangerConfirmed(Player player, String actionOperation, String actionToken, GuiClick click, String operation, String token) {
        if (DangerousGuiActionPolicy.confirmed(actionOperation, actionToken, click, operation, token)) {
            return true;
        }
        runtime.message(player, message("danger-confirm-token-invalid", "위험 작업 확인 토큰이 올바르지 않습니다. 확인 화면을 다시 열어주세요."));
        return false;
    }

    private void createIsland(Player player, String templateId) {
        UUID playerUuid = player.getUniqueId();
        if (!pendingCreations.acquire(playerUuid)) {
            runtime.message(player, runtime.playerCodeMessage("CREATE_IN_PROGRESS", message("create-progress-in-progress", "이미 섬 생성 요청을 처리하고 있습니다.")));
            return;
        }
        MessageRenderer messages = runtime.messagesFor(player);
        GuiStateMenus.openSaving(plugin, player, messages, message("create-progress-title", "섬 생성 요청 중"));
        String normalizedTemplateId = templateId == null || templateId.isBlank() ? "default" : templateId.trim();
        coreApiClient.templates().get(normalizedTemplateId)
            .thenCompose(template -> canUseTemplate(player, template)
                ? createWithTemplateCost(player, normalizedTemplateId, template)
                : CompletableFuture.completedFuture(new CreateIslandResult(false, "TEMPLATE_PERMISSION_DENIED", null, null)))
            .thenAccept(result -> {
                if (!result.accepted()) {
                    String detail = runtime.playerCodeMessage(result.code(), message("create-progress-start-failed", "섬 생성을 시작하지 못했습니다."));
                    GuiStateMenus.openError(plugin, player, messages, message("create-progress-title", "섬 생성 요청 중"), detail, "island.create.open", "island.create.open");
                    runtime.message(player, detail);
                    return;
                }
                GuiStateMenus.openSuccess(plugin, player, messages, message("create-progress-title", "섬 생성 요청 중"), message("create-progress-started", "섬 생성을 시작했습니다."), "island.main.open");
                runtime.message(player, message("create-progress-started", "섬 생성을 시작했습니다."));
            })
            .exceptionally(error -> {
                String detail = runtime.coreWriteFailureMessage(error, message("create-progress-start-failed", "섬 생성을 시작하지 못했습니다."));
                GuiStateMenus.openError(plugin, player, messages, message("create-progress-title", "섬 생성 요청 중"), detail, "island.create.open", "island.create.open");
                runtime.message(player, detail);
                return null;
            })
            .whenComplete((_ignored, _error) -> pendingCreations.release(playerUuid));
    }

    private CompletableFuture<CreateIslandResult> createWithTemplateCost(Player player, String templateId, TemplateView template) {
        BigDecimal creationCost = creationCost(template);
        if (creationCost.signum() <= 0) {
            return creationUseCase.create(player.getUniqueId(), templateId, runtime::mutate);
        }
        if (economyBridge == null || economyBridge.providerState() != EconomyProviderState.ACTIVE) {
            return CompletableFuture.completedFuture(new CreateIslandResult(false, "ECONOMY_UNAVAILABLE", null, null));
        }
        return economyBridge.withdraw(player.getUniqueId(), creationCost, "CloudIslands island creation " + template.id())
            .thenCompose(charged -> {
                if (!charged) {
                    return CompletableFuture.completedFuture(new CreateIslandResult(false, "ECONOMY_CHARGE_FAILED", null, null));
                }
                return creationUseCase.create(player.getUniqueId(), templateId, runtime::mutate)
                    .thenCompose(result -> result.accepted()
                        ? CompletableFuture.completedFuture(result)
                        : refundCreateCost(player, creationCost, template.id()).thenApply(_ignored -> result)
                            .exceptionally(_refundError -> new CreateIslandResult(false, "ECONOMY_REFUND_FAILED", result.island(), result.ticket())))
                    .exceptionallyCompose(error -> refundCreateCost(player, creationCost, template.id())
                        .thenApply(_ignored -> new CreateIslandResult(false, "CORE_CREATE_FAILED_REFUNDED", null, null))
                        .exceptionally(_refundError -> new CreateIslandResult(false, "ECONOMY_REFUND_FAILED", null, null)));
            });
    }

    private CompletableFuture<Void> refundCreateCost(Player player, BigDecimal creationCost, String templateId) {
        return economyBridge.deposit(player.getUniqueId(), creationCost, "CloudIslands island creation rollback " + templateId);
    }

    private static boolean canUseTemplate(Player player, TemplateView template) {
        return template.requiredPermission().isBlank() || player.hasPermission(template.requiredPermission());
    }

    private static BigDecimal creationCost(TemplateView template) {
        try {
            return new BigDecimal(template.creationCost()).stripTrailingZeros();
        } catch (RuntimeException exception) {
            return BigDecimal.ZERO;
        }
    }

    private void deleteIsland(Player player) {
        runtime.currentIsland(player, message("delete-island-required", "섬 안에서만 섬을 삭제할 수 있습니다.")).ifPresent(islandId -> {
            creationUseCase.delete(player.getUniqueId(), islandId, runtime::mutateIdempotent)
                .thenAccept(result -> {
                    if (!result.accepted()) {
                        runtime.message(player, runtime.playerCodeMessage(result.code(), message("delete-failed", "섬을 삭제하지 못했습니다.")));
                        return;
                    }
                    runtime.message(player, message("delete-requested", "섬 삭제를 요청했습니다."));
                })
                .exceptionally(error -> {
                    runtime.message(player, runtime.coreWriteFailureMessage(error, message("delete-failed", "섬을 삭제하지 못했습니다.")));
                    return null;
                });
        });
    }

    private void resetIsland(Player player, String reason) {
        runtime.currentIsland(player, message("reset-island-required", "섬 안에서만 섬을 리셋할 수 있습니다.")).ifPresent(islandId -> {
            creationUseCase.resetAction(islandId, player.getUniqueId(), reason, runtime::mutateIdempotent)
                .thenAccept(result -> runtime.message(player, lifecycleActionMessage(message("reset-request-label", "섬 리셋 요청"), islandId, result)))
                .exceptionally(error -> {
                    runtime.message(player, runtime.coreWriteFailureMessage(error, message("reset-failed", "섬을 리셋하지 못했습니다.")));
                    return null;
                });
        });
    }

    private String lifecycleActionMessage(String label, UUID islandId, IslandActionResult result) {
        StringBuilder builder = new StringBuilder(label)
            .append(' ')
            .append(result.accepted() ? message("lifecycle-action-complete", "완료") : message("lifecycle-action-failed", "실패"));
        if (islandId != null) {
            builder.append(message("lifecycle-action-target-prefix", ": 대상=")).append(islandId.toString(), 0, 8);
        }
        if (!result.accepted() && !result.code().isBlank()) {
            builder.append(message("lifecycle-action-reason-prefix", " 사유=")).append(result.code());
        }
        return builder.toString();
    }

    private String message(String key, String fallback) {
        return runtime.routeMessage(key, fallback);
    }

    private static String joined(String[] args, int start) {
        StringBuilder builder = new StringBuilder();
        for (int index = start; index < args.length; index++) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(args[index]);
        }
        return builder.toString();
    }

    interface Runtime {
        Optional<UUID> currentIsland(Player player, String missingMessage);

        void message(Player player, String message);

        String routeMessage(String key, String fallback);

        String playerCodeMessage(String code, String fallback);

        String coreWriteFailureMessage(Throwable error, String fallback);

        <T> CompletableFuture<T> mutate(String auditAction, Supplier<CompletableFuture<T>> operation);

        <T> CompletableFuture<T> mutateIdempotent(String auditAction, Supplier<CompletableFuture<T>> operation);

        MessageRenderer messagesFor(Player player);
    }
}
