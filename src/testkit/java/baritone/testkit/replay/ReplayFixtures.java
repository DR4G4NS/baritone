/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package baritone.testkit.replay;

import baritone.testkit.pathfinding.TestkitSeeds;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class ReplayFixtures {

    private ReplayFixtures() {}

    public static List<ControlInput> seededForwardControls(int count) {
        Random random = new Random(TestkitSeeds.REPLAY);
        List<ControlInput> inputs = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            double lateral = (random.nextDouble() - 0.5) * 0.02;
            inputs.add(new ControlInput("advance", 1.0, 0.0, lateral));
        }
        return inputs;
    }
}
