package kr.seungmin.satisskyfactory.runtime;

import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class SatisListenerRuntime {
    private final JavaPlugin plugin;

    public SatisListenerRuntime(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    public boolean registerListener(Listener listener, boolean registered) {
        if (listener == null) {
            return false;
        }
        if (registered) {
            return true;
        }
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        return true;
    }

    public boolean unregisterListener(Listener listener, boolean registered) {
        if (listener != null && registered) {
            HandlerList.unregisterAll(listener);
        }
        return false;
    }

    public static RegistrationState state(boolean present, boolean registered) {
        if (!present) {
            return RegistrationState.MISSING;
        }
        return registered ? RegistrationState.REGISTERED : RegistrationState.UNREGISTERED;
    }

    public enum RegistrationState {
        MISSING,
        UNREGISTERED,
        REGISTERED
    }
}
