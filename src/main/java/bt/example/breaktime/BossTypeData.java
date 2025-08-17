package bt.example.breaktime;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;

@Mod.EventBusSubscriber(modid = BreakTimeMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class BossTypeData {
    private static final Gson GSON = new Gson();
    private static final Map<String, JsonObject> BOSS_ATTRIBUTES = new HashMap<>();

    public static class BossReloadListener extends SimpleJsonResourceReloadListener {
        public BossReloadListener() {
            super(GSON, "bosses");
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> map, ResourceManager resourceManager, ProfilerFiller profiler) {
            BOSS_ATTRIBUTES.clear();
            for (Map.Entry<ResourceLocation, JsonElement> entry : map.entrySet()) {
                String bossId = entry.getKey().getPath();
                if (entry.getValue().isJsonObject()) {
                    BOSS_ATTRIBUTES.put(bossId, entry.getValue().getAsJsonObject());
                    System.out.println("Loaded boss: " + bossId + " with attributes: " + entry.getValue());
                } else {
                    System.err.println("Invalid JSON for boss: " + bossId);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onAddReloadListener(AddReloadListenerEvent event) {
        event.addListener(new BossReloadListener());
    }

    public static JsonObject getBossAttributes(String bossId) {
        return BOSS_ATTRIBUTES.get(bossId);
    }
}