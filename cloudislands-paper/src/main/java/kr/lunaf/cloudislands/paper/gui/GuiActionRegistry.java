package kr.lunaf.cloudislands.paper.gui;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.entity.Player;

public final class GuiActionRegistry {
    private static final AtomicReference<GuiActionExecutor> EXECUTOR = new AtomicReference<>(GuiActionExecutor.noop());

    private GuiActionRegistry() {
    }

    public static void configure(GuiActionExecutor executor) {
        EXECUTOR.set(executor == null ? GuiActionExecutor.noop() : executor);
    }

    public static void execute(Player player, String actionId, GuiClick click) {
        execute(player, actionId, Map.of(), click);
    }

    public static void execute(Player player, String actionId, Map<String, String> data, GuiClick click) {
        GuiClick safeClick = click == null ? GuiClick.UNSUPPORTED : click;
        if (!safeClick.supported()) {
            return;
        }
        EXECUTOR.get().execute(player, actionId, data == null ? Map.of() : data, safeClick);
    }
}
