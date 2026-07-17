package kr.lunaf.cloudislands.paper.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import kr.lunaf.cloudislands.coreclient.CoreGuiViews;
import org.junit.jupiter.api.Test;

class IslandMembershipCommandHandlerTest {
    @Test
    void permissionExceptionCommandUsesMemberTargetAndPermission() {
        assertEquals(
            "/is permission-exception 00000000-0000-0000-0000-000000000001 BUILD allow",
            IslandMembershipCommandHandler.permissionExceptionCommand("00000000-0000-0000-0000-000000000001", "BUILD", "allow")
        );
        assertEquals(
            "/is permission-exception <player> <permission> <allow|deny>",
            IslandMembershipCommandHandler.permissionExceptionCommand("", "", "")
        );
    }

    @Test
    void uncoopOnlyAcceptsTrustedMembershipAndNeverPermanentTeamRoles() {
        UUID trusted = UUID.fromString("00000000-0000-0000-0000-000000000011");
        UUID member = UUID.fromString("00000000-0000-0000-0000-000000000012");
        List<CoreGuiViews.MemberView> members = List.of(
            new CoreGuiViews.MemberView(trusted.toString(), "TRUSTED", "", "Coop", "", "ONLINE", "", ""),
            new CoreGuiViews.MemberView(member.toString(), "MEMBER", "", "Member", "", "ONLINE", "", "")
        );

        assertTrue(IslandMembershipCommandHandler.isTemporaryCoop(members, trusted));
        assertFalse(IslandMembershipCommandHandler.isTemporaryCoop(members, member));
        assertFalse(IslandMembershipCommandHandler.isTemporaryCoop(null, trusted));
    }

    @Test
    void memberListsPreferProfileNamesAndFallBackToCompactUuids() {
        String playerUuid = "12345678-0000-0000-0000-000000000001";

        assertEquals("Builder", IslandMembershipCommandHandler.memberDisplayName(
            new CoreGuiViews.MemberView(playerUuid, "MEMBER", "", " Builder ", "", "ONLINE", "", "")
        ));
        assertEquals("12345678", IslandMembershipCommandHandler.memberDisplayName(
            new CoreGuiViews.MemberView(playerUuid, "MEMBER", "", "", "", "UNKNOWN", "", "")
        ));
        assertEquals("", IslandMembershipCommandHandler.memberDisplayName(null));
    }

    @Test
    void inviteListsPreferNamesAndFallBackToCompactUuids() {
        CoreGuiViews.InviteView named = new CoreGuiViews.InviteView(
            "aaaaaaaa-0000-0000-0000-000000000001",
            "bbbbbbbb-0000-0000-0000-000000000002",
            "cccccccc-0000-0000-0000-000000000003",
            "dddddddd-0000-0000-0000-000000000004",
            "PENDING",
            "",
            "",
            " Builders ",
            " Alice ",
            " Bob "
        );
        CoreGuiViews.InviteView legacy = new CoreGuiViews.InviteView(
            "aaaaaaaa-0000-0000-0000-000000000001",
            "bbbbbbbb-0000-0000-0000-000000000002",
            "cccccccc-0000-0000-0000-000000000003",
            "dddddddd-0000-0000-0000-000000000004",
            "PENDING",
            "",
            ""
        );

        assertEquals("Builders", IslandMembershipCommandHandler.inviteIslandDisplay(named));
        assertEquals("Alice", IslandMembershipCommandHandler.inviteInviterDisplay(named));
        assertEquals("Bob", IslandMembershipCommandHandler.inviteTargetDisplay(named));
        assertEquals("bbbbbbbb", IslandMembershipCommandHandler.inviteIslandDisplay(legacy));
        assertEquals("cccccccc", IslandMembershipCommandHandler.inviteInviterDisplay(legacy));
        assertEquals("dddddddd", IslandMembershipCommandHandler.inviteTargetDisplay(legacy));
        assertEquals("", IslandMembershipCommandHandler.inviteIslandDisplay(null));
        assertEquals("", IslandMembershipCommandHandler.inviteInviterDisplay(null));
        assertEquals("", IslandMembershipCommandHandler.inviteTargetDisplay(null));
    }

    @Test
    void banListsPreferProfileNamesAndFallBackToCompactUuids() {
        String bannedUuid = "12345678-0000-0000-0000-000000000001";

        assertEquals("Griefer", IslandMembershipCommandHandler.banDisplayName(
            new CoreGuiViews.BanView(bannedUuid, "", "spam", "", "", " Griefer ", "")
        ));
        assertEquals("12345678", IslandMembershipCommandHandler.banDisplayName(
            new CoreGuiViews.BanView(bannedUuid, "", "spam", "", "")
        ));
        assertEquals("", IslandMembershipCommandHandler.banDisplayName(null));
    }
}
