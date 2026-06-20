package kr.lunaf.cloudislands.paper.command;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import kr.lunaf.cloudislands.api.model.IslandLocation;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
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
    private final Runtime runtime;

    IslandHomeWarpCommandHandler(Plugin plugin, CoreApiClient coreApiClient, Runtime runtime) {
        this.plugin = plugin;
        this.coreApiClient = coreApiClient;
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

    boolean handleGuiAction(Player player, String actionId, Map<String, String> data, GuiClick click) {
        return switch (actionId) {
            case "island.home" -> {
                if (click.right()) {
                    openHomeMenu(player);
                } else {
                    teleportHome(player, data.getOrDefault("homeName", "default"));
                }
                yield true;
            }
            case "island.homes.open" -> {
                openHomeMenu(player);
                yield true;
            }
            case "island.home.set" -> {
                setHome(player, data.getOrDefault("homeName", "default"));
                yield true;
            }
            case "island.warps.open" -> {
                openWarpMenu(player);
                yield true;
            }
            case "island.warp.teleport" -> {
                String islandId = data.getOrDefault("islandId", "");
                String warpName = data.getOrDefault("warpName", "default");
                UUID uuid = uuid(islandId);
                if (uuid != null) {
                    runtime.routeWarp(player, uuid, warpName);
                } else {
                    teleportWarp(player, warpName);
                }
                yield true;
            }
            case "island.warp.delete.prepare" -> {
                runtime.openConfirmation(player,
                    runtime.routeMessage("warp-delete-confirm-title", "워프 삭제 확인"),
                    runtime.routeMessage("warp-delete-confirm-description", "워프를 삭제하면 해당 이름으로 이동할 수 없습니다."),
                    Material.ENDER_PEARL,
                    runtime.routeMessage("warp-delete-confirm-name", "워프 삭제"),
                    "island.warp.delete.confirm",
                    Map.of("warpName", data.getOrDefault("warpName", "default")),
                    runtime.routeMessage("warp-delete-confirm-lore", "클릭하면 Core에 워프 삭제를 요청합니다."),
                    "island.warps.open");
                yield true;
            }
            case "island.warp.delete.confirm" -> {
                if (runtime.confirmationAccepted(player, "island.warp.delete.confirm", data, click)) {
                    deleteWarp(player, data.getOrDefault("warpName", "default"));
                }
                yield true;
            }
            case "island.warp.public" -> {
                setWarpPublicAccess(player, data.getOrDefault("warpName", "default"), true);
                yield true;
            }
            case "island.warp.private" -> {
                setWarpPublicAccess(player, data.getOrDefault("warpName", "default"), false);
                yield true;
            }
            case "island.warp.public.toggle" -> {
                setWarpPublicAccess(player, data.getOrDefault("warpName", "default"), !Boolean.parseBoolean(data.getOrDefault("publicAccess", "false")));
                yield true;
            }
            default -> false;
        };
    }

    private void setHome(Player player, String name) {
        runtime.currentIsland(player, "섬 안에서만 홈을 설정할 수 있습니다.").ifPresent(islandId -> {
            if (!runtime.allowed(player, IslandPermission.SET_HOME)) {
                runtime.message(player, runtime.routeMessage("home-set-denied", "섬 홈을 설정할 권한이 없습니다."));
                return;
            }
            runtime.mutate("island.home.set", () -> coreApiClient.setIslandHomeResult(islandId, player.getUniqueId(), name, runtime.location(player.getLocation())))
                .thenAccept(body -> runtime.message(player, runtime.actionResultMessage("섬 홈 설정 " + name, name, body)))
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
            runtime.mutate("island.warp.set", () -> coreApiClient.setIslandWarpResult(islandId, player.getUniqueId(), name, runtime.location(player.getLocation()), false))
                .thenAccept(body -> runtime.message(player, runtime.actionResultMessage("섬 워프 설정 " + name, name, body)))
                .exceptionally(error -> {
                    runtime.message(player, "섬 워프를 설정하지 못했습니다.");
                    return null;
                });
        });
    }

    private void listHomes(Player player) {
        runtime.currentIsland(player, "섬 안에서만 홈 목록을 볼 수 있습니다.").ifPresent(islandId -> {
            coreApiClient.listIslandHomes(islandId)
                .thenAccept(body -> runtime.message(player, runtime.pointListMessage(body, "섬 홈", "섬 홈이 없습니다.")))
                .exceptionally(error -> {
                    runtime.message(player, "섬 홈을 불러오지 못했습니다.");
                    return null;
                });
        });
    }

    private void listWarps(Player player) {
        runtime.currentIsland(player, "섬 안에서만 워프 목록을 볼 수 있습니다.").ifPresent(islandId -> {
            coreApiClient.listIslandWarps(islandId)
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
            coreApiClient.listIslandHomes(islandId)
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
            coreApiClient.listIslandWarps(islandId)
                .thenAccept(body -> {
                    Point point = runtime.point(body, name, player.getWorld().getName());
                    if (point == null) {
                        runtime.moveToPoint(player, null, "워프를 찾을 수 없습니다.", "섬 워프로 이동했습니다.");
                        return;
                    }
                    coreApiClient.islandInfo(islandId).thenAccept(info -> {
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
            runtime.mutateIdempotent("island.warp.delete", () -> coreApiClient.deleteIslandWarpResult(islandId, player.getUniqueId(), name))
                .thenAccept(body -> runtime.message(player, runtime.actionResultMessage("섬 워프 삭제 " + name, name, body)))
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
            runtime.mutate("island.warp.public-access.set", () -> coreApiClient.setIslandWarpPublicAccessResult(islandId, player.getUniqueId(), name, publicAccess))
                .thenAccept(body -> runtime.message(player, runtime.actionResultMessage(publicAccess ? "섬 워프 공개 " + name : "섬 워프 비공개 " + name, name, body)))
                .exceptionally(error -> {
                    runtime.message(player, "섬 워프 공개 상태를 변경하지 못했습니다.");
                    return null;
                });
        });
    }

    private void listPublicWarps(Player player, String category, String query) {
        coreApiClient.listPublicWarps(20, category, query)
            .thenAccept(body -> runtime.message(player, publicWarpListMessage(body, category, query)))
            .exceptionally(error -> {
                runtime.message(player, "공개 워프 목록을 불러오지 못했습니다.");
                return null;
            });
    }

    private static String publicWarpListMessage(String body, String category, String query) {
        if (body == null || body.isBlank()) {
            return "공개 워프가 없습니다.";
        }
        StringBuilder message = new StringBuilder();
        int index = body.indexOf("\"warps\"");
        int count = 0;
        while (index >= 0 && index < body.length() && count < 20) {
            int objectStart = body.indexOf('{', index);
            if (objectStart < 0) {
                break;
            }
            int objectEnd = body.indexOf('}', objectStart);
            if (objectEnd < 0) {
                break;
            }
            String object = body.substring(objectStart, objectEnd + 1);
            String name = text(object, "name");
            String islandId = text(object, "islandId");
            if (!name.isBlank() && !islandId.isBlank()) {
                String warpCategory = text(object, "category");
                if (message.length() > 0) {
                    message.append(" | ");
                }
                message.append(++count)
                    .append(". ")
                    .append(name)
                    .append(" (섬=")
                    .append(compactId(islandId))
                    .append(", 카테고리=")
                    .append(warpCategory.isBlank() ? "default" : warpCategory)
                    .append(')');
            }
            index = objectEnd + 1;
        }
        String suffix = (category == null || category.isBlank() ? "" : " category=" + category)
            + (query == null || query.isBlank() ? "" : " query=" + query);
        return message.length() == 0 ? "공개 워프가 없습니다." + suffix : "공개 워프" + suffix + ": " + message;
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
        return value == null || value.length() <= 8 ? String.valueOf(value) : value.substring(0, 8);
    }

    private static String text(String json, String key) {
        if (json == null || key == null) {
            return "";
        }
        String pattern = "\"" + key + "\":\"";
        int start = json.indexOf(pattern);
        if (start < 0) {
            return "";
        }
        int valueStart = start + pattern.length();
        int valueEnd = jsonStringEnd(json, valueStart);
        return valueEnd < 0 ? "" : unescape(json.substring(valueStart, valueEnd));
    }

    private static int jsonStringEnd(String value, int start) {
        boolean escaping = false;
        for (int index = start; index < value.length(); index++) {
            char current = value.charAt(index);
            if (!escaping && current == '"') {
                return index;
            }
            escaping = !escaping && current == '\\';
        }
        return -1;
    }

    private static String unescape(String value) {
        StringBuilder builder = new StringBuilder(value.length());
        boolean escaping = false;
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (!escaping) {
                if (current == '\\') {
                    escaping = true;
                } else {
                    builder.append(current);
                }
                continue;
            }
            switch (current) {
                case '"' -> builder.append('"');
                case '\\' -> builder.append('\\');
                case '/' -> builder.append('/');
                case 'b' -> builder.append('\b');
                case 'f' -> builder.append('\f');
                case 'n' -> builder.append('\n');
                case 'r' -> builder.append('\r');
                case 't' -> builder.append('\t');
                default -> builder.append(current);
            }
            escaping = false;
        }
        if (escaping) {
            builder.append('\\');
        }
        return builder.toString();
    }

    record Point(String worldName, double x, double y, double z, float yaw, float pitch, boolean publicAccess) {}

    interface Runtime {
        Optional<UUID> currentIsland(Player player, String missingMessage);

        boolean allowed(Player player, IslandPermission permission);

        void message(Player player, String message);

        String routeMessage(String key, String fallback);

        String actionResultMessage(String label, String targetId, String body);

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

        boolean confirmationAccepted(Player player, String actionId, Map<String, String> data, GuiClick click);
    }
}
