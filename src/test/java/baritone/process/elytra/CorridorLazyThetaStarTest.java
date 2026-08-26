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
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CorridorLazyThetaStarTest {

    @Test
    public void openCorridorCollapsesToAnyAngleSegment() {
        List<BetterBlockPos> corridor = Arrays.asList(
                new BetterBlockPos(0, 64, 0),
                new BetterBlockPos(1, 64, 0),
                new BetterBlockPos(2, 65, 1),
                new BetterBlockPos(3, 65, 2),
                new BetterBlockPos(4, 66, 3)
        );

        CorridorLazyThetaStar.Result result = CorridorLazyThetaStar.refine(corridor, (from, to) -> true);

        assertEquals(Arrays.asList(corridor.get(0), corridor.get(4)), result.path());
        assertTrue(result.raycasts() > 0);
    }

    @Test
    public void blockedShortcutKeepsRequiredTurn() {
        List<BetterBlockPos> corridor = Arrays.asList(
                new BetterBlockPos(0, 64, 0),
                new BetterBlockPos(1, 64, 0),
                new BetterBlockPos(2, 64, 0),
                new BetterBlockPos(2, 64, 1),
                new BetterBlockPos(2, 64, 2)
        );

        CorridorLazyThetaStar.Result result = CorridorLazyThetaStar.refine(
                corridor,
                (from, to) -> !(from.x < 2 && to.z > 0)
        );

        assertEquals(corridor.get(0), result.path().get(0));
        assertEquals(corridor.get(4), result.path().get(result.path().size() - 1));
        assertTrue(result.path().size() >= 3);
    }
}
