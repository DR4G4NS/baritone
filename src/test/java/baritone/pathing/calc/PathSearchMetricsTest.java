/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package baritone.pathing.calc;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PathSearchMetricsTest {

    @Test
    public void estimatesRetainedSearchMemoryWithoutOverflow() {
        PathSearchMetrics metrics = PathSearchMetrics.create(
                PathSearchMetrics.Outcome.PARTIAL,
                12L,
                Integer.MAX_VALUE,
                20,
                3,
                Integer.MAX_VALUE,
                Integer.MAX_VALUE,
                42.0D
        );

        assertEquals(PathSearchMetrics.Outcome.PARTIAL, metrics.outcome());
        assertEquals(42.0D, metrics.finalCost(), 0.0D);
        assertTrue(metrics.estimatedRetainedBytes() > 0L);
    }

    @Test
    public void notStartedMetricsAreExplicit() {
        PathSearchMetrics metrics = PathSearchMetrics.notStarted();

        assertEquals(PathSearchMetrics.Outcome.NOT_STARTED, metrics.outcome());
        assertEquals(0L, metrics.elapsedNanos());
        assertEquals(Double.POSITIVE_INFINITY, metrics.finalCost(), 0.0D);
    }
}
