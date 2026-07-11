package kr.lunaf.cloudislands.paper.placeholder;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

final class CloudIslandsPlaceholderValues {
    private CloudIslandsPlaceholderValues() {
    }

    static String value(Data data, String params) {
        String key = params == null ? "" : params.toLowerCase(Locale.ROOT).replace('-', '_');
        if (data == null) {
            return booleanWhenMissing(key) ? "false" : "";
        }
        String role = normalize(data.role());
        boolean coop = "TRUSTED".equals(role);
        boolean owner = "OWNER".equals(role);
        boolean member = !role.isBlank() && !coop;
        List<Member> team = data.members().stream().filter(item -> !"TRUSTED".equals(normalize(item.role()))).toList();
        List<Member> coops = data.members().stream().filter(item -> "TRUSTED".equals(normalize(item.role()))).toList();
        return switch (key) {
            case "has_island" -> Boolean.toString(member);
            case "has_associated_island" -> "true";
            case "island_id", "id" -> data.islandId();
            case "island_name", "name" -> data.name();
            case "owner_uuid", "owner" -> data.ownerUuid();
            case "state", "island_state" -> data.state();
            case "size", "island_size" -> Long.toString(data.size());
            case "border", "island_border" -> Long.toString(data.border());
            case "level", "island_level" -> Long.toString(data.level());
            case "worth", "value", "island_worth" -> data.worth();
            case "rank", "worth_rank", "island_rank", "island_worth_rank" -> rank(data.worthRank());
            case "level_rank", "island_level_rank" -> rank(data.levelRank());
            case "public", "public_access", "is_public" -> Boolean.toString(data.publicAccess());
            case "locked", "island_locked" -> Boolean.toString(data.locked());
            case "bank", "bank_balance", "balance" -> data.bankBalance();
            case "role", "island_role" -> role;
            case "is_member", "island_is_member" -> Boolean.toString(member);
            case "is_coop", "island_is_coop" -> Boolean.toString(coop);
            case "is_owner", "is_leader", "island_is_owner", "island_is_leader" -> Boolean.toString(owner);
            case "member_count", "team_size", "island_team_size" -> Integer.toString(team.size());
            case "coop_count", "coop_size", "island_coop_size" -> Integer.toString(coops.size());
            case "member_limit", "team_limit", "island_team_limit" -> Long.toString(data.memberLimit());
            case "coop_limit", "island_coop_limit" -> Long.toString(data.coopLimit());
            case "member_list", "team_list" -> names(team);
            case "coop_list" -> names(coops);
            case "created_at", "creation_time" -> data.createdAt();
            case "updated_at", "last_time_updated" -> data.updatedAt();
            default -> "";
        };
    }

    private static boolean booleanWhenMissing(String key) {
        return key.equals("has_island") || key.equals("has_associated_island") || key.startsWith("is_") || key.startsWith("island_is_");
    }

    private static String names(List<Member> members) {
        return members.stream()
            .sorted(Comparator.comparingInt((Member member) -> "OWNER".equals(normalize(member.role())) ? 0 : 1)
                .thenComparing(Member::displayName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(Member::playerUuid))
            .map(Member::displayName)
            .filter(name -> !name.isBlank())
            .reduce((left, right) -> left + ", " + right)
            .orElse("");
    }

    private static String normalize(String role) {
        return role == null ? "" : role.trim().toUpperCase(Locale.ROOT);
    }

    private static String rank(int rank) {
        return rank <= 0 ? "" : Integer.toString(rank);
    }

    record Member(String playerUuid, String playerName, String role) {
        String displayName() {
            return playerName == null || playerName.isBlank() ? (playerUuid == null ? "" : playerUuid) : playerName;
        }
    }

    record Data(String islandId, String name, String ownerUuid, String state, long size, long border, long level,
                String worth, boolean publicAccess, boolean locked, String createdAt, String updatedAt,
                String bankBalance, String role, List<Member> members, long memberLimit, long coopLimit,
                int worthRank, int levelRank) {
        Data {
            members = members == null ? List.of() : List.copyOf(members);
        }
    }
}
