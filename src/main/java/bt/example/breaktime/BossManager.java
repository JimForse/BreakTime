package bt.example.breaktime;

import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.player.ServerPlayerPatch;
import yesman.epicfight.world.entity.ai.attribute.EpicFightAttributes;

import java.util.HashMap;
import java.util.Map;

public class BossManager {
    private static final Map<ServerPlayer, ServerBossEvent> bossBars = new HashMap<>();

    public static void applyBossAttributes(ServerPlayer player, BossType bossType, int playerCount, boolean activate) {
        JsonObject attributes = BossTypeData.getBossAttributes(bossType.getId());
        double health = bossType.getScaledHealth(playerCount);
        double damage = bossType.getScaledDamage(playerCount);
        double protection = bossType.getScaledProtection(playerCount);
        double attackSpeed = bossType.getBaseAttackSpeed();
        double staminaRegen = bossType.getBaseStaminaRegen();
        double stamina = bossType.getStamina();

        if (attributes == null) {
            BreakTimeMod.LOGGER.warn("Attributes null for boss {}! Using defaults.", bossType.getId());
        } else {
            health = attributes.has("baseHealth") ? attributes.get("baseHealth").getAsDouble() * playerCount : health;
            damage = attributes.has("baseDamage") ? attributes.get("baseDamage").getAsDouble() * playerCount : damage;
            protection = attributes.has("baseProtection") ? attributes.get("baseProtection").getAsDouble() * playerCount : protection;
            attackSpeed = attributes.has("baseAttackSpeed") ? attributes.get("baseAttackSpeed").getAsDouble() : attackSpeed;
            staminaRegen = attributes.has("baseStaminaRegen") ? attributes.get("baseStaminaRegen").getAsDouble() : staminaRegen;
            stamina = attributes.has("baseStamina") ? attributes.get("baseStamina").getAsDouble() : stamina;
        }

        player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
        player.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(damage);
        player.getAttribute(Attributes.ATTACK_SPEED).setBaseValue(attackSpeed);
        player.setHealth((float) health);

        double missingHealth = health - player.getHealth();
        int regenDuration = (int) Math.ceil(missingHealth / 10.0) + 20;
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, regenDuration, 5));
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, regenDuration, 5));

        // Epic Fight
        ServerPlayerPatch playerPatch = EpicFightCapabilities.getEntityPatch(player, ServerPlayerPatch.class);
        if (playerPatch != null) {
            if (player.getAttribute(EpicFightAttributes.STAMINA_REGEN.get()) != null) { player.getAttribute(EpicFightAttributes.STAMINA_REGEN.get()).setBaseValue(staminaRegen); }
            if (player.getAttribute(EpicFightAttributes.ARMOR_NEGATION.get()) != null) { player.getAttribute(EpicFightAttributes.ARMOR_NEGATION.get()).setBaseValue(protection); }
            if (player.getAttribute(EpicFightAttributes.MAX_STAMINA.get()) != null) { player.getAttribute(EpicFightAttributes.MAX_STAMINA.get()).setBaseValue(stamina); }
            if (player.getAttribute(EpicFightAttributes.WEIGHT.get()) != null) { player.getAttribute(EpicFightAttributes.WEIGHT.get()).setBaseValue(1000.0); }
            if (player.getAttribute(EpicFightAttributes.STUN_ARMOR.get()) != null) { player.getAttribute(EpicFightAttributes.STUN_ARMOR.get()).setBaseValue(1000.0); }
        }

        // Кастомные атрибуты (Iron's Spellbooks)
        JsonObject customAttributes = bossType.getCustomAttributes();
        if (customAttributes.has("manaRegen")) {player.getPersistentData().putDouble("breaktime:manaRegen", customAttributes.get("manaRegen").getAsDouble());}
        if (customAttributes.has("bloodSpellPower")) {player.getPersistentData().putDouble("breaktime:bloodSpellPower", customAttributes.get("bloodSpellPower").getAsDouble());}
        if (customAttributes.has("cooldownReduction")) {player.getPersistentData().putDouble("breaktime:cooldownReduction", customAttributes.get("cooldownReduction").getAsDouble());}
        if (customAttributes.has("enderSpellPower")) {player.getPersistentData().putDouble("breaktime:enderSpellPower", customAttributes.get("enderSpellPower").getAsDouble());}
        if (customAttributes.has("lightningSpellPower")) {player.getPersistentData().putDouble("breaktime:lightningSpellPower", customAttributes.get("lightningSpellPower").getAsDouble());}
        if (customAttributes.has("baseManaRegen")) {player.getPersistentData().putDouble("breaktime:baseManaRegen", customAttributes.get("baseManaRegen").getAsDouble());}
        if (customAttributes.has("maxMana")) {player.getPersistentData().putDouble("breaktime:maxMana", customAttributes.get("maxMana").getAsDouble());}
        if (customAttributes.has("castTimeReduction")) {player.getPersistentData().putDouble("breaktime:castTimeReduction", customAttributes.get("castTimeReduction").getAsDouble());}
        if (customAttributes.has("staminar")) {player.getAttribute(EpicFightAttributes.MAX_STAMINA.get()).setBaseValue(customAttributes.get("staminar").getAsDouble());}

        player.onUpdateAbilities(); // Синхронизация атрибутов и способностей
        player.sendSystemMessage(Component.literal("Ты стал боссом: " + bossType.getId() + "!"));
        BreakTimeMod.LOGGER.info("Applied attributes to {}: maxHealth={}, currentHealth={}, damage={}, attackSpeed={}",
                player.getName().getString(), player.getMaxHealth(), player.getHealth(),
                player.getAttributeValue(Attributes.ATTACK_DAMAGE), player.getAttributeValue(Attributes.ATTACK_SPEED));

        if (activate) { player.addTag("boss_active"); }
    }

    public static void createBossBar(ServerPlayer bossPlayer, BossType bossType) {
        ServerBossEvent bossBar = new ServerBossEvent(Component.literal("Boss: " + bossType.getId()), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);
        bossBar.setProgress(1.0F); // Полный изначально
        bossBar.setVisible(true);
        bossBars.put(bossPlayer, bossBar);

        // Добавляем участников как зрителей бара
        bossPlayer.getServer().getPlayerList().getPlayers().stream()
                .filter(p -> p.getTags().contains("participant"))
                .forEach(bossBar::addPlayer);
    }

    public static void updateBossBar(ServerPlayer bossPlayer) {
        ServerBossEvent bossBar = bossBars.get(bossPlayer);
        if (bossBar != null) {
            float healthPercent = bossPlayer.getHealth() / bossPlayer.getMaxHealth();
            bossBar.setProgress(healthPercent);
        }
    }

    public static void removeBossBar(ServerPlayer bossPlayer) {
        ServerBossEvent bossBar = bossBars.remove(bossPlayer);
        if (bossBar != null) {
            bossBar.removeAllPlayers();
        }
    }
}