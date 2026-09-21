package baritone.process.elytra;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ElytraDimensionPolicyTest {

    @Test
    public void overworldCruiseFollowsPlayerInsteadOfY64() {
        assertEquals(106, ElytraDimensionPolicy.cruiseAltitude(ElytraDimensionPolicy.Kind.OVERWORLD, -64, 384, 106));
        assertEquals(-48, ElytraDimensionPolicy.cruiseAltitude(ElytraDimensionPolicy.Kind.OVERWORLD, -64, 384, -80));
        assertEquals(304, ElytraDimensionPolicy.cruiseAltitude(ElytraDimensionPolicy.Kind.OVERWORLD, -64, 384, 400));
    }

    @Test
    public void netherCruiseStaysInTheCaveBandOrOnTheRoof() {
        assertEquals(80, ElytraDimensionPolicy.cruiseAltitude(ElytraDimensionPolicy.Kind.NETHER, 0, 256, 80));
        assertEquals(40, ElytraDimensionPolicy.cruiseAltitude(ElytraDimensionPolicy.Kind.NETHER, 0, 256, 12));
        assertEquals(110, ElytraDimensionPolicy.cruiseAltitude(ElytraDimensionPolicy.Kind.NETHER, 0, 256, 120));
        assertEquals(128, ElytraDimensionPolicy.cruiseAltitude(ElytraDimensionPolicy.Kind.NETHER, 0, 256, 130));
    }

    @Test
    public void endCruiseStaysAboveTheVoid() {
        assertEquals(64, ElytraDimensionPolicy.cruiseAltitude(ElytraDimensionPolicy.Kind.END, 0, 256, 64));
        assertEquals(40, ElytraDimensionPolicy.cruiseAltitude(ElytraDimensionPolicy.Kind.END, 0, 256, 8));
        assertEquals(120, ElytraDimensionPolicy.cruiseAltitude(ElytraDimensionPolicy.Kind.END, 0, 256, 200));
    }

    @Test
    public void netherCavesDoNotTreatTheBedrockRoofAsALandingHeightmap() {
        assertFalse(ElytraDimensionPolicy.useHeightmapLanding(ElytraDimensionPolicy.Kind.NETHER, 80, 127));
        assertTrue(ElytraDimensionPolicy.useHeightmapLanding(ElytraDimensionPolicy.Kind.NETHER, 128, 127));
        assertTrue(ElytraDimensionPolicy.useHeightmapLanding(ElytraDimensionPolicy.Kind.OVERWORLD, 106, 80));
        assertTrue(ElytraDimensionPolicy.useHeightmapLanding(ElytraDimensionPolicy.Kind.END, 90, 64));
        assertFalse(ElytraDimensionPolicy.useHeightmapLanding(ElytraDimensionPolicy.Kind.OVERWORLD, 70, 80));
    }

    @Test
    public void onlyTheEndSkipsFluidSweeps() {
        assertTrue(ElytraDimensionPolicy.shouldSweepFluids(ElytraDimensionPolicy.Kind.OVERWORLD));
        assertTrue(ElytraDimensionPolicy.shouldSweepFluids(ElytraDimensionPolicy.Kind.NETHER));
        assertFalse(ElytraDimensionPolicy.shouldSweepFluids(ElytraDimensionPolicy.Kind.END));
        assertTrue(ElytraDimensionPolicy.preferHigherLanding(ElytraDimensionPolicy.Kind.NETHER));
        assertFalse(ElytraDimensionPolicy.preferHigherLanding(ElytraDimensionPolicy.Kind.OVERWORLD));
    }

    @Test
    public void sweptVolumeCoversNegativeMotionWithoutCollapsing() {
        AABB hitbox = new AABB(5, 10, 5, 5.6, 11.8, 5.6);
        Vec3 motion = new Vec3(-2, -1, -3);
        AABB inflated = hitbox.inflate(motion.x, motion.y, motion.z);
        AABB swept = ElytraDimensionPolicy.sweptCollisionVolume(hitbox, motion);

        assertTrue(swept.minX < swept.maxX);
        assertTrue(swept.minY < swept.maxY);
        assertTrue(swept.minZ < swept.maxZ);
        assertTrue(swept.minX <= hitbox.minX + motion.x);
        assertTrue(swept.maxX >= hitbox.maxX);
        assertTrue("inflate(motion) is not a directional sweep",
                inflated.minX > hitbox.minX + motion.x || inflated.maxX < hitbox.maxX);
    }

    @Test
    public void sweptVolumeCoversAOneBlockWallAtHighSpeed() {
        AABB hitbox = new AABB(0.2, 10, 10.2, 0.8, 10.8, 10.8);
        AABB swept = ElytraDimensionPolicy.sweptCollisionVolume(hitbox, new Vec3(0, 0, 2.4));
        AABB wall = new AABB(0, 10, 12, 1, 12, 13);
        assertTrue("high-speed flight must reserve the 1-block wall in the swept volume",
                swept.intersects(wall));
    }
}
