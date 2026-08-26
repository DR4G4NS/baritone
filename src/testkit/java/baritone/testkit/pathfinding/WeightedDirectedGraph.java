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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class WeightedDirectedGraph {

    private final Map<Integer, List<Edge>> outgoing = new LinkedHashMap<>();
    private int minimumNode = Integer.MAX_VALUE;
    private int maximumNode = Integer.MIN_VALUE;

    public void addNode(int node) {
        outgoing.computeIfAbsent(node, ignored -> new ArrayList<>());
        minimumNode = Math.min(minimumNode, node);
        maximumNode = Math.max(maximumNode, node);
    }

    public void addEdge(int from, int to, double cost) {
        if (!(cost > 0.0) || !Double.isFinite(cost)) {
            throw new IllegalArgumentException("Edge cost must be finite and positive");
        }
        addNode(from);
        addNode(to);
        outgoing.get(from).add(new Edge(to, cost));
    }

    public List<Edge> outgoing(int node) {
        List<Edge> edges = outgoing.get(node);
        if (edges == null) {
            throw new IllegalArgumentException("Unknown node " + node);
        }
        return Collections.unmodifiableList(edges);
    }

    public int size() {
        return outgoing.size();
    }

    boolean hasCompactNonNegativeNodeIds() {
        return minimumNode >= 0 && maximumNode >= 0 && (long) maximumNode + 1L <= Math.max(16L, (long) size() * 4L);
    }

    int maximumNode() {
        return maximumNode;
    }

    public static final class Edge {

        private final int to;
        private final double cost;

        private Edge(int to, double cost) {
            this.to = to;
            this.cost = cost;
        }

        public int to() {
            return to;
        }

        public double cost() {
            return cost;
        }
    }
}
