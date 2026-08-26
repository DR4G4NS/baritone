/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package baritone.testkit.replay;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class InfrastructureReplayTest {

    @Test
    public void seededReplayIsReproducibleAndMakesProgress() {
        List<ControlInput> firstInputs = ReplayFixtures.seededForwardControls(20);
        List<ControlInput> secondInputs = ReplayFixtures.seededForwardControls(20);

        ReplayResult first = InfrastructureReplay.run(
                new State3d(0.0, 64.0, 0.0), firstInputs, 20, state -> state.x() >= 10.0
        );
        ReplayResult second = InfrastructureReplay.run(
                new State3d(0.0, 64.0, 0.0), secondInputs, 20, state -> state.x() >= 10.0
        );

        assertEquals("advance", firstInputs.get(0).command());
        assertEquals(ReplayResult.Status.COMPLETED, first.status());
        assertEquals(first.trace(), second.trace());
        assertEquals(10, first.steps());
        assertEquals(new State3d(10.0, 64.0, 0.011258348418951344), first.finalState());
        for (int index = 1; index < first.trace().size(); index++) {
            assertTrue(first.trace().get(index).x() > first.trace().get(index - 1).x());
        }
    }

    @Test
    public void replayReportsTimeoutAtDeterministicStepLimit() {
        ReplayResult result = InfrastructureReplay.run(
                new State3d(0.0, 64.0, 0.0), ReplayFixtures.seededForwardControls(20), 5,
                state -> state.x() >= 10.0
        );

        assertEquals(ReplayResult.Status.TIMED_OUT, result.status());
        assertEquals(5, result.steps());
        assertEquals(5.0, result.finalState().x(), 0.0);
    }
}
