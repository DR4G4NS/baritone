/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package baritone.testkit.replay;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Pure-Java deterministic replay infrastructure. This intentionally models no Minecraft or Elytra physics.
 */
public final class InfrastructureReplay {

    private InfrastructureReplay() {}

    public static ReplayResult run(State3d initialState, List<ControlInput> inputs, int maxSteps,
                                   Predicate<State3d> completion) {
        if (maxSteps < 0) {
            throw new IllegalArgumentException("maxSteps must not be negative");
        }
        List<State3d> trace = new ArrayList<>();
        State3d state = initialState;
        trace.add(state);
        if (completion.test(state)) {
            return new ReplayResult(ReplayResult.Status.COMPLETED, trace);
        }

        int steps = Math.min(maxSteps, inputs.size());
        for (int index = 0; index < steps; index++) {
            state = state.apply(inputs.get(index));
            trace.add(state);
            if (completion.test(state)) {
                return new ReplayResult(ReplayResult.Status.COMPLETED, trace);
            }
        }
        return new ReplayResult(ReplayResult.Status.TIMED_OUT, trace);
    }
}
