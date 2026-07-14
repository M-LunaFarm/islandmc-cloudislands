package kr.lunaf.cloudislands.paper;

public final class PlayerFlightOwnershipPolicy {
    private PlayerFlightOwnershipPolicy() {
    }

    public static boolean claim(boolean currentlyAllowed, boolean personalFlightAllowed) {
        return personalFlightAllowed && !currentlyAllowed;
    }

    public static boolean revoke(boolean personallyManaged, boolean personalFlightAllowed, boolean adminFlightAllowed) {
        return personallyManaged && !personalFlightAllowed && !adminFlightAllowed;
    }
}
