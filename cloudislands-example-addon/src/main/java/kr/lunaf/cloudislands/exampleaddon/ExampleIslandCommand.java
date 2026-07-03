package kr.lunaf.cloudislands.exampleaddon;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public final class ExampleIslandCommand implements CommandExecutor {
    private final ExampleCloudIslandsEventListener listener;
    private final ExampleIslandMenuAction menuAction;

    public ExampleIslandCommand(ExampleCloudIslandsEventListener listener, ExampleIslandMenuAction menuAction) {
        this.listener = listener;
        this.menuAction = menuAction;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args != null && args.length > 0 && args[0].equalsIgnoreCase("mission")) {
            sender.sendMessage("Example mission command received. Open the CloudIslands menu button to start example-harvest.");
            return true;
        }
        sender.sendMessage(statusLine(sender.getName()));
        sender.sendMessage(menuAction.commandFor(ExampleIslandMenuAction.ACTION_ID).orElse("/exampleisland mission"));
        return true;
    }

    String statusLine(String playerName) {
        String name = playerName == null || playerName.isBlank() ? "unknown-player" : playerName;
        return "CloudIslands example addon for " + name
            + ": routeTickets=" + listener.observedRouteTickets()
            + ", completedMissions=" + listener.completedMissionEvents()
            + ", latestRoute=" + listener.latestRouteTarget();
    }
}
