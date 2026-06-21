package kr.lunaf.cloudislands.paper.command;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import kr.lunaf.cloudislands.api.model.IslandLocation;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.paper.application.IslandHomeWarpUseCase;
import kr.lunaf.cloudislands.paper.application.IslandHomeWarpUseCase.HomeWarpActionResult;
import kr.lunaf.cloudislands.paper.application.view.PaperGuiViews.WarpView;
import kr.lunaf.cloudislands.paper.gui.GuiAction;
import kr.lunaf.cloudislands.paper.gui.GuiClick;
import kr.lunaf.cloudislands.paper.gui.IslandHomeMenu;
import kr.lunaf.cloudislands.paper.gui.IslandWarpMenu;
import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

final class IslandHomeWarpCommandHandler {
    private final Plugin plugin;
    private final CoreApiClient coreApiClient;
    private final IslandHomeWarpUseCase homeWarpUseCase;
    private final Runtime runtime;

    IslandHomeWarpCommandHandler(Plugin plugin, CoreApiClient coreApiClient, Runtime runtime) {
        this.plugin = plugin;
        this.coreApiClient = coreApiClient;
        this.homeWarpUseCase = new IslandHomeWarpUseCase(coreApiClient);
        this.runtime = runtime;
    }

    boolean handleCommand(Player player, String subcommand, String[] args) {
        if (subcommand.equals("sethome") || subcommand.equals("셋홈")) {
            setHome(player, args.length > 1 ? args[1] : "default");
            return true;
        }
        if (subcommand.equals("homes") || subcommand.equals("home-menu") || subcommand.equals("홈관리")) {
            openHomeMenu(player);
            return true;
        }
        if (subcommand.equals("home-list") || subcommand.equals("홈목록")) {
            listHomes(player);
            return true;
        }
        if (subcommand.equals("home") || subcommand.equals("홈")) {
            teleportHome(player, args.length > 1 ? args[1] : "default");
            return true;
        }
        if (subcommand.equals("warps") || subcommand.equals("warp-menu") || subcommand.equals("워프") || subcommand.equals("워프관리")) {
            if (args.length > 2) {
                UUID islandId = uuid(args[1]);
                if (islandId != null) {
                    runtime.routeWarp(player, islandId, args[2]);
                    return true;
                }
            }
            if (args.length == 2) {
                UUID islandId = uuid(args[1]);
                if (islandId != null) {
                    runtime.routeWarp(player, islandId, "default");
                    return true;
                }
            }
            if (args.length > 1) {
                teleportWarp(player, args[1]);
            } else {
                openWarpMenu(player);
            }
            return true;
        }
        if (subcommand.equals("warp-list") || subcommand.equals("워프목록")) {
            listWarps(player);
            return true;
        }
        if (subcommand.equals("public-warps") || subcommand.equals("publicwarplist") || subcommand.equals("공개워프목록")) {
            if (args.length > 1) {
                listPublicWarps(player, args[1], args.length > 2 ? joined(args, 2) : "");
            } else {
                IslandWarpMenu.openPublic(plugin, coreApiClient, player, runtime.messagesFor(player));
            }
            return true;
        }
        if (subcommand.equals("warp")) {
            if (args.length > 2) {
                UUID islandId = uuid(args[1]);
                if (islandId == null) {
                    runtime.message(player, runtime.routeMessage("input-island-uuid-invalid", "섬 UUID가 올바르지 않습니다."));
                    return true;
                }
                runtime.routeWarp(player, islandId, args[2]);
                return true;
            }
            if (args.length == 2) {
                UUID islandId = uuid(args[1]);
                if (islandId != null) {
                    runtime.routeWarp(player, islandId, "default");
                    return true;
                }
            }
            if (args.length < 2) {
                runtime.message(player, runtime.routeMessage("input-warp-name-required", "워프 이름을 입력해주세요."));
                return true;
            }
            teleportWarp(player, args[1]);
            return true;
        }
        if (subcommand.equals("setwarp") || subcommand.equals("워프설정")) {
            if (args.length < 2) {
                runtime.message(player, runtime.routeMessage("input-warp-name-required", "워프 이름을 입력해주세요."));
                return true;
            }
            setWarp(player, args[1]);
            return true;
        }
        if (subcommand.equals("delwarp") || subcommand.equals("deletewarp") || subcommand.equals("워프삭제")) {
            if (args.length < 2) {
                runtime.message(player, runtime.routeMessage("input-warp-name-required", "워프 이름을 입력해주세요."));
                return true;
            }
            deleteWarp(player, args[1]);
            return true;
        }
        if (subcommand.equals("warp-public") || subcommand.equals("publicwarp") || subcommand.equals("워프공개")) {
            if (args.length < 2) {
                runtime.message(player, runtime.routeMessage("input-warp-name-required", "워프 이름을 입력해주세요."));
                return true;
            }
            setWarpPublicAccess(player, args[1], true);
            return true;
        }
        if (subcommand.equals("warp-private") || subcommand.equals("privatewarp") || subcommand.equals("워프비공개")) {
            if (args.length < 2) {
                runtime.message(player, runtime.routeMessage("input-warp-name-required", "워프 이름을 입력해주세요."));
                return true;
            }
            setWarpPublicAccess(player, args[1], false);
            return true;
        }
        return false;
    }

    boolean handleGuiAction(Player player, GuiAction action, GuiClick click) {
        if (action instanceof GuiAction.HomeTeleport homeTeleport) {
            if (click.right()) {
                openHomeMenu(player);
            } else {
                teleportHome(player, homeTeleport.homeName());
            }
            return true;
        }
        if (action instanceof GuiAction.HomeSet homeSet) {
            setHome(player, homeSet.homeName());
            return true;
        }
        if (action instanceof GuiAction.WarpTeleport warpTeleport) {
            if (warpTeleport.islandId() != null) {
                runtime.routeWarp(player, warpTeleport.islandId(), warpTeleport.warpName());
            } else {
                teleportWarp(player, warpTeleport.warpName());
            }
            return true;
        }
        if (action instanceof GuiAction.WarpDelete warpDelete) {
            if (!warpDelete.confirmation()) {
                runtime.openConfirmation(player,
                    runtime.routeMessage("warp-delete-confirm-title", "워프 삭제 확인"),
                    runtime.routeMessage("warp-delete-confirm-description", "워프를 삭제하면 해당 이름으로 이동할 수 없습니다."),
                    Material.ENDER_PEARL,
                    runtime.routeMessage("warp-delete-confirm-name", "워프 삭제"),
                    "island.warp.delete.confirm",
                    Map.of("warpName", warpDelete.warpName()),
                    runtime.routeMessage("warp-delete-confirm-lore", "클릭하면 Core에 워프 삭제를 요청합니다."),
                    "island.warps.open");
                return true;
            }
            if (runtime.confirmationAccepted(player, action, click)) {
                deleteWarp(player, warpDelete.warpName());
            }
            return true;
        }
        if (action instanceof GuiAction.WarpAccess warpAccess) {
            setWarpPublicAccess(player, warpAccess.warpName(), warpAccess.targetPublicAccess());
            return true;
        }
        if (action instanceof GuiAction.NoPayload noPayload) {
            return switch (noPayload.type()) {
                case HOMES_OPEN -> {
                    openHomeMenu(player);
                    yield true;
                }
                case WARPS_OPEN -> {
                    openWarpMenu(player);
                    yield true;
                }
                default -> false;
            };
        }
        return false;
    }

    private void setHome(Player player, String name) {
        runtime.currentIsland(player, "섬 안에서만 홈을 설정할 수 있습니다.").ifPresent(islandId -> {
            if (!runtime.allowed(player, IslandPermission.SET_HOME)) {
                runtime.message(player, runtime.routeMessage("home-set-denied", "섬 홈을 설정할 권한이 없습니다."));
                return;
            }
            homeWarpUseCase.setHomeAction(islandId, player.getUniqueId(), name, runtime.location(player.getLocation()), runtime::mutate)
                .thenAccept(result -> runtime.message(player, homeWarpActionMessage("섬 홈 설정 " + name, name, result)))
                .exceptionally(error -> {
                    runtime.message(player, "섬 홈을 설정하지 못했습니다.");
                    return null;
                });
        });
    }

    private void setWarp(Player player, String name) {
        runtime.currentIsland(player, "섬 안에서만 워프를 설정할 수 있습니다.").ifPresent(islandId -> {
            if (!runtime.allowed(player, IslandPermission.MANAGE_WARPS)) {
                runtime.message(player, runtime.routeMessage("warp-set-denied", "섬 워프를 설정할 권한이 없습니다."));
                return;
            }
            homeWarpUseCase.setWarpAction(islandId, player.getUniqueId(), name, runtime.location(player.getLocation()), false, runtime::mutate)
                .thenAccept(result -> runtime.message(player, homeWarpActionMessage("섬 워프 설정 " + name, name, result)))
                .exceptionally(error -> {
                    runtime.message(player, "섬 워프를 설정하지 못했습니다.");
                    return null;
                });
        });
    }

    private void listHomes(Player player) {
        runtime.currentIsland(player, "섬 안에서만 홈 목록을 볼 수 있습니다.").ifPresent(islandId -> {
            homeWarpUseCase.listHomes(islandId)
                .thenAccept(body -> runtime.message(player, runtime.pointListMessage(body, "섬 홈", "섬 홈이 없습니다.")))
                .exceptionally(error -> {
                    runtime.message(player, "섬 홈을 불러오지 못했습니다.");
                    return null;
                });
        });
    }

    private void listWarps(Player player) {
        runtime.currentIsland(player, "섬 안에서만 워프 목록을 볼 수 있습니다.").ifPresent(islandId -> {
            homeWarpUseCase.listWarps(islandId)
                .thenAccept(body -> runtime.message(player, runtime.pointListMessage(body, "섬 워프", "섬 워프가 없습니다.")))
                .exceptionally(error -> {
                    runtime.message(player, "섬 워프를 불러오지 못했습니다.");
                    return null;
                });
        });
    }

    private void openHomeMenu(Player player) {
        runtime.currentIsland(player, "섬 안에서만 홈 메뉴를 열 수 있습니다.").ifPresent(islandId -> IslandHomeMenu.open(plugin, coreApiClient, player, islandId, runtime.messagesFor(player)));
    }

    private void openWarpMenu(Player player) {
        runtime.currentIsland(player, "섬 안에서만 워프 메뉴를 열 수 있습니다.").ifPresent(islandId -> IslandWarpMenu.open(plugin, coreApiClient, player, islandId, runtime.messagesFor(player)));
    }

    private void teleportHome(Player player, String name) {
        runtime.currentIsland(player, "섬 안에서만 홈으로 이동할 수 있습니다.").ifPresent(islandId -> {
            if (!runtime.allowed(player, IslandPermission.INTERACT)) {
                runtime.message(player, runtime.routeMessage("home-teleport-denied", "섬 홈으로 이동할 권한이 없습니다."));
                return;
            }
            homeWarpUseCase.listHomes(islandId)
                .thenAccept(body -> runtime.moveToPoint(player, runtime.point(body, name, player.getWorld().getName()), "홈을 찾을 수 없습니다.", "섬 홈으로 이동했습니다."))
                .exceptionally(error -> {
                    if (runtime.coreUnavailable(error) && runtime.teleportLocalDefaultHome(player)) {
                        return null;
                    }
                    runtime.message(player, "섬 홈을 불러오지 못했습니다.");
                    return null;
                });
        });
    }

    private void teleportWarp(Player player, String name) {
        runtime.currentIsland(player, "섬 안에서만 워프로 이동할 수 있습니다.").ifPresent(islandId -> {
            homeWarpUseCase.listWarps(islandId)
                .thenAccept(body -> {
                    Point point = runtime.point(body, name, player.getWorld().getName());
                    if (point == null) {
                        runtime.moveToPoint(player, null, "워프를 찾을 수 없습니다.", "섬 워프로 이동했습니다.");
                        return;
                    }
                    homeWarpUseCase.islandInfo(islandId).thenAccept(info -> {
                        if (!runtime.publicWarpAllowed(player, point, info) && !runtime.allowed(player, IslandPermission.INTERACT)) {
                            runtime.message(player, runtime.routeMessage("warp-teleport-denied", "섬 워프로 이동할 권한이 없습니다."));
                            return;
                        }
                        runtime.moveToPoint(player, point, "워프를 찾을 수 없습니다.", "섬 워프로 이동했습니다.");
                    }).exceptionally(error -> {
                        runtime.message(player, "섬 정보를 불러오지 못했습니다.");
                        return null;
                    });
                })
                .exceptionally(error -> {
                    runtime.message(player, "섬 워프를 불러오지 못했습니다.");
                    return null;
                });
        });
    }

    private void deleteWarp(Player player, String name) {
        runtime.currentIsland(player, "섬 안에서만 워프를 삭제할 수 있습니다.").ifPresent(islandId -> {
            if (!runtime.allowed(player, IslandPermission.MANAGE_WARPS)) {
                runtime.message(player, runtime.routeMessage("warp-delete-denied", "섬 워프를 삭제할 권한이 없습니다."));
                return;
            }
            homeWarpUseCase.deleteWarpAction(islandId, player.getUniqueId(), name, runtime::mutateIdempotent)
                .thenAccept(result -> runtime.message(player, homeWarpActionMessage("섬 워프 삭제 " + name, name, result)))
                .exceptionally(error -> {
                    runtime.message(player, "섬 워프를 삭제하지 못했습니다.");
                    return null;
                });
        });
    }

    private void setWarpPublicAccess(Player player, String name, boolean publicAccess) {
        runtime.currentIsland(player, "섬 안에서만 워프 공개 상태를 변경할 수 있습니다.").ifPresent(islandId -> {
            if (!runtime.allowed(player, IslandPermission.MANAGE_WARPS)) {
                runtime.message(player, runtime.routeMessage("warp-access-denied", "섬 워프 공개 상태를 변경할 권한이 없습니다."));
                return;
            }
            homeWarpUseCase.setWarpPublicAccessAction(islandId, player.getUniqueId(), name, publicAccess, runtime::mutate)
                .thenAccept(result -> runtime.message(player, homeWarpActionMessage(publicAccess ? "섬 워프 공개 " + name : "섬 워프 비공개 " + name, name, result)))
                .exceptionally(error -> {
                    runtime.message(player, "섬 워프 공개 상태를 변경하지 못했습니다.");
                    return null;
                });
        });
    }

    private void listPublicWarps(Player player, String category, String query) {
        homeWarpUseCase.publicWarpViews(20, category, query)
            .thenAccept(warps -> runtime.message(player, publicWarpListMessage(warps, category, query)))
            .exceptionally(error -> {
                runtime.message(player, "공개 워프 목록을 불러오지 못했습니다.");
                return null;
            });
    }

    private static String publicWarpListMessage(java.util.List<WarpView> warps, String category, String query) {
        StringBuilder message = new StringBuilder();
        int count = 0;
        for (WarpView warp : warps == null ? java.util.List.<WarpView>of() : warps) {
            if (count >= 20) {
                break;
            }
            if (warp.name().isBlank() || warp.islandId().isBlank()) {
                continue;
            }
            if (message.length() > 0) {
                message.append(" | ");
            }
            message.append(++count)
                .append(". ")
                .append(warp.name())
                .append(" (섬=")
                .append(compactId(warp.islandId()))
                .append(", 카테고리=")
                .append(warp.category().isBlank() ? "default" : warp.category())
                .append(')');
        }
        String suffix = (category == null || category.isBlank() ? "" : " category=" + category)
            + (query == null || query.isBlank() ? "" : " query=" + query);
        return message.length() == 0 ? "공개 워프가 없습니다." + suffix : "공개 워프" + suffix + ": " + message;
    }

    private static String homeWarpActionMessage(String label, String targetId, HomeWarpActionResult result) {
        StringBuilder builder = new StringBuilder(label)
            .append(result.accepted() ? " 완료" : " 실패");
        if (targetId != null && !targetId.isBlank()) {
            builder.append(": 대상=").append(targetId);
        }
        if (!result.accepted() && !result.code().isBlank()) {
            builder.append(" 사유=").append(result.code());
        }
        return builder.toString();
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

    private static UUID uuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static String compactId(String value) {
        if (value == null || value.length() <= 8) {
            return String.valueOf(value);
        }
        return new StringBuilder(8).append(value, 0, 8).toString();
    }

    record Point(String worldName, double x, double y, double z, float yaw, float pitch, boolean publicAccess) {}

    interface Runtime {
        Optional<UUID> currentIsland(Player player, String missingMessage);

        boolean allowed(Player player, IslandPermission permission);

        void message(Player player, String message);

        String routeMessage(String key, String fallback);

        String coreWriteFailureMessage(Throwable error, String fallback);

        <T> CompletableFuture<T> mutate(String auditAction, Supplier<CompletableFuture<T>> operation);

        <T> CompletableFuture<T> mutateIdempotent(String auditAction, Supplier<CompletableFuture<T>> operation);

        MessageRenderer messagesFor(Player player);

        IslandLocation location(Location location);

        String pointListMessage(String body, String label, String emptyMessage);

        Point point(String body, String requestedName, String fallbackWorldName);

        void moveToPoint(Player player, Point point, String missingMessage, String successMessage);

        boolean teleportLocalDefaultHome(Player player);

        boolean coreUnavailable(Throwable error);

        boolean publicWarpAllowed(Player player, Point point, String islandInfo);

        void routeWarp(Player player, UUID islandId, String warpName);

        void openConfirmation(Player player, String title, String description, Material material, String confirmName, String confirmAction, Map<String, String> data, String confirmLore, String cancelAction);

        boolean confirmationAccepted(Player player, GuiAction action, GuiClick click);
    }
}
