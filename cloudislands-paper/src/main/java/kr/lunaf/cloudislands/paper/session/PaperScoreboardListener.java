package kr.lunaf.cloudislands.paper.session;

import java.util.List;
import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;

public final class PaperScoreboardListener implements Listener {
    private final MessageRenderer messages;
    private final Plugin plugin;

    public PaperScoreboardListener(MessageRenderer messages) {
        this(null, messages);
    }

    public PaperScoreboardListener(Plugin plugin, MessageRenderer messages) {
        this.plugin = plugin;
        this.messages = messages;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        refreshScoreboards();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (plugin == null) {
            refreshScoreboards();
            return;
        }
        Bukkit.getScheduler().runTask(plugin, this::refreshScoreboards);
    }

    private void refreshScoreboards() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            applyScoreboard(player);
        }
    }

    private void applyScoreboard(Player player) {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) {
            return;
        }
        Scoreboard scoreboard = manager.getNewScoreboard();
        Objective objective = scoreboard.registerNewObjective("cloudislands", "dummy", messages.plain("scoreboard-title"));
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        List<String> lines = messages.lines("scoreboard-lines",
            "player", player.getName(),
            "online", Integer.toString(Bukkit.getOnlinePlayers().size()),
            "world", player.getWorld().getName()
        );
        int score = lines.size();
        int suffix = 0;
        for (String line : lines) {
            objective.getScore(uniqueLine(line, suffix++)).setScore(score--);
        }
        player.setScoreboard(scoreboard);
    }

    private String uniqueLine(String line, int suffix) {
        return line + " ".repeat(suffix);
    }
}
