/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package baritone.utils.pathing;

import baritone.api.utils.BetterBlockPos;
import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class AvoidanceTest {

    @Test
    public void coefficientDistanceDoesNotOverflow() {
        Avoidance avoidance = new Avoidance(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, 2.0D, 10);

        assertEquals(1.0D, avoidance.coefficient(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE), 0.0D);
    }

    @Test
    public void squaredRadiusDoesNotOverflow() {
        Avoidance avoidance = new Avoidance(0, 0, 0, 2.0D, 50_000);

        assertEquals(2.0D, avoidance.coefficient(0, 0, 0), 0.0D);
        assertEquals(2.0D, avoidance.coefficient(50_000, 0, 0), 0.0D);
        assertEquals(1.0D, avoidance.coefficient(50_001, 0, 0), 0.0D);
    }

    @Test
    public void sphericalApplicationUsesSerializedPositionKeys() {
        Avoidance avoidance = new Avoidance(12, -64, -34, 2.0D, 0);
        Long2DoubleOpenHashMap coefficients = new Long2DoubleOpenHashMap();
        coefficients.defaultReturnValue(1.0D);

        avoidance.applySpherical(coefficients);

        long positionKey = BetterBlockPos.serializeToLong(12, -64, -34);
        assertEquals(2.0D, coefficients.get(positionKey), 0.0D);
        assertFalse(coefficients.containsKey(BetterBlockPos.longHash(12, -64, -34)));
    }

    @Test
    public void sphericalApplicationDoesNotWrapPastSerializedRange() {
        Avoidance avoidance = new Avoidance(33_554_431, 0, 0, 2.0D, 1);
        Long2DoubleOpenHashMap coefficients = new Long2DoubleOpenHashMap();
        coefficients.defaultReturnValue(1.0D);

        avoidance.applySpherical(coefficients);

        assertEquals(6, coefficients.size());
        assertEquals(2.0D, coefficients.get(BetterBlockPos.serializeToLong(33_554_431, 0, 0)), 0.0D);
    }
}
