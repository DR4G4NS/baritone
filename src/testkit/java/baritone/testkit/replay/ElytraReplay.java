/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package baritone.testkit.replay;

import baritone.testkit.pathfinding.VoxelGrid;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ElytraReplay {

    private ElytraReplay() {}

    public static Result run(ElytraState initial, List<ElytraControl> controls, VoxelGrid world,
                             double safetyMargin, int maximumTicks) {
        List<ElytraState> trace = new ArrayList<>();
        trace.add(initial);
        ElytraState state = initial;
        int rocketsUsed = 0;
        boolean collided = false;
        int ticks = Math.min(maximumTicks, controls.size());
        for (int index = 0; index < ticks; index++) {
            ElytraFlightModel.Step step = ElytraFlightModel.step(state, controls.get(index), world, safetyMargin);
            state = step.state();
            trace.add(state);
            rocketsUsed += step.rocketUsed() ? 1 : 0;
            if (step.collided()) {
                collided = true;
                break;
            }
        }
        return new Result(trace, collided, rocketsUsed);
    }

    public record Result(List<ElytraState> trace, boolean collided, int rocketsUsed) {
        public Result {
            trace = Collections.unmodifiableList(new ArrayList<>(trace));
        }

        public ElytraState finalState() {
            return trace.get(trace.size() - 1);
        }
    }
}
