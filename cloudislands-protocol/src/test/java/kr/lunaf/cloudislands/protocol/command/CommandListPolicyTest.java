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
    void exposesOneLineCommandPrefixContract() {
        assertEquals(" - 1 line > 1 command", CommandListPolicy.HEADER_SUFFIX);
        assertEquals("> /", CommandListPolicy.ENTRY_PREFIX);
        assertEquals("command list [page]", CommandListPolicy.PLAYER_LIST_SYNTAX);
        assertEquals("command list [page]", CommandListPolicy.ADMIN_LIST_SYNTAX);
    }
}
