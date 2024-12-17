package yuanjv.spectatormod;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.TeleportTarget;

import java.util.*;
import java.util.concurrent.CompletableFuture;

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
        UseBlockCallback.EVENT.register(
                (player, world, hand, hitResult) -> {
                    if (spectators.containsKey(player)) {
                        return ActionResult.FAIL;
                    }
                    return ActionResult.PASS;
                }
        );
        UseItemCallback.EVENT.register(
                (player, world, hand) -> {
                    if (spectators.containsKey(player)) {
                        return ActionResult.FAIL;
                    }
                    return ActionResult.PASS;
                }
        );

        UseItemCallback.EVENT.register(
                (player, world, hand) -> {
                    if (spectators.containsKey(player)) {
                        return ActionResult.FAIL;
                    }
                    return ActionResult.PASS;
                }
        );


    }

    private void startSpectating(ServerPlayerEntity source, ServerPlayerEntity target) {
        SpectateData spectateData = new SpectateData(
                source.getPos(),
                source.getYaw(),
                source.getPitch(),
                source.getWorld().getRegistryKey(),
                target,
                source.getVehicle(), // Save the current vehicle
                source.getInventory().main // Save inventory
        );
        if (source.getVehicle() != null) {
            source.stopRiding();
        }
        source.getInventory().clear();


        spectators.put(source, spectateData);

        // Use CompletableFuture to ensure dimension is loaded
        CompletableFuture<Void> dimensionLoadFuture = new CompletableFuture<>();

        source.getServer().executeSync(() -> {
                    source.teleport(
                            target.getServerWorld(),
                            0,
                            0,
                            0,
                            Set.of(),
                            target.getYaw(),
                            target.getPitch(),
                            false
                    );

                    // Wait a short tick to ensure complete loading
                    source.getServer().executeSync(() -> {
                        // Set camera entity
                        source.setCameraEntity(target);
                        dimensionLoadFuture.complete(null);
                    });
                }
        );


        // Set the camera to the target
        source.setCameraEntity(target);

    }

    private void stopSpectating(ServerPlayerEntity source) {
        if (!spectators.containsKey(source)) {
            source.sendMessage(Text.literal("You are not currently spectating anyone."), false);
            return;
        }

        // Restore the source player's camera and position
        SpectateData spectateData = spectators.remove(source);
        source.setCameraEntity(source);

        // Use CompletableFuture to ensure dimension is loaded
        CompletableFuture<Void> dimensionLoadFuture = new CompletableFuture<>();

        source.getServer().executeSync(() -> {
            // Teleport to target's world and position
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

            // Wait a short tick to ensure complete loading
            source.getServer().executeSync(() -> {
                if (spectateData.vehicle != null) {
                    source.startRiding(spectateData.vehicle, true);
                }
                //source.getInventory().main.add(spectateData.inventory);
                dimensionLoadFuture.complete(null);
            });
        });


    }

    private static class SpectateData {
        public final Vec3d position;
        public final float yaw;
        public final float pitch;
        public final net.minecraft.registry.RegistryKey<net.minecraft.world.World> dimension;
        public final ServerPlayerEntity target;
        public final Entity vehicle;
        public final List<ItemStack> inventory;

        public SpectateData(
                Vec3d position,
                float yaw,
                float pitch,
                net.minecraft.registry.RegistryKey<net.minecraft.world.World> dimension,
                ServerPlayerEntity target,
                Entity vehicle,
                List<ItemStack> inventory
        ) {
            this.position = position;
            this.yaw = yaw;
            this.pitch = pitch;
            this.dimension = dimension;
            this.target = target;
            this.vehicle = vehicle;
            this.inventory = inventory;
        }
    }
}