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
        return pages(commandCount, DEFAULT_PAGE_SIZE);
    }

    public static int pages(int commandCount, int pageSize) {
        int safePageSize = Math.max(1, pageSize);
        return Math.max(1, (Math.max(0, commandCount) + safePageSize - 1) / safePageSize);
    }

    public static Page page(List<String> commands, int requestedPage, String navigationCommand) {
        return page(commands, requestedPage, navigationCommand, DEFAULT_PAGE_SIZE);
    }

    public static Page page(List<String> commands, int requestedPage, String navigationCommand, int pageSize) {
        Objects.requireNonNull(commands, "commands");
        int safePageSize = Math.max(1, pageSize);
        int maxPage = pages(commands.size(), safePageSize);
        int safePage = Math.max(1, Math.min(requestedPage, maxPage));
        int from = Math.min(commands.size(), (safePage - 1) * safePageSize);
        int to = Math.min(commands.size(), from + safePageSize);
        List<String> entries = new ArrayList<>();
        for (String command : commands.subList(from, to)) {
            entries.add(oneLine(command));
        }
        String base = oneLine(navigationCommand);
        String previous = safePage > 1 ? base + " " + (safePage - 1) : null;
        String next = safePage < maxPage ? base + " " + (safePage + 1) : null;
        int displayFrom = commands.isEmpty() ? 0 : from + 1;
        int displayTo = commands.isEmpty() ? 0 : to;
        return new Page(safePage, maxPage, displayFrom, displayTo, commands.size(), List.copyOf(entries), previous, next);
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

    public static List<String> displayLines(Page page) {
        Objects.requireNonNull(page, "page");
        List<String> lines = new ArrayList<>();
        for (String command : commandLines(page)) {
            lines.add(ENTRY_PREFIX + oneLine(command));
        }
        return List.copyOf(lines);
    }

    public static List<String> commandLines(Page page) {
        Objects.requireNonNull(page, "page");
        List<String> lines = new ArrayList<>();
        for (String command : page.entries()) {
            lines.add(oneLine(command));
        }
        if (page.previousCommand() != null && !page.previousCommand().isBlank()) {
            lines.add(oneLine(page.previousCommand()));
        }
        if (page.nextCommand() != null && !page.nextCommand().isBlank()) {
            lines.add(oneLine(page.nextCommand()));
        }
        return List.copyOf(lines);
    }

    public record Page(int page, int pages, int fromCommand, int toCommand, int totalCommands, List<String> entries, String previousCommand, String nextCommand) {
        public Page {
            fromCommand = Math.max(0, fromCommand);
            toCommand = Math.max(fromCommand, toCommand);
            totalCommands = Math.max(0, totalCommands);
            entries = List.copyOf(entries);
        }

        public String rangeSummary() {
            return fromCommand + "-" + toCommand + "/" + totalCommands;
        }
    }
}
