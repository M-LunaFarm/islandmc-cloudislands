package kr.lunaf.cloudislands.velocity.command;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class PaperLocalCommandForwarderTest {
    @Test
    void forwardsEconomyInventoryAndLocationCommandsToPaper() {
        for (String command : List.of(
                "is deposit 100",
                "is withdraw 50",
                "섬 입금 100",
                "is sethome default",
                "is setwarp shop",
                "is warehouse-deposit DIAMOND 4",
                "is warehouse-withdraw DIAMOND 2",
                "is chest 2",
                "is vault",
                "is fly")) {
            assertTrue(PaperLocalCommandForwarder.shouldForward(command, List.of("is", "island", "섬")), command);
        }
    }

    @Test
    void supportsConfiguredRootsAndLeadingSlash() {
        assertTrue(PaperLocalCommandForwarder.shouldForward("/sky bank-deposit 10", List.of("sky")));
        assertTrue(PaperLocalCommandForwarder.shouldForward("SKY SETTELEPORT", List.of("sky")));
    }

    @Test
    void keepsGlobalAndReadOnlyCommandsOnVelocity() {
        for (String command : List.of(
                "is home",
                "is visit Steve",
                "is balance",
                "is warehouse-list",
                "is members",
                "ciadmin status",
                "say is deposit 100",
                "is")) {
            assertFalse(PaperLocalCommandForwarder.shouldForward(command, List.of("is", "island", "섬")), command);
        }
    }
}
