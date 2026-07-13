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
        assertEquals("2", value(data, "member_count"));
        assertEquals("1", value(data, "coop_size"));
        assertEquals("1", value(data, "coop_count"));
        assertEquals("Owner, member-uuid", value(data, "team_list"));
        assertEquals("Coop", value(data, "coop_list"));
        assertEquals("Owner", value(data, "leader"));
        assertEquals("Owner", value(data, "member_0"));
        assertEquals("Owner", value(data, "member_1"));
        assertEquals("member-uuid", value(data, "member_2"));
        assertEquals("", value(data, "member_3"));
        assertEquals("member-uuid", value(data, "member_index_1"));
        assertEquals("Coop", value(data, "coop_1"));
        assertEquals("1", value(data, "team_size_online"));
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
        assertEquals("12", value(data, "level_int"));
        assertEquals("123", value(data, "worth_int"));
        assertEquals("123.45", value(data, "worth_raw"));
        assertEquals("1.9M", CloudIslandsPlaceholderValues.value(dataWithWorth("1900000"), "worth_format"));
    }

    @Test
    void returnsStableMissingBooleans() {
        assertEquals("false", value(null, "has_island"));
        assertEquals("false", value(null, "is_coop"));
        assertEquals("", value(null, "island_name"));
    }

    @Test
    void exposesCoreBackedBiomeBanHomeWarpAndUpgradeValues() {
        CloudIslandsPlaceholderValues.Data source = data("OWNER");
        CloudIslandsPlaceholderValues.Data extended = new CloudIslandsPlaceholderValues.Data(
            source.islandId(), source.name(), source.ownerUuid(), source.state(), source.size(), source.border(), source.level(),
            source.worth(), source.publicAccess(), source.locked(), source.createdAt(), source.updatedAt(), source.bankBalance(),
            source.role(), source.members(), source.memberLimit(), source.coopLimit(), source.worthRank(), source.levelRank(), 6,
            "minecraft:plains", List.of("banned-b", "banned-a"),
            new CloudIslandsPlaceholderValues.Home("island_world", 10.5D, 64.0D, -3.25D), 2, 5L,
            List.of(new CloudIslandsPlaceholderValues.Upgrade("border-size", 3)), "owner-uuid",
            List.of(
                new CloudIslandsPlaceholderValues.Permission("OWNER", "owner-uuid", "BREAK", false),
                new CloudIslandsPlaceholderValues.Permission("OWNER", "", "MANAGE_WARPS", true)
            ), java.util.Map.of("ALWAYS_DAY", "enabled", "PVP", "false"));

        assertEquals("minecraft:plains", value(extended, "biome"));
        assertEquals("2", value(extended, "bans_count"));
        assertEquals("banned-a, banned-b", value(extended, "bans_list"));
        assertEquals("banned-a", value(extended, "ban_1"));
        assertEquals("banned-b", value(extended, "ban_2"));
        assertEquals("island_world, 10.5, 64, -3.25", value(extended, "home"));
        assertEquals("10.5", value(extended, "home_x"));
        assertEquals("64", value(extended, "home_y"));
        assertEquals("island_world", value(extended, "world"));
        assertEquals("2", value(extended, "warps"));
        assertEquals("5", value(extended, "warps_limit"));
        assertEquals("3", value(extended, "upgrade_border_size"));
        assertEquals("0", value(extended, "upgrade_missing"));
        assertEquals("false", value(extended, "permission_break"));
        assertEquals("true", value(extended, "permission_manage_warps"));
        assertEquals("true", value(extended, "flag_always_day"));
        assertEquals("false", value(extended, "flag_pvp"));
        assertEquals("false", value(extended, "permission_not_real"));
        assertEquals("6", value(extended, "bank_rank"));
    }

    @Test
    void exposesPlayerChatStateWithoutIslandData() {
        assertEquals("GLOBAL", CloudIslandsPlaceholderValues.playerChatValue("chat_state", "GLOBAL"));
        assertEquals("true", CloudIslandsPlaceholderValues.playerChatValue("local_chat", "ISLAND"));
        assertEquals("false", CloudIslandsPlaceholderValues.playerChatValue("team_chat", "ISLAND"));
        assertEquals("true", CloudIslandsPlaceholderValues.playerChatValue("player_team_chat", "TEAM"));
        assertEquals(null, CloudIslandsPlaceholderValues.playerChatValue("island_name", "TEAM"));
    }

    private static String value(CloudIslandsPlaceholderValues.Data data, String key) {
        return CloudIslandsPlaceholderValues.value(data, key);
    }

    private static CloudIslandsPlaceholderValues.Data data(String role) {
        return new CloudIslandsPlaceholderValues.Data(
            "island-id", "Island", "owner-uuid", "ACTIVE", 100, 50, 12, "123.45", true, true,
            "2026-07-01T00:00:00Z", "2026-07-02T00:00:00Z", "50.00", role,
            List.of(
                new CloudIslandsPlaceholderValues.Member("member-uuid", "", "MEMBER", "OFFLINE"),
                new CloudIslandsPlaceholderValues.Member("owner-uuid", "Owner", "OWNER", "ONLINE"),
                new CloudIslandsPlaceholderValues.Member("coop-uuid", "Coop", "TRUSTED", "ONLINE")
            ), 3, 8, 4, 5);
    }

    private static CloudIslandsPlaceholderValues.Data dataWithWorth(String worth) {
        CloudIslandsPlaceholderValues.Data source = data("OWNER");
        return new CloudIslandsPlaceholderValues.Data(source.islandId(), source.name(), source.ownerUuid(), source.state(), source.size(), source.border(), source.level(),
            worth, source.publicAccess(), source.locked(), source.createdAt(), source.updatedAt(), source.bankBalance(), source.role(), source.members(),
            source.memberLimit(), source.coopLimit(), source.worthRank(), source.levelRank());
    }
}
