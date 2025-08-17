package bt.example.breaktime;

import bt.example.breaktime.network.DeathCooldownPacket;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class ClientSetup {
    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.Render.Post event) {
        if (event.getScreen() instanceof DeathScreen deathScreen) {
            if (DeathCooldownPacket.isCooldownActive()) {
                for (GuiEventListener widget: deathScreen.children()) {
                    if (widget instanceof Button button) {
                        if (button.getMessage().getString().contains("Respawn")) {
                            button.active = false;
                            break;
                        }
                    }
                }

                PoseStack stack = event.getPoseStack();
                Minecraft mc = Minecraft.getInstance();
                int remainingTime = DeathCooldownPacket.getRemainingTime();
                String timerText = "Возрождение через: " + remainingTime;

                int screenWidth = mc.getWindow().getGuiScaledWidth();
                int screenHeight = mc.getWindow().getGuiScaledHeight();
                int textWidth = mc.font.width(timerText);

                mc.font.drawShadow(stack, Component.literal(timerText), (float)(screenWidth / 2) - (float)(textWidth / 2), (float)(screenHeight / 2) + 50, 0xFFFFFF);
            }
        }
    }

    @SubscribeEvent
    public static void onRenderNameTag(RenderNameTagEvent event) {
        if (event.getEntity() instanceof net.minecraft.world.entity.player.Player) {
            event.setContent(Component.empty()); // Очищаем содержимое имени
            event.setResult(net.minecraftforge.eventbus.api.Event.Result.DENY); // Отменяем рендеринг имени
        }
    }
}