package kr.lunaf.cloudislands.protocol.command;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandListPolicyTest {
    @Test
    void keepsOneLineCommandListContractStable() {
        assertEquals(12, CommandListPolicy.DEFAULT_PAGE_SIZE);
        assertEquals("> /", CommandListPolicy.ENTRY_PREFIX);
        assertTrue(CommandListPolicy.HEADER_SUFFIX.contains("1 line > 1 command"));
        assertEquals("command list [page]", CommandListPolicy.PLAYER_LIST_SYNTAX);
        assertEquals("command list [page]", CommandListPolicy.ADMIN_LIST_SYNTAX);
    }

    @Test
    void paginatesAndNormalizesCommandEntries() {
        List<String> commands = List.of(
                "factory help [page]",
                "factory list [page]",
                "factory command list [page]",
                "factory status",
                "factory main",
                "factory machines",
                "factory storage",
                "factory deposit",
                "factory withdraw <itemId> <amount>",
                "factory market",
                "factory sell hand",
                "factory sell <itemId> <amount>",
                "factory migration\nverify <sqlitePath>"
        );

        CommandListPolicy.Page page = CommandListPolicy.page(commands, 99, "factory command list");

        assertEquals(2, page.page());
        assertEquals(2, page.pages());
        assertEquals(List.of("factory migration verify <sqlitePath>"), page.entries());
        assertEquals("factory command list 1", page.previousCommand());
        assertNull(page.nextCommand());
    }

    @Test
    void keepsEmptyCommandListsOnFirstPage() {
        CommandListPolicy.Page page = CommandListPolicy.page(List.of(), -5, "factory command list");

        assertEquals(1, page.page());
        assertEquals(1, page.pages());
        assertEquals(List.of(), page.entries());
        assertNull(page.previousCommand());
        assertNull(page.nextCommand());
    }
}
