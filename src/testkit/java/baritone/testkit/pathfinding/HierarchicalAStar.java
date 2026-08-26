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
import java.util.function.IntUnaryOperator;

/**
 * Reference hierarchical search: an abstract region route selects a corridor,
 * then the original detailed directed graph produces the executable path.
 */
public final class HierarchicalAStar {

    private HierarchicalAStar() {}

    public static Result search(MutableDirectedGraph detailed, int start, int goal, IntUnaryOperator regionOf) {
        MutableDirectedGraph abstractGraph = abstractGraph(detailed, regionOf);
        int startRegion = regionOf.applyAsInt(start);
        int goalRegion = regionOf.applyAsInt(goal);
        GraphPath abstractPath = shortestPath(abstractGraph, startRegion, goalRegion, null);
        if (abstractPath.path.isEmpty()) {
            GraphPath fallback = shortestPath(detailed, start, goal, null);
            return new Result(fallback.path, Collections.emptyList(), fallback.cost, true,
                    fallback.expansions, 0);
        }

        Set<Integer> corridor = new HashSet<>(abstractPath.path);
        GraphPath refined = shortestPath(detailed, start, goal, node -> corridor.contains(regionOf.applyAsInt(node)));
        if (!refined.path.isEmpty()) {
            return new Result(refined.path, abstractPath.path, refined.cost, false,
                    refined.expansions, abstractPath.expansions);
        }

        GraphPath fallback = shortestPath(detailed, start, goal, null);
        return new Result(fallback.path, abstractPath.path, fallback.cost, true,
                fallback.expansions, abstractPath.expansions);
    }

    private static MutableDirectedGraph abstractGraph(MutableDirectedGraph detailed, IntUnaryOperator regionOf) {
        MutableDirectedGraph result = new MutableDirectedGraph();
        Map<Long, Double> cheapest = new HashMap<>();
        for (int node : detailed.nodes()) {
            int fromRegion = regionOf.applyAsInt(node);
            result.addNode(fromRegion);
            for (MutableDirectedGraph.Edge edge : detailed.successors(node)) {
                int toRegion = regionOf.applyAsInt(edge.node());
                result.addNode(toRegion);
                if (fromRegion == toRegion) {
                    continue;
                }
                long key = ((long) fromRegion << 32) ^ (toRegion & 0xFFFFFFFFL);
                cheapest.merge(key, edge.cost(), Math::min);
            }
        }
        cheapest.forEach((key, cost) -> result.updateEdge((int) (key >> 32), (int) (long) key, cost));
        return result;
    }

    private static GraphPath shortestPath(MutableDirectedGraph graph, int start, int goal, NodeFilter filter) {
        if (!graph.nodes().contains(start) || !graph.nodes().contains(goal)
                || filter != null && (!filter.accept(start) || !filter.accept(goal))) {
            return GraphPath.none();
        }
        Map<Integer, Double> distance = new HashMap<>();
        Map<Integer, Integer> previous = new HashMap<>();
        PriorityQueue<Entry> queue = new PriorityQueue<>();
        distance.put(start, 0.0D);
        queue.add(new Entry(start, 0.0D));
        int expansions = 0;
        while (!queue.isEmpty()) {
            Entry current = queue.remove();
            if (current.cost != distance.getOrDefault(current.node, Double.POSITIVE_INFINITY)) {
                continue;
            }
            expansions++;
            if (current.node == goal) {
                return new GraphPath(reconstruct(previous, start, goal), current.cost, expansions);
            }
            for (MutableDirectedGraph.Edge edge : graph.successors(current.node)) {
                if (filter != null && !filter.accept(edge.node())) {
                    continue;
                }
                double candidate = current.cost + edge.cost();
                if (candidate < distance.getOrDefault(edge.node(), Double.POSITIVE_INFINITY)) {
                    distance.put(edge.node(), candidate);
                    previous.put(edge.node(), current.node);
                    queue.add(new Entry(edge.node(), candidate));
                }
            }
        }
        return new GraphPath(Collections.emptyList(), Double.POSITIVE_INFINITY, expansions);
    }

    private static List<Integer> reconstruct(Map<Integer, Integer> previous, int start, int goal) {
        List<Integer> reverse = new ArrayList<>();
        int node = goal;
        reverse.add(node);
        while (node != start) {
            Integer parent = previous.get(node);
            if (parent == null) {
                return Collections.emptyList();
            }
            node = parent;
            reverse.add(node);
        }
        Collections.reverse(reverse);
        return reverse;
    }

    public record Result(List<Integer> detailedPath, List<Integer> regionCorridor, double cost,
                         boolean usedFallback, int detailedExpansions, int abstractExpansions) {
        public Result {
            detailedPath = Collections.unmodifiableList(new ArrayList<>(detailedPath));
            regionCorridor = Collections.unmodifiableList(new ArrayList<>(regionCorridor));
        }
    }

    private record GraphPath(List<Integer> path, double cost, int expansions) {
        private static GraphPath none() {
            return new GraphPath(Collections.emptyList(), Double.POSITIVE_INFINITY, 0);
        }
    }

    private record Entry(int node, double cost) implements Comparable<Entry> {
        @Override
        public int compareTo(Entry other) {
            int result = Double.compare(cost, other.cost);
            return result != 0 ? result : Integer.compare(node, other.node);
        }
    }

    @FunctionalInterface
    private interface NodeFilter {
        boolean accept(int node);
    }
}
