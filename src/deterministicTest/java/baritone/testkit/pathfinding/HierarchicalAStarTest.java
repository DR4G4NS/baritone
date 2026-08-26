/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package baritone.testkit.pathfinding;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HierarchicalAStarTest {

    @Test
    public void corridorRefinementKeepsDetailedOptimalCost() {
        MutableDirectedGraph graph = new MutableDirectedGraph();
        graph.updateEdge(0, 1, 1.0D);
        graph.updateEdge(1, 2, 1.0D);
        graph.updateEdge(2, 3, 1.0D);
        graph.updateEdge(3, 4, 1.0D);
        graph.updateEdge(4, 5, 1.0D);
        graph.updateEdge(0, 5, 20.0D);

        HierarchicalAStar.Result result = HierarchicalAStar.search(graph, 0, 5, node -> node / 2);

        assertEquals(Arrays.asList(0, 1, 2, 3, 4, 5), result.detailedPath());
        assertEquals(Arrays.asList(0, 1, 2), result.regionCorridor());
        assertEquals(5.0D, result.cost(), 0.0D);
        assertFalse(result.usedFallback());
    }

    @Test
    public void incompleteUnsafeAbstractionFallsBackToDetailedGraph() {
        MutableDirectedGraph graph = new MutableDirectedGraph();
        graph.addNode(0);
        graph.updateEdge(2, 3, 1.0D); // creates a tempting region 0 -> region 1 portal unreachable from start
        graph.updateEdge(0, 1, 1.0D);
        graph.updateEdge(1, 3, 3.0D);

        HierarchicalAStar.Result result = HierarchicalAStar.search(
                graph, 0, 3, node -> node == 1 ? 2 : node == 2 ? 0 : node / 2
        );

        assertEquals(Arrays.asList(0, 1, 3), result.detailedPath());
        assertEquals(4.0D, result.cost(), 0.0D);
        assertTrue(result.usedFallback());
    }
}
