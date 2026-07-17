package kr.lunaf.cloudislands.paper.command;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import kr.lunaf.cloudislands.paper.application.PermissionManagementUseCase.PermissionActionResult;
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
        UUID fallbackUuid = UUID.fromString("44444444-4444-4444-4444-444444444444");
        assertEquals(
            "BuilderPlayer",
            IslandPermissionCommandHandler.permissionOverrideDisplayName(new PermissionActionResult(true, "PERMISSION_OVERRIDE_SET", fallbackUuid.toString(), " BuilderPlayer "), null)
        );
        assertEquals(
            "44444444",
            IslandPermissionCommandHandler.permissionOverrideDisplayName(new PermissionActionResult(true, "PERMISSION_OVERRIDE_SET"), fallbackUuid)
        );
    }
}
