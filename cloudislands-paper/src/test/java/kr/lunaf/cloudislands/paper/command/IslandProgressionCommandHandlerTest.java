package kr.lunaf.cloudislands.paper.command;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class IslandProgressionCommandHandlerTest {
    @Test
    void worthRankingAliasesResolveToTheWorthRanking() {
        assertTrue(IslandProgressionCommandHandler.worthRankingArg("worth"));
        assertTrue(IslandProgressionCommandHandler.worthRankingArg("VALUE"));
        assertTrue(IslandProgressionCommandHandler.worthRankingArg("가치"));
        assertFalse(IslandProgressionCommandHandler.worthRankingArg("level"));
        assertFalse(IslandProgressionCommandHandler.worthRankingArg("bank"));
    }
}
