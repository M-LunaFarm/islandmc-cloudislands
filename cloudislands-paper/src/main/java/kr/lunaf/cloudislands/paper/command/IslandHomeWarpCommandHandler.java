package kr.lunaf.cloudislands.paper.command;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;
import kr.lunaf.cloudislands.api.model.IslandLocation;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.paper.application.IslandHomeWarpUseCase;
import kr.lunaf.cloudislands.paper.application.IslandHomeWarpUseCase.HomeWarpActionResult;
import kr.lunaf.cloudislands.paper.application.view.PaperGuiViews.HomeView;
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
    private final IslandTargetResolver targetResolver;
    private final Runtime runtime;

    IslandHomeWarpCommandHandler(Plugin plugin, CoreApiClient coreApiClient, Runtime runtime) {
        this.plugin = plugin;
        this.coreApiClient = coreApiClient;
        this.homeWarpUseCase = new IslandHomeWarpUseCase(coreApiClient);
        this.targetResolver = new IslandTargetResolver(coreApiClient);
        this.runtime = runtime;
    }

    boolean handleCommand(Player player, String subcommand, String[] args) {
        if (subcommand.equals("legacy-warp")) {
            if (args.length < 2) {
                runtime.message(player, message("input-island-target-required"));
                return true;
            }
            String warpName = args.length > 2 ? args[2] : "default";
            UUID playerUuid = player.getUniqueId();
            targetResolver.resolve(args[1])
                .thenAccept(islandId -> runSync(playerUuid, activePlayer -> runtime.routeWarp(activePlayer, islandId, warpName)))
                .exceptionally(error -> {
                    deliverMessage(playerUuid, message("input-island-target-not-found"));
                    return null;
                });
            return true;
        }
        if (subcommand.equals("sethome") || subcommand.equals("setteleport") || subcommand.equals("settp") || subcommand.equals("setgo") || subcommand.equals("setspawnpoint") || subcommand.equals("셋홈")) {
            setHome(player, args.length > 1 ? args[1] : "default");
            return true;
        }
        if (subcommand.equals("delhome") || subcommand.equals("deletehome") || subcommand.equals("home-delete") || subcommand.equals("홈삭제")) {
            deleteHome(player, args.length > 1 ? args[1] : "default");
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
        if (subcommand.equals("home") || subcommand.equals("teleport") || subcommand.equals("tp") || subcommand.equals("go") || subcommand.equals("홈")) {
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
                    runtime.message(player, message("input-island-uuid-invalid"));
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
                runtime.message(player, message("input-warp-name-required"));
                return true;
            }
            teleportWarp(player, args[1]);
            return true;
        }
        if (subcommand.equals("setwarp") || subcommand.equals("워프설정")) {
            if (args.length < 2) {
                runtime.message(player, message("input-warp-name-required"));
                return true;
            }
            setWarp(player, args[1], args.length > 2 ? args[2] : "");
            return true;
        }
        if (subcommand.equals("delwarp") || subcommand.equals("deletewarp") || subcommand.equals("warp-delete") || subcommand.equals("워프삭제")) {
            if (args.length < 2) {
                runtime.message(player, message("input-warp-name-required"));
                return true;
            }
            deleteWarp(player, args[1]);
            return true;
        }
        if (subcommand.equals("warp-public") || subcommand.equals("publicwarp") || subcommand.equals("워프공개")) {
            if (args.length < 2) {
                runtime.message(player, message("input-warp-name-required"));
                return true;
            }
            setWarpPublicAccess(player, args[1], true);
            return true;
        }
        if (subcommand.equals("warp-private") || subcommand.equals("privatewarp") || subcommand.equals("워프비공개")) {
            if (args.length < 2) {
                runtime.message(player, message("input-warp-name-required"));
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
        if (action instanceof GuiAction.HomePage page) {
            IslandHomeMenu.open(plugin, coreApiClient, player, page.islandId(), runtime.messagesFor(player), page.page());
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
        if (action instanceof GuiAction.WarpPage page) {
            IslandWarpMenu.open(plugin, coreApiClient, player, page.islandId(), runtime.messagesFor(player), page.page());
            return true;
        }
        if (action instanceof GuiAction.PublicWarpCategory publicWarpCategory) {
            IslandWarpMenu.openPublic(plugin, coreApiClient, player, runtime.messagesFor(player), publicWarpCategory.category(), publicWarpCategory.query());
            return true;
        }
        if (action instanceof GuiAction.PublicWarpPage page) {
            IslandWarpMenu.openPublic(plugin, coreApiClient, player, runtime.messagesFor(player), page.category(), page.query(), page.page());
            return true;
        }
        if (action instanceof GuiAction.WarpDelete warpDelete) {
            if (!warpDelete.confirmation()) {
                runtime.openConfirmation(player,
                    message("warp-delete-confirm-title"),
                    message("warp-delete-confirm-description"),
                    IslandWarpMenu.deleteConfirmationMaterial(),
                    message("warp-delete-confirm-name"),
                    "island.warp.delete.confirm",
                    Map.of("warpName", warpDelete.warpName()),
                    message("warp-delete-confirm-lore"),
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
        UUID playerUuid = player.getUniqueId();
        IslandLocation location = runtime.location(player.getLocation());
        runtime.currentIsland(player, message("home-set-island-required")).ifPresent(islandId -> {
            if (!runtime.allowed(player, IslandPermission.SET_HOME)) {
                runtime.message(player, message("home-set-denied"));
                return;
            }
            homeWarpUseCase.setHomeAction(islandId, playerUuid, name, location, runtime::mutate)
                .thenAccept(result -> deliverMessage(playerUuid, homeWarpActionMessage("home-set-action-label", name, result)))
                .exceptionally(error -> {
                    deliverMessage(playerUuid, message("home-set-failed"));
                    return null;
                });
        });
    }

    private void setWarp(Player player, String name, String category) {
        UUID playerUuid = player.getUniqueId();
        IslandLocation location = runtime.location(player.getLocation());
        runtime.currentIsland(player, message("warp-set-island-required")).ifPresent(islandId -> {
            if (!runtime.allowed(player, IslandPermission.MANAGE_WARPS)) {
                runtime.message(player, message("warp-set-denied"));
                return;
            }
            homeWarpUseCase.setWarpAction(islandId, playerUuid, name, location, false, category, runtime::mutate)
                .thenAccept(result -> deliverMessage(playerUuid, homeWarpActionMessage("warp-set-action-label", name, result)))
                .exceptionally(error -> {
                    deliverMessage(playerUuid, message("warp-set-failed"));
                    return null;
                });
        });
    }

    private void deleteHome(Player player, String name) {
        UUID playerUuid = player.getUniqueId();
        runtime.currentIsland(player, message("home-delete-island-required")).ifPresent(islandId -> {
            if (!runtime.allowed(player, IslandPermission.SET_HOME)) {
                runtime.message(player, message("home-delete-denied"));
                return;
            }
            homeWarpUseCase.deleteHomeAction(islandId, playerUuid, name, runtime::mutateIdempotent)
                .thenAccept(result -> deliverMessage(playerUuid, homeWarpActionMessage("home-delete-action-label", name, result)))
                .exceptionally(error -> {
                    deliverMessage(playerUuid, message("home-delete-failed"));
                    return null;
                });
        });
    }

    private void listHomes(Player player) {
        UUID playerUuid = player.getUniqueId();
        runtime.currentIsland(player, message("home-list-island-required")).ifPresent(islandId -> {
            homeWarpUseCase.homeViews(islandId)
                .thenAccept(homes -> deliverMessage(playerUuid, homeListMessage(homes)))
                .exceptionally(error -> {
                    deliverMessage(playerUuid, message("home-load-failed"));
                    return null;
                });
        });
    }

    private void listWarps(Player player) {
        UUID playerUuid = player.getUniqueId();
        runtime.currentIsland(player, message("warp-list-island-required")).ifPresent(islandId -> {
            homeWarpUseCase.warpViews(islandId)
                .thenAccept(warps -> deliverMessage(playerUuid, warpListMessage(warps)))
                .exceptionally(error -> {
                    deliverMessage(playerUuid, message("warp-load-failed"));
                    return null;
                });
        });
    }

    private void openHomeMenu(Player player) {
        runtime.currentIsland(player, message("home-menu-island-required")).ifPresent(islandId -> IslandHomeMenu.open(plugin, coreApiClient, player, islandId, runtime.messagesFor(player)));
    }

    private void openWarpMenu(Player player) {
        runtime.currentIsland(player, message("warp-menu-island-required")).ifPresent(islandId -> IslandWarpMenu.open(plugin, coreApiClient, player, islandId, runtime.messagesFor(player)));
    }

    private void teleportHome(Player player, String name) {
        UUID playerUuid = player.getUniqueId();
        Optional<UUID> currentIsland = runtime.currentIsland(player);
        if (currentIsland.isEmpty()) {
            runtime.routeHome(player, name);
            return;
        }
        UUID islandId = currentIsland.get();
        if (!runtime.allowed(player, IslandPermission.INTERACT)) {
            runtime.message(player, message("home-teleport-denied"));
            return;
        }
        homeWarpUseCase.homeViews(islandId)
            .thenAccept(homes -> runSync(playerUuid, activePlayer -> runtime.moveToPoint(activePlayer, islandId, homePoint(homes, name), message("home-not-found"), message("home-teleport-success"))))
            .exceptionally(error -> {
                runSync(playerUuid, activePlayer -> {
                    if (!runtime.coreUnavailable(error) || !runtime.teleportLocalDefaultHome(activePlayer)) {
                        runtime.message(activePlayer, message("home-load-failed"));
                    }
                });
                return null;
            });
    }

    private void teleportWarp(Player player, String name) {
        UUID playerUuid = player.getUniqueId();
        runtime.currentIsland(player, message("warp-teleport-island-required")).ifPresent(islandId -> {
            homeWarpUseCase.warpViews(islandId)
                .thenCompose(warps -> {
                    Point point = warpPoint(warps, name);
                    if (point == null) {
                        return CompletableFuture.completedFuture(new WarpTeleportView(null, false, false));
                    }
                    return homeWarpUseCase.islandInfoView(islandId)
                        .handle((info, error) -> new WarpTeleportView(point, error == null && info.publicAccess(), error != null));
                })
                .thenAccept(view -> runSync(playerUuid, activePlayer -> {
                    if (view.infoLoadFailed()) {
                        runtime.message(activePlayer, message("island-info-load-failed"));
                        return;
                    }
                    if (view.point() != null && !runtime.publicWarpAllowed(activePlayer, view.point(), view.publicAccess()) && !runtime.allowed(activePlayer, IslandPermission.INTERACT)) {
                        runtime.message(activePlayer, message("warp-teleport-denied"));
                        return;
                    }
                    runtime.moveToPoint(activePlayer, islandId, view.point(), message("warp-not-found"), message("warp-teleport-success"));
                }))
                .exceptionally(error -> {
                    deliverMessage(playerUuid, message("warp-load-failed"));
                    return null;
                });
        });
    }

    private void deleteWarp(Player player, String name) {
        UUID playerUuid = player.getUniqueId();
        runtime.currentIsland(player, message("warp-delete-island-required")).ifPresent(islandId -> {
            if (!runtime.allowed(player, IslandPermission.MANAGE_WARPS)) {
                runtime.message(player, message("warp-delete-denied"));
                return;
            }
            homeWarpUseCase.deleteWarpAction(islandId, playerUuid, name, runtime::mutateIdempotent)
                .thenAccept(result -> deliverMessage(playerUuid, homeWarpActionMessage("warp-delete-action-label", name, result)))
                .exceptionally(error -> {
                    deliverMessage(playerUuid, message("warp-delete-failed"));
                    return null;
                });
        });
    }

    private void setWarpPublicAccess(Player player, String name, boolean publicAccess) {
        UUID playerUuid = player.getUniqueId();
        runtime.currentIsland(player, message("warp-access-island-required")).ifPresent(islandId -> {
            if (!runtime.allowed(player, IslandPermission.MANAGE_WARPS)) {
                runtime.message(player, message("warp-access-denied"));
                return;
            }
            homeWarpUseCase.setWarpPublicAccessAction(islandId, playerUuid, name, publicAccess, runtime::mutate)
                .thenAccept(result -> deliverMessage(playerUuid, homeWarpActionMessage(publicAccess ? "warp-public-action-label" : "warp-private-action-label", name, result)))
                .exceptionally(error -> {
                    deliverMessage(playerUuid, message("warp-access-failed"));
                    return null;
                });
        });
    }

    private void listPublicWarps(Player player, String category, String query) {
        UUID playerUuid = player.getUniqueId();
        homeWarpUseCase.publicWarpViews(20, category, query)
            .thenAccept(warps -> deliverMessage(playerUuid, publicWarpListMessage(warps, category, query)))
            .exceptionally(error -> {
                deliverMessage(playerUuid, message("public-warp-list-load-failed"));
                return null;
        });
    }

    private String homeListMessage(List<HomeView> homes) {
        StringBuilder message = new StringBuilder();
        for (HomeView home : homes == null ? List.<HomeView>of() : homes) {
            if (home.name().isBlank()) {
                continue;
            }
            if (!message.isEmpty()) {
                message.append(", ");
            }
            message.append(home.name());
        }
        return message.isEmpty() ? message("home-list-empty") : message("home-list-prefix") + message;
    }

    private String warpListMessage(List<WarpView> warps) {
        StringBuilder message = new StringBuilder();
        for (WarpView warp : warps == null ? List.<WarpView>of() : warps) {
            if (warp.name().isBlank()) {
                continue;
            }
            if (!message.isEmpty()) {
                message.append(", ");
            }
            message.append(warp.name());
        }
        return message.isEmpty() ? message("warp-list-empty") : message("warp-list-prefix") + message;
    }

    private static Point homePoint(List<HomeView> homes, String requestedName) {
        String target = normalizeName(requestedName);
        for (HomeView home : homes == null ? List.<HomeView>of() : homes) {
            if (target.equalsIgnoreCase(home.name())) {
                return new Point(home.worldName(), home.x(), home.y(), home.z(), home.yaw(), home.pitch(), false);
            }
        }
        return null;
    }

    private static Point warpPoint(List<WarpView> warps, String requestedName) {
        String target = normalizeName(requestedName);
        for (WarpView warp : warps == null ? List.<WarpView>of() : warps) {
            if (target.equalsIgnoreCase(warp.name())) {
                return new Point(warp.worldName(), warp.x(), warp.y(), warp.z(), warp.yaw(), warp.pitch(), warp.publicAccess());
            }
        }
        return null;
    }

    private static String normalizeName(String name) {
        return name == null || name.isBlank() ? "default" : name;
    }

    private String publicWarpListMessage(java.util.List<WarpView> warps, String category, String query) {
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
                .append(" (")
                .append(message("public-warp-list-island-label"))
                .append(compactId(warp.islandId()))
                .append(", ")
                .append(message("public-warp-list-category-label"))
                .append(warp.category().isBlank() ? "default" : warp.category())
                .append(')');
        }
        String suffix = (category == null || category.isBlank() ? "" : " category=" + category)
            + (query == null || query.isBlank() ? "" : " query=" + query);
        return message.length() == 0 ? message("public-warp-list-empty") + suffix : message("public-warp-list-prefix") + suffix + ": " + message;
    }

    private String homeWarpActionMessage(String labelKey, String targetId, HomeWarpActionResult result) {
        StringBuilder builder = new StringBuilder(message(labelKey))
            .append(' ')
            .append(result.accepted() ? message("home-warp-action-complete") : message("home-warp-action-failed"));
        if (targetId != null && !targetId.isBlank()) {
            builder.append(message("home-warp-action-target-prefix")).append(targetId);
        }
        if (!result.accepted() && !result.code().isBlank()) {
            builder.append(message("home-warp-action-reason-prefix")).append(result.code());
        }
        return builder.toString();
    }

    private String message(String key) {
        return runtime.routeMessage(key, key);
    }

    private void deliverMessage(UUID playerUuid, String renderedMessage) {
        runSync(playerUuid, activePlayer -> runtime.message(activePlayer, renderedMessage));
    }

    private void runSync(UUID playerUuid, Consumer<Player> task) {
        PaperOnlinePlayer.run(plugin, playerUuid, task);
    }

    private record WarpTeleportView(Point point, boolean publicAccess, boolean infoLoadFailed) {
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

        Optional<UUID> currentIsland(Player player);

        boolean allowed(Player player, IslandPermission permission);

        void message(Player player, String message);

        String routeMessage(String key, String fallback);

        String coreWriteFailureMessage(Throwable error, String fallback);

        <T> CompletableFuture<T> mutate(String auditAction, Supplier<CompletableFuture<T>> operation);

        <T> CompletableFuture<T> mutateIdempotent(String auditAction, Supplier<CompletableFuture<T>> operation);

        MessageRenderer messagesFor(Player player);

        IslandLocation location(Location location);

        void moveToPoint(Player player, UUID islandId, Point point, String missingMessage, String successMessage);

        boolean teleportLocalDefaultHome(Player player);

        boolean coreUnavailable(Throwable error);

        boolean publicWarpAllowed(Player player, Point point, boolean islandPublicAccess);

        void routeWarp(Player player, UUID islandId, String warpName);

        void routeHome(Player player, String homeName);

        void openConfirmation(Player player, String title, String description, Material material, String confirmName, String confirmAction, Map<String, String> data, String confirmLore, String cancelAction);

        boolean confirmationAccepted(Player player, GuiAction action, GuiClick click);
    }
}
