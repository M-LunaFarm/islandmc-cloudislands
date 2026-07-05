package kr.lunaf.cloudislands.paper.command;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
