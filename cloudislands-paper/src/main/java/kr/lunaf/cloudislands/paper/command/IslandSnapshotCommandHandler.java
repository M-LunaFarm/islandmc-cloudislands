package kr.lunaf.cloudislands.paper.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
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
    private final Runtime runtime;

    IslandSnapshotCommandHandler(Plugin plugin, CoreApiClient coreApiClient, Runtime runtime) {
        this.plugin = plugin;
        this.coreApiClient = coreApiClient;
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
            restoreSnapshot(player, longValue(args[1], 0L));
            return true;
        }
        return false;
    }

    boolean handleGuiAction(Player player, GuiAction action, GuiClick click) {
        String actionId = action.actionId();
        Map<String, String> data = action.data();
        return switch (actionId) {
            case "island.snapshots.open" -> {
                openSnapshotMenu(player);
                yield true;
            }
            case "island.snapshots.list" -> {
                listSnapshots(player, 10);
                yield true;
            }
            case "island.snapshot.create" -> {
                requestSnapshot(player, data.getOrDefault("reason", "manual"));
                yield true;
            }
            case "island.snapshot.restore.prepare" -> {
                runtime.openConfirmation(
                    player,
                    runtime.routeMessage("snapshot-restore-confirm-title", "스냅샷 복원 확인"),
                    runtime.routeMessage("snapshot-restore-confirm-description", "현재 월드 상태를 선택한 스냅샷으로 복원합니다."),
                    Material.CHEST,
                    runtime.routeMessage("snapshot-restore-confirm-name", "스냅샷 복원"),
                    "island.snapshot.restore.confirm",
                    Map.of("snapshotNo", data.getOrDefault("snapshotNo", "0")),
                    runtime.routeMessage("snapshot-restore-confirm-lore", "클릭하면 Core에 스냅샷 복원을 요청합니다."),
                    "island.snapshots.open"
                );
                yield true;
            }
            case "island.snapshot.restore.confirm" -> {
                if (runtime.confirmationAccepted(player, "island.snapshot.restore.confirm", data, click)) {
                    restoreSnapshot(player, longValue(data.getOrDefault("snapshotNo", "0"), 0L));
                }
                yield true;
            }
            default -> false;
        };
    }

    private void listSnapshots(Player player, int limit) {
        runtime.currentIsland(player, "섬 안에서만 스냅샷을 확인할 수 있습니다.").ifPresent(islandId -> {
            coreApiClient.listIslandSnapshots(islandId, Math.max(1, Math.min(limit, 20)))
                .thenAccept(body -> runtime.message(player, snapshotListMessage(body)))
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
            runtime.mutate("island.snapshot.create", () -> coreApiClient.requestIslandSnapshotResult(islandId, reason))
                .thenAccept(body -> runtime.message(player, runtime.actionResultMessage("섬 스냅샷 생성 요청", islandId, body)))
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
            runtime.mutateIdempotent("island.snapshot.restore", () -> coreApiClient.restoreIslandSnapshotResult(islandId, snapshotNo))
                .thenAccept(body -> runtime.message(player, runtime.actionResultMessage("섬 스냅샷 복원 요청 #" + snapshotNo, islandId, body)))
                .exceptionally(error -> {
                    runtime.message(player, "섬 스냅샷 복원을 요청하지 못했습니다.");
                    return null;
                });
        });
    }

    private static String snapshotListMessage(String body) {
        List<String> entries = new ArrayList<>();
        int index = 0;
        while (body != null && index < body.length()) {
            int objectStart = body.indexOf('{', index);
            if (objectStart < 0) {
                break;
            }
            int objectEnd = body.indexOf('}', objectStart);
            if (objectEnd < 0) {
                break;
            }
            String object = body.substring(objectStart, objectEnd + 1);
            long snapshotNo = (long) decimal(object, "snapshotNo");
            if (snapshotNo > 0L) {
                String reason = text(object, "reason");
                String checksum = text(object, "checksum");
                long sizeBytes = (long) decimal(object, "sizeBytes");
                entries.add("#" + snapshotNo
                    + (reason.isBlank() ? "" : " 사유=" + reason)
                    + (sizeBytes <= 0L ? "" : " 크기=" + sizeBytes)
                    + (checksum.isBlank() ? "" : " checksum=" + shortChecksum(checksum)));
            }
            index = objectEnd + 1;
        }
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

    private static long longValue(String value, long fallback) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static String shortChecksum(String checksum) {
        if (checksum == null || checksum.isBlank()) {
            return "";
        }
        return checksum.length() > 12 ? checksum.substring(0, 12) : checksum;
    }

    private static String text(String json, String key) {
        String marker = "\"" + key + "\"";
        int keyIndex = json.indexOf(marker);
        if (keyIndex < 0) {
            return "";
        }
        int colon = json.indexOf(':', keyIndex + marker.length());
        if (colon < 0) {
            return "";
        }
        int start = json.indexOf('"', colon + 1);
        if (start < 0) {
            return "";
        }
        int end = json.indexOf('"', start + 1);
        if (end < 0) {
            return "";
        }
        return json.substring(start + 1, end);
    }

    private static double decimal(String json, String key) {
        String marker = "\"" + key + "\"";
        int keyIndex = json.indexOf(marker);
        if (keyIndex < 0) {
            return 0.0d;
        }
        int colon = json.indexOf(':', keyIndex + marker.length());
        if (colon < 0) {
            return 0.0d;
        }
        int end = colon + 1;
        while (end < json.length() && " \t\r\n".indexOf(json.charAt(end)) >= 0) {
            end++;
        }
        int start = end;
        while (end < json.length()) {
            char current = json.charAt(end);
            if (!(Character.isDigit(current) || current == '-' || current == '.')) {
                break;
            }
            end++;
        }
        if (start == end) {
            return 0.0d;
        }
        try {
            return Double.parseDouble(json.substring(start, end));
        } catch (NumberFormatException exception) {
            return 0.0d;
        }
    }

    interface Runtime {
        Optional<UUID> currentIsland(Player player, String missingMessage);

        void message(Player player, String message);

        String routeMessage(String key, String fallback);

        <T> CompletableFuture<T> mutate(String auditAction, Supplier<CompletableFuture<T>> operation);

        <T> CompletableFuture<T> mutateIdempotent(String auditAction, Supplier<CompletableFuture<T>> operation);

        String actionResultMessage(String action, UUID id, String body);

        MessageRenderer messagesFor(Player player);

        void openConfirmation(Player player, String title, String description, Material material, String confirmName, String confirmAction, Map<String, String> data, String confirmLore, String cancelAction);

        boolean confirmationAccepted(Player player, String actionId, Map<String, String> data, GuiClick click);
    }
}
