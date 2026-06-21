package kr.lunaf.cloudislands.paper.command;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.paper.gui.GuiAction;
import kr.lunaf.cloudislands.paper.gui.DangerousGuiActionPolicy;
import kr.lunaf.cloudislands.paper.gui.GuiClick;
import kr.lunaf.cloudislands.paper.gui.IslandCreateMenu;
import kr.lunaf.cloudislands.paper.gui.IslandDangerMenu;
import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

final class IslandLifecycleCommandHandler {
    private final Plugin plugin;
    private final CoreApiClient coreApiClient;
    private final Runtime runtime;

    IslandLifecycleCommandHandler(Plugin plugin, CoreApiClient coreApiClient, Runtime runtime) {
        this.plugin = plugin;
        this.coreApiClient = coreApiClient;
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
        if (subcommand.equals("delete") || subcommand.equals("삭제")) {
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
        if (action instanceof GuiAction.DangerResetConfirm resetConfirm) {
            if (dangerConfirmed(player, resetConfirm.data(), click, DangerousGuiActionPolicy.RESET_OPERATION, DangerousGuiActionPolicy.RESET_TOKEN)) {
                resetIsland(player, resetConfirm.reason());
            }
            return true;
        }
        if (action instanceof GuiAction.DangerDeleteConfirm deleteConfirm) {
            if (dangerConfirmed(player, deleteConfirm.data(), click, DangerousGuiActionPolicy.DELETE_OPERATION, DangerousGuiActionPolicy.DELETE_TOKEN)) {
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
        String actionId = action.actionId();
        Map<String, String> data = action.data();
        return switch (actionId) {
            case "island.create.open" -> {
                IslandCreateMenu.open(plugin, coreApiClient, player, runtime.messagesFor(player));
                yield true;
            }
            case "island.create" -> {
                createIsland(player, data.getOrDefault("templateId", "default"));
                yield true;
            }
            case "island.danger.open" -> {
                IslandDangerMenu.open(player, runtime.messagesFor(player));
                yield true;
            }
            case "island.danger.reset.prepare" -> {
                IslandDangerMenu.openResetConfirm(player, runtime.messagesFor(player));
                yield true;
            }
            case "island.danger.delete.prepare" -> {
                IslandDangerMenu.openDeleteConfirm(player, runtime.messagesFor(player));
                yield true;
            }
            case "island.danger.reset.confirm" -> {
                if (dangerConfirmed(player, data, click, DangerousGuiActionPolicy.RESET_OPERATION, DangerousGuiActionPolicy.RESET_TOKEN)) {
                    resetIsland(player, data.getOrDefault("reason", "player-reset"));
                }
                yield true;
            }
            case "island.danger.delete.confirm" -> {
                if (dangerConfirmed(player, data, click, DangerousGuiActionPolicy.DELETE_OPERATION, DangerousGuiActionPolicy.DELETE_TOKEN)) {
                    deleteIsland(player);
                }
                yield true;
            }
            default -> false;
        };
    }

    private boolean dangerConfirmed(Player player, Map<String, String> data, GuiClick click, String operation, String token) {
        if (DangerousGuiActionPolicy.confirmed(data, click, operation, token)) {
            return true;
        }
        runtime.message(player, runtime.routeMessage("danger-confirm-token-invalid", "위험 작업 확인 토큰이 올바르지 않습니다. 확인 화면을 다시 열어주세요."));
        return false;
    }

    private void createIsland(Player player, String templateId) {
        runtime.mutate("island.create", () -> coreApiClient.createIsland(player.getUniqueId(), templateId))
            .thenAccept(result -> {
                if (!result.accepted()) {
                    runtime.message(player, runtime.playerCodeMessage(result.code(), "섬 생성을 시작하지 못했습니다."));
                    return;
                }
                runtime.message(player, "섬 생성을 시작했습니다.");
            })
            .exceptionally(error -> {
                runtime.message(player, runtime.coreWriteFailureMessage(error, "섬 생성을 시작하지 못했습니다."));
                return null;
            });
    }

    private void deleteIsland(Player player) {
        runtime.currentIsland(player, "섬 안에서만 섬을 삭제할 수 있습니다.").ifPresent(islandId -> {
            runtime.mutateIdempotent("island.delete", () -> coreApiClient.deleteIsland(player.getUniqueId(), islandId))
                .thenAccept(result -> {
                    if (!result.accepted()) {
                        runtime.message(player, runtime.playerCodeMessage(result.code(), "섬을 삭제하지 못했습니다."));
                        return;
                    }
                    runtime.message(player, "섬 삭제를 요청했습니다.");
                })
                .exceptionally(error -> {
                    runtime.message(player, runtime.coreWriteFailureMessage(error, "섬을 삭제하지 못했습니다."));
                    return null;
                });
        });
    }

    private void resetIsland(Player player, String reason) {
        runtime.currentIsland(player, "섬 안에서만 섬을 리셋할 수 있습니다.").ifPresent(islandId -> {
            runtime.mutateIdempotent("island.reset", () -> coreApiClient.resetIslandResult(islandId, player.getUniqueId(), reason))
                .thenAccept(body -> runtime.message(player, runtime.actionResultMessage("섬 리셋 요청", islandId, body)))
                .exceptionally(error -> {
                    runtime.message(player, runtime.coreWriteFailureMessage(error, "섬을 리셋하지 못했습니다."));
                    return null;
                });
        });
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

        String actionResultMessage(String label, UUID targetId, String body);

        <T> CompletableFuture<T> mutate(String auditAction, Supplier<CompletableFuture<T>> operation);

        <T> CompletableFuture<T> mutateIdempotent(String auditAction, Supplier<CompletableFuture<T>> operation);

        MessageRenderer messagesFor(Player player);
    }
}
