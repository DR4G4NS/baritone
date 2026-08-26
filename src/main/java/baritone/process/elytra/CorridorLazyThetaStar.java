/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package baritone.process.elytra;

import baritone.api.utils.BetterBlockPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Lazy Theta* refinement over the ordered points supplied by the native global corridor.
 * Native adjacent edges remain the safe fallback; any-angle parent edges are validated lazily.
 */
public final class CorridorLazyThetaStar {

    private CorridorLazyThetaStar() {}

    public static Result refine(List<BetterBlockPos> corridor, SegmentValidator validator) {
        if (corridor.size() < 3) {
            return new Result(corridor, 0, 0);
        }
        int size = corridor.size();
        double[] cost = new double[size];
        int[] parent = new int[size];
        boolean[] closed = new boolean[size];
        java.util.Arrays.fill(cost, Double.POSITIVE_INFINITY);
        java.util.Arrays.fill(parent, -1);
        cost[0] = 0.0D;
        parent[0] = 0;
        PriorityQueue<Entry> open = new PriorityQueue<>();
        open.add(new Entry(0, 0.0D, distance(corridor.get(0), corridor.get(size - 1))));
        int expansions = 0;
        int raycasts = 0;

        while (!open.isEmpty()) {
            Entry entry = open.remove();
            int current = entry.index;
            if (closed[current] || entry.cost != cost[current]) {
                continue;
            }
            closed[current] = true;
            expansions++;
            if (current == size - 1) {
                return new Result(reconstruct(corridor, parent), expansions, raycasts);
            }

            int neighbor = current + 1;
            int candidateParent = current;
            double candidateCost = cost[current] + distance(corridor.get(current), corridor.get(neighbor));
            int currentParent = parent[current];
            if (currentParent != current) {
                raycasts++;
                if (validator.isValid(corridor.get(currentParent), corridor.get(neighbor))) {
                    double shortcutCost = cost[currentParent]
                            + distance(corridor.get(currentParent), corridor.get(neighbor));
                    if (shortcutCost <= candidateCost) {
                        candidateParent = currentParent;
                        candidateCost = shortcutCost;
                    }
                }
            }
            if (candidateCost < cost[neighbor]) {
                cost[neighbor] = candidateCost;
                parent[neighbor] = candidateParent;
                double estimate = distance(corridor.get(neighbor), corridor.get(size - 1));
                open.add(new Entry(neighbor, candidateCost, candidateCost + estimate));
            }
        }
        return new Result(corridor, expansions, raycasts);
    }

    private static List<BetterBlockPos> reconstruct(List<BetterBlockPos> corridor, int[] parent) {
        List<BetterBlockPos> reverse = new ArrayList<>();
        int index = corridor.size() - 1;
        while (true) {
            reverse.add(corridor.get(index));
            if (index == 0) {
                break;
            }
            index = parent[index];
            if (index < 0) {
                return corridor;
            }
        }
        Collections.reverse(reverse);
        return reverse;
    }

    private static double distance(BetterBlockPos first, BetterBlockPos second) {
        double dx = (double) first.x - second.x;
        double dy = (double) first.y - second.y;
        double dz = (double) first.z - second.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public record Result(List<BetterBlockPos> path, int expansions, int raycasts) {
        public Result {
            path = Collections.unmodifiableList(new ArrayList<>(path));
        }
    }

    @FunctionalInterface
    public interface SegmentValidator {
        boolean isValid(BetterBlockPos from, BetterBlockPos to);
    }

    private record Entry(int index, double cost, double priority) implements Comparable<Entry> {
        @Override
        public int compareTo(Entry other) {
            int result = Double.compare(priority, other.priority);
            return result != 0 ? result : Integer.compare(index, other.index);
        }
    }
}
