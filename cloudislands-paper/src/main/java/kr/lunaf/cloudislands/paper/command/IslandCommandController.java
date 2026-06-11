package kr.lunaf.cloudislands.paper.command;

import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandLocation;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.paper.ProtectionController;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

public final class IslandCommandController implements CommandExecutor {
    private final Plugin plugin;
    private final CoreApiClient coreApiClient;
    private final ProtectionController protection;

    public IslandCommandController(Plugin plugin, CoreApiClient coreApiClient, ProtectionController protection) {
        this.plugin = plugin;
        this.coreApiClient = coreApiClient;
        this.protection = protection;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("플레이어만 사용할 수 있습니다.");
            return true;
        }
        if (args.length == 0) {
            player.sendMessage("/" + label + " sethome [이름] 또는 /" + label + " setwarp <이름>");
            return true;
        }
        String subcommand = args[0].toLowerCase();
        if (subcommand.equals("sethome") || subcommand.equals("셋홈")) {
            setHome(player, args.length > 1 ? args[1] : "default");
            return true;
        }
        if (subcommand.equals("setwarp") || subcommand.equals("워프설정")) {
            if (args.length < 2) {
                player.sendMessage("워프 이름을 입력해주세요.");
                return true;
            }
            setWarp(player, args[1]);
            return true;
        }
        return false;
    }

    private void setHome(Player player, String name) {
        currentIsland(player, "섬 안에서만 홈을 설정할 수 있습니다.").ifPresent(islandId -> {
            if (!allowed(player, IslandPermission.SET_HOME)) {
                player.sendMessage("섬 홈을 설정할 권한이 없습니다.");
                return;
            }
            coreApiClient.setIslandHome(islandId, player.getUniqueId(), name, location(player.getLocation()))
                .thenRun(() -> message(player, "섬 홈을 설정했습니다."))
                .exceptionally(error -> {
                    message(player, "섬 홈을 설정하지 못했습니다.");
                    return null;
                });
        });
    }

    private void setWarp(Player player, String name) {
        currentIsland(player, "섬 안에서만 워프를 설정할 수 있습니다.").ifPresent(islandId -> {
            if (!allowed(player, IslandPermission.MANAGE_WARPS)) {
                player.sendMessage("섬 워프를 설정할 권한이 없습니다.");
                return;
            }
            coreApiClient.setIslandWarp(islandId, player.getUniqueId(), name, location(player.getLocation()), false)
                .thenRun(() -> message(player, "섬 워프를 설정했습니다."))
                .exceptionally(error -> {
                    message(player, "섬 워프를 설정하지 못했습니다.");
                    return null;
                });
        });
    }

    private java.util.Optional<UUID> currentIsland(Player player, String missingMessage) {
        java.util.Optional<UUID> islandId = protection.islandAt(player.getLocation().getBlock());
        if (islandId.isEmpty()) {
            player.sendMessage(missingMessage);
        }
        return islandId;
    }

    private boolean allowed(Player player, IslandPermission permission) {
        Location location = player.getLocation();
        return protection.checkBlock(
            player.getUniqueId(),
            location.getWorld().getName(),
            location.getBlockX(),
            location.getBlockY(),
            location.getBlockZ(),
            permission,
            player.isOp()
        ).allowed();
    }

    private IslandLocation location(Location location) {
        return new IslandLocation(
            location.getWorld().getName(),
            location.getX(),
            location.getY(),
            location.getZ(),
            location.getYaw(),
            location.getPitch()
        );
    }

    private void message(Player player, String message) {
        plugin.getServer().getScheduler().runTask(plugin, () -> player.sendMessage(message));
    }
}
