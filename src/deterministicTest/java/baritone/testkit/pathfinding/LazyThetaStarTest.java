package baritone.testkit.pathfinding;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LazyThetaStarTest {
    @Test
    public void openSpaceCollapsesToAnyAngleSegment() {
        VoxelGrid grid = new VoxelGrid(12, 5, 12);
        VoxelPoint start = new VoxelPoint(1, 2, 1);
        VoxelPoint goal = new VoxelPoint(10, 3, 8);
        LazyThetaStar.Result result = LazyThetaStar.search(grid, start, goal, 0.2);
        assertEquals(2, result.path.size());
        assertEquals(start.distance(goal), result.cost, 1.0E-9);
        assertTrue(result.raycasts > 0);
    }

    @Test
    public void wallForcesVisibleDetour() {
        VoxelGrid grid = new VoxelGrid(12, 5, 12);
        for (int z = 0; z < 9; z++) for (int y = 0; y < 5; y++) grid.setBlocked(6, y, z, true);
        VoxelPoint start = new VoxelPoint(2, 2, 2);
        VoxelPoint goal = new VoxelPoint(9, 2, 2);
        LazyThetaStar.Result result = LazyThetaStar.search(grid, start, goal, 0.1);
        assertFalse(result.path.isEmpty());
        assertTrue(result.path.size() > 2);
        for (int i = 1; i < result.path.size(); i++) assertTrue(grid.hasLineOfSight(result.path.get(i - 1), result.path.get(i), 0.1));
    }

    @Test
    public void sweptMarginRejectsNarrowCorridor() {
        VoxelGrid grid = new VoxelGrid(8, 3, 5);
        for (int x = 0; x < 8; x++) for (int y = 0; y < 3; y++) {
            grid.setBlocked(x, y, 1, true);
            grid.setBlocked(x, y, 3, true);
        }
        VoxelPoint start = new VoxelPoint(1, 1, 2);
        VoxelPoint goal = new VoxelPoint(6, 1, 2);
        assertFalse(LazyThetaStar.search(grid, start, goal, 0.2).path.isEmpty());
        assertTrue(LazyThetaStar.search(grid, start, goal, 0.51).path.isEmpty());
    }

    @Test
    public void cornerCuttingIsRejected() {
        VoxelGrid grid = new VoxelGrid(3, 1, 3);
        grid.setBlocked(1, 0, 0, true);
        grid.setBlocked(0, 0, 1, true);
        assertTrue(LazyThetaStar.search(grid, new VoxelPoint(0, 0, 0), new VoxelPoint(1, 0, 1), 0.0).path.isEmpty());
    }
}
