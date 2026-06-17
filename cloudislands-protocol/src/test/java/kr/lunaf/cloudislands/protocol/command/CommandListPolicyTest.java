package kr.lunaf.cloudislands.protocol.command;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
