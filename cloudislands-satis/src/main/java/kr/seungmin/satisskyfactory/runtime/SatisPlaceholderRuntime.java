package kr.seungmin.satisskyfactory.runtime;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.function.Supplier;

public final class SatisPlaceholderRuntime {
    private final JavaPlugin plugin;

    public SatisPlaceholderRuntime(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    public boolean placeholderApiInstalled() {
        return plugin.getServer().getPluginManager().isPluginEnabled("PlaceholderAPI");
    }

    public boolean runtimeEnabled(boolean placeholdersEnabled, boolean machinesEnabled) {
        return gate(placeholdersEnabled, machinesEnabled, placeholderApiInstalled()).enabled();
    }

    public <T extends PlaceholderExpansionHandle> T refresh(T current, Supplier<T> expansionFactory, boolean runtimeEnabled) {
        if (!runtimeEnabled) {
            return unregister(current);
        }
        if (current != null) {
            return current;
        }
        T expansion = expansionFactory.get();
        if (!expansion.register()) {
            plugin.getLogger().warning("PlaceholderAPI refused the " + expansion.identifier() + " expansion; Satis placeholders remain disabled.");
            return null;
        }
        plugin.getLogger().info("Registered PlaceholderAPI expansion: " + expansion.identifier());
        return expansion;
    }

    public <T extends PlaceholderExpansionHandle> T unregister(T current) {
        if (current != null) {
            current.unregisterExpansion();
        }
        return null;
    }

    public interface PlaceholderExpansionHandle {
        boolean register();

        void unregisterExpansion();

        String identifier();
    }

    public static RuntimeGate gate(boolean placeholdersEnabled, boolean machinesEnabled, boolean placeholderApiInstalled) {
        if (!placeholdersEnabled) {
            return RuntimeGate.disabled("placeholders-feature-disabled");
        }
        if (!machinesEnabled) {
            return RuntimeGate.disabled("machines-feature-disabled");
        }
        if (!placeholderApiInstalled) {
            return RuntimeGate.disabled("placeholderapi-not-installed");
        }
        return RuntimeGate.active();
    }

    public record RuntimeGate(boolean enabled, String reason) {
        public static RuntimeGate active() {
            return new RuntimeGate(true, "none");
        }

        public static RuntimeGate disabled(String reason) {
            return new RuntimeGate(false, reason);
        }
    }
}
