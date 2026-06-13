package kr.lunaf.cloudislands.paper.session;

import java.util.List;
import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;

public final class PaperScoreboardListener implements Listener {
    private final MessageRenderer messages;

    public PaperScoreboardListener(MessageRenderer messages) {
        this.messages = messages;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) {
            return;
        }
        Scoreboard scoreboard = manager.getNewScoreboard();
        Objective objective = scoreboard.registerNewObjective("cloudislands", "dummy", messages.plain("scoreboard-title"));
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        Player player = event.getPlayer();
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
        event.getPlayer().setScoreboard(scoreboard);
    }

    private String uniqueLine(String line, int suffix) {
        return line + " ".repeat(suffix);
    }
}
