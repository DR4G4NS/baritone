/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package baritone.testkit.pathfinding;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;

public final class WeightedAStar {

    private WeightedAStar() {}

    public static SearchResult search(WeightedDirectedGraph graph, int start, int goal, double weight,
                                      IntHeuristic heuristic) {
        if (weight < 1.0 || !Double.isFinite(weight)) {
            throw new IllegalArgumentException("Weight must be finite and at least one");
        }

        if (graph.hasCompactNonNegativeNodeIds()) {
            return searchDense(graph, start, goal, weight, heuristic);
        }
        Map<Integer, Double> distance = new HashMap<>();
        PriorityQueue<Entry> open = new PriorityQueue<>();
        distance.put(start, 0.0);
        open.add(new Entry(start, 0.0, weight * checkedHeuristic(heuristic, start, goal)));
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
                    double priority = candidate + weight * checkedHeuristic(heuristic, edge.to(), goal);
                    open.add(new Entry(edge.to(), candidate, priority));
                    peakOpenSetSize = Math.max(peakOpenSetSize, open.size());
                }
            }
        }
        return new SearchResult(Double.POSITIVE_INFINITY, expansions, peakOpenSetSize);
    }

    private static SearchResult searchDense(WeightedDirectedGraph graph, int start, int goal, double weight,
                                            IntHeuristic heuristic) {
        double[] distance = new double[graph.maximumNode() + 1];
        Arrays.fill(distance, Double.POSITIVE_INFINITY);
        PriorityQueue<Entry> open = new PriorityQueue<>();
        distance[start] = 0.0D;
        open.add(new Entry(start, 0.0D, weight * checkedHeuristic(heuristic, start, goal)));
        int expansions = 0;
        int peakOpenSetSize = 1;
        while (!open.isEmpty()) {
            Entry current = open.remove();
            if (current.cost != distance[current.node]) {
                continue;
            }
            expansions++;
            if (current.node == goal) {
                return new SearchResult(current.cost, expansions, peakOpenSetSize);
            }
            for (WeightedDirectedGraph.Edge edge : graph.outgoing(current.node)) {
                double candidate = current.cost + edge.cost();
                if (candidate < distance[edge.to()]) {
                    distance[edge.to()] = candidate;
                    double priority = candidate + weight * checkedHeuristic(heuristic, edge.to(), goal);
                    open.add(new Entry(edge.to(), candidate, priority));
                    peakOpenSetSize = Math.max(peakOpenSetSize, open.size());
                }
            }
        }
        return new SearchResult(Double.POSITIVE_INFINITY, expansions, peakOpenSetSize);
    }

    private static double checkedHeuristic(IntHeuristic heuristic, int node, int goal) {
        double estimate = heuristic.applyAsDouble(node, goal);
        if (estimate < 0.0D || !Double.isFinite(estimate)) {
            throw new IllegalArgumentException("Heuristic must be finite and non-negative");
        }
        return estimate;
    }

    @FunctionalInterface
    public interface IntHeuristic {
        double applyAsDouble(int node, int goal);
    }

    private static final class Entry implements Comparable<Entry> {

        private final int node;
        private final double cost;
        private final double priority;

        private Entry(int node, double cost, double priority) {
            this.node = node;
            this.cost = cost;
            this.priority = priority;
        }

        @Override
        public int compareTo(Entry other) {
            int byPriority = Double.compare(priority, other.priority);
            if (byPriority != 0) {
                return byPriority;
            }
            int byCost = Double.compare(cost, other.cost);
            return byCost != 0 ? byCost : Integer.compare(node, other.node);
        }
    }
}
