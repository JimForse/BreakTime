package bt.example.breaktime;

import bt.example.breaktime.network.*;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Material;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;

import java.util.*;

@Mod(BreakTimeMod.MODID)
public class BreakTimeMod {
    public static final String MODID = "breaktime";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);

    public static final RegistryObject<Block> EXAMPLE_BLOCK = BLOCKS.register("example_block", () -> new Block(BlockBehaviour.Properties.of(Material.STONE)));
    public static final RegistryObject<Item> EXAMPLE_BLOCK_ITEM = ITEMS.register("example_block", () -> new BlockItem(EXAMPLE_BLOCK.get(), new Item.Properties().tab(CreativeModeTab.TAB_BUILDING_BLOCKS)));

    private static final Map<String, TimerData> timers = new HashMap<>();
    private static final Map<String, BlockPos> deathLocations = new HashMap<>(); // Используем String для ника
    private static final Map<ServerPlayer, Long> damageTicks = new HashMap<>(); // Для урона нарушителям

    public BreakTimeMod(FMLJavaModLoadingContext context) {
        PacketHandler.register();
        IEventBus modEventBus = context.getModEventBus();
        modEventBus.addListener(this::commonSetup);

        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        MinecraftForge.EVENT_BUS.register(this);

        context.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::clientSetup);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("HELLO FROM COMMON SETUP");
        if (Config.logDirtBlock)
            LOGGER.info("DIRT BLOCK >> {}", ForgeRegistries.BLOCKS.getKey(Blocks.DIRT));
        LOGGER.info(Config.magicNumberIntroduction + Config.magicNumber);
        Config.items.forEach((item) -> LOGGER.info("ITEM >> {}", item.toString()));
    }

    private void onBossDefeated(ServerLevel level, TimerData timer) {
        Entity boss = null;
        for (Entity entity : level.getEntities().getAll()) {
            if (entity.getTags().contains(timer.getBossType().getId()) && entity.getTags().contains("boss_active")) {
                boss = entity;
                break;
            }
        }
        level.getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(false, level.getServer());
        if (boss instanceof ServerPlayer bossPlayer) {
            bossPlayer.removeTag("boss_active");
            BossManager.removeBossBar(bossPlayer);

            // Найти убийцу
            ServerPlayer killer = null;
            DamageSource lastDamage = bossPlayer.getLastDamageSource();
            if (lastDamage != null && lastDamage.getEntity() instanceof ServerPlayer damageSourcePlayer) {
                killer = damageSourcePlayer;
            }

            // Дроп первого предмета убийце
            if (killer != null) {
                ItemStack drop = bossPlayer.getInventory().getItem(0).copy();
                if (!drop.isEmpty()) {
                    killer.addItem(drop);
                    killer.sendSystemMessage(Component.literal("Получен предмет от босса: " + drop.getHoverName().getString()));
                    BreakTimeMod.LOGGER.info("Dropped {} to killer {}", drop.getHoverName().getString(), killer.getName().getString());
                }
            }
        }
        for (ServerPlayer player : level.getPlayers(p -> p.getTags().contains("participant"))) {
            player.sendSystemMessage(Component.literal("§aПобеда! Босс был повержен."));
            player.getTags().remove("participant");
        }
    }

    private void onParticipantsDefeated(ServerLevel level, TimerData timer, Entity boss) {
        level.getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(false, level.getServer());
        if (boss instanceof ServerPlayer playerBoss) {
            playerBoss.setGameMode(GameType.SPECTATOR);
            playerBoss.sendSystemMessage(Component.literal("§cПроигрыш! Участники повержены."));
        }

        for (ServerPlayer player : level.getPlayers(p -> p.getTags().contains("participant"))) {
            player.sendSystemMessage(Component.literal("§cПроигрыш! Босс оказался слишком силён."));
            player.getTags().remove("participant");
        }

        // Устанавливаем новый таймер на 24 часа
        TimerData newTimer = new TimerData(timer.getName(), timer.getPos(), level.getGameTime() + 24 * 60 * 60 * 20, timer.getBossType());
        newTimer.setCustomMessage("Проигрыш! Босс заспавниться через: ");
        timers.put(newTimer.getName(), newTimer);
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("HELLO from server starting");
    }

    private void clientSetup(final FMLClientSetupEvent event) {
        MinecraftForge.EVENT_BUS.register(new ClientSetup());
    }

    // Обработка смерти босса
    @SubscribeEvent
    public void onBossDeath(LivingDeathEvent event) {
        Entity entity = event.getEntity();
        DamageSource source = event.getSource();

        for (TimerData timer : timers.values()) {
            if (entity.getTags().contains(timer.getBossType().getId())) {
                // Если его убил игрок
                if (source.getEntity() instanceof ServerPlayer player) {
                    ItemStack bossItem = player.getInventory().getItem(0); // Получаем предмет из первого слота инвентаря
                    if (!bossItem.isEmpty()) {
                        player.getInventory().add(bossItem);
                        player.sendSystemMessage(Component.literal("§6Вы победили босса и получили его оружие!"));
                    }
                }
                break;
            }
        }
    }

    // Обработчик тиков сервера
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) return;
        ServerLevel timerLevel = null;

        ServerLevel level = event.getServer().overworld();
        long currentTime = level.getGameTime();

        Iterator<Map.Entry<String, TimerData>> iterator = timers.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, TimerData> entry = iterator.next();
            TimerData timer = entry.getValue();

            if (timer.getEntity() == null || timer.getEntity().isRemoved()) {
                timer.spawnHologram(level);
            }

            timer.updateHologram(level); // Обновляем hologram каждый тик

            if (!timer.isEventActive()) {
                if (!timer.isTimerStarted()) {
                    List<ServerPlayer> players = level.getEntitiesOfClass(ServerPlayer.class, new AABB(
                            timer.getX() - 15, timer.getY() - 15, timer.getZ() - 15,
                            timer.getX() + 15, timer.getY() + 15, timer.getZ() + 15
                    ), player -> !player.getTags().contains("admin"));

                    if (!players.isEmpty()) {
                        timer.setEndTime(currentTime + timer.getInitialDuration());
                        timer.setTimerStarted(true);
                        timer.updateHologram(level);
                    }
                    continue;
                }

                long ticksLeft = timer.getEndTime() - currentTime;
                if (ticksLeft <= 0) {
                    List<ServerPlayer> participants = level.getEntitiesOfClass(ServerPlayer.class, new AABB(
                            timer.getX() - 10, timer.getY() - 10, timer.getZ() - 10,
                            timer.getX() + 10, timer.getY() + 10, timer.getZ() + 10
                    ), player -> !player.getTags().contains("admin") && !player.getTags().contains(timer.getBossType().getId()));
                    if (participants.size() < 3) {
                        timer.setEndTime(currentTime + 72000);
                        level.getPlayers(p -> true).forEach(p ->
                                p.sendSystemMessage(Component.literal("Недостаточно игроков для спавна босса " + timer.getBossType().getId() + "! Таймер перезапущен на 1 час.")));
                        continue;
                    }

                    ServerPlayer bossPlayer = level.getEntitiesOfClass(ServerPlayer.class, new AABB(
                            timer.getX() - 15, timer.getY() - 15, timer.getZ() - 15,
                            timer.getX() + 15, timer.getY() + 15, timer.getZ() + 15
                    ), player -> player.getTags().contains(timer.getBossType().getId())).stream().findFirst().orElse(null);
                    if (bossPlayer != null) {
                        int playerCount = participants.size();
                        BossManager.applyBossAttributes(bossPlayer, timer.getBossType(), playerCount, true);
                        BossManager.createBossBar(bossPlayer, timer.getBossType());
                        timer.setEventActive(true);
                        level.getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(true, level.getServer());
                        BreakTimeMod.LOGGER.info("KeepInventory enabled for boss fight.");
                        BossManager.createBossBar(bossPlayer, timer.getBossType());
                        bossPlayer.sendSystemMessage(Component.literal("Ты стал боссом " + timer.getBossType().getId() + "!"));
                        for (ServerPlayer participant : participants) {
                            participant.addTag("participant");
                            participant.sendSystemMessage(Component.literal("Ты стал участником битвы с " + timer.getBossType().getId() + "!"));
                        }
                    } else {
                        level.getPlayers(p -> true).forEach(p ->
                                p.sendSystemMessage(Component.literal("Игрок с тэгом " + timer.getBossType().getId() + " не найден! Таймер перезапущен на 1 час.")));
                        timer.setEndTime(currentTime + 72000);
                        continue;
                    }
                    removeTimer(timer.getName());
                    continue;
                }

                timer.spawnParticleRing(level, ticksLeft);
            } else {
                List<ServerPlayer> participants = timerLevel.getPlayers(p -> p.getTags().contains("participant"));
                Entity boss = null;
                for (Entity entity : timerLevel.getEntities().getAll()) { // Поиск boss
                    if (entity.getTags().contains(timer.getBossType().getId())) {
                        boss = entity;
                        break; // Нашли, выходим из цикла
                    }
                }

                // Теперь, вне цикла, обновляем бар если boss найден и это игрок
                if (boss != null && boss instanceof ServerPlayer bossPlayer)
                    BossManager.updateBossBar(bossPlayer);

                if (boss == null) {
                    onBossDefeated(timerLevel, timer);
                    iterator.remove();
                    continue;
                }

                boolean allParticipantsDefeated = participants.stream()
                        .allMatch(p -> p.isDeadOrDying() || deathLocations.containsKey(p.getGameProfile().getName()));

                if (allParticipantsDefeated) {
                    onParticipantsDefeated(timerLevel, timer, boss);
                    iterator.remove();
                }
            }
        }

        // Наносим урон нарушителям
        for (ServerPlayer player : level.getPlayers(p -> true)) {
            if (player.getTags().contains("admin") || player.getTags().contains("participant")) continue;
            ServerPlayer bossPlayer = level.getPlayers(p -> p.getTags().contains("boss_active") &&
                    p.getTags().stream().anyMatch(tag -> {
                        for (BossType bossType : BossType.values()) {
                            if (tag.equals(bossType.getId())) return true;
                        }
                        return false;
                    })
            ).stream().findFirst().orElse(null);
            if (bossPlayer != null) {
                BreakTimeMod.LOGGER.info("Active boss " + bossPlayer.getName().getString() + " detected for damage logic.");
                double distance = player.distanceTo(bossPlayer);
                if (distance <= 50.0) {
                    Long lastDamageTick = damageTicks.getOrDefault(player, 0L);
                    if (currentTime - lastDamageTick >= 30) {
                        player.hurt(DamageSource.GENERIC, 10.0F);
                        player.sendSystemMessage(Component.literal("Ты не участник битвы! Уходи или умрёшь!"));
                        damageTicks.put(player, currentTime);
                    }
                }
            } else {
                BreakTimeMod.LOGGER.debug("No active boss found for damage logic.");
            }
        }
    }

    public static void addTimer(TimerData timer) {
        timers.put(timer.getName(), timer);
    }

    public static void removeTimer(String name) {
        TimerData timer = timers.remove(name);
        if (timer != null) {
            if (timer.getEntity() != null)
                timer.getEntity().discard();
            timer.setParticlesActive(false);
        }
    }

    public static Map<String, TimerData> getTimers() {
        return timers;
    }

    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            LOGGER.info("HELLO FROM CLIENT SETUP");
            LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
        }

        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase == TickEvent.Phase.END) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.screen instanceof DeathScreen && DeathCooldownPacket.isCooldownActive()) {
                }
            }
        }
    }


    @SubscribeEvent
    public void onPlayerDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.getTags().contains("participant")) {
            deathLocations.put(player.getGameProfile().getName(), player.blockPosition());
            PacketHandler.sendToPlayer(new DeathCooldownPacket(7), player);

            String victimName = player.getGameProfile().getName();
            DamageSource source = event.getSource();
            String killerName = "???";

            if (source.getEntity() instanceof ServerPlayer killer && !killer.getTags().contains("admin")) {
                killerName = "???";
            } else if (source.getEntity() != null) {
                killerName = source.getEntity().getDisplayName().getString();
            }

            Component deathMessage = Component.literal(victimName + " был убит " + killerName);
            player.getServer().getPlayerList().getPlayers().forEach(p ->
                    p.sendSystemMessage(deathMessage)
            );
            BreakTimeMod.LOGGER.info("Player {} died as participant. Cooldown packet sent.", victimName);
        }
    }
}