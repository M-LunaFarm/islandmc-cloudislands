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
import kr.lunaf.cloudislands.paper.PlayerConnectionSession;
import kr.lunaf.cloudislands.paper.application.IslandCreationUseCase;
import kr.lunaf.cloudislands.paper.application.IslandCreationUseCase.IslandActionResult;
import kr.lunaf.cloudislands.paper.gui.GuiAction;
import kr.lunaf.cloudislands.paper.gui.DangerousGuiActionPolicy;
import kr.lunaf.cloudislands.paper.gui.GuiClick;
import kr.lunaf.cloudislands.paper.gui.GuiStateMenus;
import kr.lunaf.cloudislands.paper.gui.GuiSession;
import kr.lunaf.cloudislands.paper.gui.IslandCreateMenu;
import kr.lunaf.cloudislands.paper.gui.IslandDangerMenu;
import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

final class IslandLifecycleCommandHandler {
    private final Plugin plugin;
    private final CoreApiClient coreApiClient;
    private final EconomyBridge economyBridge;
    private final IslandCreationUseCase creationUseCase;
    private final Runtime runtime;
    private final IslandRoutingCommandHandler routingCommands;
    private final PendingIslandCreationOperations pendingCreations = new PendingIslandCreationOperations();

    IslandLifecycleCommandHandler(Plugin plugin, CoreApiClient coreApiClient, Runtime runtime) {
        this(plugin, coreApiClient, null, runtime);
    }

    IslandLifecycleCommandHandler(Plugin plugin, CoreApiClient coreApiClient, EconomyBridge economyBridge, Runtime runtime) {
        this(plugin, coreApiClient, economyBridge, runtime, null);
    }

    IslandLifecycleCommandHandler(Plugin plugin, CoreApiClient coreApiClient, EconomyBridge economyBridge, Runtime runtime, IslandRoutingCommandHandler routingCommands) {
        this.plugin = plugin;
        this.coreApiClient = coreApiClient;
        this.economyBridge = economyBridge;
        this.creationUseCase = new IslandCreationUseCase(coreApiClient);
        this.runtime = runtime;
        this.routingCommands = routingCommands;
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
        PlayerConnectionSession playerSession = PlayerConnectionSession.capture(player);
        UUID playerUuid = playerSession.playerUuid();
        if (!pendingCreations.acquire(playerUuid)) {
            runtime.message(player, runtime.playerCodeMessage("CREATE_IN_PROGRESS", message("create-progress-in-progress", "이미 섬 생성 요청을 처리하고 있습니다.")));
            return;
        }
        MessageRenderer messages = runtime.messagesFor(player);
        GuiSession session = GuiStateMenus.openSaving(plugin, player, messages, message("create-progress-title", "섬 생성 요청 중"));
        String normalizedTemplateId = templateId == null || templateId.isBlank() ? "default" : templateId.trim();
        coreApiClient.templates().get(normalizedTemplateId)
            .thenCompose(template -> PaperSchedulers.supply(plugin, () -> canUseTemplate(playerSession, template))
                .thenCompose(allowed -> allowed
                    ? createWithTemplateCost(playerSession, normalizedTemplateId, template)
                    : CompletableFuture.completedFuture(new CreateIslandResult(false, "TEMPLATE_PERMISSION_DENIED", null, null))))
            .thenAccept(result -> finishCreate(playerSession, session, messages, result))
            .exceptionally(error -> {
                failCreate(playerSession, session, messages, error);
                return null;
            })
            .whenComplete((_ignored, _error) -> pendingCreations.release(playerUuid));
    }

    private void finishCreate(PlayerConnectionSession playerSession, GuiSession session, MessageRenderer messages, CreateIslandResult result) {
        PaperSchedulers.run(plugin, () -> {
            Player activePlayer = currentPlayer(playerSession);
            if (activePlayer == null) {
                return;
            }
            if (!result.accepted()) {
                String detail = runtime.playerCodeMessage(result.code(), message("create-progress-start-failed", "섬 생성을 시작하지 못했습니다."));
                GuiStateMenus.openError(plugin, activePlayer, session, messages, message("create-progress-title", "섬 생성 요청 중"), detail, "island.create.open", "island.create.open");
                runtime.message(activePlayer, detail);
                return;
            }
            String detail = message("create-progress-started", "섬 생성을 시작했습니다.");
            GuiStateMenus.openSuccess(plugin, activePlayer, session, messages, message("create-progress-title", "섬 생성 요청 중"), detail, "island.main.open");
            runtime.message(activePlayer, detail);
            if (result.ticket() != null && routingCommands != null) {
                routingCommands.routeTicket(
                    activePlayer,
                    CompletableFuture.completedFuture(result.ticket()),
                    message("create-route-failed", "섬은 생성됐지만 이동하지 못했습니다. /island home 명령으로 다시 시도해주세요.")
                );
            }
        });
    }

    private void failCreate(PlayerConnectionSession playerSession, GuiSession session, MessageRenderer messages, Throwable error) {
        String detail = runtime.coreWriteFailureMessage(error, message("create-progress-start-failed", "섬 생성을 시작하지 못했습니다."));
        PaperSchedulers.run(plugin, () -> {
            Player activePlayer = currentPlayer(playerSession);
            if (activePlayer == null) {
                return;
            }
            GuiStateMenus.openError(plugin, activePlayer, session, messages, message("create-progress-title", "섬 생성 요청 중"), detail, "island.create.open", "island.create.open");
            runtime.message(activePlayer, detail);
        });
    }

    private CompletableFuture<CreateIslandResult> createWithTemplateCost(PlayerConnectionSession playerSession, String templateId, TemplateView template) {
        UUID playerUuid = playerSession.playerUuid();
        BigDecimal creationCost = creationCost(template);
        if (creationCost.signum() <= 0) {
            return creationUseCase.create(playerUuid, templateId, runtime::mutate);
        }
        if (economyBridge == null || economyBridge.providerState() != EconomyProviderState.ACTIVE) {
            return CompletableFuture.completedFuture(new CreateIslandResult(false, "ECONOMY_UNAVAILABLE", null, null));
        }
        return economyBridge.withdraw(playerUuid, creationCost, "CloudIslands island creation " + template.id())
            .thenCompose(charged -> {
                if (!charged) {
                    return CompletableFuture.completedFuture(new CreateIslandResult(false, "ECONOMY_CHARGE_FAILED", null, null));
                }
                return PaperSchedulers.supply(plugin, () -> currentPlayer(playerSession) != null)
                    .thenCompose(current -> current
                        ? settleChargedCreate(playerUuid, templateId, template, creationCost)
                        : refundReplacedCreate(playerUuid, creationCost, template.id()));
            });
    }

    private CompletableFuture<CreateIslandResult> settleChargedCreate(UUID playerUuid, String templateId, TemplateView template, BigDecimal creationCost) {
        return creationUseCase.createWithManagedEconomySettlement(playerUuid, templateId, creationCost, runtime::mutate)
            .thenCompose(result -> result.accepted()
                ? CompletableFuture.completedFuture(result)
                : refundCreateCost(playerUuid, creationCost, template.id()).thenApply(_ignored -> result)
                    .exceptionally(_refundError -> new CreateIslandResult(false, "ECONOMY_REFUND_FAILED", result.island(), result.ticket())))
            .exceptionallyCompose(error -> refundCreateCost(playerUuid, creationCost, template.id())
                .thenApply(_ignored -> new CreateIslandResult(false, "CORE_CREATE_FAILED_REFUNDED", null, null))
                .exceptionally(_refundError -> new CreateIslandResult(false, "ECONOMY_REFUND_FAILED", null, null)));
    }

    private CompletableFuture<CreateIslandResult> refundReplacedCreate(UUID playerUuid, BigDecimal creationCost, String templateId) {
        return refundCreateCost(playerUuid, creationCost, templateId)
            .thenApply(_ignored -> new CreateIslandResult(false, "PLAYER_SESSION_REPLACED", null, null))
            .exceptionally(_refundError -> new CreateIslandResult(false, "ECONOMY_REFUND_FAILED", null, null));
    }

    private CompletableFuture<Void> refundCreateCost(UUID playerUuid, BigDecimal creationCost, String templateId) {
        return economyBridge.deposit(playerUuid, creationCost, "CloudIslands island creation rollback " + templateId);
    }

    private boolean canUseTemplate(PlayerConnectionSession playerSession, TemplateView template) {
        Player activePlayer = currentPlayer(playerSession);
        return activePlayer != null && (template.requiredPermission().isBlank() || activePlayer.hasPermission(template.requiredPermission()));
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
            PlayerConnectionSession playerSession = PlayerConnectionSession.capture(player);
            UUID actorUuid = playerSession.playerUuid();
            creationUseCase.delete(actorUuid, islandId, runtime::mutateIdempotent)
                .thenAccept(result -> {
                    if (!result.accepted()) {
                        deliverMessage(playerSession, runtime.playerCodeMessage(result.code(), message("delete-failed", "섬을 삭제하지 못했습니다.")));
                        return;
                    }
                    deliverMessage(playerSession, message("delete-requested", "섬 삭제를 요청했습니다."));
                })
                .exceptionally(error -> {
                    deliverMessage(playerSession, runtime.coreWriteFailureMessage(error, message("delete-failed", "섬을 삭제하지 못했습니다.")));
                    return null;
                });
        });
    }

    private void resetIsland(Player player, String reason) {
        runtime.currentIsland(player, message("reset-island-required", "섬 안에서만 섬을 리셋할 수 있습니다.")).ifPresent(islandId -> {
            PlayerConnectionSession playerSession = PlayerConnectionSession.capture(player);
            UUID actorUuid = playerSession.playerUuid();
            creationUseCase.resetAction(islandId, actorUuid, reason, runtime::mutateIdempotent)
                .thenAccept(result -> deliverMessage(playerSession, lifecycleActionMessage(message("reset-request-label", "섬 리셋 요청"), islandId, result)))
                .exceptionally(error -> {
                    deliverMessage(playerSession, runtime.coreWriteFailureMessage(error, message("reset-failed", "섬을 리셋하지 못했습니다.")));
                    return null;
                });
        });
    }

    private void deliverMessage(PlayerConnectionSession playerSession, String detail) {
        PaperSchedulers.run(plugin, () -> {
            Player activePlayer = currentPlayer(playerSession);
            if (activePlayer != null) {
                runtime.message(activePlayer, detail);
            }
        });
    }

    private Player currentPlayer(PlayerConnectionSession playerSession) {
        Player activePlayer = plugin.getServer().getPlayer(playerSession.playerUuid());
        return playerSession.isCurrent(activePlayer) ? activePlayer : null;
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
