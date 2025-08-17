package bt.example.breaktime;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.List;

public class TimerData {
    private final String name;
    private long initialDuration;
    private final BlockPos pos;
    private boolean hologramSpawned = false;
    private long endTime;
    private String message;
    private boolean isTimerStarted = false;
    private ArmorStand entity;
    private final BossType bossType;
    private boolean isEventActive = false;
    private boolean particlesActive;

    public TimerData(String name, BlockPos pos, long ticks, BossType bossType) {
        this.name = name;
        this.pos = pos;
        this.initialDuration = ticks;
        this.endTime = 0; // Хранит длительность в тиках, не время окончания
        this.bossType = bossType;
        this.particlesActive = false;
        this.message = "Время до спавна босса: ";
    }

    public String getName() {
        return name;
    }

    public double getX() {
        return pos.getX();
    }

    public double getY() {
        return pos.getY();
    }

    public double getZ() {
        return pos.getZ();
    }

    public long getEndTime() {
        return endTime;
    }

    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }

    public ArmorStand getEntity() {
        return entity;
    }

    public BlockPos getPos() {
        return pos;
    }

    public long getInitialDuration() {
        return initialDuration;
    }

    public boolean isEventActive() {
        return isEventActive;
    }

    public void setEventActive(boolean eventActive) {
        isEventActive = eventActive;
    }

    public BossType getBossType() {
        return bossType;
    }

    public boolean isTimerStarted() {
        return isTimerStarted;
    }

    public void setTimerStarted(boolean timerStarted) {
        isTimerStarted = timerStarted;
    }

    public boolean areParticlesActive() {
        return particlesActive;
    }

    public void setParticlesActive(boolean active) {
        this.particlesActive = active;
    }

    public void setCustomMessage(String message) {
        this.message = message;
    }

    public void spawnHologram(Level level) {
        List<ArmorStand> existing = findExistingHolograms(level);
        if (!existing.isEmpty()) {
            for (int i = 1; i < existing.size(); i++) {
                existing.get(i).discard();
                BreakTimeMod.LOGGER.warn("Discarded duplicate hologram for " + name);
            }
            this.entity = existing.get(0);
            BreakTimeMod.LOGGER.info("Recovered hologram for " + name + " at " + pos);
            updateHologram(level);
            return;
        }
        if (hologramSpawned) return;

        ArmorStand armorStand = new ArmorStand(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        armorStand.setCustomNameVisible(true);
        armorStand.setInvisible(true);
        armorStand.setNoGravity(true);
        armorStand.addTag("hologram_" + name);
        level.addFreshEntity(armorStand);
        this.entity = armorStand;
        hologramSpawned = true;
        updateHologram(level);
        BreakTimeMod.LOGGER.info("Spawned new hologram for " + name + " at " + pos);
    }

    public void updateHologram(Level level) {
        if (entity == null || entity.isRemoved()) {
            List<ArmorStand> existing = findExistingHolograms(level);
            if (!existing.isEmpty()) {
                for (int i = 1; i < existing.size(); i++) {
                    existing.get(i).discard();
                    BreakTimeMod.LOGGER.warn("Discarded duplicate hologram for " + name);
                }
                entity = existing.get(0);
                BreakTimeMod.LOGGER.info("Recovered hologram for " + name);
            } else {
                spawnHologram(level);
                return;
            }
        }
        if (entity != null) {
            if (!isTimerStarted) {
                entity.setCustomName(Component.literal("Ожидание активации (подойдите ближе)"));
            } else {
                long ticksLeft = endTime - level.getGameTime();
                String timeText = formatTime(ticksLeft);
                entity.setCustomName(Component.literal(message + timeText));
            }
        }
    }

    private List<ArmorStand> findExistingHolograms(Level level) {
        AABB searchArea = new AABB(pos).inflate(1.0);
        return level.getEntitiesOfClass(ArmorStand.class, searchArea, e -> e.getTags().contains("hologram_" + name));
    }

    private String formatTime(long ticks) {
        long seconds = ticks / 20;
        if (seconds >= 24 * 60 * 60) {
            long days = seconds / (24 * 60 * 60);
            return days + " дней";
        } else if (seconds >= 60 * 60) {
            long hours = seconds / (60 * 60);
            return hours + " часов";
        } else if (seconds >= 60) {
            long minutes = seconds / 60;
            return minutes + " минут";
        } else if (seconds > 0) {
            return seconds + " секунд";
        } else {
            return "0 секунд";
        }
    }

    public void spawnParticleRing(ServerLevel level, long ticksLeft) {
        if (ticksLeft > 6000) return;
        if (!particlesActive) { particlesActive = true; }
        double radius = 10.0;
        int particleCount = 50;
        for (int i = 0; i < particleCount; i++) {
            double angle = 2 * Math.PI * i / particleCount;
            double x = pos.getX() + 0.5 + radius * Math.cos(angle);
            double z = pos.getZ() + 0.5 + radius * Math.sin(angle);
            level.sendParticles(ParticleTypes.FLAME, x, pos.getY() + 1.0, z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    public boolean isExpired(long currentTime) {
        return currentTime >= endTime;
    }
}