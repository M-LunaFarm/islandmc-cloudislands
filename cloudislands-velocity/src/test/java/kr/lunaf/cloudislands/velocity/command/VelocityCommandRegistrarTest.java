package kr.lunaf.cloudislands.velocity.command;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

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
}
