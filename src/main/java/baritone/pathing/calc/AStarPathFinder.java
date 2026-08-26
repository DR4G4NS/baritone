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

package baritone.pathing.calc;

import baritone.Baritone;
import baritone.api.pathing.calc.IPath;
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.movement.ActionCosts;
import baritone.api.utils.BetterBlockPos;
import baritone.api.utils.SettingsUtil;
import baritone.pathing.calc.openset.BinaryHeapOpenSet;
import baritone.pathing.movement.CalculationContext;
import baritone.pathing.movement.Moves;
import baritone.utils.pathing.BetterWorldBorder;
import baritone.utils.pathing.Favoring;
import baritone.utils.pathing.MutableMoveResult;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * The actual A* pathfinding
 *
 * @author leijurv
 */
public final class AStarPathFinder extends AbstractNodeCostSearch {

    private final Favoring favoring;
    private final CalculationContext calcContext;

    public AStarPathFinder(BetterBlockPos realStart, int startX, int startY, int startZ, Goal goal, Favoring favoring, CalculationContext context) {
        super(realStart, startX, startY, startZ, goal, context);
        this.favoring = favoring;
        this.calcContext = context;
    }

    @Override
    protected Optional<IPath> calculate0(long primaryTimeout, long failureTimeout) {
        int minY = calcContext.world.dimensionType().minY();
        int height = calcContext.world.dimensionType().height();
        long maxYExclusive = (long) minY + height;
        if (height <= 0 || !isYInBounds(startY, minY, maxYExclusive)
                || !BetterBlockPos.isValidForLongSerialization(startX, startY, startZ)) {
            setMetrics(PathSearchMetrics.create(PathSearchMetrics.Outcome.INVALID_START, 0L,
                    0, 0, 0, 0, 0, Double.POSITIVE_INFINITY));
            return Optional.empty();
        }
        startNode = getNodeAtPosition(startX, startY, startZ, BetterBlockPos.serializeToLong(startX, startY, startZ));
        startNode.cost = 0;
        startNode.combinedCost = startNode.estimatedCostToGoal;
        BinaryHeapOpenSet openSet = new BinaryHeapOpenSet();
        openSet.insert(startNode);
        double[] bestHeuristicSoFar = new double[COEFFICIENTS.length];//keep track of the best node by the metric of (estimatedCostToGoal + cost / COEFFICIENTS[i])
        for (int i = 0; i < bestHeuristicSoFar.length; i++) {
            bestHeuristicSoFar[i] = startNode.estimatedCostToGoal;
            bestSoFar[i] = startNode;
        }
        MutableMoveResult res = new MutableMoveResult();
        BetterWorldBorder worldBorder = new BetterWorldBorder(calcContext.world.getWorldBorder());
        long startTime = System.nanoTime();
        boolean slowPath = Baritone.settings().slowPath.value;
        if (slowPath) {
            logDebug("slowPath is on, path timeout will be " + Baritone.settings().slowPathTimeoutMS.value + "ms instead of " + primaryTimeout + "ms");
        }
        long primaryTimeoutNanos = timeoutToNanos(slowPath ? Baritone.settings().slowPathTimeoutMS.value : primaryTimeout);
        long failureTimeoutNanos = timeoutToNanos(slowPath ? Baritone.settings().slowPathTimeoutMS.value : failureTimeout);
        boolean failing = true;
        int numNodes = 0;
        int numMovementsConsidered = 0;
        int numReopened = 0;
        int peakOpenSetSize = openSet.size();
        int numEmptyChunk = 0;
        boolean isFavoring = !favoring.isEmpty();
        int timeCheckInterval = 1 << 6;
        int pathingMaxChunkBorderFetch = Baritone.settings().pathingMaxChunkBorderFetch.value; // grab all settings beforehand so that changing settings during pathing doesn't cause a crash or unpredictable behavior
        double minimumImprovement = Baritone.settings().minimumImprovementRepropagation.value ? MIN_IMPROVEMENT : 0;
        Moves[] allMoves = Moves.values();
        while (!openSet.isEmpty() && numEmptyChunk < pathingMaxChunkBorderFetch && !cancelRequested) {
            if ((numNodes & (timeCheckInterval - 1)) == 0) { // only call this once every 64 nodes (about half a millisecond)
                long elapsedNanos = System.nanoTime() - startTime;
                if (elapsedNanos >= failureTimeoutNanos || (!failing && elapsedNanos >= primaryTimeoutNanos)) {
                    break;
                }
            }
            if (slowPath) {
                try {
                    Thread.sleep(Baritone.settings().slowPathTimeDelayMS.value);
                } catch (InterruptedException ignored) {}
            }
            PathNode currentNode = openSet.removeLowest();
            mostRecentConsidered = currentNode;
            numNodes++;
            if (goal.isInGoal(currentNode.x, currentNode.y, currentNode.z)) {
                long elapsedNanos = System.nanoTime() - startTime;
                setMetrics(PathSearchMetrics.create(PathSearchMetrics.Outcome.SUCCESS, elapsedNanos,
                        numNodes, numMovementsConsidered, numReopened, mapSize(), peakOpenSetSize, currentNode.cost));
                logDebug("Took " + TimeUnit.NANOSECONDS.toMillis(elapsedNanos) + "ms, " + numMovementsConsidered + " movements considered");
                return Optional.of(new Path(realStart, startNode, currentNode, numNodes, goal, calcContext));
            }
            for (Moves moves : allMoves) {
                int newX = currentNode.x + moves.xOffset;
                int newZ = currentNode.z + moves.zOffset;
                if (!moves.dynamicXZ
                        && isDifferentChunk(currentNode.x, currentNode.z, newX, newZ)
                        && !calcContext.isLoaded(newX, newZ)) {
                    // only need to check if the destination is a loaded chunk if it's in a different chunk than the start of the movement
                    numEmptyChunk++;
                    continue;
                }
                if (!moves.dynamicXZ && !worldBorder.entirelyContains(newX, newZ)) {
                    continue;
                }
                if (!moves.dynamicY && !isYInBounds((long) currentNode.y + moves.yOffset, minY, maxYExclusive)) {
                    continue;
                }
                res.reset();
                moves.apply(calcContext, currentNode.x, currentNode.y, currentNode.z, res);
                numMovementsConsidered++;
                double actionCost = res.cost;
                if (actionCost >= ActionCosts.COST_INF) {
                    continue;
                }
                if (actionCost <= 0 || Double.isNaN(actionCost)) {
                    throw new IllegalStateException(String.format(
                            "%s from %s %s %s calculated implausible cost %s",
                            moves,
                            SettingsUtil.maybeCensor(currentNode.x),
                            SettingsUtil.maybeCensor(currentNode.y),
                            SettingsUtil.maybeCensor(currentNode.z),
                            actionCost));
                }
                // check destination after verifying it's not COST_INF -- some movements return COST_INF without adjusting the destination
                if (!isYInBounds(res.y, minY, maxYExclusive)) {
                    continue;
                }
                if (moves.dynamicXZ && !worldBorder.entirelyContains(res.x, res.z)) { // see issue #218
                    continue;
                }
                if (moves.dynamicXZ
                        && isDifferentChunk(currentNode.x, currentNode.z, res.x, res.z)
                        && !calcContext.isLoaded(res.x, res.z)) {
                    continue;
                }
                if (!moves.dynamicXZ && (res.x != newX || res.z != newZ)) {
                    throw new IllegalStateException(String.format(
                            "%s from %s %s %s ended at x z %s %s instead of %s %s",
                            moves,
                            SettingsUtil.maybeCensor(currentNode.x),
                            SettingsUtil.maybeCensor(currentNode.y),
                            SettingsUtil.maybeCensor(currentNode.z),
                            SettingsUtil.maybeCensor(res.x),
                            SettingsUtil.maybeCensor(res.z),
                            SettingsUtil.maybeCensor(newX),
                            SettingsUtil.maybeCensor(newZ)));
                }
                if (!moves.dynamicY && res.y != currentNode.y + moves.yOffset) {
                    throw new IllegalStateException(String.format(
                            "%s from %s %s %s ended at y %s instead of %s",
                            moves,
                            SettingsUtil.maybeCensor(currentNode.x),
                            SettingsUtil.maybeCensor(currentNode.y),
                            SettingsUtil.maybeCensor(currentNode.z),
                            SettingsUtil.maybeCensor(res.y),
                            SettingsUtil.maybeCensor(currentNode.y + moves.yOffset)));
                }
                if (!BetterBlockPos.isValidForLongSerialization(res.x, res.y, res.z)) {
                    continue;
                }
                long positionKey = BetterBlockPos.serializeToLong(res.x, res.y, res.z);
                if (isFavoring) {
                    // see issue #18
                    actionCost *= favoring.calculate(positionKey);
                }
                PathNode neighbor = getNodeAtPosition(res.x, res.y, res.z, positionKey);
                double tentativeCost = currentNode.cost + actionCost;
                if (neighbor.cost - tentativeCost > minimumImprovement) {
                    if (neighbor.cost < ActionCosts.COST_INF && !neighbor.isOpen()) {
                        numReopened++;
                    }
                    neighbor.previous = currentNode;
                    neighbor.cost = tentativeCost;
                    neighbor.combinedCost = tentativeCost + neighbor.estimatedCostToGoal;
                    if (neighbor.isOpen()) {
                        openSet.update(neighbor);
                    } else {
                        openSet.insert(neighbor);//dont double count, dont insert into open set if it's already there
                        peakOpenSetSize = Math.max(peakOpenSetSize, openSet.size());
                    }
                    for (int i = 0; i < COEFFICIENTS.length; i++) {
                        double heuristic = neighbor.estimatedCostToGoal + neighbor.cost / COEFFICIENTS[i];
                        if (bestHeuristicSoFar[i] - heuristic > minimumImprovement) {
                            bestHeuristicSoFar[i] = heuristic;
                            bestSoFar[i] = neighbor;
                            if (failing && getDistFromStartSq(neighbor) > MIN_DIST_PATH * MIN_DIST_PATH) {
                                failing = false;
                            }
                        }
                    }
                }
            }
        }
        if (cancelRequested) {
            long elapsedNanos = System.nanoTime() - startTime;
            setMetrics(PathSearchMetrics.create(PathSearchMetrics.Outcome.CANCELLED, elapsedNanos,
                    numNodes, numMovementsConsidered, numReopened, mapSize(), peakOpenSetSize, Double.POSITIVE_INFINITY));
            return Optional.empty();
        }
        System.out.println(numMovementsConsidered + " movements considered");
        System.out.println("Open set size: " + openSet.size());
        System.out.println("PathNode map size: " + mapSize());
        long elapsedNanos = System.nanoTime() - startTime;
        long nodesPerSecond = elapsedNanos <= 0 ? 0 : Math.round(numNodes * 1_000_000_000.0D / elapsedNanos);
        System.out.println(nodesPerSecond + " nodes per second");
        Optional<IPath> result = bestSoFar(true, numNodes);
        PathSearchMetrics.Outcome outcome = result.isPresent()
                ? PathSearchMetrics.Outcome.PARTIAL
                : PathSearchMetrics.Outcome.FAILURE;
        double finalCost = result.map(path -> costAt(path.getDest())).orElse(Double.POSITIVE_INFINITY);
        setMetrics(PathSearchMetrics.create(outcome, elapsedNanos, numNodes, numMovementsConsidered,
                numReopened, mapSize(), peakOpenSetSize, finalCost));
        if (result.isPresent()) {
            logDebug("Took " + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime) + "ms, " + numMovementsConsidered + " movements considered");
        }
        return result;
    }

    static boolean isYInBounds(long y, int minY, long maxYExclusive) {
        return y >= minY && y < maxYExclusive;
    }

    static boolean isDifferentChunk(int startX, int startZ, int endX, int endZ) {
        return startX >> 4 != endX >> 4 || startZ >> 4 != endZ >> 4;
    }

    static long timeoutToNanos(long timeoutMillis) {
        if (timeoutMillis < 0) {
            throw new IllegalArgumentException("Pathing timeout must not be negative: " + timeoutMillis + "ms");
        }
        return Math.min(TimeUnit.MILLISECONDS.toNanos(timeoutMillis), Long.MAX_VALUE >>> 1);
    }
}
