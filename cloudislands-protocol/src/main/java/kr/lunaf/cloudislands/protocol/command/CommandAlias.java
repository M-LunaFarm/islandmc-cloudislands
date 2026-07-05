package kr.lunaf.cloudislands.protocol.command;

public record CommandAlias(String value) {
    public CommandAlias {
        value = value == null ? "" : value.trim();
    }
}
