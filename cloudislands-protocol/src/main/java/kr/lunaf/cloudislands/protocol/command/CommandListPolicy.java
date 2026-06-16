package kr.lunaf.cloudislands.protocol.command;

public final class CommandListPolicy {
    public static final int DEFAULT_PAGE_SIZE = 12;
    public static final String HEADER_SUFFIX = " - 1 line > 1 command";
    public static final String ENTRY_PREFIX = "> /";
    public static final String PLAYER_LIST_SYNTAX = "command list [page]";
    public static final String ADMIN_LIST_SYNTAX = "command list [page]";

    private CommandListPolicy() {
    }
}
