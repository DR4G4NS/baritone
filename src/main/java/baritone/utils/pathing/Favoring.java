/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package baritone.utils.pathing;

import baritone.api.pathing.calc.IPath;
import baritone.api.utils.BetterBlockPos;
import baritone.api.utils.Helper;
import baritone.api.utils.IPlayerContext;
import baritone.pathing.movement.CalculationContext;
import baritone.pathing.calc.HierarchicalPathPlanner;
import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;

public final class Favoring {

    private final Long2DoubleOpenHashMap favorings;
    private final HierarchicalPathPlanner.Corridor corridor;

    public Favoring(IPlayerContext ctx, IPath previous, CalculationContext context) {
        this(previous, context, null);
        for (Avoidance avoid : Avoidance.create(ctx)) {
            avoid.applySpherical(favorings);
        }
        Helper.HELPER.logDebug("Favoring size: " + favorings.size());
    }

    public Favoring(IPath previous, CalculationContext context) { // create one just from previous path, no mob avoidances
        this(previous, context, null);
    }

    public Favoring(IPlayerContext ctx, IPath previous, CalculationContext context,
                    HierarchicalPathPlanner.Corridor corridor) {
        this(previous, context, corridor);
        for (Avoidance avoid : Avoidance.create(ctx)) {
            avoid.applySpherical(favorings);
        }
        Helper.HELPER.logDebug("Favoring size: " + favorings.size());
    }

    private Favoring(IPath previous, CalculationContext context, HierarchicalPathPlanner.Corridor corridor) {
        favorings = new Long2DoubleOpenHashMap();
        favorings.defaultReturnValue(1.0D);
        this.corridor = corridor;
        double coeff = context.backtrackCostFavoringCoefficient;
        if (coeff != 1D && previous != null) {
            previous.positions().forEach(pos -> {
                if (BetterBlockPos.isValidForLongSerialization(pos.x, pos.y, pos.z)) {
                    favorings.put(BetterBlockPos.serializeToLong(pos.x, pos.y, pos.z), coeff);
                }
            });
        }
    }

    public boolean isEmpty() {
        return favorings.isEmpty() && (corridor == null || !corridor.isPresent());
    }

    public double calculate(long positionKey) {
        double result = favorings.get(positionKey);
        if (corridor != null && corridor.isPresent()) {
            BetterBlockPos pos = BetterBlockPos.deserializeFromLong(positionKey);
            if (!corridor.containsBlock(pos.x, pos.z)) {
                result *= 1.05D;
            }
        }
        return result;
    }
}
