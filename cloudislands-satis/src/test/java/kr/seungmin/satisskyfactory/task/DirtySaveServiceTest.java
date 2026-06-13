package kr.seungmin.satisskyfactory.task;

import kr.seungmin.satisskyfactory.database.DatabaseService;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class DirtySaveServiceTest {
    @Test
    void ignoresNullQueueInputs() {
        DirtySaveService service = new DirtySaveService(null, new DatabaseService(new File("unused")));

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
