package kr.lunaf.cloudislands.paper;

import kr.lunaf.cloudislands.common.protection.IslandRegion;
import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import kr.lunaf.cloudislands.paper.platform.player.BukkitPlayerGateway;
import kr.lunaf.cloudislands.paper.platform.player.PaperPlayerGateway;
import kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers;
import kr.lunaf.cloudislands.paper.platform.world.BukkitWorldGateway;
import kr.lunaf.cloudislands.paper.platform.world.PaperWorldGateway;
import kr.lunaf.cloudislands.paper.platform.world.SafeTeleportResolver;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.Plugin;

public final class IslandBoundaryListener implements Listener {
    private final ProtectionController protection;
    private final MessageRenderer messages;
    private final PaperPlayerGateway players;
    private final PaperWorldGateway worlds;
    private final Plugin plugin;
    private final java.util.Set<java.util.UUID> pendingReturns = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final java.util.concurrent.atomic.AtomicLong memberReturns = new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.atomic.AtomicLong visitorReturns = new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.atomic.AtomicLong adminBypasses = new java.util.concurrent.atomic.AtomicLong();

    public IslandBoundaryListener(ProtectionController protection) {
        this(protection, null, new BukkitPlayerGateway(), null, null);
    }

    public IslandBoundaryListener(ProtectionController protection, MessageRenderer messages) {
        this(protection, messages, new BukkitPlayerGateway(), null, null);
    }

    IslandBoundaryListener(ProtectionController protection, MessageRenderer messages, PaperPlayerGateway players) {
        this(protection, messages, players, null, null);
    }

    public IslandBoundaryListener(Plugin plugin, ProtectionController protection, MessageRenderer messages) {
        this(protection, messages, new BukkitPlayerGateway(), new BukkitWorldGateway(plugin), plugin);
    }

    IslandBoundaryListener(ProtectionController protection, MessageRenderer messages, PaperPlayerGateway players, PaperWorldGateway worlds, Plugin plugin) {
        this.protection = protection;
        this.messages = messages;
        this.players = players;
        this.worlds = worlds;
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null || sameBlock(from, to)) {
            return;
        }
        if (event.getPlayer().hasPermission("cloudislands.admin.bypass")) {
            adminBypasses.incrementAndGet();
            return;
        }
        java.util.Optional<IslandRegion> fromRegion = protection.regionAt(from.getBlock());
        if (fromRegion.isEmpty()) {
            return;
        }
        java.util.Optional<IslandRegion> toRegion = protection.regionAt(to.getBlock());
        if (toRegion.isPresent() && toRegion.get().islandId().equals(fromRegion.get().islandId())) {
            return;
        }
        IslandRegion region = fromRegion.get();
        boolean member = protection.memberOrTrusted(region.islandId(), event.getPlayer().getUniqueId());
        if (member) {
            memberReturns.incrementAndGet();
        } else {
            visitorReturns.incrementAndGet();
        }
        Location target = member ? memberSpawn(from, region) : visitorSpawn(from, region);
        org.bukkit.entity.Player player = event.getPlayer();
        java.util.UUID playerUuid = player.getUniqueId();
        event.setCancelled(true);
        if (!pendingReturns.add(playerUuid)) {
            return;
        }
        if (worlds == null) {
            pendingReturns.remove(playerUuid);
            if (players.teleport(player, target)) {
                player.sendActionBar(Component.text(member
                    ? message("boundary-member-return", "섬 경계 밖으로 이동할 수 없어 섬 스폰으로 돌려보냈습니다.")
                    : message("boundary-visitor-return", "섬 경계 밖으로 이동할 수 없어 방문자 위치로 돌려보냈습니다.")));
            }
            return;
        }
        worlds.safeDestination(target, region).whenComplete((destination, error) -> runSync(() -> {
            try {
                if (error != null || destination == null || destination.isEmpty()
                    || !SafeTeleportResolver.isSafe(destination.get(), region)) {
                    player.sendActionBar(Component.text(message("boundary-return-unsafe", "섬 안에서 안전한 복귀 위치를 찾을 수 없습니다.")));
                    return;
                }
                if (players.teleport(player, destination.get())) {
                    player.sendActionBar(Component.text(member
                        ? message("boundary-member-return", "섬 경계 밖으로 이동할 수 없어 섬 스폰으로 돌려보냈습니다.")
                        : message("boundary-visitor-return", "섬 경계 밖으로 이동할 수 없어 방문자 위치로 돌려보냈습니다.")));
                }
            } finally {
                pendingReturns.remove(playerUuid);
            }
        }));
    }

    private void runSync(Runnable task) {
        if (plugin == null) {
            task.run();
        } else {
            PaperSchedulers.run(plugin, task);
        }
    }

    private String message(String key, String fallback) {
        if (messages == null) {
            return fallback;
        }
        String rendered = messages.plain(key);
        return rendered.isBlank() ? fallback : rendered;
    }

    private Location memberSpawn(Location from, IslandRegion region) {
        return new Location(from.getWorld(), region.originX() + 0.5D, Math.max(from.getY(), 100.0D), region.originZ() + 0.5D, from.getYaw(), from.getPitch());
    }

    private Location visitorSpawn(Location from, IslandRegion region) {
        return new Location(from.getWorld(), region.originX() + 0.5D, Math.max(from.getY(), 100.0D), region.originZ() + 2.5D, 180.0F, 0.0F);
    }

    private boolean sameBlock(Location from, Location to) {
        return from.getWorld().equals(to.getWorld())
            && from.getBlockX() == to.getBlockX()
            && from.getBlockY() == to.getBlockY()
            && from.getBlockZ() == to.getBlockZ();
    }

    public long memberReturns() {
        return memberReturns.get();
    }

    public long visitorReturns() {
        return visitorReturns.get();
    }

    public long adminBypasses() {
        return adminBypasses.get();
    }
}
