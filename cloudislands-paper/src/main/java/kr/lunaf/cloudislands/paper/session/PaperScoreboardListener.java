package kr.lunaf.cloudislands.paper.session;

import java.util.List;
import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import org.bukkit.Bukkit;
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
        List<String> lines = messages.lines("scoreboard-lines");
        int score = lines.size();
        for (String line : lines) {
            objective.getScore(line).setScore(score--);
        }
        event.getPlayer().setScoreboard(scoreboard);
    }
}
