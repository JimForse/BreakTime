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
            BreakTimeMod.LOGGER.info("Инициализация BossReloadListener для data/breaktime/bosses/");
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> map, ResourceManager resourceManager, ProfilerFiller profiler) {
            BreakTimeMod.LOGGER.info("Начало загрузки JSON боссов. Найдено файлов: {}", map.size());
            BOSS_ATTRIBUTES.clear();
            for (Map.Entry<ResourceLocation, JsonElement> entry : map.entrySet()) {
                String bossId = entry.getKey().getPath();
                BreakTimeMod.LOGGER.info("Обработка файла: {} (полный путь: {})", bossId, entry.getKey());
                if (entry.getValue().isJsonObject()) {
                    BOSS_ATTRIBUTES.put(bossId, entry.getValue().getAsJsonObject());
                    BreakTimeMod.LOGGER.info("Загружен босс: {} с атрибутами: {}", bossId, entry.getValue());
                } else {
                    BreakTimeMod.LOGGER.warn("Неверный JSON для босса: {}", bossId);
                }
            }
            if (BOSS_ATTRIBUTES.isEmpty()) {
                BreakTimeMod.LOGGER.warn("Не найдено ни одного JSON для боссов!");
            } else {
                BreakTimeMod.LOGGER.info("Загружено {} боссов", BOSS_ATTRIBUTES.size());
            }
        }
    }

    @SubscribeEvent
    public static void onAddReloadListener(AddReloadListenerEvent event) {
        BreakTimeMod.LOGGER.info("Регистрация BossReloadListener");
        event.addListener(new BossReloadListener());
    }

    public static JsonObject getBossAttributes(String bossId) {
        JsonObject attributes = BOSS_ATTRIBUTES.get(bossId);
        if (attributes == null) {
            BreakTimeMod.LOGGER.warn("Атрибуты для босса {} не найдены!", bossId);
        } else {
            BreakTimeMod.LOGGER.info("Возвращены атрибуты для босса {}: {}", bossId, attributes);
        }
        return attributes;
    }
}