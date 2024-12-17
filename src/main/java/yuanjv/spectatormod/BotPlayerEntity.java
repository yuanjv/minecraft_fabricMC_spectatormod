package yuanjv.spectatormod;



import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.packet.c2s.common.SyncedClientOptions;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import com.mojang.authlib.GameProfile;

import java.util.UUID;

public class BotPlayerEntity extends ServerPlayerEntity {

    public BotPlayerEntity(ServerPlayerEntity spawner) {
        super(spawner.getServer(), spawner.getServerWorld(), createBotGameProfile(spawner), SyncedClientOptions.createDefault());

        // Clone entire player data via NBT
        clonePlayerData(spawner);

        this.networkHandler=new DummyNetworkHandler(this);
    }

    private static GameProfile createBotGameProfile(ServerPlayerEntity spawner) {
        // Create a new game profile with a new UUID, but similar name
        return new GameProfile(UUID.randomUUID(), spawner.getGameProfile().getName() + "_Bot");
    }
    // Inner class to create a dummy network handler
    static class DummyNetworkHandler extends ServerPlayNetworkHandler {
        public DummyNetworkHandler(ServerPlayerEntity player) {
            // Pass a fake client connection and the player to the parent constructor
            super(player.getServer(),new ClientConnection(NetworkSide.SERVERBOUND), player, ConnectedClientData.createDefault(player.getGameProfile(), false));
        }
    }


        private void clonePlayerData(ServerPlayerEntity spawner) {
        // Write spawner's data to an NBT compound
        NbtCompound spawnerNbt = new NbtCompound();
        spawner.writeNbt(spawnerNbt);

        // Remove UUID to ensure a new unique identifier
        spawnerNbt.remove("UUID");

        // Read the NBT data into the bot, effectively cloning most attributes
        readNbt(spawnerNbt);
    }

    @Override
    public boolean isSpectator() {
        // Prevent bot from being counted in player list
        return true;
    }

    @Override
    public void tick() {
        super.tick(); // Run the default tick behavior

        // Check if the bot is dead and remove it
        if (this.isDead()) {
            this.server.execute(() -> {
                System.out.println(this.getGameProfile().getName() + " has been removed after death.");
                this.remove(RemovalReason.KILLED);  // Safely remove the bot entity
            });
        }
    }
}

