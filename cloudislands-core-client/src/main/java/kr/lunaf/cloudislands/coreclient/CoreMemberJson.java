package kr.lunaf.cloudislands.coreclient;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandBanSnapshot;
import kr.lunaf.cloudislands.api.model.IslandInviteSnapshot;
import kr.lunaf.cloudislands.api.model.IslandMemberSnapshot;
import kr.lunaf.cloudislands.api.model.IslandRole;

final class CoreMemberJson {
    private static final UUID EMPTY_UUID = new UUID(0L, 0L);

    private CoreMemberJson() {
    }

    static List<IslandMemberSnapshot> members(UUID islandId, String body) {
        return CoreJson.entries(body).stream()
            .map(values -> member(islandId, values))
            .filter(member -> member.playerUuid() != null && !member.playerUuid().equals(EMPTY_UUID))
            .toList();
    }

    static List<IslandInviteSnapshot> invites(String body) {
        return CoreJson.entries(body).stream()
            .map(CoreMemberJson::invite)
            .filter(invite -> invite.inviteId() != null && !invite.inviteId().equals(EMPTY_UUID))
            .toList();
    }

    static List<IslandBanSnapshot> bans(UUID islandId, String body) {
        return CoreJson.entries(body).stream()
            .map(values -> ban(islandId, values))
            .filter(ban -> ban.bannedUuid() != null && !ban.bannedUuid().equals(EMPTY_UUID))
            .toList();
    }

    static CoreGuiViews.MemberView memberView(IslandMemberSnapshot member) {
        return new CoreGuiViews.MemberView(
            member == null || member.playerUuid() == null || member.playerUuid().equals(EMPTY_UUID) ? "" : member.playerUuid().toString(),
            member == null ? "" : member.effectiveRoleKey(),
            member == null || member.joinedAt() == null || member.joinedAt().equals(Instant.EPOCH) ? "" : member.joinedAt().toString(),
            "",
            "",
            "",
            "",
            member == null || member.expiresAt() == null || member.expiresAt().equals(Instant.EPOCH) ? "" : member.expiresAt().toString()
        );
    }

    static CoreGuiViews.InviteView inviteView(IslandInviteSnapshot invite) {
        return new CoreGuiViews.InviteView(
            invite == null || invite.inviteId() == null || invite.inviteId().equals(EMPTY_UUID) ? "" : invite.inviteId().toString(),
            invite == null || invite.islandId() == null || invite.islandId().equals(EMPTY_UUID) ? "" : invite.islandId().toString(),
            invite == null || invite.inviterUuid() == null || invite.inviterUuid().equals(EMPTY_UUID) ? "" : invite.inviterUuid().toString(),
            invite == null || invite.targetUuid() == null || invite.targetUuid().equals(EMPTY_UUID) ? "" : invite.targetUuid().toString(),
            invite == null || invite.state() == null || invite.state().isBlank() ? "PENDING" : invite.state(),
            invite == null || invite.createdAt() == null || invite.createdAt().equals(Instant.EPOCH) ? "" : invite.createdAt().toString(),
            invite == null || invite.expiresAt() == null || invite.expiresAt().equals(Instant.EPOCH) ? "" : invite.expiresAt().toString()
        );
    }

    static CoreGuiViews.BanView banView(IslandBanSnapshot ban) {
        return new CoreGuiViews.BanView(
            ban == null || ban.bannedUuid() == null || ban.bannedUuid().equals(EMPTY_UUID) ? "" : ban.bannedUuid().toString(),
            ban == null || ban.actorUuid() == null || ban.actorUuid().equals(EMPTY_UUID) ? "" : ban.actorUuid().toString(),
            ban == null ? "" : ban.reason(),
            ban == null || ban.createdAt() == null || ban.createdAt().equals(Instant.EPOCH) ? "" : ban.createdAt().toString(),
            ban == null || ban.expiresAt() == null || ban.expiresAt().equals(Instant.EPOCH) ? "" : ban.expiresAt().toString()
        );
    }

    private static IslandMemberSnapshot member(UUID islandId, Map<?, ?> values) {
        String roleKey = CoreJson.firstText(values, "roleKey", "role");
        return new IslandMemberSnapshot(
            islandId == null ? uuid(CoreJson.text(values, "islandId")) : islandId,
            uuid(CoreJson.text(values, "playerUuid")),
            parseRole(roleKey),
            instant(CoreJson.text(values, "joinedAt")),
            nullableInstant(CoreJson.text(values, "expiresAt")),
            roleKey
        );
    }

    private static IslandInviteSnapshot invite(Map<?, ?> values) {
        return new IslandInviteSnapshot(
            uuid(CoreJson.text(values, "inviteId")),
            uuid(CoreJson.text(values, "islandId")),
            uuid(CoreJson.text(values, "inviterUuid")),
            uuid(CoreJson.text(values, "targetUuid")),
            CoreJson.text(values, "state").isBlank() ? "PENDING" : CoreJson.text(values, "state"),
            instant(CoreJson.text(values, "createdAt")),
            nullableInstant(CoreJson.text(values, "expiresAt"))
        );
    }

    private static IslandBanSnapshot ban(UUID islandId, Map<?, ?> values) {
        return new IslandBanSnapshot(
            islandId == null ? uuid(CoreJson.text(values, "islandId")) : islandId,
            uuid(CoreJson.firstText(values, "bannedUuid", "playerUuid")),
            uuid(CoreJson.text(values, "actorUuid")),
            CoreJson.text(values, "reason"),
            instant(CoreJson.text(values, "createdAt")),
            nullableInstant(CoreJson.text(values, "expiresAt"))
        );
    }

    private static IslandRole parseRole(String roleKey) {
        try {
            return roleKey == null || roleKey.isBlank() ? IslandRole.VISITOR : IslandRole.valueOf(roleKey.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static UUID uuid(String value) {
        try {
            return value == null || value.isBlank() ? EMPTY_UUID : UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            return EMPTY_UUID;
        }
    }

    private static Instant instant(String value) {
        Instant parsed = nullableInstant(value);
        return parsed == null ? Instant.EPOCH : parsed;
    }

    private static Instant nullableInstant(String value) {
        try {
            return value == null || value.isBlank() ? null : Instant.parse(value);
        } catch (RuntimeException exception) {
            return null;
        }
    }
}
