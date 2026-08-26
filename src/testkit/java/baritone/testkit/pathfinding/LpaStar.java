/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package baritone.testkit.pathfinding;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.function.ToDoubleBiFunction;

/** Deterministic LPA* implementation intended for the manageable abstract region graph. */
public final class LpaStar {

    private final MutableDirectedGraph graph;
    private final int start;
    private final int goal;
    private final ToDoubleBiFunction<Integer, Integer> heuristic;
    private final Map<Integer, Double> g = new HashMap<>();
    private final Map<Integer, Double> rhs = new HashMap<>();
    private final PriorityQueue<Entry> queue = new PriorityQueue<>();
    private final Map<Integer, Key> queuedKeys = new HashMap<>();
    private int peakQueueSize;

    public LpaStar(MutableDirectedGraph graph, int start, int goal,
                   ToDoubleBiFunction<Integer, Integer> heuristic) {
        this.graph = graph;
        this.start = start;
        this.goal = goal;
        this.heuristic = heuristic;
        if (!graph.nodes().contains(start) || !graph.nodes().contains(goal)) {
            throw new IllegalArgumentException("Start and goal must exist in the graph");
        }
        rhs.put(start, 0.0D);
        enqueue(start);
    }

    public void updateEdge(int from, int to, double cost) {
        graph.updateEdge(from, to, cost);
        updateVertex(to);
    }

    public Result repair() {
        int expansions = 0;
        while (topKey().compareTo(calculateKey(goal)) < 0 || !equal(g(goal), rhs(goal))) {
            Entry entry = pollValid();
            if (entry == null) {
                break;
            }
            int node = entry.node;
            Key currentKey = calculateKey(node);
            if (entry.key.compareTo(currentKey) < 0) {
                enqueue(node);
            } else if (g(node) > rhs(node)) {
                g.put(node, rhs(node));
                for (MutableDirectedGraph.Edge edge : graph.successors(node)) {
                    updateVertex(edge.node());
                }
            } else {
                g.put(node, Double.POSITIVE_INFINITY);
                updateVertex(node);
                for (MutableDirectedGraph.Edge edge : graph.successors(node)) {
                    updateVertex(edge.node());
                }
            }
            expansions++;
        }
        return result(expansions);
    }

    private void updateVertex(int node) {
        if (node != start) {
            double best = Double.POSITIVE_INFINITY;
            for (MutableDirectedGraph.Edge edge : graph.predecessors(node)) {
                best = Math.min(best, g(edge.node()) + edge.cost());
            }
            rhs.put(node, best);
        }
        if (!equal(g(node), rhs(node))) {
            enqueue(node);
        } else {
            queuedKeys.remove(node);
        }
    }

    private void enqueue(int node) {
        Key key = calculateKey(node);
        queuedKeys.put(node, key);
        queue.add(new Entry(node, key));
        peakQueueSize = Math.max(peakQueueSize, queuedKeys.size());
    }

    private Entry pollValid() {
        while (!queue.isEmpty()) {
            Entry entry = queue.remove();
            if (entry.key.equals(queuedKeys.get(entry.node))) {
                queuedKeys.remove(entry.node);
                return entry;
            }
        }
        return null;
    }

    private Key topKey() {
        while (!queue.isEmpty()) {
            Entry entry = queue.peek();
            if (entry.key.equals(queuedKeys.get(entry.node))) {
                return entry.key;
            }
            queue.remove();
        }
        return Key.INFINITY;
    }

    private Key calculateKey(int node) {
        double minimum = Math.min(g(node), rhs(node));
        double estimate = heuristic.applyAsDouble(node, goal);
        if (estimate < 0.0D || !Double.isFinite(estimate)) {
            throw new IllegalArgumentException("Heuristic must be finite and non-negative");
        }
        return new Key(minimum + estimate, minimum);
    }

    private Result result(int expansions) {
        if (!Double.isFinite(g(goal))) {
            return new Result(Collections.emptyList(), Double.POSITIVE_INFINITY, expansions, peakQueueSize);
        }
        List<Integer> reverse = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        int node = goal;
        reverse.add(node);
        seen.add(node);
        while (node != start) {
            int predecessor = Integer.MAX_VALUE;
            double best = Double.POSITIVE_INFINITY;
            for (MutableDirectedGraph.Edge edge : graph.predecessors(node)) {
                double candidate = g(edge.node()) + edge.cost();
                if (candidate < best || candidate == best && edge.node() < predecessor) {
                    best = candidate;
                    predecessor = edge.node();
                }
            }
            if (predecessor == Integer.MAX_VALUE || !seen.add(predecessor)) {
                return new Result(Collections.emptyList(), Double.POSITIVE_INFINITY, expansions, peakQueueSize);
            }
            node = predecessor;
            reverse.add(node);
        }
        Collections.reverse(reverse);
        return new Result(reverse, g(goal), expansions, peakQueueSize);
    }

    private double g(int node) {
        return g.getOrDefault(node, Double.POSITIVE_INFINITY);
    }

    private double rhs(int node) {
        return rhs.getOrDefault(node, Double.POSITIVE_INFINITY);
    }

    private static boolean equal(double first, double second) {
        return Double.doubleToLongBits(first) == Double.doubleToLongBits(second);
    }

    public record Result(List<Integer> path, double cost, int expansions, int peakQueueSize) {
        public Result {
            path = Collections.unmodifiableList(new ArrayList<>(path));
        }
    }

    private record Entry(int node, Key key) implements Comparable<Entry> {
        @Override
        public int compareTo(Entry other) {
            int result = key.compareTo(other.key);
            return result != 0 ? result : Integer.compare(node, other.node);
        }
    }

    private record Key(double first, double second) implements Comparable<Key> {
        private static final Key INFINITY = new Key(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);

        @Override
        public int compareTo(Key other) {
            int result = Double.compare(first, other.first);
            return result != 0 ? result : Double.compare(second, other.second);
        }
    }
}
