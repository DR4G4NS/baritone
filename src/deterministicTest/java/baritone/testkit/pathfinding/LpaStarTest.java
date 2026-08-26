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
import static org.junit.Assert.assertTrue;

public class LpaStarTest {

    @Test
    public void repairsChangedAbstractConnection() {
        MutableDirectedGraph graph = fixture();
        LpaStar planner = new LpaStar(graph, 0, 5, (node, goal) -> 0.0D);

        LpaStar.Result initial = planner.repair();
        assertEquals(Arrays.asList(0, 1, 2, 5), initial.path());
        assertEquals(3.0D, initial.cost(), 0.0D);

        planner.updateEdge(1, 2, Double.POSITIVE_INFINITY);
        LpaStar.Result repaired = planner.repair();

        assertEquals(Arrays.asList(0, 3, 4, 5), repaired.path());
        assertEquals(4.0D, repaired.cost(), 0.0D);
        assertTrue(repaired.expansions() > 0);
        assertTrue(repaired.expansions() < graph.size());
    }

    @Test
    public void restoresCheaperConnectionWithoutNewPlanner() {
        MutableDirectedGraph graph = fixture();
        LpaStar planner = new LpaStar(graph, 0, 5, (node, goal) -> 0.0D);
        planner.repair();
        planner.updateEdge(1, 2, Double.POSITIVE_INFINITY);
        planner.repair();

        planner.updateEdge(1, 2, 1.0D);
        LpaStar.Result repaired = planner.repair();

        assertEquals(Arrays.asList(0, 1, 2, 5), repaired.path());
        assertEquals(3.0D, repaired.cost(), 0.0D);
    }

    private static MutableDirectedGraph fixture() {
        MutableDirectedGraph graph = new MutableDirectedGraph();
        graph.updateEdge(0, 1, 1.0D);
        graph.updateEdge(1, 2, 1.0D);
        graph.updateEdge(2, 5, 1.0D);
        graph.updateEdge(0, 3, 1.0D);
        graph.updateEdge(3, 4, 1.0D);
        graph.updateEdge(4, 5, 2.0D);
        return graph;
    }
}
