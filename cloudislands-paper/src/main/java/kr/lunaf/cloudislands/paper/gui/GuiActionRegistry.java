package kr.lunaf.cloudislands.paper.gui;

import java.util.Map;
import org.bukkit.entity.Player;

public final class GuiActionRegistry {
    private final GuiActionExecutor executor;

    public GuiActionRegistry(GuiActionExecutor executor) {
        this.executor = executor == null ? GuiActionExecutor.noop() : executor;
    }

    public void execute(Player player, String actionId, GuiClick click) {
        execute(player, actionId, Map.of(), click);
    }

    public void execute(Player player, String actionId, Map<String, String> data, GuiClick click) {
        GuiClick safeClick = click == null ? GuiClick.UNSUPPORTED : click;
        if (!safeClick.supported()) {
            return;
        }
        GuiActionParser.parse(actionId, data)
            .ifPresent(action -> executor.execute(player, action.actionId(), action.data(), safeClick));
    }
}
