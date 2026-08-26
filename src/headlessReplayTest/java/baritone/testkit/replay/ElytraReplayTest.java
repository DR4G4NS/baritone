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
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ElytraReplayTest {

    @Test
    public void openFlightIsDeterministicSmoothAndResourceBounded() {
        VoxelGrid world = new VoxelGrid(200, 100, 200);
        ElytraState initial = new ElytraState(100.0D, 50.0D, 100.0D,
                0.0D, 0.0D, 0.3D, 0.0D, 0.0D, 3, 100, 0);
        List<ElytraControl> controls = controls(40, 45.0D, -2.0D, 0, 15, 30);

        ElytraReplay.Result first = ElytraReplay.run(initial, controls, world, 0.1D, 40);
        ElytraReplay.Result second = ElytraReplay.run(initial, controls, world, 0.1D, 40);

        assertEquals(first, second);
        assertFalse(first.collided());
        assertEquals(3, first.rocketsUsed());
        assertEquals(98, first.finalState().durability());
        assertTrue(first.finalState().x() < initial.x());
        assertTrue(first.finalState().z() > initial.z());
        for (int index = 1; index < first.trace().size(); index++) {
            assertTrue(Math.abs(first.trace().get(index).yaw() - first.trace().get(index - 1).yaw()) <= 8.0D);
            assertTrue(Math.abs(first.trace().get(index).pitch() - first.trace().get(index - 1).pitch()) <= 6.0D);
        }
    }

    @Test
    public void sweptPlayerVolumeDetectsWallCollision() {
        VoxelGrid world = new VoxelGrid(30, 20, 30);
        for (int x = 0; x < 30; x++) {
            for (int y = 0; y < 20; y++) {
                world.setBlocked(x, y, 15, true);
            }
        }
        ElytraState initial = new ElytraState(10.0D, 10.0D, 10.0D,
                0.0D, 0.0D, 1.0D, 0.0D, 0.0D, 0, 100, 0);

        ElytraReplay.Result result = ElytraReplay.run(initial, controls(20, 0.0D, 0.0D), world, 0.1D, 20);

        assertTrue(result.collided());
        assertTrue(result.finalState().z() < 15.0D);
        assertEquals(0.0D, result.finalState().speed(), 0.0D);
    }

    private static List<ElytraControl> controls(int count, double yaw, double pitch, int... rocketTicks) {
        List<ElytraControl> result = new ArrayList<>(count);
        for (int tick = 0; tick < count; tick++) {
            boolean rocket = false;
            for (int rocketTick : rocketTicks) {
                rocket |= tick == rocketTick;
            }
            result.add(new ElytraControl(yaw, pitch, rocket));
        }
        return result;
    }
}
