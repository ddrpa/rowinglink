package cc.ddrpa.minecraft.rowinglink.client.feedback;

import cc.ddrpa.minecraft.rowinglink.client.config.RowingConfig;
import cc.ddrpa.minecraft.rowinglink.client.controller.RowingController;
import cc.ddrpa.minecraft.rowinglink.client.ftms.RowingData;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Handles visual and audio feedback during rowing: splash particles and paddle sounds.
 */
public class RowingEffects {
    private int lastStrokeCount = 0;
    private long lastSoundTick = 0;
    private long lastParticleTick = 0;

    /**
     * Call every client tick when the player is in a boat and rowing.
     */
    public void tick(AbstractBoat boat, RowingData data, RowingConfig config) {
        Minecraft client = Minecraft.getInstance();
        long currentTick = client.level != null ? client.level.getGameTime() : 0;

        int power = data.getPowerOrZero();

        // Detect new stroke: cadence tells us stroke rate
        double cadence = data.getCadenceOrZero();
        if (cadence > 0) {
            // Estimate strokes per tick from cadence (spm)
            // strokes_per_tick = cadence / (60 * 20)
            double strokesPerTick = cadence / 1200.0;
            int expectedStrokes = (int) (currentTick * strokesPerTick);
            if (expectedStrokes > lastStrokeCount) {
                lastStrokeCount = expectedStrokes;
                onNewStroke(boat, power, currentTick, config);
            }
        }

        // Continuous wake trail particles behind boat
        RowingController controller = RowingController.getInstance();
        double speed = controller.getCurrentSpeed();
        if (speed > 0.01 && currentTick != lastParticleTick) {
            lastParticleTick = currentTick;
            spawnWakeParticles(boat, speed);
        }
    }

    private void onNewStroke(AbstractBoat boat, int power, long currentTick, RowingConfig config) {
        Level level = boat.level();
        if (level == null || !level.isClientSide()) return;

        Vec3 pos = boat.position();
        float yaw = boat.getYRot();

        // Splash particles at boat stern
        double behindX = pos.x + Math.sin(Math.toRadians(yaw)) * 1.2;
        double behindZ = pos.z - Math.cos(Math.toRadians(yaw)) * 1.2;

        int particleCount = Math.max(2, power / 40);
        for (int i = 0; i < particleCount; i++) {
            double offsetX = (level.random.nextDouble() - 0.5) * 0.8;
            double offsetZ = (level.random.nextDouble() - 0.5) * 0.8;
            level.addParticle(
                    ParticleTypes.SPLASH,
                    behindX + offsetX, pos.y + 0.2, behindZ + offsetZ,
                    (level.random.nextDouble() - 0.5) * 0.1,
                    level.random.nextDouble() * 0.2,
                    (level.random.nextDouble() - 0.5) * 0.1
            );
        }

        // Paddle sound
        if (currentTick - lastSoundTick > 8) { // max once per 8 ticks (0.4s)
            lastSoundTick = currentTick;
            float volume = net.minecraft.util.Mth.clamp(power / 200f, 0.3f, 1.0f);
            level.playLocalSound(
                    pos.x, pos.y, pos.z,
                    SoundEvents.BOAT_PADDLE_WATER,
                    SoundSource.PLAYERS,
                    volume, 0.8f + level.random.nextFloat() * 0.4f,
                    false
            );
        }
    }

    private void spawnWakeParticles(AbstractBoat boat, double speed) {
        Level level = boat.level();
        if (level == null) return;

        Vec3 pos = boat.position();
        float yaw = boat.getYRot();

        // Spawn behind the boat
        double behindX = pos.x + Math.sin(Math.toRadians(yaw)) * 1.5;
        double behindZ = pos.z - Math.cos(Math.toRadians(yaw)) * 1.5;

        // Intensity scales with speed
        int count = (int) Math.min(3, speed * 10);
        for (int i = 0; i < count; i++) {
            double offsetX = (level.random.nextDouble() - 0.5) * 0.6;
            double offsetZ = (level.random.nextDouble() - 0.5) * 0.6;
            level.addParticle(
                    ParticleTypes.BUBBLE,
                    behindX + offsetX, pos.y - 0.3, behindZ + offsetZ,
                    (level.random.nextDouble() - 0.5) * 0.05,
                    level.random.nextDouble() * 0.05,
                    (level.random.nextDouble() - 0.5) * 0.05
            );
        }
    }

    public void reset() {
        lastStrokeCount = 0;
        lastSoundTick = 0;
        lastParticleTick = 0;
    }
}
