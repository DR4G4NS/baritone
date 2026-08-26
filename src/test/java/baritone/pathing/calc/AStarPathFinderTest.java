/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package baritone.pathing.calc;

import baritone.api.pathing.calc.IPath;
import baritone.api.pathing.goals.Goal;
import baritone.api.utils.BetterBlockPos;
import baritone.api.utils.PathCalculationResult;
import org.junit.Test;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class AStarPathFinderTest {

    @Test
    public void distanceSquaredDoesNotOverflowBeforeConversion() {
        double expected = 3.0D * 4_294_967_295.0D * 4_294_967_295.0D;
        assertEquals(
                expected,
                AbstractNodeCostSearch.distanceSquared(
                        Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE,
                        Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE
                ),
                expected * 1.0E-15D
        );
    }

    @Test
    public void verticalBoundsUseMinYAndExclusiveMaximum() {
        int minY = -64;
        long maxYExclusive = (long) minY + 384;

        assertFalse(AStarPathFinder.isYInBounds(minY - 1L, minY, maxYExclusive));
        assertTrue(AStarPathFinder.isYInBounds(minY, minY, maxYExclusive));
        assertTrue(AStarPathFinder.isYInBounds(maxYExclusive - 1L, minY, maxYExclusive));
        assertFalse(AStarPathFinder.isYInBounds(maxYExclusive, minY, maxYExclusive));
    }

    @Test
    public void verticalMaximumCalculationDoesNotOverflow() {
        int minY = Integer.MAX_VALUE - 5;
        long maxYExclusive = (long) minY + 10;

        assertTrue(maxYExclusive > Integer.MAX_VALUE);
        assertTrue(AStarPathFinder.isYInBounds(Integer.MAX_VALUE, minY, maxYExclusive));
        assertFalse(AStarPathFinder.isYInBounds(maxYExclusive, minY, maxYExclusive));
    }

    @Test
    public void nodeMapKeepsKnownLongHashCollisionSeparate() {
        TestNodeSearch search = new TestNodeSearch();
        BetterBlockPos first = new BetterBlockPos(0, 0, 0);
        BetterBlockPos second = new BetterBlockPos(0, 1, -2_873_465);
        assertEquals(BetterBlockPos.longHash(first), BetterBlockPos.longHash(second));

        PathNode firstNode = search.node(first);
        PathNode secondNode = search.node(second);

        assertNotSame(firstNode, secondNode);
        assertEquals(2, search.mapSize());
    }

    @Test
    public void dynamicDestinationsAreCheckedAgainstTheirActualChunk() {
        assertFalse(AStarPathFinder.isDifferentChunk(15, 0, 15, 3));
        assertTrue(AStarPathFinder.isDifferentChunk(15, 0, 16, 3));
        assertTrue(AStarPathFinder.isDifferentChunk(-16, 0, -17, 0));
    }

    @Test
    public void timeoutConversionKeepsMillisecondsAndCannotOverflowElapsedComparison() {
        assertEquals(TimeUnit.MILLISECONDS.toNanos(500), AStarPathFinder.timeoutToNanos(500));
        assertEquals(Long.MAX_VALUE >>> 1, AStarPathFinder.timeoutToNanos(Long.MAX_VALUE));
        assertThrows(IllegalArgumentException.class, () -> AStarPathFinder.timeoutToNanos(-1));
    }

    @Test
    public void cancellationIsVisibleAcrossThreads() throws Exception {
        CancellableSearch search = new CancellableSearch();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<PathCalculationResult> result = executor.submit(() -> search.calculate(1_000L, 1_000L));
            assertTrue(search.started.await(5L, TimeUnit.SECONDS));

            search.cancel();

            assertEquals(PathCalculationResult.Type.CANCELLATION, result.get(5L, TimeUnit.SECONDS).getType());
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5L, TimeUnit.SECONDS));
        }
    }

    private static final class TestNodeSearch extends AbstractNodeCostSearch {

        private TestNodeSearch() {
            super(BetterBlockPos.ORIGIN, 0, 0, 0, new Goal() {
                @Override
                public boolean isInGoal(int x, int y, int z) {
                    return false;
                }

                @Override
                public double heuristic(int x, int y, int z) {
                    return 0;
                }
            }, null, 16, 0.75F);
        }

        private PathNode node(BetterBlockPos pos) {
            return getNodeAtPosition(pos.x, pos.y, pos.z, BetterBlockPos.serializeToLong(pos.x, pos.y, pos.z));
        }

        @Override
        protected Optional<IPath> calculate0(long primaryTimeout, long failureTimeout) {
            return Optional.empty();
        }
    }

    private static final class CancellableSearch extends AbstractNodeCostSearch {

        private final CountDownLatch started = new CountDownLatch(1);

        private CancellableSearch() {
            super(BetterBlockPos.ORIGIN, 0, 0, 0, NEVER_GOAL, null, 16, 0.75F);
        }

        @Override
        protected Optional<IPath> calculate0(long primaryTimeout, long failureTimeout) {
            started.countDown();
            while (!cancelRequested) {
                Thread.onSpinWait();
            }
            return Optional.empty();
        }
    }

    private static final Goal NEVER_GOAL = new Goal() {
        @Override
        public boolean isInGoal(int x, int y, int z) {
            return false;
        }

        @Override
        public double heuristic(int x, int y, int z) {
            return 0.0D;
        }
    };
}
