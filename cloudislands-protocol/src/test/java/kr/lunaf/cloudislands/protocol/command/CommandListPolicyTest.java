package kr.lunaf.cloudislands.protocol.command;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CommandListPolicyTest {
    @Test
    void pagesCommandListWithOneCommandPerLine() {
        List<String> commands = IntStream.rangeClosed(1, 13)
            .mapToObj(index -> "섬 command-" + index)
            .toList();

        CommandListPolicy.Page firstPage = CommandListPolicy.page(commands, 1, "섬 command list");
        CommandListPolicy.Page secondPage = CommandListPolicy.page(commands, 2, "섬 command list");

        assertEquals(1, firstPage.page());
        assertEquals(2, firstPage.pages());
        assertEquals(12, firstPage.entries().size());
        assertEquals("섬 command-1", firstPage.entries().getFirst());
        assertNull(firstPage.previousCommand());
        assertEquals("섬 command list 2", firstPage.nextCommand());
        assertEquals(List.of("섬 command-13"), secondPage.entries());
        assertEquals("섬 command list 1", secondPage.previousCommand());
        assertNull(secondPage.nextCommand());
    }

    @Test
    void normalizesEntriesToSingleLineCommands() {
        CommandListPolicy.Page page = CommandListPolicy.page(List.of("섬\n  방문   player\rname"), 1, "섬\ncommand   list");

        assertEquals(List.of("섬 방문 player name"), page.entries());
        assertNull(page.previousCommand());
        assertNull(page.nextCommand());
    }

    @Test
    void rendersDisplayLinesWithOneCommandPerChatLine() {
        List<String> commands = List.of("섬 생성", "섬\n  방문", "섬 홈", "섬 설정", "섬 삭제");

        CommandListPolicy.Page page = CommandListPolicy.page(commands, 2, "섬\ncommand   list", 2);

        assertEquals(List.of(
            "섬 홈",
            "섬 설정",
            "섬 command list 1",
            "섬 command list 3"
        ), CommandListPolicy.commandLines(page));
        assertEquals(List.of(
            "> /섬 홈",
            "> /섬 설정",
            "> /섬 command list 1",
            "> /섬 command list 3"
        ), CommandListPolicy.displayLines(page));
    }

    @Test
    void clampsInvalidPageRequestsToAvailableRange() {
        List<String> commands = IntStream.rangeClosed(1, 25)
            .mapToObj(index -> "factory command-" + index)
            .toList();

        CommandListPolicy.Page beforeFirst = CommandListPolicy.page(commands, -10, "factory command list");
        CommandListPolicy.Page afterLast = CommandListPolicy.page(commands, 99, "factory command list");

        assertEquals(1, beforeFirst.page());
        assertNull(beforeFirst.previousCommand());
        assertEquals("factory command list 2", beforeFirst.nextCommand());
        assertEquals(3, afterLast.page());
        assertEquals(List.of("factory command-25"), afterLast.entries());
        assertEquals("factory command list 2", afterLast.previousCommand());
        assertNull(afterLast.nextCommand());
    }

    @Test
    void exposesOneLineCommandPrefixContract() {
        assertEquals(" - 1 line > 1 command", CommandListPolicy.HEADER_SUFFIX);
        assertEquals("> /", CommandListPolicy.ENTRY_PREFIX);
        assertEquals("command list [page]", CommandListPolicy.PLAYER_LIST_SYNTAX);
        assertEquals("command list [page]", CommandListPolicy.ADMIN_LIST_SYNTAX);
    }
}
