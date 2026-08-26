/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package baritone.testkit.replay;

import baritone.testkit.pathfinding.VoxelGrid;

/** Headless deterministic approximation of vanilla Elytra motion and firework acceleration. */
public final class ElytraFlightModel {

    public static final double PLAYER_HALF_WIDTH = 0.3D;
    public static final double FLYING_HEIGHT = 0.6D;
    private static final double MAX_YAW_CHANGE = 8.0D;
    private static final double MAX_PITCH_CHANGE = 6.0D;

    private ElytraFlightModel() {}

    public static Step step(ElytraState state, ElytraControl control, VoxelGrid world, double safetyMargin) {
        double yaw = approachWrapped(state.yaw(), control.targetYaw(), MAX_YAW_CHANGE);
        double pitch = approach(state.pitch(), clamp(control.targetPitch(), -89.0D, 89.0D), MAX_PITCH_CHANGE);
        double yawRadians = Math.toRadians(yaw);
        double pitchRadians = Math.toRadians(pitch);
        double horizontalLook = Math.cos(pitchRadians);
        double lookX = -Math.sin(yawRadians) * horizontalLook;
        double lookY = -Math.sin(pitchRadians);
        double lookZ = Math.cos(yawRadians) * horizontalLook;

        double velocityX = state.velocityX();
        double velocityY = state.velocityY();
        double velocityZ = state.velocityZ();
        double horizontalSpeed = Math.sqrt(velocityX * velocityX + velocityZ * velocityZ);
        double horizontalLookLength = Math.sqrt(lookX * lookX + lookZ * lookZ);
        double liftFactor = horizontalLook * horizontalLook;

        velocityY += -0.08D + liftFactor * 0.06D;
        if (velocityY < 0.0D && horizontalLookLength > 0.0D) {
            double lift = velocityY * -0.1D * liftFactor;
            velocityX += lookX / horizontalLookLength * lift;
            velocityY += lift;
            velocityZ += lookZ / horizontalLookLength * lift;
        }
        if (pitchRadians < 0.0D && horizontalLookLength > 0.0D) {
            double climb = horizontalSpeed * -Math.sin(pitchRadians) * 0.04D;
            velocityX -= lookX / horizontalLookLength * climb;
            velocityY += climb * 3.2D;
            velocityZ -= lookZ / horizontalLookLength * climb;
        }
        if (horizontalLookLength > 0.0D) {
            velocityX += (lookX / horizontalLookLength * horizontalSpeed - velocityX) * 0.1D;
            velocityZ += (lookZ / horizontalLookLength * horizontalSpeed - velocityZ) * 0.1D;
        }

        boolean rocketUsed = control.useRocket() && state.rockets() > 0;
        int rockets = state.rockets();
        if (rocketUsed) {
            velocityX += lookX * 0.1D + (lookX * 1.5D - velocityX) * 0.5D;
            velocityY += lookY * 0.1D + (lookY * 1.5D - velocityY) * 0.5D;
            velocityZ += lookZ * 0.1D + (lookZ * 1.5D - velocityZ) * 0.5D;
            rockets--;
        }

        velocityX *= 0.99D;
        velocityY *= 0.98D;
        velocityZ *= 0.99D;
        double nextX = state.x() + velocityX;
        double nextY = state.y() + velocityY;
        double nextZ = state.z() + velocityZ;
        boolean clear = world.isSweptAabbClear(state.x(), state.y(), state.z(), nextX, nextY, nextZ,
                PLAYER_HALF_WIDTH, FLYING_HEIGHT, safetyMargin);
        int nextTick = state.tick() + 1;
        int durability = Math.max(0, state.durability() - (nextTick % 20 == 0 ? 1 : 0));
        ElytraState next = clear
                ? new ElytraState(nextX, nextY, nextZ, velocityX, velocityY, velocityZ,
                yaw, pitch, rockets, durability, nextTick)
                : new ElytraState(state.x(), state.y(), state.z(), 0.0D, 0.0D, 0.0D,
                yaw, pitch, rockets, durability, nextTick);
        return new Step(next, !clear, rocketUsed);
    }

    private static double approach(double current, double target, double maximumChange) {
        return current + clamp(target - current, -maximumChange, maximumChange);
    }

    private static double approachWrapped(double current, double target, double maximumChange) {
        double difference = (target - current) % 360.0D;
        if (difference >= 180.0D) difference -= 360.0D;
        if (difference < -180.0D) difference += 360.0D;
        return current + clamp(difference, -maximumChange, maximumChange);
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    public record Step(ElytraState state, boolean collided, boolean rocketUsed) {}
}
