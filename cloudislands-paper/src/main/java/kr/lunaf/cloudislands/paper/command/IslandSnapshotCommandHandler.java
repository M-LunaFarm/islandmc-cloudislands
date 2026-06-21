package kr.lunaf.cloudislands.paper.command;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.paper.application.SnapshotUseCase;
import kr.lunaf.cloudislands.paper.application.SnapshotUseCase.SnapshotActionResult;
import kr.lunaf.cloudislands.paper.application.SnapshotUseCase.SnapshotView;
import kr.lunaf.cloudislands.paper.gui.GuiAction;
import kr.lunaf.cloudislands.paper.gui.GuiClick;
import kr.lunaf.cloudislands.paper.gui.IslandSnapshotMenu;
import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

final class IslandSnapshotCommandHandler {
    private final Plugin plugin;
    private final CoreApiClient coreApiClient;
    private final SnapshotUseCase snapshotUseCase;
    private final Runtime runtime;

    IslandSnapshotCommandHandler(Plugin plugin, CoreApiClient coreApiClient, Runtime runtime) {
        this.plugin = plugin;
        this.coreApiClient = coreApiClient;
        this.snapshotUseCase = new SnapshotUseCase(coreApiClient);
        this.runtime = runtime;
    }

    boolean handleCommand(Player player, String subcommand, String[] args) {
        if (subcommand.equals("snapshot") || subcommand.equals("스냅샷")) {
            if (args.length > 1) {
                requestSnapshot(player, joined(args, 1));
            } else {
                openSnapshotMenu(player);
            }
            return true;
        }
        if (subcommand.equals("snapshots") || subcommand.equals("snapshot-menu")) {
            openSnapshotMenu(player);
            return true;
        }
        if (subcommand.equals("snapshot-list") || subcommand.equals("스냅샷목록")) {
            listSnapshots(player, args.length > 1 ? integer(args[1], 10) : 10);
            return true;
        }
        if (subcommand.equals("snapshot-create") || subcommand.equals("snapshot-request") || subcommand.equals("스냅샷생성")) {
            requestSnapshot(player, args.length > 1 ? joined(args, 1) : "manual");
            return true;
        }
        if (subcommand.equals("snapshot-restore") || subcommand.equals("restore") || subcommand.equals("rollback") || subcommand.equals("스냅샷복원") || subcommand.equals("복원") || subcommand.equals("롤백")) {
            if (args.length < 2) {
                runtime.message(player, runtime.routeMessage("input-snapshot-number-required", "복원할 스냅샷 번호를 입력해주세요."));
                return true;
            }
            restoreSnapshot(player, SnapshotUseCase.positiveSnapshotNo(args[1]));
            return true;
        }
        return false;
    }

    boolean handleGuiAction(Player player, GuiAction action, GuiClick click) {
        if (action instanceof GuiAction.SnapshotCreate snapshotCreate) {
            requestSnapshot(player, snapshotCreate.reason());
            return true;
        }
        if (action instanceof GuiAction.SnapshotRestore snapshotRestore) {
            if (snapshotRestore.confirmation()) {
                if (runtime.confirmationAccepted(player, action, click)) {
                    restoreSnapshot(player, snapshotRestore.snapshotNo());
                }
            } else {
                runtime.openConfirmation(
                    player,
                    runtime.routeMessage("snapshot-restore-confirm-title", "스냅샷 복원 확인"),
                    runtime.routeMessage("snapshot-restore-confirm-description", "현재 월드 상태를 선택한 스냅샷으로 복원합니다."),
                    Material.CHEST,
                    runtime.routeMessage("snapshot-restore-confirm-name", "스냅샷 복원"),
                    "island.snapshot.restore.confirm",
                    Map.of("snapshotNo", Long.toString(snapshotRestore.snapshotNo())),
                    runtime.routeMessage("snapshot-restore-confirm-lore", "클릭하면 Core에 스냅샷 복원을 요청합니다."),
                    "island.snapshots.open"
                );
            }
            return true;
        }
        if (action instanceof GuiAction.NoPayload noPayload) {
            return switch (noPayload.type()) {
                case SNAPSHOTS_OPEN -> {
                    openSnapshotMenu(player);
                    yield true;
                }
                case SNAPSHOTS_LIST -> {
                    listSnapshots(player, 10);
                    yield true;
                }
                default -> false;
            };
        }
        return false;
    }

    private void listSnapshots(Player player, int limit) {
        runtime.currentIsland(player, "섬 안에서만 스냅샷을 확인할 수 있습니다.").ifPresent(islandId -> {
            snapshotUseCase.snapshotViews(islandId, limit)
                .thenAccept(snapshots -> runtime.message(player, snapshotListMessage(snapshots)))
                .exceptionally(error -> {
                    runtime.message(player, "섬 스냅샷을 불러오지 못했습니다.");
                    return null;
                });
        });
    }

    private void openSnapshotMenu(Player player) {
        runtime.currentIsland(player, "섬 안에서만 스냅샷 메뉴를 열 수 있습니다.").ifPresent(islandId -> IslandSnapshotMenu.open(plugin, coreApiClient, player, islandId, runtime.messagesFor(player)));
    }

    private void requestSnapshot(Player player, String reason) {
        runtime.currentIsland(player, "섬 안에서만 스냅샷을 생성할 수 있습니다.").ifPresent(islandId -> {
            if (!player.isOp()) {
                runtime.message(player, runtime.routeMessage("snapshot-create-denied", "섬 스냅샷을 생성할 관리자 권한이 없습니다."));
                return;
            }
            snapshotUseCase.requestSnapshotAction(islandId, reason, runtime::mutate)
                .thenAccept(result -> runtime.message(player, snapshotActionMessage("섬 스냅샷 생성 요청", islandId, result)))
                .exceptionally(error -> {
                    runtime.message(player, "섬 스냅샷 생성을 요청하지 못했습니다.");
                    return null;
                });
        });
    }

    private void restoreSnapshot(Player player, long snapshotNo) {
        runtime.currentIsland(player, "섬 안에서만 스냅샷을 복원할 수 있습니다.").ifPresent(islandId -> {
            if (!player.isOp()) {
                runtime.message(player, runtime.routeMessage("snapshot-restore-denied", "섬 스냅샷을 복원할 관리자 권한이 없습니다."));
                return;
            }
            if (snapshotNo <= 0L) {
                runtime.message(player, runtime.routeMessage("input-snapshot-number-invalid", "올바른 스냅샷 번호를 입력해주세요."));
                return;
            }
            snapshotUseCase.restoreSnapshotAction(islandId, snapshotNo, runtime::mutateIdempotent)
                .thenAccept(result -> runtime.message(player, snapshotActionMessage("섬 스냅샷 복원 요청 #" + snapshotNo, islandId, result)))
                .exceptionally(error -> {
                    runtime.message(player, "섬 스냅샷 복원을 요청하지 못했습니다.");
                    return null;
                });
        });
    }

    private String snapshotActionMessage(String label, UUID islandId, SnapshotActionResult result) {
        StringBuilder builder = new StringBuilder(label)
            .append(result.accepted() ? " 완료" : " 실패");
        if (islandId != null) {
            builder.append(": 대상=").append(islandId.toString(), 0, 8);
        }
        if (!result.accepted() && !result.code().isBlank()) {
            builder.append(" 사유=").append(result.code());
        }
        return builder.toString();
    }

    private static String snapshotListMessage(List<SnapshotView> snapshots) {
        List<String> entries = snapshots.stream()
            .map(snapshot -> "#" + snapshot.snapshotNo()
                + (snapshot.reason().isBlank() ? "" : " 사유=" + snapshot.reason())
                + (snapshot.sizeBytes() <= 0L ? "" : " 크기=" + snapshot.sizeBytes())
                + (snapshot.checksum().isBlank() ? "" : " checksum=" + shortChecksum(snapshot.checksum())))
            .toList();
        return entries.isEmpty() ? "섬 스냅샷이 없습니다." : "섬 스냅샷: " + String.join(", ", entries);
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

    private static int integer(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static String shortChecksum(String checksum) {
        if (checksum == null || checksum.isBlank()) {
            return "";
        }
        if (checksum.length() <= 12) {
            return checksum;
        }
        return new StringBuilder(12).append(checksum, 0, 12).toString();
    }

    interface Runtime {
        Optional<UUID> currentIsland(Player player, String missingMessage);

        void message(Player player, String message);

        String routeMessage(String key, String fallback);

        <T> CompletableFuture<T> mutate(String auditAction, Supplier<CompletableFuture<T>> operation);

        <T> CompletableFuture<T> mutateIdempotent(String auditAction, Supplier<CompletableFuture<T>> operation);

        MessageRenderer messagesFor(Player player);

        void openConfirmation(Player player, String title, String description, Material material, String confirmName, String confirmAction, Map<String, String> data, String confirmLore, String cancelAction);

        boolean confirmationAccepted(Player player, GuiAction action, GuiClick click);
    }
}
