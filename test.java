public class SpectatorMod implements ModInitializer {

    private final Map<ServerPlayerEntity, SpectateData> spectators = new HashMap<>();

    @Override
    public void onInitialize() {
        // Register /spectate command
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
    }

    private void startSpectating(ServerPlayerEntity sourcePlayer, ServerPlayerEntity targetPlayer) {
        // Create the dummy player based on the original player's profile (including username and skin)
        GameProfile gameProfile = sourcePlayer.getGameProfile();
        DummyPlayerEntity dummyPlayer = new DummyPlayerEntity(sourcePlayer.getServer(), gameProfile);

        // Copy necessary attributes from the original player to the dummy
        dummyPlayer.setHealth(sourcePlayer.getHealth());
        dummyPlayer.setInventory(sourcePlayer.getInventory().copy());
        dummyPlayer.setPosition(sourcePlayer.getX(), sourcePlayer.getY(), sourcePlayer.getZ());
        dummyPlayer.setGameMode(GameMode.SURVIVAL);  // Set the dummy to a normal game mode (not spectator)

        // Update dummy's appearance to match the original player (this includes skin)
        dummyPlayer.setCustomName(sourcePlayer.getName());
        dummyPlayer.setDisplayName(sourcePlayer.getDisplayName());

        // Add dummy player to the server (they will appear in the world)
        sourcePlayer.getServer().getPlayerManager().onPlayerConnect(sourcePlayer.getServer().getOverworld(), dummyPlayer);

        // Make the dummy player "take the place" of the original player by synchronizing actions
        dummyPlayer.setPos(sourcePlayer.getX(), sourcePlayer.getY(), sourcePlayer.getZ());
        dummyPlayer.setRotation(sourcePlayer.getYaw(), sourcePlayer.getPitch());

        // Set the original player to Spectator Mode and follow the target player
        sourcePlayer.setGameMode(GameMode.SPECTATOR);
        sourcePlayer.teleport(targetPlayer.getX(), targetPlayer.getY(), targetPlayer.getZ());

        // Synchronize the original player's perspective with the target
        sourcePlayer.lookAt(EntityAnchorArgumentType.EntityAnchor.EYES, targetPlayer.getPos());

        // Keep track of the spectator and their dummy
        spectators.put(sourcePlayer, new SpectateData(dummyPlayer, targetPlayer));
    }

    private void stopSpectating(ServerPlayerEntity sourcePlayer) {
        if (spectators.containsKey(sourcePlayer)) {
            SpectateData data = spectators.get(sourcePlayer);
            ServerPlayerEntity dummyPlayer = data.getDummyPlayer();

            // Copy attributes back to the source player from the dummy
            sourcePlayer.setHealth(dummyPlayer.getHealth());
            sourcePlayer.getInventory().clear();
            sourcePlayer.getInventory().addAll(dummyPlayer.getInventory());
            sourcePlayer.setPosition(dummyPlayer.getX(), dummyPlayer.getY(), dummyPlayer.getZ());

            // Remove dummy player from the server
            sourcePlayer.getServer().getPlayerManager().removePlayer(dummyPlayer);

            // Set the original player back to Survival Mode
            sourcePlayer.setGameMode(GameMode.SURVIVAL);

            // Remove the spectator from the map
            spectators.remove(sourcePlayer);
        }
    }

    // Helper class to store SpectatorData
    private static class SpectateData {
        private final ServerPlayerEntity dummyPlayer;
        private final ServerPlayerEntity targetPlayer;

        public SpectateData(ServerPlayerEntity dummyPlayer, ServerPlayerEntity targetPlayer) {
            this.dummyPlayer = dummyPlayer;
            this.targetPlayer = targetPlayer;
        }

        public ServerPlayerEntity getDummyPlayer() {
            return dummyPlayer;
        }

        public ServerPlayerEntity getTargetPlayer() {
            return targetPlayer;
        }
    }
    // SpectateData class remains the same
    private static class SpectateData {
        public final Vec3d position;
        public final float yaw;
        public final float pitch;
        public final net.minecraft.registry.RegistryKey<net.minecraft.world.World> dimension;
        public final ServerPlayerEntity target;

        public SpectateData(
                Vec3d position,
                float yaw,
                float pitch,
                net.minecraft.registry.RegistryKey<net.minecraft.world.World> dimension,
                ServerPlayerEntity target
        ) {
            this.position = position;
            this.yaw = yaw;
            this.pitch = pitch;
            this.dimension = dimension;
            this.target = target;
        }
    }
}
}
