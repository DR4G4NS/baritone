/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package baritone.pathing.calc;

import it.unimi.dsi.fastutil.longs.Long2BooleanOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.PriorityQueue;

/**
 * A bounded abstract planner for long terrestrial routes. Regions deliberately
 * contain no movement semantics: the normal detailed A* remains responsible
 * for mining, placing, falling, pillar and parkour actions.
 *
 * <p>The planner keeps an LPA* session while start and destination regions are
 * stable. A changed chunk invalidates its containing region; the next plan
 * refreshes only incident abstract edges and repairs the previous search.</p>
 */
public final class HierarchicalPathPlanner {

    static final int REGION_BITS = 6;
    static final int REGION_SIZE = 1 << REGION_BITS;
    private static final int MIN_LONG_ROUTE_BLOCKS = REGION_SIZE * 4;
    private static final int CORRIDOR_MARGIN_REGIONS = 1;
    private static final int SEARCH_PADDING_REGIONS = 8;
    private static final int MAX_ABSTRACT_REGIONS = 65_536;
    private static final int[][] DIRECTIONS = {
            {-1, -1}, {-1, 0}, {-1, 1}, {0, -1},
            {0, 1}, {1, -1}, {1, 0}, {1, 1}
    };

    private final LongSet invalidated = new LongOpenHashSet();
    private Session session;
    private volatile Metrics lastMetrics = Metrics.EMPTY;

    public synchronized Corridor plan(int startX, int startZ, int goalX, int goalZ, RegionKnowledge knowledge) {
        long dx = (long) goalX - startX;
        long dz = (long) goalZ - startZ;
        if (dx * dx + dz * dz < (long) MIN_LONG_ROUTE_BLOCKS * MIN_LONG_ROUTE_BLOCKS) {
            lastMetrics = Metrics.EMPTY;
            return Corridor.NONE;
        }
        int startRegionX = startX >> REGION_BITS;
        int startRegionZ = startZ >> REGION_BITS;
        int goalRegionX = goalX >> REGION_BITS;
        int goalRegionZ = goalZ >> REGION_BITS;
        Bounds bounds = Bounds.around(startRegionX, startRegionZ, goalRegionX, goalRegionZ);
        if (bounds.size() > MAX_ABSTRACT_REGIONS) {
            lastMetrics = Metrics.EMPTY;
            return Corridor.NONE;
        }
        long start = key(startRegionX, startRegionZ);
        long goal = key(goalRegionX, goalRegionZ);
        boolean reused = session != null && session.start == start && session.goal == goal && session.bounds.equals(bounds);
        if (!reused) {
            session = new Session(start, goal, bounds, knowledge);
            invalidated.clear();
        } else {
            session.refresh(knowledge, invalidated);
            invalidated.clear();
        }
        RepairResult result = session.repair();
        if (result.path.isEmpty()) {
            lastMetrics = new Metrics(result.expansions, result.peakQueue, 0, reused, true);
            return Corridor.NONE;
        }
        LongSet allowed = new LongOpenHashSet();
        for (long region : result.path) {
            int x = x(region);
            int z = z(region);
            for (int ox = -CORRIDOR_MARGIN_REGIONS; ox <= CORRIDOR_MARGIN_REGIONS; ox++) {
                for (int oz = -CORRIDOR_MARGIN_REGIONS; oz <= CORRIDOR_MARGIN_REGIONS; oz++) {
                    allowed.add(key(x + ox, z + oz));
                }
            }
        }
        lastMetrics = new Metrics(result.expansions, result.peakQueue, result.path.size(), reused, false);
        return new Corridor(allowed);
    }

    public synchronized void invalidateBlock(int blockX, int blockZ) {
        invalidated.add(key(blockX >> REGION_BITS, blockZ >> REGION_BITS));
    }

    public Metrics lastMetrics() {
        return lastMetrics;
    }

    @FunctionalInterface
    public interface RegionKnowledge {
        boolean isKnown(int centerBlockX, int centerBlockZ);
    }

    public static final class Corridor {
        public static final Corridor NONE = new Corridor(null);
        private final LongSet regions;

        private Corridor(LongSet regions) {
            this.regions = regions;
        }

        public boolean isPresent() {
            return regions != null;
        }

        public boolean containsBlock(int blockX, int blockZ) {
            return regions == null || regions.contains(key(blockX >> REGION_BITS, blockZ >> REGION_BITS));
        }
    }

    public record Metrics(int abstractExpansions, int peakQueueSize, int corridorLength,
                          boolean repairedExistingSearch, boolean fallback) {
        private static final Metrics EMPTY = new Metrics(0, 0, 0, false, true);
    }

    private static final class Session {
        private final long start;
        private final long goal;
        private final Bounds bounds;
        private final Long2BooleanOpenHashMap known = new Long2BooleanOpenHashMap();
        private final Long2DoubleOpenHashMap g = infinityMap();
        private final Long2DoubleOpenHashMap rhs = infinityMap();
        private final Long2LongOpenHashMap queuedGeneration = new Long2LongOpenHashMap();
        private final PriorityQueue<Entry> queue = new PriorityQueue<>();
        private long generation;
        private int peakQueue;

        private Session(long start, long goal, Bounds bounds, RegionKnowledge knowledge) {
            this.start = start;
            this.goal = goal;
            this.bounds = bounds;
            for (int regionX = bounds.minX; regionX <= bounds.maxX; regionX++) {
                for (int regionZ = bounds.minZ; regionZ <= bounds.maxZ; regionZ++) {
                    long region = key(regionX, regionZ);
                    known.put(region, knowledge.isKnown(center(regionX), center(regionZ)));
                }
            }
            rhs.put(start, 0.0D);
            enqueue(start);
        }

        private void refresh(RegionKnowledge knowledge, LongSet explicitlyInvalidated) {
            LongSet changed = new LongOpenHashSet(explicitlyInvalidated);
            for (long region : explicitlyInvalidated) {
                if (!bounds.contains(x(region), z(region))) {
                    continue;
                }
                boolean value = knowledge.isKnown(center(x(region)), center(z(region)));
                if (known.put(region, value) != value) {
                    changed.add(region);
                }
            }
            for (long region : changed) {
                updateVertex(region);
                forEachNeighbor(region, this::updateVertex);
            }
        }

        private RepairResult repair() {
            int expansions = 0;
            while (topKey().compareTo(calculateKey(goal)) < 0 || !equal(g.get(goal), rhs.get(goal))) {
                Entry entry = pollValid();
                if (entry == null) {
                    break;
                }
                Key newKey = calculateKey(entry.node);
                if (entry.key.compareTo(newKey) < 0) {
                    enqueue(entry.node);
                } else if (g.get(entry.node) > rhs.get(entry.node)) {
                    g.put(entry.node, rhs.get(entry.node));
                    forEachNeighbor(entry.node, this::updateVertex);
                } else {
                    g.put(entry.node, Double.POSITIVE_INFINITY);
                    updateVertex(entry.node);
                    forEachNeighbor(entry.node, this::updateVertex);
                }
                expansions++;
            }
            return new RepairResult(reconstruct(), expansions, peakQueue);
        }

        private List<Long> reconstruct() {
            if (!Double.isFinite(g.get(goal))) {
                return Collections.emptyList();
            }
            List<Long> reverse = new ArrayList<>();
            LongSet seen = new LongOpenHashSet();
            long node = goal;
            reverse.add(node);
            seen.add(node);
            while (node != start) {
                final long current = node;
                long bestNode = Long.MIN_VALUE;
                double bestCost = Double.POSITIVE_INFINITY;
                for (int[] direction : DIRECTIONS) {
                    long predecessor = key(x(current) + direction[0], z(current) + direction[1]);
                    if (!bounds.contains(x(predecessor), z(predecessor))) {
                        continue;
                    }
                    double candidate = g.get(predecessor) + edgeCost(predecessor, current);
                    if (candidate < bestCost || candidate == bestCost && predecessor < bestNode) {
                        bestCost = candidate;
                        bestNode = predecessor;
                    }
                }
                if (bestNode == Long.MIN_VALUE || !seen.add(bestNode)) {
                    return Collections.emptyList();
                }
                node = bestNode;
                reverse.add(node);
            }
            Collections.reverse(reverse);
            return reverse;
        }

        private void updateVertex(long node) {
            if (!bounds.contains(x(node), z(node))) {
                return;
            }
            if (node != start) {
                double best = Double.POSITIVE_INFINITY;
                for (int[] direction : DIRECTIONS) {
                    long predecessor = key(x(node) + direction[0], z(node) + direction[1]);
                    if (bounds.contains(x(predecessor), z(predecessor))) {
                        best = Math.min(best, g.get(predecessor) + edgeCost(predecessor, node));
                    }
                }
                rhs.put(node, best);
            }
            if (!equal(g.get(node), rhs.get(node))) {
                enqueue(node);
            } else {
                queuedGeneration.remove(node);
            }
        }

        private void forEachNeighbor(long node, NodeConsumer consumer) {
            for (int[] direction : DIRECTIONS) {
                int neighborX = x(node) + direction[0];
                int neighborZ = z(node) + direction[1];
                if (bounds.contains(neighborX, neighborZ)) {
                    consumer.accept(key(neighborX, neighborZ));
                }
            }
        }

        private double edgeCost(long from, long to) {
            boolean diagonal = x(from) != x(to) && z(from) != z(to);
            double distance = diagonal ? Math.sqrt(2.0D) : 1.0D;
            // Unknown regions remain traversable: this is a preference, never a wall.
            return distance * (known.get(from) && known.get(to) ? 1.0D : 1.08D);
        }

        private void enqueue(long node) {
            Key key = calculateKey(node);
            long ticket = ++generation;
            queuedGeneration.put(node, ticket);
            queue.add(new Entry(node, key, ticket));
            peakQueue = Math.max(peakQueue, queuedGeneration.size());
        }

        private Entry pollValid() {
            while (!queue.isEmpty()) {
                Entry entry = queue.remove();
                if (queuedGeneration.get(entry.node) == entry.ticket) {
                    queuedGeneration.remove(entry.node);
                    return entry;
                }
            }
            return null;
        }

        private Key topKey() {
            while (!queue.isEmpty()) {
                Entry entry = queue.peek();
                if (queuedGeneration.get(entry.node) == entry.ticket) {
                    return entry.key;
                }
                queue.remove();
            }
            return Key.INFINITY;
        }

        private Key calculateKey(long node) {
            double minimum = Math.min(g.get(node), rhs.get(node));
            return new Key(minimum + octile(node, goal), minimum);
        }
    }

    private record Bounds(int minX, int maxX, int minZ, int maxZ) {
        private static Bounds around(int startX, int startZ, int goalX, int goalZ) {
            return new Bounds(Math.min(startX, goalX) - SEARCH_PADDING_REGIONS,
                    Math.max(startX, goalX) + SEARCH_PADDING_REGIONS,
                    Math.min(startZ, goalZ) - SEARCH_PADDING_REGIONS,
                    Math.max(startZ, goalZ) + SEARCH_PADDING_REGIONS);
        }

        private long size() {
            return (long) (maxX - minX + 1) * (maxZ - minZ + 1);
        }

        private boolean contains(int x, int z) {
            return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
        }
    }

    private record RepairResult(List<Long> path, int expansions, int peakQueue) {}

    private record Entry(long node, Key key, long ticket) implements Comparable<Entry> {
        @Override
        public int compareTo(Entry other) {
            int result = key.compareTo(other.key);
            return result != 0 ? result : Long.compare(node, other.node);
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

    @FunctionalInterface
    private interface NodeConsumer {
        void accept(long node);
    }

    private static Long2DoubleOpenHashMap infinityMap() {
        Long2DoubleOpenHashMap result = new Long2DoubleOpenHashMap();
        result.defaultReturnValue(Double.POSITIVE_INFINITY);
        return result;
    }

    private static double octile(long from, long to) {
        int dx = Math.abs(x(from) - x(to));
        int dz = Math.abs(z(from) - z(to));
        int minimum = Math.min(dx, dz);
        return Math.max(dx, dz) + (Math.sqrt(2.0D) - 1.0D) * minimum;
    }

    private static int center(int region) {
        return (region << REGION_BITS) + (REGION_SIZE >>> 1);
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }

    private static int x(long key) {
        return (int) (key >> 32);
    }

    private static int z(long key) {
        return (int) key;
    }

    private static boolean equal(double first, double second) {
        return Double.doubleToLongBits(first) == Double.doubleToLongBits(second);
    }
}
