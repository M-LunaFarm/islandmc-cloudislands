package kr.lunaf.cloudislands.paper.gui;

import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import org.bukkit.plugin.java.JavaPlugin;

public final class IslandGuiMenuRegistrar {
    private IslandGuiMenuRegistrar() {
    }

    public static void register(JavaPlugin plugin, MessageRenderer messages) {
        plugin.getServer().getPluginManager().registerEvents(new AdminNodeMenu(messages), plugin);
        plugin.getServer().getPluginManager().registerEvents(new IslandBankMenu(messages), plugin);
        plugin.getServer().getPluginManager().registerEvents(new IslandBanMenu(messages), plugin);
        plugin.getServer().getPluginManager().registerEvents(new IslandBiomeMenu(messages), plugin);
        plugin.getServer().getPluginManager().registerEvents(new IslandChatMenu(messages), plugin);
        plugin.getServer().getPluginManager().registerEvents(new IslandCreateMenu(messages), plugin);
        plugin.getServer().getPluginManager().registerEvents(new IslandDangerMenu(messages), plugin);
        plugin.getServer().getPluginManager().registerEvents(new IslandFlagMenu(messages), plugin);
        plugin.getServer().getPluginManager().registerEvents(new IslandHomeMenu(messages), plugin);
        plugin.getServer().getPluginManager().registerEvents(new IslandInfoMenu(messages), plugin);
        plugin.getServer().getPluginManager().registerEvents(new IslandInviteMenu(messages), plugin);
        plugin.getServer().getPluginManager().registerEvents(new IslandLimitMenu(messages), plugin);
        plugin.getServer().getPluginManager().registerEvents(new IslandLogMenu(messages), plugin);
        plugin.getServer().getPluginManager().registerEvents(new IslandMainMenu(messages), plugin);
        plugin.getServer().getPluginManager().registerEvents(new IslandMemberMenu(messages), plugin);
        plugin.getServer().getPluginManager().registerEvents(new IslandMissionMenu(messages), plugin);
        plugin.getServer().getPluginManager().registerEvents(new IslandMyIslandsMenu(messages), plugin);
        plugin.getServer().getPluginManager().registerEvents(new IslandPermissionMenu(messages), plugin);
        plugin.getServer().getPluginManager().registerEvents(new IslandRankingMenu(messages), plugin);
        plugin.getServer().getPluginManager().registerEvents(new IslandRoleMenu(messages), plugin);
        plugin.getServer().getPluginManager().registerEvents(new IslandSettingsMenu(messages), plugin);
        plugin.getServer().getPluginManager().registerEvents(new IslandSnapshotMenu(messages), plugin);
        plugin.getServer().getPluginManager().registerEvents(new IslandUpgradeMenu(messages), plugin);
        plugin.getServer().getPluginManager().registerEvents(new IslandVisitMenu(messages), plugin);
        plugin.getServer().getPluginManager().registerEvents(new IslandWarpMenu(messages), plugin);
    }
}
