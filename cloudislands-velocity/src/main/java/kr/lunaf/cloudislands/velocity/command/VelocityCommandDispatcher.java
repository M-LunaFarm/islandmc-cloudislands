package kr.lunaf.cloudislands.velocity.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import java.util.List;
import kr.lunaf.cloudislands.velocity.VelocityRoutingController;
import kr.lunaf.cloudislands.velocity.config.VelocityConfig;

public final class VelocityCommandDispatcher {
    private final VelocityAdminCommandDispatcher admin;
    private final VelocityPlayerCommandDispatcher player;
    private final VelocityCommandSuggestions suggestions;

    public VelocityCommandDispatcher(ProxyServer proxy, VelocityRoutingController routingController, VelocityConfig config) {
        this.admin = new VelocityAdminCommandDispatcher(proxy, routingController, config);
        this.player = new VelocityPlayerCommandDispatcher(proxy, routingController, config);
        this.suggestions = new VelocityCommandSuggestions(proxy, routingController, config);
    }

    public void dispatchAdmin(Player player, String[] args) {
        admin.dispatchAdmin(player, args);
    }

    public void dispatch(Player player, String[] args) {
        this.player.dispatch(player, args);
    }

    public List<String> playerSuggestions(String[] args) {
        return suggestions.playerSuggestions(args);
    }

    public boolean hasAdminAccess(CommandSource source, String[] args) {
        return suggestions.hasAdminAccess(source, args);
    }

    public List<String> adminSuggestions(String[] args) {
        return suggestions.adminSuggestions(args);
    }
}
