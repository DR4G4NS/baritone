/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package baritone.testkit.replay;

public final class ControlInput {

    private final String command;
    private final double deltaX;
    private final double deltaY;
    private final double deltaZ;

    public ControlInput(String command, double deltaX, double deltaY, double deltaZ) {
        this.command = command;
        this.deltaX = deltaX;
        this.deltaY = deltaY;
        this.deltaZ = deltaZ;
    }

    public String command() {
        return command;
    }

    public double deltaX() {
        return deltaX;
    }

    public double deltaY() {
        return deltaY;
    }

    public double deltaZ() {
        return deltaZ;
    }
}
