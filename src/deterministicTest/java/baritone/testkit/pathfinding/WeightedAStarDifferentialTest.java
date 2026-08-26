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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class WeightedAStarDifferentialTest {

    private static final double EPSILON = 1.0E-9;

    @Test
    public void weightOneMatchesReferenceDijkstra() {
        for (int index = 0; index < 32; index++) {
            TestGraphs.GridGraph fixture = TestGraphs.directedGrid(8, 8, TestkitSeeds.PATHFINDING + index);
            SearchResult expected = ReferenceDijkstra.search(fixture.graph(), fixture.start(), fixture.goal());
            SearchResult actual = WeightedAStar.search(
                    fixture.graph(), fixture.start(), fixture.goal(), 1.0, fixture.manhattanHeuristic()
            );

            assertEquals("seed index " + index, expected.cost(), actual.cost(), EPSILON);
            assertTrue(actual.expansions() > 0);
            assertTrue(actual.peakOpenSetSize() > 0);
        }
    }

    @Test
    public void weightOneIsOptimalOnKnownDirectedGraph() {
        WeightedDirectedGraph graph = new WeightedDirectedGraph();
        graph.addEdge(0, 1, 2.0);
        graph.addEdge(1, 3, 2.0);
        graph.addEdge(0, 2, 1.0);
        graph.addEdge(2, 3, 8.0);

        SearchResult result = WeightedAStar.search(graph, 0, 3, 1.0, (node, goal) -> 0.0);

        assertEquals(4.0, result.cost(), EPSILON);
    }

    @Test
    public void weightedSearchCanBeSuboptimalWithoutExceedingBound() {
        WeightedDirectedGraph graph = new WeightedDirectedGraph();
        graph.addEdge(0, 1, 2.0);
        graph.addEdge(1, 3, 2.0);
        graph.addEdge(0, 2, 1.0);
        graph.addEdge(2, 3, 4.0);
        double weight = 2.0;

        double optimalCost = ReferenceDijkstra.search(graph, 0, 3).cost();
        SearchResult weighted = WeightedAStar.search(graph, 0, 3, weight, (node, goal) -> node == 1 ? 2.0 : 0.0);

        assertEquals(4.0, optimalCost, EPSILON);
        assertEquals(5.0, weighted.cost(), EPSILON);
        assertTrue(weighted.cost() <= weight * optimalCost + EPSILON);
    }

    @Test
    public void weightedSearchRespectsSuboptimalityBound() {
        double[] weights = {1.25, 1.5, 2.0, 3.0};
        for (int index = 0; index < 32; index++) {
            TestGraphs.GridGraph fixture = TestGraphs.directedGrid(10, 10, TestkitSeeds.PATHFINDING + index);
            double optimalCost = ReferenceDijkstra.search(fixture.graph(), fixture.start(), fixture.goal()).cost();
            for (double weight : weights) {
                SearchResult weighted = WeightedAStar.search(
                        fixture.graph(), fixture.start(), fixture.goal(), weight, fixture.manhattanHeuristic()
                );
                assertTrue(weighted.cost() + EPSILON >= optimalCost);
                assertTrue(
                        "weight " + weight + ", seed index " + index,
                        weighted.cost() <= weight * optimalCost + EPSILON
                );
            }
        }
    }
}
