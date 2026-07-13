package kr.lunaf.cloudislands.paper.command;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class CurrentIslandVisitorPolicyTest {
    private static final UUID ISLAND = UUID.fromString("00000000-0000-0000-0000-000000000176");

    @Test
    void includesGuestsAndTemporaryCoopsStandingOnTheIsland() {
        assertTrue(CurrentIslandVisitorPolicy.visitor(ISLAND, ISLAND, ""));
        assertTrue(CurrentIslandVisitorPolicy.visitor(ISLAND, ISLAND, "trusted"));
    }

    @Test
    void excludesPermanentTeamRolesAndPlayersOnAnotherIsland() {
        assertFalse(CurrentIslandVisitorPolicy.visitor(ISLAND, ISLAND, "OWNER"));
        assertFalse(CurrentIslandVisitorPolicy.visitor(ISLAND, ISLAND, "MEMBER"));
        assertFalse(CurrentIslandVisitorPolicy.visitor(ISLAND, ISLAND, "BUILDER"));
        assertFalse(CurrentIslandVisitorPolicy.visitor(ISLAND, UUID.randomUUID(), ""));
        assertFalse(CurrentIslandVisitorPolicy.visitor(null, ISLAND, ""));
    }
}
