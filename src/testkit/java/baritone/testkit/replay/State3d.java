/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package baritone.testkit.replay;

import java.util.Objects;

public final class State3d {

    private final double x;
    private final double y;
    private final double z;

    public State3d(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public double x() {
        return x;
    }

    public double y() {
        return y;
    }

    public double z() {
        return z;
    }

    public State3d apply(ControlInput input) {
        return new State3d(x + input.deltaX(), y + input.deltaY(), z + input.deltaZ());
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof State3d)) {
            return false;
        }
        State3d state = (State3d) other;
        return Double.compare(x, state.x) == 0
                && Double.compare(y, state.y) == 0
                && Double.compare(z, state.z) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y, z);
    }

    @Override
    public String toString() {
        return "State3d{" + x + ", " + y + ", " + z + '}';
    }
}
