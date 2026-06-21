package kr.lunaf.cloudislands.paper.gui;

import java.util.Map;
import org.bukkit.entity.Player;

public final class GuiActionRegistry {
    private final GuiActionExecutor executor;
    private final GuiActionDedupePolicy dedupePolicy;

    public GuiActionRegistry(GuiActionExecutor executor) {
        this(executor, new GuiActionDedupePolicy());
    }

    GuiActionRegistry(GuiActionExecutor executor, GuiActionDedupePolicy dedupePolicy) {
        this.executor = executor == null ? GuiActionExecutor.noop() : executor;
        this.dedupePolicy = dedupePolicy == null ? new GuiActionDedupePolicy() : dedupePolicy;
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
            .filter(action -> player != null && dedupePolicy.accept(player.getUniqueId(), action, safeClick, System.currentTimeMillis()))
            .ifPresent(action -> executor.execute(player, action, safeClick));
    }
}
