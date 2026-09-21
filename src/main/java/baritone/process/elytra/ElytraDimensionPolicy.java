/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package baritone.process.elytra;

import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Dimension-specific cruise, landing, and collision policy for {@code #elytragoto}.
 * Native nether-pathfinder is 128 blocks tall, so Overworld / End / Nether-roof
 * flights keep a window around the player instead of a hardcoded Y=64.
 */
public final class ElytraDimensionPolicy {

    public enum Kind {
        OVERWORLD,
        NETHER,
        END
    }

    static final int NETHER_ROOF_Y = 128;
    static final int NETHER_CAVE_MIN_CRUISE = 40;
    static final int NETHER_CAVE_MAX_CRUISE = 110;
    static final int END_MIN_CRUISE = 40;
    static final int END_MAX_CRUISE = 120;

    private ElytraDimensionPolicy() {}

    public static Kind kind(ResourceKey<Level> dimension) {
        if (dimension == Level.NETHER) {
            return Kind.NETHER;
        }
        if (dimension == Level.END) {
            return Kind.END;
        }
        return Kind.OVERWORLD;
    }

    public static int cruiseAltitude(ResourceKey<Level> dimension, int minY, int worldHeight, int playerY) {
        return cruiseAltitude(kind(dimension), minY, worldHeight, playerY);
    }

    public static int cruiseAltitude(Kind kind, int minY, int worldHeight, int playerY) {
        if (kind == Kind.NETHER) {
            if (playerY >= NETHER_ROOF_Y) {
                return NETHER_ROOF_Y;
            }
            return Mth.clamp(playerY, NETHER_CAVE_MIN_CRUISE, NETHER_CAVE_MAX_CRUISE);
        }
        if (kind == Kind.END) {
            return Mth.clamp(playerY, END_MIN_CRUISE, END_MAX_CRUISE);
        }
        final int minCruise = minY + 16;
        final int maxCruise = minY + worldHeight - 16;
        if (maxCruise < minCruise) {
            return Mth.clamp(playerY, minY, minY + Math.max(0, worldHeight - 1));
        }
        return Mth.clamp(playerY, minCruise, maxCruise);
    }

    public static boolean useHeightmapLanding(ResourceKey<Level> dimension, int playerY, int motionBlockingHeight) {
        return useHeightmapLanding(kind(dimension), playerY, motionBlockingHeight);
    }

    public static boolean useHeightmapLanding(Kind kind, int playerY, int motionBlockingHeight) {
        // Nether caves sit under a bedrock roof. The heightmap is that roof, not a
        // safe floor, so only use it on the actual nether roof or in open dimensions.
        if (kind == Kind.NETHER && playerY < NETHER_ROOF_Y) {
            return false;
        }
        return playerY > motionBlockingHeight;
    }

    /**
     * True when MOTION_BLOCKING is a ceiling/overhang at or above the flyer
     * rather than a floor they can descend onto.
     */
    public static boolean isColumnRoof(int playerY, int motionBlockingHeight) {
        return motionBlockingHeight >= playerY;
    }

    public static boolean shouldSweepFluids(ResourceKey<Level> dimension) {
        return shouldSweepFluids(kind(dimension));
    }

    public static boolean shouldSweepFluids(Kind kind) {
        return kind != Kind.END;
    }

    public static boolean preferHigherLanding(ResourceKey<Level> dimension) {
        return preferHigherLanding(kind(dimension));
    }

    public static boolean preferHigherLanding(Kind kind) {
        return kind == Kind.NETHER;
    }

    /**
     * Directional swept volume used by the flight simulator.
     * {@link AABB#inflate(double, double, double)} collapses when any axis is
     * negative; {@link AABB#expandTowards(double, double, double)} covers the
     * whole motion segment, which is what stops high-speed tunneling.
     */
    public static AABB sweptCollisionVolume(AABB hitbox, Vec3 motion) {
        return hitbox.expandTowards(motion.x, motion.y, motion.z).inflate(0.01D);
    }
}
