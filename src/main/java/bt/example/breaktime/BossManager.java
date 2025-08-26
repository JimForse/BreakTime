package bt.example.breaktime;

import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.player.ServerPlayerPatch;
import yesman.epicfight.world.entity.ai.attribute.EpicFightAttributes;

import java.util.*;

public class BossManager {
    private static final Map<ServerPlayer, ServerBossEvent> bossBars = new HashMap<>();

    public static void applyBossAttributes(ServerPlayer player, BossType bossType, int playerCount, boolean activate) {
        // Загружаем атрибуты из JSON
        JsonObject attributes = BossTypeData.getBossAttributes(bossType.getId());
        double health = bossType.getScaledHealth(playerCount);
        double damage = bossType.getScaledDamage(playerCount);
        double protection = bossType.getScaledProtection(playerCount);
        double attackSpeed = bossType.getBaseAttackSpeed() * playerCount;
        double stamina = bossType.getStamina();
        double staminaRegen = bossType.getBaseStaminaRegen();

        if (attributes == null) {
            BreakTimeMod.LOGGER.warn("Не нашёл атрибуты для босса {}! Использую дефолтные.", bossType.getId());
        } else {
            health = attributes.has("baseHealth") ? attributes.get("baseHealth").getAsDouble() * playerCount : health;
            damage = attributes.has("baseDamage") ? attributes.get("baseDamage").getAsDouble() * playerCount : damage;
            protection = attributes.has("baseProtection") ? attributes.get("baseProtection").getAsDouble() * playerCount : protection;
            attackSpeed = attributes.has("baseAttackSpeed") ? attributes.get("baseAttackSpeed").getAsDouble() : attackSpeed;
            staminaRegen = attributes.has("baseStaminaRegen") ? attributes.get("baseStaminaRegen").getAsDouble() : staminaRegen;
            stamina = attributes.has("baseStamina") ? attributes.get("baseStamina").getAsDouble() : stamina;
        }
        staminaRegen = Math.max(0.1, staminaRegen); // Минимум, чтобы не крашилось
        if (staminaRegen == 0.1 && bossType.getBaseStaminaRegen() == 0.0) {
            BreakTimeMod.LOGGER.warn("Выносливость (staminaRegen) была 0 для босса {}, установил 0.1.", bossType.getId());
        }
        BreakTimeMod.LOGGER.info("Рассчитанное здоровье: {}", health);

        // Устанавливаем ванильные атрибуты
        player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
        player.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(damage);

        AttributeInstance attackSpeedAttribute = player.getAttribute(Attributes.ATTACK_SPEED);
        UUID attackSpeedModifierUUID = UUID.fromString("5e0c8d6a-8b9a-4a7b-9c2d-2f6b8d4e3c5a");
        attackSpeedAttribute.removeModifier(attackSpeedModifierUUID);
        attackSpeedAttribute.addTransientModifier(new AttributeModifier(attackSpeedModifierUUID, "boss_attack_speed", attackSpeed - attackSpeedAttribute.getBaseValue(), AttributeModifier.Operation.ADDITION));

        // Epic Fight атрибуты
        ServerPlayerPatch playerPatch = EpicFightCapabilities.getEntityPatch(player, ServerPlayerPatch.class);
        if (playerPatch == null) {
            BreakTimeMod.LOGGER.warn("EpicFight не нашёл данные для игрока {}!", player.getName().getString());
        } else {
            if (player.getAttribute(EpicFightAttributes.STAMINA_REGEN.get()) != null)
                player.getAttribute(EpicFightAttributes.STAMINA_REGEN.get()).setBaseValue(staminaRegen);
            if (player.getAttribute(EpicFightAttributes.ARMOR_NEGATION.get()) != null)
                player.getAttribute(EpicFightAttributes.ARMOR_NEGATION.get()).setBaseValue(protection);
            if (player.getAttribute(EpicFightAttributes.MAX_STAMINA.get()) != null)
                player.getAttribute(EpicFightAttributes.MAX_STAMINA.get()).setBaseValue(stamina);
            if (player.getAttribute(EpicFightAttributes.WEIGHT.get()) != null)
                player.getAttribute(EpicFightAttributes.WEIGHT.get()).setBaseValue(1000.0);
            if (player.getAttribute(EpicFightAttributes.STUN_ARMOR.get()) != null)
                player.getAttribute(EpicFightAttributes.STUN_ARMOR.get()).setBaseValue(1000.0);
        }

        // Iron's Spellbooks кастомные атрибуты (в NBT)
        JsonObject customAttributes = bossType.getCustomAttributes();
        if (customAttributes.has("manaRegen")) player.getPersistentData().putDouble("breaktime:manaRegen", customAttributes.get("manaRegen").getAsDouble());
        if (customAttributes.has("bloodSpellPower")) player.getPersistentData().putDouble("breaktime:bloodSpellPower", customAttributes.get("bloodSpellPower").getAsDouble());
        if (customAttributes.has("cooldownReduction")) player.getPersistentData().putDouble("breaktime:cooldownReduction", customAttributes.get("cooldownReduction").getAsDouble());
        if (customAttributes.has("enderSpellPower")) player.getPersistentData().putDouble("breaktime:enderSpellPower", customAttributes.get("enderSpellPower").getAsDouble());
        if (customAttributes.has("lightningSpellPower")) player.getPersistentData().putDouble("breaktime:lightningSpellPower", customAttributes.get("lightningSpellPower").getAsDouble());
        if (customAttributes.has("baseManaRegen")) player.getPersistentData().putDouble("breaktime:baseManaRegen", customAttributes.get("baseManaRegen").getAsDouble());
        if (customAttributes.has("maxMana")) player.getPersistentData().putDouble("breaktime:maxMana", customAttributes.get("maxMana").getAsDouble());
        if (customAttributes.has("castTimeReduction")) player.getPersistentData().putDouble("breaktime:castTimeReduction", customAttributes.get("castTimeReduction").getAsDouble());
        if (customAttributes.has("staminar")) player.getAttribute(EpicFightAttributes.MAX_STAMINA.get()).setBaseValue(customAttributes.get("staminar").getAsDouble());

        // Лечим до максимума
        double missingHealth = health - player.getHealth();
        if (missingHealth > 0) {
            player.heal((float) missingHealth); // Мгновенно лечит до max_health
        }

        // Триггер для внутреннего обновления
        player.hurt(DamageSource.GENERIC.bypassArmor(), 0.0F);

        // Собираем все изменённые атрибуты для синхронизации
        List<AttributeInstance> modifiedAttributes = new ArrayList<>();
        modifiedAttributes.add(player.getAttribute(Attributes.MAX_HEALTH));
        modifiedAttributes.add(player.getAttribute(Attributes.ATTACK_DAMAGE));
        modifiedAttributes.add(player.getAttribute(Attributes.ATTACK_SPEED));

        if (playerPatch != null) {
            if (player.getAttribute(EpicFightAttributes.STAMINA_REGEN.get()) != null) modifiedAttributes.add(player.getAttribute(EpicFightAttributes.STAMINA_REGEN.get()));
            if (player.getAttribute(EpicFightAttributes.ARMOR_NEGATION.get()) != null) modifiedAttributes.add(player.getAttribute(EpicFightAttributes.ARMOR_NEGATION.get()));
            if (player.getAttribute(EpicFightAttributes.MAX_STAMINA.get()) != null) modifiedAttributes.add(player.getAttribute(EpicFightAttributes.MAX_STAMINA.get()));
            if (player.getAttribute(EpicFightAttributes.WEIGHT.get()) != null) modifiedAttributes.add(player.getAttribute(EpicFightAttributes.WEIGHT.get()));
            if (player.getAttribute(EpicFightAttributes.STUN_ARMOR.get()) != null) modifiedAttributes.add(player.getAttribute(EpicFightAttributes.STUN_ARMOR.get()));
        }

        // Отправляем атрибуты на клиент

        player.connection.send(new ClientboundUpdateAttributesPacket(
                player.getId(),
                modifiedAttributes
        ));
        // Отправляем текущее здоровье
        player.connection.send(new ClientboundSetHealthPacket(
                player.getHealth(),
                player.getFoodData().getFoodLevel(),
                player.getFoodData().getSaturationLevel()
        ));

        // Синхронизация способностей
        player.onUpdateAbilities();

        // Логи для дебага
        BreakTimeMod.LOGGER.info("Применены атрибуты для {}: maxHealth={}, currentHealth={}, damage={}, attackSpeed={}",
                player.getName().getString(), player.getMaxHealth(), player.getHealth(),
                player.getAttributeValue(Attributes.ATTACK_DAMAGE), player.getAttributeValue(Attributes.ATTACK_SPEED));
        BreakTimeMod.LOGGER.info("После синхронизации: maxHealth={}, currentHealth={}",
                player.getMaxHealth(), player.getHealth());

        // Добавляем тег босса
        if (activate) { player.addTag("boss_active"); }
    }

    public static void updateBossBar(ServerPlayer bossPlayer) {
        ServerBossEvent bossBar = bossBars.get(bossPlayer);
        if (bossBar != null) {
            float healthPercent = Math.max(0.0F, Math.min(1.0F, bossPlayer.getHealth() / bossPlayer.getMaxHealth()));
            bossBar.setProgress(healthPercent);
            bossBar.setName(Component.literal("Boss: " + (int)bossPlayer.getHealth() + "/" + (int)bossPlayer.getMaxHealth()));
            List<ServerPlayer> participants = bossPlayer.getServer().getPlayerList().getPlayers().stream()
                    .filter(p -> p.getTags().contains("participant") && !p.getTags().contains("admin") && !bossBar.getPlayers().contains(p))
                    .toList();
            participants.forEach(bossBar::addPlayer);
            if (!participants.isEmpty()) {
                BreakTimeMod.LOGGER.debug("Re-added {} participants to bossbar: {}",
                        participants.size(), participants.stream().map(p -> p.getName().getString()).toList());
            }
        }
    }

    public static void createBossBar(ServerPlayer bossPlayer, BossType bossType) {
        JsonObject bossbarAttributes = bossType.getCustomBossbarAttributes();
        String barName = bossbarAttributes.has("name") ? bossbarAttributes.get("name").getAsString() : "Boss: " + bossType.getId();
        BossEvent.BossBarColor barColor = BossEvent.BossBarColor.RED; // Дефолт
        if (bossbarAttributes.has("color")) {
            try {
                barColor = BossEvent.BossBarColor.valueOf(bossbarAttributes.get("color").getAsString().toUpperCase());
            } catch (IllegalArgumentException e) {
                BreakTimeMod.LOGGER.warn("Неверный цвет боссбара для {}: {}", bossType.getId(), bossbarAttributes.get("color").getAsString());
            }
        }
        BossEvent.BossBarOverlay barOverlay = BossEvent.BossBarOverlay.PROGRESS;
        if (bossbarAttributes.has("overlay")) {
            try {
                barOverlay = BossEvent.BossBarOverlay.valueOf(bossbarAttributes.get("overlay").getAsString().toUpperCase());
            } catch (IllegalArgumentException e) {
                BreakTimeMod.LOGGER.warn("Неверный overlay боссбара для {}: {}", bossType.getId(), bossbarAttributes.get("overlay").getAsString());
            }
        }

        ServerBossEvent bossBar = new ServerBossEvent(
                Component.literal(barName),
                barColor,
                barOverlay
        );
        bossBar.setProgress(1.0F);
        bossBar.setVisible(true);
        bossBar.setDarkenScreen(true);
        bossBar.setPlayBossMusic(true);
        bossBars.put(bossPlayer, bossBar);

        List<ServerPlayer> participants = bossPlayer.getServer().getPlayerList().getPlayers().stream()
                .filter(p -> p.getTags().contains("participant") && !p.getTags().contains("admin"))
                .toList();
        participants.forEach(bossBar::addPlayer);
        BreakTimeMod.LOGGER.info("Created bossbar for {}, added {} participants: {}", bossType.getId(), participants.size(), participants.stream().map(p -> p.getName().getString()).toList());
    }

    public static void removeBossBar(ServerPlayer bossPlayer) {
        ServerBossEvent bossBar = bossBars.remove(bossPlayer);
        if (bossBar != null) {
            bossBar.removeAllPlayers();
        }
    }
}