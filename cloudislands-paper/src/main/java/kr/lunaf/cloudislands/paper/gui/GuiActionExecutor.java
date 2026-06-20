package kr.lunaf.cloudislands.paper.gui;

import java.util.Map;
import org.bukkit.entity.Player;

public interface GuiActionExecutor {
    void execute(Player player, String actionId, Map<String, String> data, GuiClick click);

    static GuiActionExecutor noop() {
        return (player, actionId, data, click) -> player.sendMessage("이 GUI 작업은 아직 연결되지 않았습니다: " + actionId);
    }
}
