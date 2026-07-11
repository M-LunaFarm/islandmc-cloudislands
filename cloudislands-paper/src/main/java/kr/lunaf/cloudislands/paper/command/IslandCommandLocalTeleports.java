package kr.lunaf.cloudislands.paper.command;

import java.util.Optional;
import java.util.UUID;
import kr.lunaf.cloudislands.common.failure.CoreApiDegradedModePolicy;
import kr.lunaf.cloudislands.common.protection.IslandRegion;
import kr.lunaf.cloudislands.paper.ProtectionController;
import kr.lunaf.cloudislands.paper.platform.player.PaperPlayerGateway;
import kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers;
import kr.lunaf.cloudislands.paper.platform.world.PaperWorldGateway;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

final class IslandCommandLocalTeleports {
    private final Plugin plugin;
    private final ProtectionController protection;
    private final PaperPlayerGateway players;
    private final PaperWorldGateway worlds;
    private final IslandCommandMessenger messages;

    IslandCommandLocalTeleports(
        Plugin plugin,
        ProtectionController protection,
        PaperPlayerGateway players,
        PaperWorldGateway worlds,
        IslandCommandMessenger messages
    ) {
        this.plugin = plugin;
        this.protection = protection;
        this.players = players;
        this.worlds = worlds;
        this.messages = messages;
    }

    void moveToPoint(Player player, UUID islandId, IslandHomeWarpCommandHandler.Point point, String missingMessage, String successMessage) {
        PaperSchedulers.run(plugin, () -> {
            if (point == null) {
                player.sendMessage(missingMessage);
                return;
            }
            if (point.worldName().isBlank()) {
                messages.message(player, messages.routeMessage("route-target-world-missing", "대상 월드를 찾을 수 없습니다."));
                return;
            }
            World world = worlds.world(point.worldName());
            if (world == null) {
                messages.message(player, messages.routeMessage("route-target-world-missing", "대상 월드를 찾을 수 없습니다."));
                return;
            }
            Optional<IslandRegion> region = protection.region(islandId);
            if (region.isEmpty()) {
                messages.message(player, messages.routeMessage("route-target-region-missing", "대상 섬의 로컬 영역을 찾을 수 없습니다."));
                return;
            }
            double targetX = region.map(value -> value.originX() + point.x()).orElse(point.x());
            double targetZ = region.map(value -> value.originZ() + point.z()).orElse(point.z());
            Location requested = new Location(world, targetX, point.y(), targetZ, point.yaw(), point.pitch());
            worlds.safeDestination(requested, region.get()).whenComplete((destination, error) -> PaperSchedulers.run(plugin, () -> {
                if (error != null || destination == null || destination.isEmpty()) {
                    messages.message(player, messages.routeMessage("route-target-unsafe", "안전한 이동 위치를 찾을 수 없습니다."));
                    return;
                }
                if (players.teleport(player, destination.get())) {
                    player.sendMessage(successMessage);
                } else {
                    messages.message(player, messages.routeMessage("route-teleport-rejected", "서버가 순간이동을 허용하지 않았습니다."));
                }
            }));
        });
    }

    boolean teleportLocalDefaultHome(Player player) {
        Optional<IslandRegion> region = protection.regionAt(player.getLocation().getBlock());
        if (region.isEmpty()) {
            return false;
        }
        IslandRegion current = region.get();
        moveToPoint(
            player,
            current.islandId(),
            new IslandHomeWarpCommandHandler.Point(current.world(), 0.5D, 100.0D, 0.5D, 180.0F, 0.0F, false),
            messages.routeMessage("route-target-world-missing", "대상 월드를 찾을 수 없습니다."),
            messages.routeMessage("core-service-home-fallback", CoreApiDegradedModePolicy.HOME_FALLBACK_MESSAGE)
        );
        return true;
    }
}
