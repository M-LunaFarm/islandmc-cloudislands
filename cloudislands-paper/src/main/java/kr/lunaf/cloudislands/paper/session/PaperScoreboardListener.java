package kr.lunaf.cloudislands.paper.session;

import java.util.List;
import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;

public final class PaperScoreboardListener implements Listener {
    private final MessageRenderer messages;
    private final Plugin plugin;
    private final PlayerLocaleCache locales;

    public PaperScoreboardListener(MessageRenderer messages) {
        this(null, messages);
    }

    public PaperScoreboardListener(Plugin plugin, MessageRenderer messages) {
        this(plugin, messages, null);
    }

    public PaperScoreboardListener(Plugin plugin, MessageRenderer messages, PlayerLocaleCache locales) {
        this.plugin = plugin;
        this.messages = messages;
        this.locales = locales;
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
        kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers.run(plugin, this::refreshScoreboards);
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
        String locale = locales == null ? PlayerLocaleCache.clientLocale(player) : locales.locale(player);
        Objective objective = scoreboard.registerNewObjective("cloudislands", Criteria.DUMMY, messages.componentForLocale(locale, "scoreboard-title"));
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        List<Component> lines = messages.componentLinesForLocale(locale, "scoreboard-lines",
            "player", player.getName(),
            "online", Integer.toString(Bukkit.getOnlinePlayers().size()),
            "world", player.getWorld().getName()
        );
        int visibleLines = Math.min(15, lines.size());
        int score = visibleLines;
        for (int index = 0; index < visibleLines; index++) {
            String entry = uniqueEntry(index);
            Team team = scoreboard.registerNewTeam("ci_line_" + index);
            team.addEntry(entry);
            team.prefix(lines.get(index));
            objective.getScore(entry).setScore(score--);
        }
        player.setScoreboard(scoreboard);
    }

    private String uniqueEntry(int index) {
        return "\u00a7" + Integer.toHexString(index);
    }
}
