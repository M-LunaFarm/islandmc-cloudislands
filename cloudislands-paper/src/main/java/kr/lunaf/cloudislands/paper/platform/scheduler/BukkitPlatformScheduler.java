package kr.lunaf.cloudislands.paper.platform.scheduler;

import java.time.Duration;
import java.util.UUID;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

public final class BukkitPlatformScheduler implements PlatformScheduler {
    private final Plugin plugin;

    public BukkitPlatformScheduler(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public TaskHandle runGlobal(Runnable task) {
        return handle(plugin.getServer().getScheduler().runTask(plugin, task));
    }

    @Override
    public TaskHandle runAsync(Runnable task) {
        return handle(plugin.getServer().getScheduler().runTaskAsynchronously(plugin, task));
    }

    @Override
    public TaskHandle runForPlayer(UUID playerId, Runnable task) {
        return runGlobal(task);
    }

    @Override
    public TaskHandle runForChunk(String worldKey, int chunkX, int chunkZ, Runnable task) {
        return runGlobal(task);
    }

    @Override
    public TaskHandle repeatGlobal(Duration delay, Duration interval, Runnable task) {
        long delayTicks = ticks(delay);
        long intervalTicks = Math.max(1L, ticks(interval));
        return handle(plugin.getServer().getScheduler().runTaskTimer(plugin, task, delayTicks, intervalTicks));
    }

    @Override
    public void close() {
        plugin.getServer().getScheduler().cancelTasks(plugin);
    }

    private static TaskHandle handle(BukkitTask task) {
        return task == null ? TaskHandle.noop() : task::cancel;
    }

    private static long ticks(Duration duration) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            return 0L;
        }
        return Math.max(1L, duration.toMillis() / 50L);
    }
}
