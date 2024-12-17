package yuanjv.spectatormod;


import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.item.ItemStack;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.packet.c2s.common.SyncedClientOptions;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.net.SocketAddress;
import java.util.*;

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
	}
	private void startSpectating(ServerPlayerEntity sourcePlayer, ServerPlayerEntity targetPlayer) {
		copyPlayer(sourcePlayer);
	}
	private void stopSpectating(ServerPlayerEntity sourcePlayer) {}
	public static void copyPlayer(ServerPlayerEntity sourcePlayer) {
		// Create a new GameProfile
		GameProfile newProfile = new GameProfile(UUID.randomUUID(), sourcePlayer.getGameProfile().getName() + "_copy");

		// Copy texture properties if present
		if (sourcePlayer.getGameProfile().getProperties().containsKey("textures")) {
			Property texture = sourcePlayer.getGameProfile().getProperties().get("textures").iterator().next();
			newProfile.getProperties().put("textures", new Property("textures", texture.value(), texture.signature()));
		}

		// Create the new player entity
		ServerPlayerEntity newPlayer = new ServerPlayerEntity(
				sourcePlayer.getServer(),
				sourcePlayer.getServerWorld(),
				newProfile,
				SyncedClientOptions.createDefault()
		);

		// Copy player state
		newPlayer.refreshPositionAndAngles(
				sourcePlayer.getX(),
				sourcePlayer.getY(),
				sourcePlayer.getZ(),
				sourcePlayer.getYaw(),
				sourcePlayer.getPitch()
		);

		// Copy inventory
		for (int i = 0; i < sourcePlayer.getInventory().size(); i++) {
			ItemStack stack = sourcePlayer.getInventory().getStack(i);
			newPlayer.getInventory().setStack(i, stack.copy());
		}

		// Copy player attributes
		newPlayer.setHealth(sourcePlayer.getHealth());
		newPlayer.setExperienceLevel(sourcePlayer.experienceLevel);
		newPlayer.setExperiencePoints(sourcePlayer.totalExperience);

		// Spawn the player in the world
		sourcePlayer.getServerWorld().spawnEntity(newPlayer);

		// Properly handle player connection and visibility
		ServerPlayNetworkHandler networkHandler = new ServerPlayNetworkHandler(
				sourcePlayer.getServer(),
				((ServerPlayerEntity)sourcePlayer).networkHandler.getConnection(),
				newPlayer
		);
		newPlayer.networkHandler = networkHandler;

		// Add player to the server's player list
		sourcePlayer.getServer().getPlayerManager().createPlayer()
		// Ensure all players are notified about the new player
		sourcePlayer.getServer().getPlayerManager().sendToAll(
				new PlayerListS2CPacket(PlayerListS2CPacket.Action.ADD_PLAYER, newPlayer)
		);

		// Spawn player for all nearby clients
		ServerWorldAccessor worldAccessor = (ServerWorldAccessor)sourcePlayer.getServerWorld();
		worldAccessor.getChunkManager().sendToNearbyPlayers(newPlayer,
				new EntitySpawnS2CPacket(newPlayer));

		// Optional: Log the player creation (if you want server-side logging)
		sourcePlayer.getServer().getLogger().info("Created player copy: " + newPlayer.getGameProfile().getName());
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
