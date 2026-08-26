/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package baritone.testkit.pathfinding;

import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;

public final class ReferenceDijkstra {

    private ReferenceDijkstra() {}

    public static SearchResult search(WeightedDirectedGraph graph, int start, int goal) {
        Map<Integer, Double> distance = new HashMap<>();
        PriorityQueue<Entry> open = new PriorityQueue<>();
        distance.put(start, 0.0);
        open.add(new Entry(start, 0.0));
        int expansions = 0;
        int peakOpenSetSize = 1;

        while (!open.isEmpty()) {
            Entry current = open.remove();
            if (current.cost != distance.getOrDefault(current.node, Double.POSITIVE_INFINITY)) {
                continue;
            }
            expansions++;
            if (current.node == goal) {
                return new SearchResult(current.cost, expansions, peakOpenSetSize);
            }
            for (WeightedDirectedGraph.Edge edge : graph.outgoing(current.node)) {
                double candidate = current.cost + edge.cost();
                if (candidate < distance.getOrDefault(edge.to(), Double.POSITIVE_INFINITY)) {
                    distance.put(edge.to(), candidate);
                    open.add(new Entry(edge.to(), candidate));
                    peakOpenSetSize = Math.max(peakOpenSetSize, open.size());
                }
            }
        }
        return new SearchResult(Double.POSITIVE_INFINITY, expansions, peakOpenSetSize);
    }

    private static final class Entry implements Comparable<Entry> {

        private final int node;
        private final double cost;

        private Entry(int node, double cost) {
            this.node = node;
            this.cost = cost;
        }

        @Override
        public int compareTo(Entry other) {
            int byCost = Double.compare(cost, other.cost);
            return byCost != 0 ? byCost : Integer.compare(node, other.node);
        }
    }
}
