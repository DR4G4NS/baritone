package baritone.testkit.pathfinding;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/** Deterministic 3-D Lazy Theta* reference implementation used by headless validation. */
public final class LazyThetaStar {
    private LazyThetaStar() {}

    public static Result search(VoxelGrid grid, VoxelPoint start, VoxelPoint goal, double margin) {
        if (grid.isBlocked(start.x, start.y, start.z) || grid.isBlocked(goal.x, goal.y, goal.z)) return Result.none();
        Map<VoxelPoint, Double> cost = new HashMap<>();
        Map<VoxelPoint, VoxelPoint> parent = new HashMap<>();
        PriorityQueue<Entry> open = new PriorityQueue<>();
        Set<VoxelPoint> closed = new HashSet<>();
        cost.put(start, 0.0);
        parent.put(start, start);
        open.add(new Entry(start, 0.0, start.distance(goal)));
        int expansions = 0;
        int raycasts = 0;
        while (!open.isEmpty()) {
            Entry entry = open.remove();
            if (entry.g != cost.getOrDefault(entry.point, Double.POSITIVE_INFINITY) || !closed.add(entry.point)) continue;
            VoxelPoint current = entry.point;
            expansions++;
            if (current.equals(goal)) return new Result(reconstruct(parent, goal), entry.g, expansions, raycasts);
            VoxelPoint currentParent = parent.get(current);
            for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dy == 0 && dz == 0) continue;
                VoxelPoint neighbor = new VoxelPoint(current.x + dx, current.y + dy, current.z + dz);
                if (grid.isBlocked(neighbor.x, neighbor.y, neighbor.z) || closed.contains(neighbor)) continue;
                double candidate;
                VoxelPoint candidateParent;
                raycasts++;
                if (grid.hasLineOfSight(currentParent, neighbor, margin)) {
                    candidateParent = currentParent;
                    candidate = cost.get(currentParent) + currentParent.distance(neighbor);
                } else {
                    raycasts++;
                    if (!grid.hasLineOfSight(current, neighbor, margin)) continue;
                    candidateParent = current;
                    candidate = entry.g + current.distance(neighbor);
                }
                if (candidate < cost.getOrDefault(neighbor, Double.POSITIVE_INFINITY)) {
                    cost.put(neighbor, candidate);
                    parent.put(neighbor, candidateParent);
                    open.add(new Entry(neighbor, candidate, candidate + neighbor.distance(goal)));
                }
            }
        }
        return new Result(Collections.emptyList(), Double.POSITIVE_INFINITY, expansions, raycasts);
    }

    private static List<VoxelPoint> reconstruct(Map<VoxelPoint, VoxelPoint> parent, VoxelPoint goal) {
        List<VoxelPoint> path = new ArrayList<>();
        VoxelPoint point = goal;
        while (true) {
            path.add(point);
            VoxelPoint next = parent.get(point);
            if (next.equals(point)) break;
            point = next;
        }
        Collections.reverse(path);
        return path;
    }

    public static final class Result {
        public final List<VoxelPoint> path;
        public final double cost;
        public final int expansions;
        public final int raycasts;
        private Result(List<VoxelPoint> path, double cost, int expansions, int raycasts) {
            this.path = Collections.unmodifiableList(new ArrayList<>(path));
            this.cost = cost;
            this.expansions = expansions;
            this.raycasts = raycasts;
        }
        private static Result none() { return new Result(Collections.emptyList(), Double.POSITIVE_INFINITY, 0, 0); }
    }

    private static final class Entry implements Comparable<Entry> {
        private final VoxelPoint point; private final double g; private final double f;
        private Entry(VoxelPoint point, double g, double f) { this.point = point; this.g = g; this.f = f; }
        @Override public int compareTo(Entry other) {
            int result = Double.compare(f, other.f);
            if (result == 0) result = Double.compare(g, other.g);
            if (result == 0) result = Integer.compare(point.x, other.point.x);
            if (result == 0) result = Integer.compare(point.y, other.point.y);
            return result != 0 ? result : Integer.compare(point.z, other.point.z);
        }
    }
}
