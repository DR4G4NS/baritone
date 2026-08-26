/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package baritone.testkit.pathfinding;

public final class SearchResult {

    private final double cost;
    private final int expansions;
    private final int peakOpenSetSize;

    public SearchResult(double cost, int expansions, int peakOpenSetSize) {
        this.cost = cost;
        this.expansions = expansions;
        this.peakOpenSetSize = peakOpenSetSize;
    }

    public double cost() {
        return cost;
    }

    public int expansions() {
        return expansions;
    }

    public int peakOpenSetSize() {
        return peakOpenSetSize;
    }
}
