package bt.example.breaktime.network;

import bt.example.breaktime.BreakTimeMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public class PacketHandler {
    private static SimpleChannel INSTANCE;
    private static int packetId = 0;

    public static void register() {
        INSTANCE = NetworkRegistry.newSimpleChannel(
                ResourceLocation.fromNamespaceAndPath(BreakTimeMod.MODID, "main"),
                () -> "1.0",
                s -> true,
                s -> true
        );
        INSTANCE.registerMessage(packetId++,
                DeathCooldownPacket.class,
                DeathCooldownPacket::encode,
                DeathCooldownPacket::decode,
                DeathCooldownPacket::handle,
                java.util.Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );
    }

    public static <MSG> void sendToPlayer(MSG message, ServerPlayer player) {
        INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), message);
    }
}