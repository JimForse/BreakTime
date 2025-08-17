package bt.example.breaktime;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import yesman.epicfight.world.entity.ai.attribute.EpicFightAttributes;

@Mod.EventBusSubscriber(modid = BreakTimeMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class Command {
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        // Команда /timer
        dispatcher.register(
                Commands.literal("timer")
                        .executes(context -> {
                            context.getSource().sendSuccess(Component.literal("Usage: /timer start <name> <x> <y> <z> <seconds> <bossType>"), false);
                            return 1;
                        })
                        .then(Commands.literal("start")
                                .then(Commands.argument("name", StringArgumentType.string())
                                        .then(Commands.argument("x", IntegerArgumentType.integer())
                                                .then(Commands.argument("y", IntegerArgumentType.integer())
                                                        .then(Commands.argument("z", IntegerArgumentType.integer())
                                                                .then(Commands.argument("time", IntegerArgumentType.integer(1))
                                                                        .then(Commands.argument("bossType", StringArgumentType.string())
                                                                                .suggests((context, builder) -> {
                                                                                    for (BossType bossType : BossType.values()) { builder.suggest(bossType.getId()); }
                                                                                    return builder.buildFuture();
                                                                                })
                                                                                .executes(context -> {
                                                                                    String name = StringArgumentType.getString(context, "name");
                                                                                    int x = IntegerArgumentType.getInteger(context, "x");
                                                                                    int y = IntegerArgumentType.getInteger(context, "y");
                                                                                    int z = IntegerArgumentType.getInteger(context, "z");
                                                                                    int timeSeconds = IntegerArgumentType.getInteger(context, "time");
                                                                                    String bossTypeName = StringArgumentType.getString(context, "bossType");

                                                                                    BossType bossType = null;
                                                                                    for (BossType type : BossType.values()) {
                                                                                        if (type.getId().equalsIgnoreCase(bossTypeName)) {
                                                                                            bossType = type;
                                                                                            break;
                                                                                        }
                                                                                    }
                                                                                    if (bossType == null) {
                                                                                        context.getSource().sendFailure(Component.literal("Босс " + bossTypeName + " не найден!"));
                                                                                        return 0;
                                                                                    }

                                                                                    ServerLevel level = context.getSource().getLevel();
                                                                                    TimerData timer = new TimerData(name, new BlockPos(x, y, z), timeSeconds * 20L, bossType);
                                                                                    BreakTimeMod.addTimer(timer);
                                                                                    context.getSource().sendSuccess(Component.literal("Таймер " + name + " создан для босса " + bossType.getId() + ". Активируется при появлении игрока!"), false);
                                                                                    return 1;
                                                                                }))))))))
                        .then(Commands.literal("stop")
                                .then(Commands.argument("name", StringArgumentType.string())
                                        .then(Commands.argument("delay", IntegerArgumentType.integer(0))
                                                .executes(context -> {
                                                    String name = StringArgumentType.getString(context, "name");
                                                    int delay = IntegerArgumentType.getInteger(context, "delay");

                                                    TimerData timer = BreakTimeMod.getTimers().get(name);
                                                    if (timer == null) {
                                                        context.getSource().sendFailure(Component.literal("Таймер " + name + " не найден!"));
                                                        return 0;
                                                    }

                                                    if (delay == 0) {
                                                        BreakTimeMod.removeTimer(name);
                                                        context.getSource().sendSuccess(Component.literal("Таймер " + name + " остановлен!"), false);
                                                    } else {
                                                        timer.setEndTime(context.getSource().getLevel().getGameTime() + delay * 20L);
                                                        context.getSource().sendSuccess(Component.literal("Таймер " + name + " будет остановлен через " + delay + " секунд!"), false);
                                                    }
                                                    return 1;
                                                }))))
                        .then(Commands.literal("kill")
                                .then(Commands.argument("name", StringArgumentType.string())
                                        .executes(context -> {
                                            String name = StringArgumentType.getString(context, "name");
                                            if (!BreakTimeMod.getTimers().containsKey(name)) {
                                                context.getSource().sendFailure(Component.literal("Таймер " + name + " не найден!"));
                                                return 0;
                                            }
                                            BreakTimeMod.removeTimer(name);
                                            context.getSource().sendSuccess(Component.literal("Таймер " + name + " полностью удален!"), false);
                                            return 1;
                                        })))
        );

        // Команда /setboss
        dispatcher.register(
                Commands.literal("setboss")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("bossType", StringArgumentType.string())
                                        .suggests((context, builder) -> {
                                            for (BossType bossType : BossType.values()) {
                                                builder.suggest(bossType.getId());
                                            }
                                            return builder.buildFuture();
                                        })
                                        .executes(context -> {
                                            ServerPlayer player = EntityArgument.getPlayer(context, "player");
                                            String bossTypeName = StringArgumentType.getString(context, "bossType");
                                            BossType bossType = null;
                                            for (BossType type : BossType.values()) {
                                                if (type.getId().equalsIgnoreCase(bossTypeName)) {
                                                    bossType = type;
                                                    break;
                                                }
                                            }
                                            if (bossType == null) {
                                                context.getSource().sendFailure(Component.literal("Босс " + bossTypeName + " не найден!"));
                                                return 0;
                                            }

                                            BossManager.applyBossAttributes(player, bossType, 1, true); // playerCount = 1
                                            context.getSource().sendSuccess(Component.literal(player.getName().getString() + " теперь босс " + bossType.getId() + "!"), true);
                                            return 1;
                                        })))
        );

        // Команда /removeboss
        dispatcher.register(
                Commands.literal("removeboss")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> {
                                    ServerPlayer player = EntityArgument.getPlayer(context, "player");
                                    // Сбрасываем атрибуты
                                    player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(20.0);
                                    player.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(1.0);
                                    player.getAttribute(Attributes.ATTACK_SPEED).setBaseValue(4.0);
                                    if (player.getAttribute(EpicFightAttributes.STAMINA_REGEN.get()) != null) {player.getAttribute(EpicFightAttributes.STAMINA_REGEN.get()).setBaseValue(1.0);}
                                    if (player.getAttribute(EpicFightAttributes.ARMOR_NEGATION.get()) != null) {player.getAttribute(EpicFightAttributes.ARMOR_NEGATION.get()).setBaseValue(0.0);}
                                    if (player.getAttribute(EpicFightAttributes.WEIGHT.get()) != null) {player.getAttribute(EpicFightAttributes.WEIGHT.get()).setBaseValue(1.0);}
                                    player.getPersistentData().remove("breaktime:manaRegen");
                                    player.getPersistentData().remove("breaktime:bloodSpellPower");
                                    player.getPersistentData().remove("breaktime:cooldownReduction");
                                    player.getPersistentData().remove("breaktime:enderSpellPower");
                                    player.getPersistentData().remove("breaktime:stamina");
                                    player.setHealth(player.getMaxHealth());
                                    context.getSource().sendSuccess(Component.literal(player.getName().getString() + " больше не босс!"), false);
                                    return 1;
                                }))
        );
        dispatcher.register(
                Commands.literal("removeboss")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> {
                                    ServerPlayer player = EntityArgument.getPlayer(context, "player");
                                    // Сбрасываем атрибуты
                                    player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(20.0);
                                    // ... остальное
                                    context.getSource().sendSuccess(Component.literal(player.getName().getString() + " больше не босс!"), false);
                                    return 1;
                                }))
        );
    }
}