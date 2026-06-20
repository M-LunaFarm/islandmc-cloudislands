package kr.lunaf.cloudislands.paper.gui;

import org.bukkit.entity.Player;

public interface GuiActionExecutor {
    void execute(Player player, GuiAction action, GuiClick click);

    static GuiActionExecutor noop() {
        return (player, action, click) -> player.sendMessage("이 GUI 작업은 아직 연결되지 않았습니다: " + action.actionId());
    }
}
