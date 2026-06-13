package kr.seungmin.satisskyfactory.task;

import kr.seungmin.satisskyfactory.database.DatabaseService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class DirtySaveServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void ignoresNullQueueInputs() {
        DirtySaveService service = new DirtySaveService(null, new DatabaseService(tempDir.resolve("unused").toFile()));

        assertDoesNotThrow(() -> service.markMachine(null));
        assertDoesNotThrow(() -> service.forgetMachine(null));
        assertDoesNotThrow(() -> service.markInventory(null));
        assertDoesNotThrow(() -> service.forgetInventory(null));
        assertDoesNotThrow(() -> service.markNode(null));
        assertDoesNotThrow(() -> service.markIsland(null));
        assertDoesNotThrow(() -> service.forgetIsland(null));
        assertDoesNotThrow(() -> service.flushIslandSafely((UUID) null));
    }
}
