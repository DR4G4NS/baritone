/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package baritone.testkit.pathfinding;

import java.util.Random;

public final class TestGraphs {

    private TestGraphs() {}

    public static GridGraph directedGrid(int width, int height, long seed) {
        if (width < 2 || height < 2) {
            throw new IllegalArgumentException("Grid dimensions must be at least two");
        }
        WeightedDirectedGraph graph = new WeightedDirectedGraph();
        Random random = new Random(seed);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int node = node(x, y, width);
                graph.addNode(node);
                if (x + 1 < width) {
                    addDirectedPair(graph, node, node(x + 1, y, width), random);
                }
                if (y + 1 < height) {
                    addDirectedPair(graph, node, node(x, y + 1, width), random);
                }
            }
        }
        return new GridGraph(graph, width, height);
    }

    private static void addDirectedPair(WeightedDirectedGraph graph, int first, int second, Random random) {
        graph.addEdge(first, second, 1 + random.nextInt(4));
        graph.addEdge(second, first, 1 + random.nextInt(4));
    }

    private static int node(int x, int y, int width) {
        return y * width + x;
    }

    public static final class GridGraph {

        private final WeightedDirectedGraph graph;
        private final int width;
        private final int height;

        private GridGraph(WeightedDirectedGraph graph, int width, int height) {
            this.graph = graph;
            this.width = width;
            this.height = height;
        }

        public WeightedDirectedGraph graph() {
            return graph;
        }

        public int start() {
            return 0;
        }

        public int goal() {
            return width * height - 1;
        }

        public WeightedAStar.IntHeuristic manhattanHeuristic() {
            return (node, goal) -> {
                int nodeX = node % width;
                int nodeY = node / width;
                int goalX = goal % width;
                int goalY = goal / width;
                return Math.abs(nodeX - goalX) + Math.abs(nodeY - goalY);
            };
        }
    }
}
