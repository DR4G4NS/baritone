/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package baritone.process.elytra;

import baritone.Baritone;
import baritone.api.utils.IPlayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.OptionalLong;

/**
 * Discovers the vanilla world seed without bypassing server permissions.
 */
public final class ElytraSeedDiscovery {

    private static final Pattern SEED_RESPONSE = Pattern.compile(
            "(?i)(?:seed|semilla).*?\\[?(-?\\d{1,20})\\]?"
    );
    private static volatile long awaitingResponseUntil;
    private static volatile boolean enablePredictionOnResponse;

    private ElytraSeedDiscovery() {}

    public static boolean tryDiscover(IPlayerContext ctx) {
        final Minecraft minecraft = ctx.minecraft();
        final boolean mayPredictNether = ctx.world() != null && ctx.world().dimension() == Level.NETHER
                && Baritone.settings().elytraAutoSeedAndPrediction.value;
        if (minecraft.getSingleplayerServer() != null) {
            acceptSeed(minecraft.getSingleplayerServer().getWorldGenSettings().options().seed(), mayPredictNether);
            return true;
        }
        if (ctx.player() == null || ctx.player().connection == null
                || ctx.player().connection.getCommands().getRoot().getChild("seed") == null) {
            return false;
        }
        enablePredictionOnResponse = mayPredictNether;
        awaitingResponseUntil = System.currentTimeMillis() + 10_000L;
        ctx.player().connection.sendCommand("seed");
        return true;
    }

    public static boolean observeServerMessage(Component message) {
        if (System.currentTimeMillis() > awaitingResponseUntil) {
            return false;
        }
        final OptionalLong parsed = parseSeedMessage(message.getString());
        if (parsed.isEmpty()) {
            return false;
        }
        acceptSeed(parsed.getAsLong(), enablePredictionOnResponse);
        awaitingResponseUntil = 0L;
        enablePredictionOnResponse = false;
        return true;
    }

    static OptionalLong parseSeedMessage(String message) {
        final Matcher matcher = SEED_RESPONSE.matcher(message);
        if (!matcher.find()) {
            return OptionalLong.empty();
        }
        try {
            return OptionalLong.of(Long.parseLong(matcher.group(1)));
        } catch (NumberFormatException ignored) {
            return OptionalLong.empty();
        }
    }

    static void acceptSeed(long seed, boolean enablePrediction) {
        Baritone.settings().elytraNetherSeed.value = seed;
        if (enablePrediction) {
            Baritone.settings().elytraPredictTerrain.value = true;
        }
    }
}
