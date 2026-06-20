package kr.lunaf.cloudislands.paper.gui;

import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import org.bukkit.plugin.java.JavaPlugin;

public final class IslandGuiMenuRegistrar {
    private IslandGuiMenuRegistrar() {
    }

    public static void register(JavaPlugin plugin, MessageRenderer messages) {
        register(plugin, messages, GuiActionExecutor.noop());
    }

    public static void register(JavaPlugin plugin, MessageRenderer messages, GuiActionExecutor actions) {
        GuiActionExecutor executor = actions == null ? GuiActionExecutor.noop() : actions;
        GuiActionRegistry registry = new GuiActionRegistry(executor);
        kr.lunaf.cloudislands.paper.platform.event.PaperEvents.register(plugin, new GuiEventGuard());
        kr.lunaf.cloudislands.paper.platform.event.PaperEvents.register(plugin, GuiStateMenus.listener(registry));
        kr.lunaf.cloudislands.paper.platform.event.PaperEvents.register(plugin, new AdminNodeMenu(messages, registry));
        kr.lunaf.cloudislands.paper.platform.event.PaperEvents.register(plugin, new IslandBankMenu(messages, registry));
        kr.lunaf.cloudislands.paper.platform.event.PaperEvents.register(plugin, new IslandBanMenu(messages, registry));
        kr.lunaf.cloudislands.paper.platform.event.PaperEvents.register(plugin, new IslandBiomeMenu(messages, registry));
        kr.lunaf.cloudislands.paper.platform.event.PaperEvents.register(plugin, new IslandChatMenu(messages, registry));
        kr.lunaf.cloudislands.paper.platform.event.PaperEvents.register(plugin, new IslandConfirmationMenu(messages, executor));
        kr.lunaf.cloudislands.paper.platform.event.PaperEvents.register(plugin, new IslandCreateMenu(messages, registry));
        kr.lunaf.cloudislands.paper.platform.event.PaperEvents.register(plugin, new IslandDangerMenu(messages, executor));
        kr.lunaf.cloudislands.paper.platform.event.PaperEvents.register(plugin, new IslandFlagMenu(messages, registry));
        kr.lunaf.cloudislands.paper.platform.event.PaperEvents.register(plugin, new IslandHomeMenu(messages, registry));
        kr.lunaf.cloudislands.paper.platform.event.PaperEvents.register(plugin, new IslandInfoMenu(messages, registry));
        kr.lunaf.cloudislands.paper.platform.event.PaperEvents.register(plugin, new IslandInviteMenu(messages, registry));
        kr.lunaf.cloudislands.paper.platform.event.PaperEvents.register(plugin, new IslandLimitMenu(messages, registry));
        kr.lunaf.cloudislands.paper.platform.event.PaperEvents.register(plugin, new IslandLogMenu(messages, registry));
        kr.lunaf.cloudislands.paper.platform.event.PaperEvents.register(plugin, new IslandMainMenu(messages, executor));
        kr.lunaf.cloudislands.paper.platform.event.PaperEvents.register(plugin, new IslandMemberMenu(messages, executor));
        kr.lunaf.cloudislands.paper.platform.event.PaperEvents.register(plugin, new IslandMissionMenu(messages, registry));
        kr.lunaf.cloudislands.paper.platform.event.PaperEvents.register(plugin, new IslandMyIslandsMenu(messages, registry));
        kr.lunaf.cloudislands.paper.platform.event.PaperEvents.register(plugin, new IslandPermissionMenu(messages, executor));
        kr.lunaf.cloudislands.paper.platform.event.PaperEvents.register(plugin, new IslandRankingMenu(messages, registry));
        kr.lunaf.cloudislands.paper.platform.event.PaperEvents.register(plugin, new IslandRoleMenu(messages, registry));
        kr.lunaf.cloudislands.paper.platform.event.PaperEvents.register(plugin, new IslandSettingsMenu(messages, registry));
        kr.lunaf.cloudislands.paper.platform.event.PaperEvents.register(plugin, new IslandSnapshotMenu(messages, registry));
        kr.lunaf.cloudislands.paper.platform.event.PaperEvents.register(plugin, new IslandUpgradeMenu(messages, registry));
        kr.lunaf.cloudislands.paper.platform.event.PaperEvents.register(plugin, new IslandVisitMenu(messages, registry));
        kr.lunaf.cloudislands.paper.platform.event.PaperEvents.register(plugin, new IslandWarpMenu(messages, registry));
    }
}
