package bt.example.breaktime.network;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import bt.example.breaktime.BreakTimeMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class DeathCooldownPacket {
    private final int cooldownTicks;

    private static long cooldownEndTime = 0;

    public DeathCooldownPacket(int cooldownSeconds) {
        this.cooldownTicks = cooldownSeconds * 20;
    }

    public static void encode(DeathCooldownPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.cooldownTicks);
    }

    public static DeathCooldownPacket decode(FriendlyByteBuf buf) {
        return new DeathCooldownPacket(buf.readInt() / 20);
    }

    public static void handle(DeathCooldownPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (ctx.get().getDirection().getReceptionSide() == LogicalSide.CLIENT) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.level != null) {
                    BreakTimeMod.LOGGER.info("Client received DeathCooldownPacket: {} ticks ({} seconds)",
                            msg.cooldownTicks, msg.cooldownTicks / 20);
                    cooldownEndTime = mc.level.getGameTime() + msg.cooldownTicks;
                } else {
                    BreakTimeMod.LOGGER.warn("Client received DeathCooldownPacket, but level is null!");
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }

    public static boolean isCooldownActive() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && cooldownEndTime > 0) {
            return mc.level.getGameTime() < cooldownEndTime;
        }
        return false;
    }

    public static int getRemainingTime() {
        if (!isCooldownActive()) {
            return 0;
        }
        Minecraft mc = Minecraft.getInstance();
        return Math.max(0, (int) ((cooldownEndTime - mc.level.getGameTime()) / 20));
    }
}