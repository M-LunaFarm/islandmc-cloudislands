package kr.lunaf.cloudislands.velocity.command;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.velocitypowered.api.permission.Tristate;
import java.util.List;
import org.junit.jupiter.api.Test;

class VelocityCommandRegistrarTest {
    @Test
    void commandAliasesExcludePrimaryKoreanCommandAndDeduplicateCaseInsensitively() {
        assertArrayEquals(
            new String[] {"is", "island"},
            VelocityCommandRegistrar.commandAliasArray(List.of("is", "is", "IS", "island", "섬", "", " "))
        );
    }

    @Test
    void basePlayerCommandDefaultsToAllowedUnlessExplicitlyDenied() {
        assertTrue(VelocityCommandRegistrar.canUsePlayerCommands(Tristate.UNDEFINED));
        assertTrue(VelocityCommandRegistrar.canUsePlayerCommands(Tristate.TRUE));
        assertFalse(VelocityCommandRegistrar.canUsePlayerCommands(Tristate.FALSE));
    }
}
