package kr.lunaf.cloudislands.paper.admin;

import kr.lunaf.cloudislands.paper.CloudIslandsPaperAgent;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public final class AdminCommandController implements CommandExecutor {
    private final CloudIslandsPaperAgent agent;

    public AdminCommandController(CloudIslandsPaperAgent agent) {
        this.agent = agent;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("cloudislands.admin")) {
            sender.sendMessage("권한이 없습니다.");
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            sender.sendMessage("CloudIslands agent role=" + agent.role());
            return true;
        }
        if (args[0].equalsIgnoreCase("cache") && args.length > 1 && args[1].equalsIgnoreCase("clear")) {
            agent.permissionCache().invalidateAll();
            sender.sendMessage("CloudIslands local cache cleared.");
            return true;
        }
        sender.sendMessage("사용법: /ciadmin status, /ciadmin cache clear");
        return true;
    }
}
