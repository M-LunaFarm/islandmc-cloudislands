package kr.lunaf.cloudislands.protocol.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class CommandListPolicy {
    public static final int DEFAULT_PAGE_SIZE = 12;
    public static final String HEADER_SUFFIX = " - 1 line > 1 command";
    public static final String ENTRY_PREFIX = "> /";
    public static final String PLAYER_LIST_SYNTAX = "command list [page]";
    public static final String ADMIN_LIST_SYNTAX = "command list [page]";

    private CommandListPolicy() {
    }

    public static int pages(int commandCount) {
        return Math.max(1, (Math.max(0, commandCount) + DEFAULT_PAGE_SIZE - 1) / DEFAULT_PAGE_SIZE);
    }

    public static Page page(List<String> commands, int requestedPage, String navigationCommand) {
        Objects.requireNonNull(commands, "commands");
        int maxPage = pages(commands.size());
        int safePage = Math.max(1, Math.min(requestedPage, maxPage));
        int from = Math.min(commands.size(), (safePage - 1) * DEFAULT_PAGE_SIZE);
        int to = Math.min(commands.size(), from + DEFAULT_PAGE_SIZE);
        List<String> entries = new ArrayList<>();
        for (String command : commands.subList(from, to)) {
            entries.add(oneLine(command));
        }
        String base = oneLine(navigationCommand);
        String previous = safePage > 1 ? base + " " + (safePage - 1) : null;
        String next = safePage < maxPage ? base + " " + (safePage + 1) : null;
        return new Page(safePage, maxPage, List.copyOf(entries), previous, next);
    }

    public static String oneLine(String command) {
        if (command == null) {
            return "";
        }
        return command
                .replace('\r', ' ')
                .replace('\n', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    public record Page(int page, int pages, List<String> entries, String previousCommand, String nextCommand) {
        public Page {
            entries = List.copyOf(entries);
        }
    }
}
