package kr.lunaf.cloudislands.paper.command;

import java.util.Map;
import kr.lunaf.cloudislands.paper.gui.ConfirmationTokenPolicy;
import kr.lunaf.cloudislands.paper.gui.GuiAction;
import kr.lunaf.cloudislands.paper.gui.GuiClick;
import kr.lunaf.cloudislands.paper.gui.IslandConfirmationMenu;
import org.bukkit.Material;
import org.bukkit.entity.Player;

final class IslandCommandConfirmations {
    private final IslandCommandMessenger messages;

    IslandCommandConfirmations(IslandCommandMessenger messages) {
        this.messages = messages;
    }

    void open(
        Player player,
        String title,
        String description,
        Material material,
        String confirmName,
        String confirmAction,
        Map<String, String> data,
        String confirmLore,
        String cancelAction
    ) {
        IslandConfirmationMenu.open(player, messages.messagesFor(player), IslandConfirmationMenu.Confirmation.of(
            title,
            description,
            material,
            confirmName,
            confirmAction,
            ConfirmationTokenPolicy.withToken(confirmAction, data),
            confirmLore,
            cancelAction
        ));
    }

    boolean accepted(Player player, GuiAction action, GuiClick click) {
        if (ConfirmationTokenPolicy.confirmed(action, click)) {
            return true;
        }
        messages.message(player, messages.routeMessage("confirmation-token-invalid", "확인 토큰이 올바르지 않습니다. 확인 화면을 다시 열어주세요."));
        return false;
    }
}
