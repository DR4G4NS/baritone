package baritone.process.elytra;

import baritone.testkit.pathfinding.VoxelGrid;
import baritone.testkit.replay.ElytraControl;
import baritone.testkit.replay.ElytraFlightModel;
import baritone.testkit.replay.ElytraState;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ElytraLandingSafetyTest {

    @Test
    public void flareStartsBeforeDescentBecomesCritical() {
        assertFalse(ElytraBehavior.requiresLandingFlare(-0.35));
        assertTrue(ElytraBehavior.requiresLandingFlare(-0.36));
        assertFalse(ElytraBehavior.requiresLandingBoost(12.0D, -0.18, false));
        assertFalse(ElytraBehavior.requiresLandingBoost(12.0D, -0.39, false));
        assertTrue(ElytraBehavior.requiresLandingBoost(12.0D, -0.41, false));
        assertFalse(ElytraBehavior.requiresLandingBoost(20.0D, -0.50, false));
        assertFalse(ElytraBehavior.requiresLandingBoost(12.0D, -0.71, true));
        assertFalse(ElytraBehavior.requiresLandingBoost(4.0D, -0.90, false));
    }

    @Test
    public void recoveryRocketWaitsUntilTheLookVectorIsFlaring() {
        assertFalse(ElytraBehavior.canDeployLandingRecovery(true, 8.0F));
        assertFalse(ElytraBehavior.canDeployLandingRecovery(true, -49.9F));
        assertTrue(ElytraBehavior.canDeployLandingRecovery(true, -50.0F));
        assertFalse(ElytraBehavior.canDeployLandingRecovery(false, -80.0F));
    }

    @Test
    public void landingPhasesBleedSpeedThenFlareBeforeImpact() {
        assertTrue(ElytraBehavior.landingPitch(50.0D, -0.10D, 0.0F) > 0.0F);
        assertEquals(0.0F, ElytraBehavior.landingPitch(20.0D, -0.10D, 0.0F), 0.0F);
        assertTrue(ElytraBehavior.landingPitch(12.0D, -0.10D, 0.0F) < 0.0F);
        assertTrue(ElytraBehavior.landingPitch(7.0D, -0.10D, 0.0F) <= ElytraBehavior.LANDING_FLARE_PITCH);
        assertTrue(ElytraBehavior.landingPitch(20.0D, -0.51D, 8.0F) <= ElytraBehavior.LANDING_FLARE_PITCH);
        assertTrue(ElytraBehavior.shouldWaitForChunks(40.0D, 1.4D, 88.0D));
        assertFalse(ElytraBehavior.shouldWaitForChunks(Double.POSITIVE_INFINITY, 1.4D, 88.0D));
    }

    @Test
    public void overworldLandingTouchesTheSurfaceBelowVanillaFallDamageSpeed() {
        VoxelGrid world = new VoxelGrid(40, 32, 40);
        fillLayer(world, 0, 0, 0, 39, 39);
        LandingResult result = landWithController(
                world,
                new ElytraState(20.0D, 24.5D, 20.0D, 0.70D, -0.12D, 0.10D, 0.0D, 8.0D, 2, 100, 0),
                1.0D
        );
        assertSafeTouchdown("overworld", result);
        assertTrue(result.lastAirborne.y() < 26.0D);
    }

    @Test
    public void netherLandingTouchesNetherrackWithoutHittingTheRoof() {
        VoxelGrid world = new VoxelGrid(40, 48, 40);
        fillLayer(world, 0, 0, 0, 39, 39);
        fillLayer(world, 40, 0, 0, 39, 39);
        LandingResult result = landWithController(
                world,
                new ElytraState(20.0D, 24.5D, 20.0D, 0.70D, -0.12D, 0.10D, 0.0D, 8.0D, 2, 100, 0),
                1.0D
        );
        assertSafeTouchdown("nether", result);
        assertTrue("nether landing climbed into the roof, y=" + result.lastAirborne.y(),
                result.lastAirborne.y() < 38.0D);
    }

    @Test
    public void endLandingStaysOnTheIslandInsteadOfFallingIntoTheVoid() {
        VoxelGrid world = new VoxelGrid(48, 32, 64);
        fillLayer(world, 0, 18, 0, 29, 63);
        LandingResult result = landWithController(
                world,
                new ElytraState(24.0D, 16.5D, 8.0D, 0.0D, -0.10D, 0.05D, 0.0D, -18.0D, 2, 100, 0),
                1.0D
        );
        assertSafeTouchdown("end", result);
        assertTrue("end landing fell off the island sides x=" + result.lastAirborne.x(),
                result.lastAirborne.x() >= 18.3D && result.lastAirborne.x() <= 29.7D);
        assertTrue("end landing hit the void instead of the island y=" + result.lastAirborne.y(),
                result.lastAirborne.y() >= 0.8D);
    }

    @Test
    public void verticalWindowTracksDimensionHeightAndKeepsNativeCoordinatesStable() {
        int overworld = ElytraVerticalWindow.choose(-64, 384, 80, 96, false);
        assertTrue(overworld <= 80);
        assertTrue(overworld + 128 > 96);
        int nether = ElytraVerticalWindow.choose(0, 256, 80, 90, false);
        assertTrue(nether <= 80);
        assertTrue(nether + 128 > 90);
        int end = ElytraVerticalWindow.choose(0, 256, 64, 80, false);
        assertTrue(end <= 64);
        assertTrue(end + 128 > 80);
        assertTrue(ElytraVerticalWindow.choose(0, 256, 220, 230, false) >= 112);
        assertTrue(ElytraVerticalWindow.choose(0, 256, 80, 90, true) == 0);
    }

    private static void fillLayer(VoxelGrid world, int y, int minX, int minZ, int maxX, int maxZ) {
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                world.setBlocked(x, y, z, true);
            }
        }
    }

    private static void assertSafeTouchdown(String dimension, LandingResult result) {
        assertTrue(dimension + " never touched the surface", result.touchedGround);
        assertTrue(dimension + " impact vy=" + result.impactVy,
                result.impactVy > ElytraBehavior.VANILLA_FALL_DISTANCE_RESET_SPEED);
    }

    private static LandingResult landWithController(VoxelGrid world, ElytraState initial, double groundTopY) {
        ElytraState state = initial;
        double impactVy = state.velocityY();
        ElytraState lastAirborne = state;
        boolean recoveryOnCooldown = false;
        int cooldown = 0;
        for (int tick = 0; tick < 160; tick++) {
            double height = state.y() - groundTopY;
            boolean needsBoost = ElytraBehavior.requiresLandingBoost(height, state.velocityY(), recoveryOnCooldown);
            float commandedPitch = needsBoost
                    ? ElytraBehavior.LANDING_RECOVERY_PITCH
                    : ElytraBehavior.landingPitch(height, state.velocityY(), (float) state.pitch());
            boolean rocket = ElytraBehavior.canDeployLandingRecovery(needsBoost, (float) state.pitch());
            ElytraFlightModel.Step step = ElytraFlightModel.step(
                    state,
                    new ElytraControl(state.yaw(), commandedPitch, rocket),
                    world,
                    0.0D
            );
            if (rocket && step.rocketUsed()) {
                recoveryOnCooldown = true;
                cooldown = ElytraBehavior.LANDING_RECOVERY_ROCKET_COOLDOWN_TICKS;
            } else if (cooldown > 0) {
                cooldown--;
                recoveryOnCooldown = cooldown > 0;
            }
            if (step.collided()) {
                return new LandingResult(true, impactVy, lastAirborne);
            }
            state = step.state();
            lastAirborne = state;
            impactVy = state.velocityY();
        }
        return new LandingResult(false, impactVy, lastAirborne);
    }

    private record LandingResult(boolean touchedGround, double impactVy, ElytraState lastAirborne) {}
}
