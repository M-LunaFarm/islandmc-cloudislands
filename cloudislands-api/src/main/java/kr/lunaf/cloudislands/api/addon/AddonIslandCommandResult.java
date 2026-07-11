package kr.lunaf.cloudislands.api.addon;

import java.util.List;

public record AddonIslandCommandResult(boolean accepted, List<String> messages) {
    public AddonIslandCommandResult {
        messages = messages == null ? List.of() : messages.stream().filter(java.util.Objects::nonNull).toList();
    }

    public static AddonIslandCommandResult success() {
        return new AddonIslandCommandResult(true, List.of());
    }

    public static AddonIslandCommandResult message(String message) {
        return new AddonIslandCommandResult(true, message == null ? List.of() : List.of(message));
    }

    public static AddonIslandCommandResult rejected(String message) {
        return new AddonIslandCommandResult(false, message == null ? List.of() : List.of(message));
    }
}
