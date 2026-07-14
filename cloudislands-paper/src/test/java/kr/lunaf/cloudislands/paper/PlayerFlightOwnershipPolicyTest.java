package kr.lunaf.cloudislands.paper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PlayerFlightOwnershipPolicyTest {
    @Test
    void onlyClaimsFlightThatCloudIslandsActuallyEnables() {
        assertTrue(PlayerFlightOwnershipPolicy.claim(false, true));
        assertFalse(PlayerFlightOwnershipPolicy.claim(true, true));
        assertFalse(PlayerFlightOwnershipPolicy.claim(false, false));
    }

    @Test
    void onlyRevokesOwnedFlightAndPreservesAdminOrExternalFlight() {
        assertTrue(PlayerFlightOwnershipPolicy.revoke(true, false, false));
        assertFalse(PlayerFlightOwnershipPolicy.revoke(false, false, false));
        assertFalse(PlayerFlightOwnershipPolicy.revoke(true, true, false));
        assertFalse(PlayerFlightOwnershipPolicy.revoke(true, false, true));
    }
}
