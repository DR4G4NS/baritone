/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package baritone.process.elytra;

import java.util.Locale;

/**
 * Coarse user-facing policies for trading fireworks against travel speed.
 * Safety decisions are deliberately not configurable through this profile.
 */
public enum ElytraFlightProfile {
    MIN("min", 0.75D, true, 30, 2.0D, 72.0D),
    MED("med", 1.20D, false, 20, 2.5D, 88.0D),
    MAX("max", 1.45D, false, 16, 3.0D, 104.0D);

    private final String settingValue;
    private final double targetSpeed;
    private final boolean conserveOnDescent;
    private final int simulationTicks;
    private final double fluidMargin;
    private final double loadedHorizon;

    ElytraFlightProfile(String settingValue, double targetSpeed, boolean conserveOnDescent,
                        int simulationTicks, double fluidMargin, double loadedHorizon) {
        this.settingValue = settingValue;
        this.targetSpeed = targetSpeed;
        this.conserveOnDescent = conserveOnDescent;
        this.simulationTicks = simulationTicks;
        this.fluidMargin = fluidMargin;
        this.loadedHorizon = loadedHorizon;
    }

    public double targetSpeed(double configuredMediumSpeed) {
        return switch (this) {
            case MIN -> Math.min(configuredMediumSpeed, this.targetSpeed);
            case MED -> configuredMediumSpeed;
            case MAX -> Math.max(configuredMediumSpeed, this.targetSpeed);
        };
    }

    public boolean conserveOnDescent(boolean explicitlyConserve) {
        return explicitlyConserve || this.conserveOnDescent;
    }

    public int simulationTicks(int configuredMediumTicks) {
        return this == MED ? configuredMediumTicks : this.simulationTicks;
    }

    public double fluidMargin() {
        return this.fluidMargin;
    }

    public double loadedHorizon() {
        return this.loadedHorizon;
    }

    public String settingValue() {
        return this.settingValue;
    }

    public static ElytraFlightProfile fromSetting(String value) {
        if (value == null) {
            return MED;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "min" -> MIN;
            case "max" -> MAX;
            default -> MED;
        };
    }
}
