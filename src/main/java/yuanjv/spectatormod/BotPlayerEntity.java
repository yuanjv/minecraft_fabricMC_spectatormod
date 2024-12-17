package yuanjv.spectatormod;



import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.c2s.common.SyncedClientOptions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import com.mojang.authlib.GameProfile;

import java.util.UUID;

public class BotPlayerEntity extends ServerPlayerEntity {

    public BotPlayerEntity(ServerPlayerEntity spawner) {
        super(spawner.getServer(), spawner.getServerWorld(), createBotGameProfile(spawner), SyncedClientOptions.createDefault());

        // Clone entire player data via NBT
        clonePlayerData(spawner);
    }

    private static GameProfile createBotGameProfile(ServerPlayerEntity spawner) {
        // Create a new game profile with a new UUID, but similar name
        return new GameProfile(UUID.randomUUID(), spawner.getGameProfile().getName() + "_Bot");
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

