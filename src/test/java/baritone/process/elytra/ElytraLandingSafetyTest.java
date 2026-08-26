package baritone.process.elytra;

import baritone.process.ElytraProcess;
import baritone.testkit.pathfinding.VoxelGrid;
import baritone.testkit.replay.ElytraControl;
import baritone.testkit.replay.ElytraFlightModel;
import baritone.testkit.replay.ElytraState;
import net.minecraft.world.level.block.Blocks;
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
        assertFalse(ElytraBehavior.canDeployLandingRecovery(true, -14.9F));
        assertTrue(ElytraBehavior.canDeployLandingRecovery(true, -15.0F));
        assertFalse(ElytraBehavior.canDeployLandingRecovery(false, -30.0F));
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
    public void landingFlightControlsDoNotDisableRocketsDuringApproach() {
        assertFalse(ElytraProcess.shouldUseLandingFlightControls(ElytraProcess.State.FLYING));
        assertFalse(ElytraProcess.shouldUseLandingFlightControls(ElytraProcess.State.START_FLYING));
        assertTrue(ElytraProcess.shouldUseLandingFlightControls(ElytraProcess.State.LANDING));
    }

    @Test
    public void hazardousSurfacesAreRejectedBeforeThePlayerTouchesDown() {
        assertTrue(ElytraProcess.isHazardousLandingSurface(Blocks.MAGMA_BLOCK.defaultBlockState()));
        assertTrue(ElytraProcess.isHazardousLandingSurface(Blocks.FIRE.defaultBlockState()));
        assertTrue(ElytraProcess.isHazardousLandingSurface(Blocks.CAMPFIRE.defaultBlockState()));
        assertTrue(ElytraProcess.isHazardousLandingSurface(Blocks.CACTUS.defaultBlockState()));
        assertFalse(ElytraProcess.isHazardousLandingSurface(Blocks.NETHERRACK.defaultBlockState()));
        assertFalse(ElytraProcess.isHazardousLandingSurface(Blocks.STONE.defaultBlockState()));
    }

    @Test
    public void landingFlareKeepsImpactSpeedBelowVanillaFallDamageReset() {
        VoxelGrid world = new VoxelGrid(40, 32, 40);
        for (int x = 0; x < 40; x++) {
            for (int z = 0; z < 40; z++) {
                world.setBlocked(x, 0, z, true);
            }
        }
        ElytraState state = new ElytraState(20.0D, 24.5D, 20.0D,
                0.85D, -0.12D, 0.05D, 0.0D, 8.0D, 2, 100, 0);
        double lastSafeVerticalSpeed = state.velocityY();
        boolean touchedGround = false;
        boolean recoveryOnCooldown = false;
        int cooldown = 0;
        for (int tick = 0; tick < 160; tick++) {
            double height = state.y() - 1.0D;
            boolean needsBoost = ElytraBehavior.requiresLandingBoost(height, state.velocityY(), recoveryOnCooldown);
            float commandedPitch = needsBoost
                    ? ElytraBehavior.LANDING_RECOVERY_PITCH
                    : ElytraBehavior.landingPitch(height, state.velocityY(), (float) state.pitch());
            boolean rocket = ElytraBehavior.canDeployLandingRecovery(needsBoost, (float) state.pitch());
            ElytraFlightModel.Step step = ElytraFlightModel.step(
                    state,
                    new ElytraControl(0.0D, commandedPitch, rocket),
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
                touchedGround = true;
                break;
            }
            state = step.state();
            lastSafeVerticalSpeed = state.velocityY();
        }
        assertTrue(touchedGround);
        assertTrue("impact vy=" + lastSafeVerticalSpeed, lastSafeVerticalSpeed > ElytraBehavior.VANILLA_FALL_DISTANCE_RESET_SPEED);
    }

    @Test
    public void verticalWindowTracksDimensionHeightAndKeepsNativeCoordinatesStable() {
        int overworld = ElytraVerticalWindow.choose(-64, 384, 80, 96, false);
        assertTrue(overworld <= 80);
        assertTrue(overworld + 128 > 96);
        assertTrue(ElytraVerticalWindow.choose(0, 256, 220, 230, false) >= 112);
        assertTrue(ElytraVerticalWindow.choose(0, 256, 80, 90, true) == 0);
    }
}
