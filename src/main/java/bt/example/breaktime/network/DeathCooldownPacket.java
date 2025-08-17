package bt.example.breaktime.network;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import bt.example.breaktime.BreakTimeMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class DeathCooldownPacket {
    private final int cooldownSeconds;

    // Переменная на клиенте для отслеживания состояния кулдауна
    private static long cooldownEndTime = 0;

    public DeathCooldownPacket(int cooldownSeconds) {
        this.cooldownSeconds = cooldownSeconds;
    }

    public static void encode(DeathCooldownPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.cooldownSeconds);
    }

    public static DeathCooldownPacket decode(FriendlyByteBuf buf) {
        return new DeathCooldownPacket(buf.readInt());
    }

    public static void handle(DeathCooldownPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (ctx.get().getDirection().getReceptionSide() == LogicalSide.CLIENT) {
                BreakTimeMod.LOGGER.info("Received DeathCooldownPacket with {} seconds", msg.cooldownSeconds);
                cooldownEndTime = System.currentTimeMillis() + (long) msg.cooldownSeconds * 1000;
            }
        });
        ctx.get().setPacketHandled(true);
    }

    public static boolean isCooldownActive() {
        return System.currentTimeMillis() < cooldownEndTime;
    }

    public static int getRemainingTime() {
        if (!isCooldownActive()) {
            return 0;
        }
        return (int) ((cooldownEndTime - System.currentTimeMillis()) / 1000) + 1;
    }
}