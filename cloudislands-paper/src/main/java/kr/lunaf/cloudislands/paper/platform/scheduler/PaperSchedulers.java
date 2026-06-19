package kr.lunaf.cloudislands.paper.platform.scheduler;

import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

public final class PaperSchedulers {
    private PaperSchedulers() {
    }

    public static BukkitTask run(Plugin plugin, Runnable task) {
        return plugin.getServer().getScheduler().runTask(plugin, task);
    }

    public static BukkitTask runAsync(Plugin plugin, Runnable task) {
        return plugin.getServer().getScheduler().runTaskAsynchronously(plugin, task);
    }

    public static BukkitTask runLater(Plugin plugin, Runnable task, long delayTicks) {
        return plugin.getServer().getScheduler().runTaskLater(plugin, task, delayTicks);
    }

    public static BukkitTask runTimer(Plugin plugin, Runnable task, long delayTicks, long intervalTicks) {
        return plugin.getServer().getScheduler().runTaskTimer(plugin, task, delayTicks, intervalTicks);
    }

    public static BukkitTask runTimerAsync(Plugin plugin, Runnable task, long delayTicks, long intervalTicks) {
        return plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, task, delayTicks, intervalTicks);
    }

    public static void cancelAll(Plugin plugin) {
        plugin.getServer().getScheduler().cancelTasks(plugin);
    }
}
