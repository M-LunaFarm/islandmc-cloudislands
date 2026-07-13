package kr.lunaf.cloudislands.paper.level;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class IslandScanCursorTest {
    @Test
    void visitsEveryCoordinateExactlyOnceAcrossBatchBoundaries() {
        IslandScanCursor cursor = new IslandScanCursor(10, 11, -1, 1, 20, 21);
        List<String> visited = new ArrayList<>();

        while (cursor.hasNext()) {
            for (int batch = 0; batch < 5 && cursor.hasNext(); batch++) {
                visited.add(cursor.x() + ":" + cursor.y() + ":" + cursor.z());
                cursor.advance();
            }
        }

        assertEquals(12, visited.size());
        assertEquals(12, visited.stream().distinct().count());
        assertEquals("10:-1:20", visited.getFirst());
        assertEquals("11:1:21", visited.getLast());
        assertFalse(cursor.hasNext());
    }

    @Test
    void checksHorizontalIslandBoundsForFurniture() {
        IslandScanCursor cursor = new IslandScanCursor(-2, 2, -64, 319, -3, 3);

        assertTrue(cursor.contains(-2, -3));
        assertTrue(cursor.contains(2, 3));
        assertFalse(cursor.contains(-3, 0));
        assertFalse(cursor.contains(0, 4));
    }
}
