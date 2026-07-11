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
    void forwardsOfficialMenuCommandsWithoutReplacingTargetedProxyExtensions() {
        for (String command : List.of(
                "is bank",
                "is bank logs",
                "is biome",
                "is border",
                "is homes",
                "is warps",
                "is visitors",
                "is members",
                "is settings",
                "is permissions",
                "is upgrade",
                "is top",
                "is ratings",
                "is values",
                "is panel")) {
            assertTrue(PaperLocalCommandForwarder.shouldForward(command, List.of("is")), command);
        }
        for (String command : List.of(
                "is members OtherIsland",
                "is warps 00000000-0000-0000-0000-000000000001",
                "is values Steve 20",
                "is ratings 50")) {
            assertFalse(PaperLocalCommandForwarder.shouldForward(command, List.of("is")), command);
        }
    }

    @Test
    void keepsGlobalAndReadOnlyCommandsOnVelocity() {
        for (String command : List.of(
                "is home",
                "is visit Steve",
                "is balance",
                "is warehouse-list",
                "ciadmin status",
                "say is deposit 100",
                "is")) {
            assertFalse(PaperLocalCommandForwarder.shouldForward(command, List.of("is", "island", "섬")), command);
        }
    }
}
