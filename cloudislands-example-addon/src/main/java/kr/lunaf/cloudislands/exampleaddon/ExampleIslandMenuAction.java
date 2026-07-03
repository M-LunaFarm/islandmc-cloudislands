package kr.lunaf.cloudislands.exampleaddon;

import java.util.List;
import java.util.Optional;
import kr.lunaf.cloudislands.api.model.AddonMenuButtonSnapshot;

public final class ExampleIslandMenuAction {
    public static final String ACTION_ID = "example.open";

    private final List<AddonMenuButtonSnapshot> menuButtons;

    public ExampleIslandMenuAction(List<AddonMenuButtonSnapshot> menuButtons) {
        this.menuButtons = menuButtons == null ? List.of() : List.copyOf(menuButtons);
    }

    public Optional<AddonMenuButtonSnapshot> button(String actionId) {
        return menuButtons.stream()
            .filter(AddonMenuButtonSnapshot::enabled)
            .filter(button -> button.actionId().equals(actionId))
            .findFirst();
    }

    public Optional<String> commandFor(String actionId) {
        return button(actionId)
            .map(AddonMenuButtonSnapshot::command)
            .filter(command -> !command.isBlank());
    }
}
