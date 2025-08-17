package bt.example.breaktime;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static bt.example.breaktime.BreakTimeMod.ITEMS;

@Mod.EventBusSubscriber(modid = BreakTimeMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class Config {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    private static final ForgeConfigSpec.BooleanValue LOG_DIRT_BLOCK = BUILDER
            .comment("Whether to log the dirt block on common setup")
            .define("logDirtBlock", true);

    private static final ForgeConfigSpec.IntValue MAGIC_NUMBER = BUILDER
            .comment("A magic number")
            .defineInRange("magicNumber", 42, 0, Integer.MAX_VALUE);

    public static final ForgeConfigSpec.ConfigValue<String> MAGIC_NUMBER_INTRODUCTION = BUILDER
            .comment("What you want the introduction message to be for the magic number")
            .define("magicNumberIntroduction", "The magic number is... ");

    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> ITEM_STRINGS = BUILDER
            .comment("A list of items to log on common setup.")
            .defineListAllowEmpty(Collections.singletonList("items"), () -> List.of("minecraft:iron_ingot"), Config::validateItemName);

    static final ForgeConfigSpec SPEC = BUILDER.build();

    public static boolean logDirtBlock;
    public static int magicNumber;
    public static String magicNumberIntroduction;
    public static Set<Item> items;


    private static ResourceLocation createResourceLocation(String name) {
        if (name.contains(":")) {
            return new ResourceLocation(name);
        } else {
            return ResourceLocation.fromNamespaceAndPath("minecraft", name);
        }
    }

    private static boolean validateItemName(final Object obj) {
        if (obj instanceof final String itemName) {
            ResourceLocation location = createResourceLocation(itemName);
            return ForgeRegistries.ITEMS.containsKey(location);
        }
        return false;
    }

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        items = ITEM_STRINGS.get().stream()
                .map(Config::createResourceLocation)
                .map(ForgeRegistries.ITEMS::getValue)
                .collect(Collectors.toSet());

        // Например, если ты хочешь что-то делать с предметами, делай это здесь:
        System.out.println("Loaded " + items.size() + " items from config: " + items);
    }
}
