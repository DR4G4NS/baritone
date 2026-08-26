/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package baritone.command.defaults;

import baritone.api.IBaritone;
import baritone.api.command.Command;
import baritone.api.command.argument.IArgConsumer;
import baritone.api.command.datatypes.RelativeCoordinate;
import baritone.api.command.datatypes.RelativeGoal;
import baritone.api.command.exception.CommandException;
import baritone.api.command.exception.CommandInvalidStateException;
import baritone.api.pathing.goals.Goal;
import baritone.api.utils.BetterBlockPos;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/** Sets an explicit coordinate goal and starts the existing Elytra process atomically. */
public final class ElytraGotoCommand extends Command {

    public ElytraGotoCommand(IBaritone baritone) {
        super(baritone, "elytragoto");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        if (args.peekDatatypeOrNull(RelativeCoordinate.INSTANCE) == null) {
            throw new CommandInvalidStateException("Expected destination coordinates");
        }
        args.requireMax(3);
        BetterBlockPos origin = ctx.playerFeet();
        Goal goal = args.getDatatypePost(RelativeGoal.INSTANCE, origin);
        // Use the same validation and warnings as #elytra, but propagate failures to this command.
        new ElytraCommand(baritone).start(goal);
        logDirect("Elytra flying to: " + goal);
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) {
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "Elytra fly to coordinates";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "Sets a coordinate goal and starts the existing Elytra process.",
                "Relative coordinates use the same syntax as #goto.",
                "",
                "Usage:",
                "> elytragoto <x> <z>",
                "> elytragoto <x> <y> <z>"
        );
    }
}
