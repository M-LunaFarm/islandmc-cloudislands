package kr.lunaf.cloudislands.api.addon;

import java.util.List;
import java.util.UUID;

public record AddonIslandCommandContext(UUID playerUuid, String label, String alias, List<String> arguments) {
    public AddonIslandCommandContext {
        label = label == null ? "island" : label.trim();
        alias = alias == null ? "" : alias.trim().toLowerCase(java.util.Locale.ROOT);
        arguments = arguments == null ? List.of() : List.copyOf(arguments);
    }
}
