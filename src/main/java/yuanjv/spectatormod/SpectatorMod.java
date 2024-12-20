package yuanjv.spectatormod;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.TeleportTarget;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class SpectatorMod implements ModInitializer {

    private final Map<ServerPlayerEntity, SpectateData> spectators = new HashMap<>();

    @Override
    public void onInitialize() {
        // Register the /spectate command
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("view")
                    .executes(context -> {
                        ServerPlayerEntity sourcePlayer = context.getSource().getPlayer();
                        stopSpectating(sourcePlayer);
                        sourcePlayer.sendMessage(Text.literal("Stopped spectating."), false);
                        return 1;
                    })
                    .then(CommandManager.argument("target", EntityArgumentType.player())
                            .executes(context -> {
                                ServerPlayerEntity sourcePlayer = context.getSource().getPlayer();
                                ServerPlayerEntity targetPlayer;

                                try {
                                    targetPlayer = EntityArgumentType.getPlayer(context, "target");
                                } catch (CommandSyntaxException e) {
                                    sourcePlayer.sendMessage(Text.literal("Player not found."), false);
                                    return 0;
                                }

                                if (spectators.containsKey(sourcePlayer)) {
                                    //sourcePlayer.sendMessage(Text.literal("You are already spectating someone. Use /spectate to stop first."), false);
                                    //return 0;
                                    stopSpectating(sourcePlayer);
                                    startSpectating(sourcePlayer, targetPlayer);
                                    sourcePlayer.sendMessage(Text.literal("Now spectating " + targetPlayer.getName().getString() + "."), false);
                                    return 1;

                                }

                                startSpectating(sourcePlayer, targetPlayer);
                                sourcePlayer.sendMessage(Text.literal("Now spectating " + targetPlayer.getName().getString() + "."), false);
                                return 1;
                            })));

        });

        // Handle player disconnection to reset spectate state
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            if (spectators.containsKey(player)) {
                stopSpectating(player);
            }
        });

        // Prevent interactions while spectating
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (spectators.containsKey(player)) {
                return ActionResult.FAIL;
            }
            return ActionResult.PASS;
        });

        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            if (spectators.containsKey(player)) {
                return ActionResult.FAIL;
            }
            return ActionResult.PASS;
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            // Create a copy of the entries to avoid concurrent modification
            for (Map.Entry<ServerPlayerEntity, SpectateData> entry : new HashMap<>(spectators).entrySet()) {
                ServerPlayerEntity source = entry.getKey();
                SpectateData spectateData = entry.getValue();

                // prevent race
                if (spectateData == null) {
                    continue;
                }

                ServerPlayerEntity target = spectateData.target;

                // Additional checks to ensure the spectating session is valid
                if (!target.isAlive()) { //|| !server.getPlayerManager().getPlayer(target.getUuid()).equals(target)) {
                    stopSpectating(source);
                    continue;
                }

                // Check if target's dimension has changed
                if (!target.getWorld().getRegistryKey().equals(source.getWorld().getRegistryKey())) {
                    source.teleportTo(
                            new TeleportTarget(
                                    target.getServerWorld(),
                                    spectateData.position,
                                    Vec3d.ZERO,
                                    target.getYaw(),
                                    target.getPitch(),
                                    Set.of(),
                                    TeleportTarget.NO_OP
                            )
                    );
                    source.setCameraEntity(target);


                }

            }
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(
                server -> {
                    for (ServerPlayerEntity source : server.getPlayerManager().getPlayerList()) {
                        if (spectators.containsKey(source)) {
                            // prevent client crashes from too many package
                            source.networkHandler.disableFlush();
                            forceStopSpectating(source);

                        }
                    }
                }
        );


    }

    private void startSpectating(ServerPlayerEntity source, ServerPlayerEntity target) {
        GameMode sourceGamemode = source.isSpectator() ? GameMode.SPECTATOR
                : source.isCreative() ? GameMode.CREATIVE
                : GameMode.SURVIVAL;

        SpectateData spectateData = new SpectateData(
                source.getPos(),
                source.getYaw(),
                source.getPitch(),
                source.getWorld().getRegistryKey(),
                target,
                source.getVehicle(),
                sourceGamemode

        );
        if (source.getVehicle() != null) {
            source.stopRiding();
        }
        source.changeGameMode(GameMode.SPECTATOR);

        source.teleportTo(
                new TeleportTarget(
                        target.getServerWorld(),
                        spectateData.position,
                        Vec3d.ZERO,
                        target.getYaw(),
                        target.getPitch(),
                        Set.of(),
                        TeleportTarget.NO_OP


                )
        );
        source.setCameraEntity(target);


        spectators.put(source, spectateData);
    }

    private void stopSpectating(ServerPlayerEntity source) {
        if (!spectators.containsKey(source)) {
            source.sendMessage(Text.literal("You are not currently spectating anyone."), false);
            return;
        }
        if (source.getServer().isRunning()) {
            // Restore the source player's camera and position
            forceStopSpectating(source);
        }

    }

    private void forceStopSpectating(ServerPlayerEntity source) {
        SpectateData spectateData = spectators.remove(source);

        source.setCameraEntity(source);


        source.teleportTo(
                new TeleportTarget(
                        source.getServer().getWorld(spectateData.dimension),
                        spectateData.position,
                        Vec3d.ZERO,
                        spectateData.yaw,
                        spectateData.pitch,
                        Set.of(),
                        TeleportTarget.NO_OP


                )
        );

        source.changeGameMode(spectateData.gameMode);

        if (spectateData.vehicle != null) {
            source.startRiding(spectateData.vehicle, true);
        }
    }

    private static class SpectateData {
        public final Vec3d position;
        public final float yaw;
        public final float pitch;
        public final RegistryKey<World> dimension;
        public final ServerPlayerEntity target;
        public final Entity vehicle;
        public final GameMode gameMode;

        public SpectateData(
                Vec3d position,
                float yaw,
                float pitch,
                RegistryKey<World> dimension,
                ServerPlayerEntity target,
                Entity vehicle,
                GameMode gameMode
        ) {
            this.position = position;
            this.yaw = yaw;
            this.pitch = pitch;
            this.dimension = dimension;
            this.target = target;
            this.vehicle = vehicle;
            this.gameMode = gameMode;
        }
    }
}