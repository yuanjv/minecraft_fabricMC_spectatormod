package yuanjv.spectatormod;

import com.mojang.authlib.GameProfile;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

public class BotPlayerEntity extends ServerPlayer {
    private final ServerPlayer originalPlayer;

    public BotPlayerEntity(ServerPlayer spawner) {
        super(
                spawner.getServer(),
                spawner.serverLevel(),
                createBotGameProfile(spawner),
                ClientInformation.createDefault()
        );
        this.originalPlayer = spawner;

        // Setup bot player
        setupBotPlayer(spawner);
    }

    private void setupBotPlayer(ServerPlayer spawner) {
        // Copy player's position and rotation
        this.setPos(spawner.getX(), spawner.getY(), spawner.getZ());
        this.setXRot(spawner.getXRot());
        this.setYRot(spawner.getYRot());
        this.setYHeadRot(spawner.getYHeadRot());

        // Copy game mode
        this.gameMode.changeGameModeForPlayer(spawner.gameMode.getGameModeForPlayer());

        // Copy abilities
        this.getAbilities().flying = spawner.getAbilities().flying;

        // Spawn the bot
        spawnBotPlayer();
    }

    private void spawnBotPlayer() {
        MinecraftServer server = this.getServer();
        ServerLevel world = this.serverLevel();

        // Create a fake client connection
        ClientConnection fakeConnection = new ClientConnection(NetworkSide.SERVERBOUND);

        // Place the new player
        server.getPlayerList().placeNewPlayer(
                fakeConnection,
                this,
                new CommonListenerCookie(
                        this.getGameProfile(),
                        0,
                        this.clientInformation(),
                        false
                )
        );

        // Broadcast player info
        server.getPlayerList().broadcastAll(
                new ClientboundRotateHeadPacket(this, (byte) (this.getYHeadRot() * 256 / 360)),
                this.level().dimension()
        );
    }

    private static GameProfile createBotGameProfile(ServerPlayer spawner) {
        // Create a new game profile with a unique UUID and similar name
        return new GameProfile(
                UUID.randomUUID(),
                spawner.getGameProfile().getName() + "_Bot"
        );
    }

    @Override
    public void tick() {
        // Periodic position reset and chunk management
        if (this.getServer().getTickCount() % 10 == 0) {
            this.connection.resetPosition();
            this.serverLevel().getChunkSource().move(this);
        }

        // Standard player tick
        super.tick();
    }

    @Override
    public void die(DamageSource cause) {
        // Handle bot player death
        super.die(cause);
        this.remove(RemovalReason.KILLED);
    }

    @Override
    public void onEquipItem(EquipmentSlot slot, ItemStack previous, ItemStack stack) {
        // Prevent unnecessary item equip actions
        if (!this.isUsingItem()) {
            super.onEquipItem(slot, previous, stack);
        }
    }

    @Override
    public boolean isSpectator() {
        // Ensure bot is treated as a spectator
        return true;
    }

    // Optional: Custom disconnect handling
    public void disconnectBot(Component reason) {
        this.connection.onDisconnect(new DisconnectionDetails(reason));
    }
}