/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package baritone.gametest;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.Settings;
import baritone.api.pathing.goals.GoalBlock;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.level.Level;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@SuppressWarnings("UnstableApiUsage")
public final class BaritoneClientSmokeTest implements FabricClientGameTest {

    private static final String PLAYER = "BaritoneGameTest";
    private static final int PATH_TIMEOUT_TICKS = 1_200;

    @Override
    public void runTest(ClientGameTestContext context) {
        if (!FabricLoader.getInstance().isModLoaded("baritone-meteor")) {
            throw new AssertionError("The remapped Baritone Fabric artifact was not loaded");
        }
        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
            singleplayer.getClientLevel().waitForChunksRender();
            try {
                if (!Boolean.parseBoolean(System.getenv("BARITONE_ELYTRA_ONLY"))) {
                    prepareFlatCourse(singleplayer);
                    waitAt(context, 0, 80, 0, 200);
                    gotoAndWait(context, 20, 80, 0, "flat");

                    prepareStairs(singleplayer);
                    waitAt(context, 0, 80, 0, 200);
                    withMovementSettings(context, false, false);
                    gotoAndWait(context, 6, 86, 0, "stairs-and-height-change");
                    withMovementSettings(context, true, true);

                    prepareMiningTunnel(singleplayer);
                    waitAt(context, 0, 80, 0, 200);
                    gotoAndWait(context, 20, 80, 0, "mining-tunnel");

                    prepareFlatCourse(singleplayer);
                    waitAt(context, 0, 80, 0, 200);
                    withMovementSettings(context, false, false);
                    context.runOnClient(client -> primary().getCustomGoalProcess()
                            .setGoalAndPath(new GoalBlock(30, 80, 0)));
                    context.waitTicks(10);
                    singleplayer.getServer().runCommand("fill 12 80 -3 12 82 3 minecraft:stone");
                    waitAt(context, 30, 80, 0, PATH_TIMEOUT_TICKS);
                    assertStopped(context, "dynamic-obstacle-replan");
                    withMovementSettings(context, true, true);

                    preparePillar(singleplayer);
                    waitAt(context, 0, 80, 0, 200);
                    context.waitTicks(20);
                    gotoAndWait(context, 0, 86, 0, "pillar-placement");

                    prepareFlatCourse(singleplayer);
                    waitAt(context, 0, 80, 0, 200);
                    context.runOnClient(client -> primary().getCustomGoalProcess()
                            .setGoalAndPath(new GoalBlock(30, 80, 0)));
                    context.waitTicks(20);
                    context.runOnClient(client -> {
                        primary().getCustomGoalProcess().onLostControl();
                        primary().getCustomGoalProcess().setGoalAndPath(new GoalBlock(-16, 80, 0));
                    });
                    waitAt(context, -16, 80, 0, PATH_TIMEOUT_TICKS);
                    assertStopped(context, "cancel-and-retarget");
                }

                runOpenOverworldElytraCommandScenario(context, singleplayer);
                runNetherLavaElytraCommandScenario(context, singleplayer);
                runDimensionElytraLandingScenario(context, singleplayer, Level.OVERWORLD,
                        "minecraft:overworld", "minecraft:stone", "overworld");
                runDimensionElytraLandingScenario(context, singleplayer, Level.NETHER,
                        "minecraft:the_nether", "minecraft:netherrack", "nether");
                runDimensionElytraLandingScenario(context, singleplayer, Level.END,
                        "minecraft:the_end", "minecraft:end_stone", "end");
                context.takeScreenshot("baritone-client-pathfinding-passed");
            } catch (Throwable failure) {
                try {
                    context.takeScreenshot("baritone-client-pathfinding-failed");
                } catch (Throwable screenshotFailure) {
                    failure.addSuppressed(screenshotFailure);
                }
                throw failure;
            }
        }
    }

    private static void prepareFlatCourse(TestSingleplayerContext singleplayer) {
        singleplayer.getServer().runCommand("fill -24 79 -6 40 79 6 minecraft:stone");
        singleplayer.getServer().runCommand("fill -24 80 -6 40 85 6 minecraft:air");
        singleplayer.getServer().runCommand("tp " + PLAYER + " 0.5 80 0.5");
    }

    private static void prepareMiningTunnel(TestSingleplayerContext singleplayer) {
        singleplayer.getServer().runCommand("fill -2 79 -2 22 83 2 minecraft:stone");
        singleplayer.getServer().runCommand("fill -1 80 -1 21 82 1 minecraft:air");
        singleplayer.getServer().runCommand("fill 9 80 -1 10 81 1 minecraft:stone");
        singleplayer.getServer().runCommand("give " + PLAYER + " minecraft:diamond_pickaxe");
        singleplayer.getServer().runCommand("tp " + PLAYER + " 0.5 80 0.5");
    }

    private static void prepareStairs(TestSingleplayerContext singleplayer) {
        singleplayer.getServer().runCommand("fill -4 79 -3 3 79 3 minecraft:stone");
        singleplayer.getServer().runCommand("fill -4 80 -3 12 90 3 minecraft:air");
        for (int step = 0; step < 6; step++) {
            singleplayer.getServer().runCommand("setblock " + (step + 1) + " " + (80 + step)
                    + " 0 minecraft:stone");
        }
        singleplayer.getServer().runCommand("tp " + PLAYER + " 0.5 80 0.5");
    }

    private static void preparePillar(TestSingleplayerContext singleplayer) {
        singleplayer.getServer().runCommand("fill -4 79 -4 4 79 4 minecraft:stone");
        singleplayer.getServer().runCommand("fill -4 80 -4 4 90 4 minecraft:air");
        singleplayer.getServer().runCommand("give " + PLAYER + " minecraft:cobblestone 16");
        singleplayer.getServer().runCommand("tp " + PLAYER + " 0.5 80 0.5");
    }

    private static void withMovementSettings(ClientGameTestContext context, boolean allowBreak, boolean allowPlace) {
        context.runOnClient(client -> {
            Settings settings = BaritoneAPI.getSettings();
            settings.allowBreak.value = allowBreak;
            settings.allowPlace.value = allowPlace;
        });
    }

    private static void runOpenOverworldElytraCommandScenario(ClientGameTestContext context,
                                                               TestSingleplayerContext singleplayer) {
        singleplayer.getServer().runCommand("execute in minecraft:overworld run fill -8 70 -8 24 100 8 minecraft:air");
        singleplayer.getServer().runCommand("execute in minecraft:overworld run fill 25 70 -8 56 100 8 minecraft:air");
        singleplayer.getServer().runCommand("execute in minecraft:overworld run fill -8 69 -8 56 69 8 minecraft:stone");
        singleplayer.getServer().runCommand("item replace entity " + PLAYER + " armor.chest with minecraft:elytra");
        giveBoostingRockets(singleplayer, 32);
        singleplayer.getServer().runCommand("execute in minecraft:overworld run tp " + PLAYER + " 0.5 95 0.5");
        context.waitFor(client -> client.player != null && client.player.level().dimension() == Level.OVERWORLD, 400);
        runElytraFlight(context, 40, false, singleplayer);
    }

    private static void runNetherLavaElytraCommandScenario(ClientGameTestContext context,
                                                            TestSingleplayerContext singleplayer) {
        singleplayer.getServer().runCommand("item replace entity " + PLAYER + " armor.chest with minecraft:elytra");
        giveBoostingRockets(singleplayer, 32);
        singleplayer.getServer().runCommand("execute in minecraft:the_nether run tp " + PLAYER + " 0.5 95 0.5");
        context.waitFor(client -> client.player != null && client.player.level().dimension() == Level.NETHER, 400);
        for (int minX : new int[]{-8, 22, 52}) {
            int maxX = Math.min(minX + 29, 80);
            for (int minZ : new int[]{-64, -31, 2, 35}) {
                int maxZ = Math.min(minZ + 32, 64);
                singleplayer.getServer().runCommand(
                        "execute in minecraft:the_nether run fill "
                                + minX + " 80 " + minZ + " "
                                + maxX + " 104 " + maxZ + " minecraft:air"
                );
            }
        }
        singleplayer.getServer().runCommand("execute in minecraft:the_nether run fill -8 79 -64 80 79 64 minecraft:netherrack");
        context.waitTicks(10);
        runElytraFlight(context, 64, true, singleplayer);
    }

    private static void runDimensionElytraLandingScenario(ClientGameTestContext context,
                                                          TestSingleplayerContext singleplayer,
                                                          net.minecraft.resources.ResourceKey<Level> dimension,
                                                          String dimensionId,
                                                          String surfaceBlock,
                                                          String scenario) {
        final int surfaceY = 80;
        final int destY = surfaceY + 24;
        final int startY = destY + 2;
        final int targetX = 24;
        final String in = "execute in " + dimensionId + " run ";
        singleplayer.getServer().runCommand("difficulty peaceful");
        singleplayer.getServer().runCommand("gamerule doMobSpawning false");
        singleplayer.getServer().runCommand("gamerule fallDamage true");
        if (dimension == Level.END) {
            singleplayer.getServer().runCommand(in + "kill @e[type=minecraft:ender_dragon]");
        }
        singleplayer.getServer().runCommand(in + "fill -16 " + surfaceY + " -16 80 " + surfaceY + " 16 " + surfaceBlock);
        singleplayer.getServer().runCommand(in + "fill -16 " + (surfaceY + 1) + " -16 80 120 16 minecraft:air");
        singleplayer.getServer().runCommand("item replace entity " + PLAYER + " armor.chest with minecraft:elytra");
        giveBoostingRockets(singleplayer, 32);
        singleplayer.getServer().runCommand(in + "tp " + PLAYER + " 0.5 " + startY + " 0.5");
        singleplayer.getServer().runCommand("effect give " + PLAYER + " minecraft:instant_health 1 10 true");
        context.waitFor(client -> client.player != null && client.player.level().dimension() == dimension, 400);
        context.waitTicks(15);
        runElytraLanding(context, scenario, targetX, destY, surfaceY);
        context.takeScreenshot("elytra-landing-" + scenario);
    }

    private static void runElytraLanding(ClientGameTestContext context, String scenario,
                                         int targetX, int destY, int surfaceY) {
        context.waitFor(client -> client.player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST)
                .is(Items.ELYTRA), 200);
        context.runOnClient(client -> {
            Settings settings = BaritoneAPI.getSettings();
            settings.elytraTermsAccepted.value = true;
            settings.elytraPredictTerrain.value = false;
            settings.elytraAutoJump.value = false;
            settings.elytraMinFireworksBeforeLanding.value = 0;
            settings.elytraAllowEmergencyLand.value = false;
            settings.elytraAutoSeedAndPrediction.value = true;
            settings.elytraFlightProfile.value = "med";
            settings.disconnectOnArrival.value = false;
        });
        context.waitTicks(5);
        float startHealth = context.computeOnClient(client -> client.player.getHealth());
        AtomicBoolean touchedEnvironmentalHazard = new AtomicBoolean();
        AtomicReference<String> lastFlightState = new AtomicReference<>("not airborne");
        context.runOnClient(client -> {
            client.player.startFallFlying();
            client.player.connection.send(new ServerboundPlayerCommandPacket(
                    client.player,
                    ServerboundPlayerCommandPacket.Action.START_FALL_FLYING
            ));
            if (!primary().getCommandManager().execute("elytragoto " + targetX + " " + destY + " 0")) {
                throw new AssertionError("#elytragoto was not accepted by the command manager for " + scenario);
            }
        });
        context.waitFor(client -> {
            recordFlightState(client.player, touchedEnvironmentalHazard, lastFlightState);
            requireFlightAlive(client.player, lastFlightState);
            return client.player != null && client.player.isFallFlying();
        }, 200);
        context.waitFor(client -> {
            recordFlightState(client.player, touchedEnvironmentalHazard, lastFlightState);
            requireFlightAlive(client.player, lastFlightState);
            return !primary().getElytraProcess().isActive();
        }, PATH_TIMEOUT_TICKS);
        context.waitTicks(30);
        LandingSnapshot snapshot = context.computeOnClient(client -> new LandingSnapshot(
                client.player != null && client.player.isAlive(),
                client.player != null && client.player.onGround(),
                client.player != null && client.player.isFallFlying(),
                client.player != null && client.player.isInLava(),
                client.player != null && client.player.isOnFire(),
                client.player == null ? 0.0F : client.player.getHealth(),
                client.player == null ? 0.0D : client.player.getY(),
                client.player == null ? Double.POSITIVE_INFINITY
                        : client.player.position().distanceToSqr(targetX + 0.5D, client.player.getY(), 0.5D)
        ));
        if (touchedEnvironmentalHazard.get() || !snapshot.alive() || snapshot.lava() || snapshot.fire()) {
            throw new AssertionError(scenario + " landing hit a hazard; last state: " + lastFlightState.get());
        }
        if (snapshot.flying()) {
            throw new AssertionError(scenario + " landing left the player gliding; last state: " + lastFlightState.get());
        }
        if (!snapshot.onGround()) {
            throw new AssertionError(scenario + " landing did not put the player on the ground; last state: "
                    + lastFlightState.get());
        }
        float healthLost = startHealth - snapshot.health();
        if (healthLost > 2.0F) {
            throw new AssertionError(scenario + " landing dealt too much damage: lost " + healthLost
                    + " health (start=" + startHealth + ", end=" + snapshot.health() + "); last state: "
                    + lastFlightState.get());
        }
        if (Math.abs(snapshot.y() - (surfaceY + 1.0D)) > 4.0D) {
            throw new AssertionError(scenario + " landing Y " + snapshot.y() + " is not on surface " + surfaceY
                    + "; last state: " + lastFlightState.get());
        }
        if (snapshot.horizontalDistanceSqr() > 16.0D * 16.0D) {
            throw new AssertionError(scenario + " landing missed the pad; last state: " + lastFlightState.get());
        }
    }

    private record LandingSnapshot(boolean alive, boolean onGround, boolean flying, boolean lava, boolean fire,
                                   float health, double y, double horizontalDistanceSqr) {}

    private static void runElytraFlight(ClientGameTestContext context, int targetX, boolean addLateLava,
                                         TestSingleplayerContext singleplayer) {
        context.waitFor(client -> client.player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST)
                .is(Items.ELYTRA), 200);
        context.runOnClient(client -> {
            Settings settings = BaritoneAPI.getSettings();
            settings.elytraTermsAccepted.value = true;
            settings.elytraPredictTerrain.value = false;
            settings.elytraAutoJump.value = false;
            settings.elytraMinFireworksBeforeLanding.value = 0;
            settings.elytraAllowEmergencyLand.value = false;
            settings.elytraAutoSeedAndPrediction.value = true;
            settings.elytraFlightProfile.value = "med";
        });
        context.waitTicks(5);
        int rocketsBefore = rocketCount(context);
        AtomicBoolean touchedEnvironmentalHazard = new AtomicBoolean();
        AtomicReference<String> lastFlightState = new AtomicReference<>("not airborne");
        context.runOnClient(client -> {
            client.player.startFallFlying();
            client.player.connection.send(new ServerboundPlayerCommandPacket(
                    client.player,
                    ServerboundPlayerCommandPacket.Action.START_FALL_FLYING
            ));
            if (!primary().getCommandManager().execute("elytragoto " + targetX + " 90 0")) {
                throw new AssertionError("#elytragoto was not accepted by the command manager");
            }
        });
        context.waitFor(client -> {
            recordFlightState(client.player, touchedEnvironmentalHazard, lastFlightState);
            requireFlightAlive(client.player, lastFlightState);
            return client.player != null && client.player.isFallFlying();
        }, 200);
        if (addLateLava) {
            context.waitFor(client -> {
                recordFlightState(client.player, touchedEnvironmentalHazard, lastFlightState);
                requireFlightAlive(client.player, lastFlightState);
                return client.player != null && client.player.getX() > 8.0D;
            }, PATH_TIMEOUT_TICKS);
            singleplayer.getServer().runCommand(
                    "execute in minecraft:the_nether run fill 28 99 -2 28 99 2 minecraft:lava"
            );
        }
        context.waitFor(client -> {
            recordFlightState(client.player, touchedEnvironmentalHazard, lastFlightState);
            requireFlightAlive(client.player, lastFlightState);
            return (client.player != null && client.player.getX() > targetX - 10.0D)
                    || !primary().getElytraProcess().isActive();
        }, PATH_TIMEOUT_TICKS);
        context.waitFor(client -> {
            recordFlightState(client.player, touchedEnvironmentalHazard, lastFlightState);
            requireFlightAlive(client.player, lastFlightState);
            return !primary().getElytraProcess().isActive();
        }, PATH_TIMEOUT_TICKS);
        boolean healthyAtEnd = context.computeOnClient(client -> client.player != null
                && client.player.isAlive() && !client.player.isInLava() && !client.player.isOnFire());
        if (touchedEnvironmentalHazard.get() || !healthyAtEnd) {
            throw new AssertionError("Open #elytragoto touched lava, burned, or died; last state: "
                    + lastFlightState.get());
        }
        int rocketsUsed = rocketsBefore - rocketCount(context);
        if (rocketsUsed < 0 || rocketsUsed > 16) {
            throw new AssertionError("Open #elytragoto used implausible rocket count " + rocketsUsed);
        }
        boolean nearDestination = context.computeOnClient(client -> client.player != null
                && client.player.position().distanceToSqr(targetX + 0.5D, client.player.getY(), 0.5D) <= 21.0D * 21.0D);
        if (!nearDestination) {
            throw new AssertionError("Open #elytragoto stopped without reaching destination tolerance; last state: "
                    + lastFlightState.get());
        }
    }

    private static void recordFlightState(net.minecraft.client.player.LocalPlayer player,
                                          AtomicBoolean touchedEnvironmentalHazard,
                                          AtomicReference<String> lastFlightState) {
        if (player == null) {
            lastFlightState.set("player=null");
            return;
        }
        String state = "pos=" + player.position()
                + ", motion=" + player.getDeltaMovement()
                + ", pitch=" + player.getXRot()
                + ", flying=" + player.isFallFlying()
                + ", alive=" + player.isAlive()
                + ", health=" + player.getHealth()
                + ", lava=" + player.isInLava()
                + ", fire=" + player.isOnFire();
        if (player.isAlive()) {
            lastFlightState.set(state);
        }
        if (!player.isAlive() || player.isInLava() || player.isOnFire()) {
            touchedEnvironmentalHazard.set(true);
        }
    }

    private static void requireFlightAlive(net.minecraft.client.player.LocalPlayer player,
                                           AtomicReference<String> lastFlightState) {
        if (player != null && !player.isAlive()) {
            throw new AssertionError("#elytragoto player died; " + lastFlightState.get());
        }
    }

    private static int rocketCount(ClientGameTestContext context) {
        return context.computeOnClient(client -> client.player.getInventory().getNonEquipmentItems().stream()
                .filter(stack -> stack.is(Items.FIREWORK_ROCKET))
                .mapToInt(stack -> stack.getCount())
                .sum());
    }

    private static void giveBoostingRockets(TestSingleplayerContext singleplayer, int count) {
        singleplayer.getServer().runCommand("clear " + PLAYER + " minecraft:firework_rocket");
        singleplayer.getServer().runOnServer(server -> {
            net.minecraft.server.level.ServerPlayer player = server.getPlayerList().getPlayerByName(PLAYER);
            if (player == null) {
                throw new AssertionError("Missing GameTest player while giving fireworks");
            }
            ItemStack rockets = new ItemStack(Items.FIREWORK_ROCKET, count);
            rockets.set(DataComponents.FIREWORKS, new Fireworks(3, List.of()));
            if (!player.getInventory().add(rockets)) {
                throw new AssertionError("Could not add boosting fireworks to GameTest inventory");
            }
            player.getInventory().setChanged();
            player.containerMenu.broadcastChanges();
        });
    }

    private static void gotoAndWait(ClientGameTestContext context, int x, int y, int z, String scenario) {
        context.runOnClient(client -> primary().getCustomGoalProcess().setGoalAndPath(new GoalBlock(x, y, z)));
        waitAt(context, x, y, z, PATH_TIMEOUT_TICKS);
        assertStopped(context, scenario);
    }

    private static void waitAt(ClientGameTestContext context, int x, int y, int z, int timeoutTicks) {
        context.waitFor(client -> client.player != null
                && client.player.blockPosition().distSqr(new GoalBlock(x, y, z).getGoalPos()) <= 2.25D,
                timeoutTicks);
    }

    private static void assertStopped(ClientGameTestContext context, String scenario) {
        try {
            context.waitFor(client -> !primary().getCustomGoalProcess().isActive(), 200);
        } catch (AssertionError timeout) {
            throw new AssertionError("Baritone process survived completed scenario " + scenario, timeout);
        }
    }

    private static IBaritone primary() {
        return BaritoneAPI.getProvider().getPrimaryBaritone();
    }
}
