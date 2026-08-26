package baritone.process.elytra;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ElytraFlightProfileTest {

    @Test
    public void profilesHaveOrderedSpeedSafetyAndLookaheadPolicies() {
        assertTrue(ElytraFlightProfile.MIN.targetSpeed(1.2D) < ElytraFlightProfile.MED.targetSpeed(1.2D));
        assertTrue(ElytraFlightProfile.MED.targetSpeed(1.2D) < ElytraFlightProfile.MAX.targetSpeed(1.2D));
        assertTrue(ElytraFlightProfile.MIN.conserveOnDescent(false));
        assertFalse(ElytraFlightProfile.MAX.conserveOnDescent(false));
        assertTrue(ElytraFlightProfile.MIN.loadedHorizon() < ElytraFlightProfile.MAX.loadedHorizon());
        assertTrue(ElytraFlightProfile.MIN.fluidMargin() < ElytraFlightProfile.MAX.fluidMargin());
    }

    @Test
    public void parserIsCaseInsensitiveAndFallsBackSafely() {
        assertEquals(ElytraFlightProfile.MIN, ElytraFlightProfile.fromSetting("MIN"));
        assertEquals(ElytraFlightProfile.MED, ElytraFlightProfile.fromSetting("unknown"));
        assertEquals(ElytraFlightProfile.MAX, ElytraFlightProfile.fromSetting(" max "));
    }
}
