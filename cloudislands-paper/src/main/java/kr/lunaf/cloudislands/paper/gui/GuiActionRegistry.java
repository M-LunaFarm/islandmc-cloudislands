package kr.lunaf.cloudislands.paper.gui;

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

    public void execute(Player player, GuiAction action, GuiClick click) {
        GuiClick safeClick = click == null ? GuiClick.UNSUPPORTED : click;
        if (player == null || action == null || !safeClick.supported()) {
            return;
        }
        if (dedupePolicy.accept(player.getUniqueId(), action, safeClick, System.currentTimeMillis())) {
            executor.execute(player, action, safeClick);
        }
    }
}
