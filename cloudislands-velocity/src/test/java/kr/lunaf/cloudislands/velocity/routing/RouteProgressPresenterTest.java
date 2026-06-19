package kr.lunaf.cloudislands.velocity.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class RouteProgressPresenterTest {
    @Test
    void formatsPreparingProgressPercent() {
        assertEquals("20", RouteProgressPresenter.progressValue(0));
        assertEquals("95", RouteProgressPresenter.progressValue(19));
        assertEquals("95", RouteProgressPresenter.progressValue(100));
    }
}
