/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package baritone.testkit.replay;

public record ElytraState(double x, double y, double z,
                          double velocityX, double velocityY, double velocityZ,
                          double yaw, double pitch,
                          int rockets, int durability, int tick) {

    public ElytraState {
        if (rockets < 0 || durability < 0 || tick < 0) {
            throw new IllegalArgumentException("Resources and tick must not be negative");
        }
    }

    public double speed() {
        return Math.sqrt(velocityX * velocityX + velocityY * velocityY + velocityZ * velocityZ);
    }
}
