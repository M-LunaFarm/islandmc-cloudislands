package kr.lunaf.cloudislands.protocol.command;

public record CommandPermission(String node) {
    public CommandPermission {
        node = node == null || node.isBlank() ? "IslandCommandPermission.fromSubcommand" : node.trim();
    }
}
