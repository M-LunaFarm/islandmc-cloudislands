package kr.lunaf.cloudislands.protocol.command;

import java.util.List;

public record CommandHelpCategory(String name, List<String> aliases, String title, List<String> commands) {
    public CommandHelpCategory {
        name = name == null || name.isBlank() ? "기본" : name.trim();
        aliases = aliases == null ? List.of(name) : List.copyOf(aliases);
        title = title == null || title.isBlank() ? name : title.trim();
        commands = commands == null ? List.of() : List.copyOf(commands);
    }
}
