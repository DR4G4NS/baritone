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
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Mutable deterministic graph with explicit successors and predecessors for incremental search. */
public final class MutableDirectedGraph {

    private final Map<Integer, Map<Integer, Double>> outgoing = new LinkedHashMap<>();
    private final Map<Integer, Map<Integer, Double>> incoming = new LinkedHashMap<>();

    public void addNode(int node) {
        outgoing.computeIfAbsent(node, ignored -> new LinkedHashMap<>());
        incoming.computeIfAbsent(node, ignored -> new LinkedHashMap<>());
    }

    public void updateEdge(int from, int to, double cost) {
        if (!(cost > 0.0D) && cost != Double.POSITIVE_INFINITY || Double.isNaN(cost)) {
            throw new IllegalArgumentException("Edge cost must be positive or infinity");
        }
        addNode(from);
        addNode(to);
        if (cost == Double.POSITIVE_INFINITY) {
            outgoing.get(from).remove(to);
            incoming.get(to).remove(from);
        } else {
            outgoing.get(from).put(to, cost);
            incoming.get(to).put(from, cost);
        }
    }

    public Collection<Edge> successors(int node) {
        return edges(outgoing, node);
    }

    public Collection<Edge> predecessors(int node) {
        return edges(incoming, node);
    }

    public Collection<Integer> nodes() {
        return Collections.unmodifiableSet(outgoing.keySet());
    }

    public int size() {
        return outgoing.size();
    }

    private static Collection<Edge> edges(Map<Integer, Map<Integer, Double>> graph, int node) {
        Map<Integer, Double> adjacent = graph.get(node);
        if (adjacent == null) {
            throw new IllegalArgumentException("Unknown node " + node);
        }
        ArrayList<Edge> result = new ArrayList<>(adjacent.size());
        adjacent.forEach((other, cost) -> result.add(new Edge(other, cost)));
        return Collections.unmodifiableList(result);
    }

    public record Edge(int node, double cost) {}
}
