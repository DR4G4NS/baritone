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

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HierarchicalPathPlannerTest {

    @Test
    public void longRouteProducesCorridorAndShortRouteFallsBack() {
        HierarchicalPathPlanner planner = new HierarchicalPathPlanner();
        HierarchicalPathPlanner.Corridor shortRoute = planner.plan(0, 0, 100, 0, (x, z) -> true);
        assertFalse(shortRoute.isPresent());

        HierarchicalPathPlanner.Corridor longRoute = planner.plan(0, 0, 640, 0, (x, z) -> true);
        assertTrue(longRoute.isPresent());
        assertTrue(longRoute.containsBlock(320, 0));
        assertTrue(planner.lastMetrics().abstractExpansions() > 0);
    }

    @Test
    public void invalidatedKnowledgeRepairsExistingLpaSession() {
        HierarchicalPathPlanner planner = new HierarchicalPathPlanner();
        Set<Long> unknown = new HashSet<>();
        HierarchicalPathPlanner.RegionKnowledge knowledge = (x, z) -> !unknown.contains(key(x, z));
        assertTrue(planner.plan(0, 0, 640, 0, knowledge).isPresent());

        int changedCenterX = centerOfRegion(5);
        int changedCenterZ = centerOfRegion(0);
        unknown.add(key(changedCenterX, changedCenterZ));
        planner.invalidateBlock(changedCenterX, changedCenterZ);

        assertTrue(planner.plan(0, 0, 640, 0, knowledge).isPresent());
        assertTrue(planner.lastMetrics().repairedExistingSearch());
        assertTrue(planner.lastMetrics().abstractExpansions() > 0);
    }

    @Test
    public void hugeSearchIsStrictlyBoundedAndUsesDetailedFallback() {
        HierarchicalPathPlanner planner = new HierarchicalPathPlanner();
        HierarchicalPathPlanner.Corridor corridor = planner.plan(-30_000_000, -30_000_000,
                30_000_000, 30_000_000, (x, z) -> true);
        assertFalse(corridor.isPresent());
        assertTrue(planner.lastMetrics().fallback());
    }

    private static int centerOfRegion(int region) {
        return region * HierarchicalPathPlanner.REGION_SIZE + HierarchicalPathPlanner.REGION_SIZE / 2;
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }
}
