package baritone;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BaritoneExecutorTest {
    @Test
    public void globalExecutorHasStrictThreadAndQueueBounds() {
        assertEquals(4, Baritone.getExecutorMaximumPoolSize());
        assertTrue(Baritone.getExecutorQueueCapacity() > 0);
        assertTrue(Baritone.getExecutorQueueCapacity() <= 1024);
    }
}
