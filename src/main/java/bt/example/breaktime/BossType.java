package bt.example.breaktime;

import com.google.gson.JsonObject;

public enum BossType {
    AGONY("agony"),
    DEATH("death"),
    RUINE("ruine"),
    CC("cc"),
    TORMENT("torment"),
    SATSUJIN("satsujin"),
    LAST_KNIGHT("last_knight"),
    MOONLESS("moonless");

    private final String id;
    private final JsonObject attributes;

    BossType(String id) {
        this.id = id;
        this.attributes = BossTypeData.getBossAttributes(id);
    }

    public String getId() {
        return id;
    }

    public double getBaseHealth() {
        JsonObject attributes = BossTypeData.getBossAttributes(id);
        return attributes != null && attributes.has("baseHealth") ? attributes.get("baseHealth").getAsDouble() : 20.0;
    }

    public double getBaseDamage() {
        JsonObject attributes = BossTypeData.getBossAttributes(id);
        return attributes != null && attributes.has("baseDamage") ? attributes.get("baseDamage").getAsDouble() : 1.0;
    }

    public double getBaseStaminaRegen() {
        JsonObject attributes = BossTypeData.getBossAttributes(id);
        return attributes != null && (attributes.has("baseStaminaRegen") || attributes.has("customAttributes") && attributes.get("customAttributes").getAsJsonObject().has("baseStaminaRegen"))
                ? (attributes.has("baseStaminaRegen") ? attributes.get("baseStaminaRegen").getAsDouble() : attributes.get("customAttributes").getAsJsonObject().get("baseStaminaRegen").getAsDouble())
                : 0.0;
    }

    public double getBaseProtection() {
        JsonObject attributes = BossTypeData.getBossAttributes(id);
        return attributes != null && attributes.has("baseProtection") ? attributes.get("baseProtection").getAsDouble() : 0.0;
    }

    public double getBaseAttackSpeed() {
        JsonObject attributes = BossTypeData.getBossAttributes(id);
        return attributes != null && attributes.has("baseAttackSpeed") ? attributes.get("baseAttackSpeed").getAsDouble() : 4.0;
    }

    public double getStamina() {
        JsonObject attributes = BossTypeData.getBossAttributes(id);
        return attributes != null && (attributes.has("stamina") || (attributes.has("customAttributes") && attributes.get("customAttributes").getAsJsonObject().has("staminar")))
                ? (attributes.has("stamina") ? attributes.get("stamina").getAsDouble() : attributes.get("customAttributes").getAsJsonObject().get("staminar").getAsDouble())
                : 0.0;
    }

    public double getArmorNegation() {
        JsonObject attributes = BossTypeData.getBossAttributes(id);
        return attributes != null && attributes.has("armorNegation") ? attributes.get("armorNegation").getAsDouble() : 0.0;
    }

    public JsonObject getCustomAttributes() {
        JsonObject attributes = BossTypeData.getBossAttributes(id);
        return attributes != null && attributes.has("customAttributes") ? attributes.get("customAttributes").getAsJsonObject() : new JsonObject();
    }

    public JsonObject getCustomBossbarAttributes() {
        return attributes != null && attributes.has("customBossbarAttributes") ? attributes.get("customBossbarAttributes").getAsJsonObject() : new JsonObject();
    }

    public double getScaledHealth(int playerCount) {
        return getBaseHealth() * playerCount;
    }

    public double getScaledDamage(int playerCount) {
        return getBaseDamage() * playerCount;
    }

    public double getScaledProtection(int playerCount) {
        return getBaseProtection() * playerCount;
    }
}