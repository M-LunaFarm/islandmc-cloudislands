package kr.lunaf.cloudislands.paper.command;

import static org.junit.jupiter.api.Assertions.assertEquals;

import kr.lunaf.cloudislands.paper.application.PermissionManagementUseCase.PermissionView;
import org.junit.jupiter.api.Test;

class IslandPermissionCommandHandlerTest {
    @Test
    void permissionOverrideUsesProfileNameWithUuidFallback() {
        assertEquals(
            "BuilderPlayer",
            IslandPermissionCommandHandler.permissionDisplayName(new PermissionView("", "33333333-3333-3333-3333-333333333333", "BREAK", false, " BuilderPlayer "))
        );
        assertEquals(
            "33333333",
            IslandPermissionCommandHandler.permissionDisplayName(new PermissionView("", "33333333-3333-3333-3333-333333333333", "BREAK", false))
        );
    }
}
