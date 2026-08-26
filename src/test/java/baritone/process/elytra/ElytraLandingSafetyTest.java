package baritone.process.elytra;

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
        assertTrue(ElytraBehavior.requiresLandingBoost(12.0D, -0.19, false));
        assertFalse(ElytraBehavior.requiresLandingBoost(20.0D, -0.50, false));
        assertFalse(ElytraBehavior.requiresLandingBoost(12.0D, -0.71, true));
        assertFalse(ElytraBehavior.requiresLandingBoost(4.0D, -0.90, false));
    }

    @Test
    public void landingPhasesDescendThenFlareWithoutRepeatedRocketRecovery() {
        assertTrue(ElytraBehavior.landingPitch(50.0D, -0.10D, 0.0F) > 0.0F);
        assertEquals(0.0F, ElytraBehavior.landingPitch(20.0D, -0.10D, 0.0F), 0.0F);
        assertTrue(ElytraBehavior.landingPitch(7.0D, -0.30D, 0.0F) < 0.0F);
        assertTrue(ElytraBehavior.shouldWaitForChunks(40.0D, 1.4D, 88.0D));
        assertFalse(ElytraBehavior.shouldWaitForChunks(Double.POSITIVE_INFINITY, 1.4D, 88.0D));
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
