package kr.lunaf.cloudislands.paper.gui;

import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import org.bukkit.plugin.java.JavaPlugin;

public final class IslandGuiMenuRegistrar {
    private IslandGuiMenuRegistrar() {
    }

    public static void register(JavaPlugin plugin, MessageRenderer messages) {
        kr.lunaf.cloudislands.paper.platform.event.PaperEvents.register(plugin, new AdminNodeMenu(messages));
        kr.lunaf.cloudislands.paper.platform.event.PaperEvents.register(plugin, new IslandBankMenu(messages));
        kr.lunaf.cloudislands.paper.platform.event.PaperEvents.register(plugin, new IslandBanMenu(messages));
        kr.lunaf.cloudislands.paper.platform.event.PaperEvents.register(plugin, new IslandBiomeMenu(messages));
        kr.lunaf.cloudislands.paper.platform.event.PaperEvents.register(plugin, new IslandChatMenu(messages));
        kr.lunaf.cloudislands.paper.platform.event.PaperEvents.register(plugin, new IslandCreateMenu(messages));
        kr.lunaf.cloudislands.paper.platform.event.PaperEvents.register(plugin, new IslandDangerMenu(messages));
        kr.lunaf.cloudislands.paper.platform.event.PaperEvents.register(plugin, new IslandFlagMenu(messages));
        kr.lunaf.cloudislands.paper.platform.event.PaperEvents.register(plugin, new IslandHomeMenu(messages));
        kr.lunaf.cloudislands.paper.platform.event.PaperEvents.register(plugin, new IslandInfoMenu(messages));
        kr.lunaf.cloudislands.paper.platform.event.PaperEvents.register(plugin, new IslandInviteMenu(messages));
        kr.lunaf.cloudislands.paper.platform.event.PaperEvents.register(plugin, new IslandLimitMenu(messages));
        kr.lunaf.cloudislands.paper.platform.event.PaperEvents.register(plugin, new IslandLogMenu(messages));
        kr.lunaf.cloudislands.paper.platform.event.PaperEvents.register(plugin, new IslandMainMenu(messages));
        kr.lunaf.cloudislands.paper.platform.event.PaperEvents.register(plugin, new IslandMemberMenu(messages));
        kr.lunaf.cloudislands.paper.platform.event.PaperEvents.register(plugin, new IslandMissionMenu(messages));
        kr.lunaf.cloudislands.paper.platform.event.PaperEvents.register(plugin, new IslandMyIslandsMenu(messages));
        kr.lunaf.cloudislands.paper.platform.event.PaperEvents.register(plugin, new IslandPermissionMenu(messages));
        kr.lunaf.cloudislands.paper.platform.event.PaperEvents.register(plugin, new IslandRankingMenu(messages));
        kr.lunaf.cloudislands.paper.platform.event.PaperEvents.register(plugin, new IslandRoleMenu(messages));
        kr.lunaf.cloudislands.paper.platform.event.PaperEvents.register(plugin, new IslandSettingsMenu(messages));
        kr.lunaf.cloudislands.paper.platform.event.PaperEvents.register(plugin, new IslandSnapshotMenu(messages));
        kr.lunaf.cloudislands.paper.platform.event.PaperEvents.register(plugin, new IslandUpgradeMenu(messages));
        kr.lunaf.cloudislands.paper.platform.event.PaperEvents.register(plugin, new IslandVisitMenu(messages));
        kr.lunaf.cloudislands.paper.platform.event.PaperEvents.register(plugin, new IslandWarpMenu(messages));
    }
}
