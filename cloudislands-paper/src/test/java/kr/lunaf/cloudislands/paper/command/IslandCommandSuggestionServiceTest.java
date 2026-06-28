package kr.lunaf.cloudislands.paper.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class IslandCommandSuggestionServiceTest {
    private final IslandCommandSuggestionService suggestions = new IslandCommandSuggestionService();

    @Test
    void suggestsKnownKoreanShortcutAliases() {
        assertEquals("업그레이드", suggestions.suggest("업글", IslandCommandCatalog.SUBCOMMANDS).orElseThrow());
        assertEquals("업그레이드구매", suggestions.suggest("업글구매", IslandCommandCatalog.SUBCOMMANDS).orElseThrow());
    }

    @Test
    void suggestsCloseEnglishSubcommands() {
        assertEquals("generator", suggestions.suggest("generatr", IslandCommandCatalog.SUBCOMMANDS).orElseThrow());
        assertEquals("withdraw", suggestions.suggest("withdra", IslandCommandCatalog.SUBCOMMANDS).orElseThrow());
    }

    @Test
    void ignoresDistantInput() {
        assertTrue(suggestions.suggest("zzzzzzzz", IslandCommandCatalog.SUBCOMMANDS).isEmpty());
    }
}
