/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package baritone.pathing.calc;

/** Immutable measurements for one pathfinder invocation. */
public record PathSearchMetrics(
        Outcome outcome,
        long elapsedNanos,
        int expandedNodes,
        int movementEvaluations,
        int reopenedNodes,
        int discoveredNodes,
        int peakOpenSetSize,
        long estimatedRetainedBytes,
        double finalCost
) {

    private static final long ESTIMATED_BYTES_PER_NODE_AND_MAP_ENTRY = 88L;
    private static final long ESTIMATED_BYTES_PER_OPEN_REFERENCE = 8L;

    public static PathSearchMetrics notStarted() {
        return create(Outcome.NOT_STARTED, 0L, 0, 0, 0, 0, 0, Double.POSITIVE_INFINITY);
    }

    /**
     * The byte count estimates search-owned nodes, map entries and open-set references.
     * It is not a measurement of total JVM heap usage.
     */
    public static PathSearchMetrics create(Outcome outcome, long elapsedNanos, int expandedNodes,
                                           int movementEvaluations, int reopenedNodes, int discoveredNodes,
                                           int peakOpenSetSize, double finalCost) {
        long estimatedBytes = saturatedAdd(
                saturatedMultiply(discoveredNodes, ESTIMATED_BYTES_PER_NODE_AND_MAP_ENTRY),
                saturatedMultiply(peakOpenSetSize, ESTIMATED_BYTES_PER_OPEN_REFERENCE)
        );
        return new PathSearchMetrics(outcome, Math.max(0L, elapsedNanos), expandedNodes, movementEvaluations,
                reopenedNodes, discoveredNodes, peakOpenSetSize, estimatedBytes, finalCost);
    }

    private static long saturatedMultiply(int value, long multiplier) {
        if (value <= 0) {
            return 0L;
        }
        return value > Long.MAX_VALUE / multiplier ? Long.MAX_VALUE : value * multiplier;
    }

    private static long saturatedAdd(long first, long second) {
        return Long.MAX_VALUE - first < second ? Long.MAX_VALUE : first + second;
    }

    public enum Outcome {
        NOT_STARTED,
        SUCCESS,
        PARTIAL,
        FAILURE,
        CANCELLED,
        INVALID_START
    }
}
