package kr.lunaf.cloudislands.paper.placeholder;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class CloudIslandsPlaceholderValuesTest {
    @Test
    void exposesTeamAndCoopValuesWithoutTreatingTemporaryTrustAsMembership() {
        CloudIslandsPlaceholderValues.Data data = data("TRUSTED");

        assertEquals("false", value(data, "has_island"));
        assertEquals("true", value(data, "has_associated_island"));
        assertEquals("true", value(data, "is_coop"));
        assertEquals("false", value(data, "is_member"));
        assertEquals("TRUSTED", value(data, "island_role"));
        assertEquals("2", value(data, "team_size"));
        assertEquals("1", value(data, "coop_size"));
        assertEquals("Owner, member-uuid", value(data, "team_list"));
        assertEquals("Coop", value(data, "coop_list"));
        assertEquals("3", value(data, "team_limit"));
        assertEquals("8", value(data, "coop_limit"));
        assertEquals("true", value(data, "locked"));
    }

    @Test
    void exposesOwnerAndTimestampAliases() {
        CloudIslandsPlaceholderValues.Data data = data("owner");

        assertEquals("true", value(data, "has_island"));
        assertEquals("true", value(data, "is_leader"));
        assertEquals("2026-07-01T00:00:00Z", value(data, "creation_time"));
        assertEquals("2026-07-02T00:00:00Z", value(data, "last_time_updated"));
        assertEquals("4", value(data, "rank"));
        assertEquals("5", value(data, "level_rank"));
    }

    @Test
    void returnsStableMissingBooleans() {
        assertEquals("false", value(null, "has_island"));
        assertEquals("false", value(null, "is_coop"));
        assertEquals("", value(null, "island_name"));
    }

    private static String value(CloudIslandsPlaceholderValues.Data data, String key) {
        return CloudIslandsPlaceholderValues.value(data, key);
    }

    private static CloudIslandsPlaceholderValues.Data data(String role) {
        return new CloudIslandsPlaceholderValues.Data(
            "island-id", "Island", "owner-uuid", "ACTIVE", 100, 50, 12, "123.45", true, true,
            "2026-07-01T00:00:00Z", "2026-07-02T00:00:00Z", "50.00", role,
            List.of(
                new CloudIslandsPlaceholderValues.Member("member-uuid", "", "MEMBER"),
                new CloudIslandsPlaceholderValues.Member("owner-uuid", "Owner", "OWNER"),
                new CloudIslandsPlaceholderValues.Member("coop-uuid", "Coop", "TRUSTED")
            ), 3, 8, 4, 5);
    }
}
