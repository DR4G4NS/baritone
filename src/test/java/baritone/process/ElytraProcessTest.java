package baritone.process;

import net.minecraft.world.phys.Vec3;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ElytraProcessTest {

    @Test
    public void normalLandingDoesNotStartAtTheOldFortyEightBlockBoundary() {
        assertFalse(ElytraProcess.shouldBeginLandingApproach(48.0 * 48.0, false));
        assertFalse(ElytraProcess.shouldBeginLandingApproach(16.0 * 16.0, false));
        assertTrue(ElytraProcess.shouldBeginLandingApproach(15.9 * 15.9, false));
    }

    @Test
    public void emergencyLandingBypassesTheDestinationApproachRadius() {
        assertTrue(ElytraProcess.shouldBeginLandingApproach(1_000_000.0, true));
    }

    @Test
    public void landingColumnUsesAFlightAppropriateCaptureCylinder() {
        Vec3 top = new Vec3(48.5, 85.5, 0.5);
        assertTrue(ElytraProcess.isInsideLandingCapture(new Vec3(51.9, 81.0, 1.0), top));
        assertTrue(ElytraProcess.isInsideLandingCapture(new Vec3(55.5, 90.0, 0.5), top));
        assertFalse(ElytraProcess.isInsideLandingCapture(new Vec3(61.0, 85.5, 0.5), top));
        assertFalse(ElytraProcess.isInsideLandingCapture(new Vec3(48.5, 68.0, 0.5), top));
    }

    @Test
    public void landingFlightControlsDoNotDisableRocketsDuringApproach() {
        assertFalse(ElytraProcess.shouldUseLandingFlightControls(ElytraProcess.State.FLYING));
        assertFalse(ElytraProcess.shouldUseLandingFlightControls(ElytraProcess.State.START_FLYING));
        assertTrue(ElytraProcess.shouldUseLandingFlightControls(ElytraProcess.State.LANDING));
    }

    @Test
    public void landingSearchIsBoundedAndOverflowSafe() {
        baritone.api.utils.BetterBlockPos start = new baritone.api.utils.BetterBlockPos(0, 90, 0);
        assertTrue(ElytraProcess.isWithinLandingSearch(new baritone.api.utils.BetterBlockPos(32, 80, 32), start));
        assertFalse(ElytraProcess.isWithinLandingSearch(
                new baritone.api.utils.BetterBlockPos(Integer.MAX_VALUE, 90, Integer.MIN_VALUE), start));
        assertFalse(ElytraProcess.isWithinLandingSearch(new baritone.api.utils.BetterBlockPos(0, 123, 0), start));
    }
}
