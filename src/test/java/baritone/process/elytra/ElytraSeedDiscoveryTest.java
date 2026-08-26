package baritone.process.elytra;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ElytraSeedDiscoveryTest {

    @Test
    public void parserAcceptsLocalizedSeedResponseShapeAndRejectsOtherNumbers() {
        assertTrue(ElytraSeedDiscovery.parseSeedMessage("Seed: [-123456789]").isPresent());
        assertEquals(-123456789L, ElytraSeedDiscovery.parseSeedMessage("Semilla: [-123456789]").getAsLong());
        assertFalse(ElytraSeedDiscovery.parseSeedMessage("There are 42 players online").isPresent());
    }
}
