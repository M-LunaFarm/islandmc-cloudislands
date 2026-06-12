package kr.lunaf.cloudislands.paper.session;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;

public final class PaperScoreboardListener implements Listener {
    private final String serviceName;

    public PaperScoreboardListener(String serviceName) {
        this.serviceName = serviceName == null || serviceName.isBlank() ? "CloudIslands" : serviceName;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) {
            return;
        }
        Scoreboard scoreboard = manager.getNewScoreboard();
        Objective objective = scoreboard.registerNewObjective("cloudislands", "dummy", serviceName);
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        objective.getScore("섬 이동: /섬").setScore(3);
        objective.getScore("방문: /섬 방문").setScore(2);
        objective.getScore("관리: /섬 설정").setScore(1);
        event.getPlayer().setScoreboard(scoreboard);
    }
}
