package kr.lunaf.cloudislands.paper.platform.event;

import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

public final class PaperEvents {
    private PaperEvents() {
    }

    public static void register(Plugin plugin, Listener listener) {
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
    }

    public static void call(Event event) {
        Bukkit.getPluginManager().callEvent(event);
    }
}
