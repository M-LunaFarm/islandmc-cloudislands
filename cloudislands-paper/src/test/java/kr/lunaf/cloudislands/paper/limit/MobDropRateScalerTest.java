package kr.lunaf.cloudislands.paper.limit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class MobDropRateScalerTest {
    @Test
    void splitsAmountsAboveTheMinecraftStackLimitWithoutLosingItems() {
        List<Integer> amounts = MobDropRateScaler.splitAmounts(64, 64, 250L, 99, 100);

        assertEquals(List.of(64, 64, 32), amounts);
        assertEquals(160, amounts.stream().mapToInt(Integer::intValue).sum());
    }

    @Test
    void usesDeterministicFractionalRoundingAndZeroCanDisableDrops() {
        assertEquals(1L, MobDropRateScaler.scaledAmount(1, 50L, 49));
        assertEquals(0L, MobDropRateScaler.scaledAmount(1, 50L, 50));

        assertEquals(List.of(), MobDropRateScaler.splitAmounts(3, 64, 0L, 0, 100));
    }

    @Test
    void clampsUntrustedPersistedRatesToTheOperationalSafetyLimit() {
        assertEquals(0L, MobDropRateScaler.normalizePercent(-1L));
        assertEquals(10_000L, MobDropRateScaler.normalizePercent(Long.MAX_VALUE));
        assertEquals(100L, MobDropRateScaler.scaledAmount(1, Long.MAX_VALUE, 99));
        assertEquals(List.of(64, 36), MobDropRateScaler.splitAmounts(1, 64, Long.MAX_VALUE, 99, 2));
    }
}
