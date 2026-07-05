package kr.lunaf.cloudislands.paper.command;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import org.junit.jupiter.api.Test;

class IslandCommandMessagesTest {
    @Test
    void playerCodeMessagesUseLocalizedFailureKeysAndHints() {
        Map<String, String> messages = Map.of(
            "failure-code-template-permission-denied", "Template locked.",
            "failure-code-template-permission-denied-hint", " Choose another template."
        );

        assertEquals(
            "Template locked. Choose another template.",
            IslandCommandMessages.playerCodeMessage("TEMPLATE_PERMISSION_DENIED", "fallback", (key, fallback) -> messages.getOrDefault(key, fallback))
        );
    }

    @Test
    void unknownCodesKeepTheProvidedFallback() {
        assertEquals(
            "fallback",
            IslandCommandMessages.playerCodeMessage("UNKNOWN_LOCAL_CODE", "fallback", (_key, fallback) -> fallback)
        );
    }
}
