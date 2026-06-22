package kr.lunaf.cloudislands.paper.command;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class IslandMembershipCommandHandlerTest {
    @Test
    void permissionExceptionCommandUsesMemberTargetAndPermission() {
        assertEquals(
            "/섬 권한예외 00000000-0000-0000-0000-000000000001 BUILD 허용",
            IslandMembershipCommandHandler.permissionExceptionCommand("00000000-0000-0000-0000-000000000001", "BUILD", "허용")
        );
        assertEquals(
            "/섬 권한예외 <player> <permission> <허용|거부>",
            IslandMembershipCommandHandler.permissionExceptionCommand("", "", "")
        );
    }
}
