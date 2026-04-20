package cc.ddrpa.minecraft.rowinglink.client.controller;

import cc.ddrpa.minecraft.rowinglink.RowingLinkMod;
import cc.ddrpa.minecraft.rowinglink.client.config.RowingConfig;
import cc.ddrpa.minecraft.rowinglink.client.ftms.RowingData;
import cc.ddrpa.minecraft.rowinglink.client.udp.RowingUdpReceiver;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

/**
 * Simulates player paddle input based on rowing machine data.
 * Detects strokes from FTMS cadence and calls boat.setInput() to trigger
 * vanilla paddle physics — no manual velocity manipulation.
 */
public class RowingController {
    private static final Logger LOGGER = RowingLinkMod.LOGGER;
    private static final RowingController INSTANCE = new RowingController();
    private final AutoSteer autoSteer = new AutoSteer();
    private RowingConfig config;

    // Stroke tracking
    private boolean wasActive = false;
    private double accumulatedStrokes = 0;
    private long lastUpdateTick = -1;

    // Paddle state: how many ticks to keep "paddling" after a stroke
    private int paddleTicksRemaining = 0;
    private double paddleImpulseBuffer = 0;
    private double smoothedStrokeImpulse = 0;
    private double adaptiveDistanceScale = 1.0;

    // Distance alignment tracking (game boat vs machine odometer)
    private boolean hasLastBoatPos = false;
    private double lastBoatX = 0;
    private double lastBoatZ = 0;
    private Integer lastMachineDistanceMeters = null;
    private double gameDistanceMeters = 0;
    private double machineDistanceMeters = 0;

    // Speed tracking for HUD
    private double lastForwardSpeed = 0;

    private RowingController() {
        this.config = new RowingConfig();
    }

    public static RowingController getInstance() {
        return INSTANCE;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public RowingConfig getConfig() {
        return config;
    }

    public void setConfig(RowingConfig config) {
        this.config = config;
    }

    public void tick(Minecraft client) {
        if (client.player == null) return;
        if (!(client.player.getVehicle() instanceof AbstractBoat boat)) return;

        RowingUdpReceiver receiver = RowingUdpReceiver.getInstance();
        RowingData data = receiver.isRunning() ? receiver.getLatestData() : null;
        boolean connected = data != null && receiver.isConnected(config.network.connectionTimeoutMs);

        // Check machine state
        boolean hasData = connected && data.hasValidData();
        boolean isActive = hasData && (
                data.machineState() == RowingData.MachineState.STARTED
                        || data.machineState() == RowingData.MachineState.STARTED_BY_USER
                        || data.machineState() == RowingData.MachineState.UNKNOWN
        );

        // Auto-steer (always apply when connected and has data)
        if (isActive) {
            AutoSteer.AutoSteerResult steerResult = autoSteer.steer(boat, config.steering);
            boat.setYRot(steerResult.targetYaw());
        }

        if (isActive && client.level != null) {
            long currentTick = client.level.getGameTime();
            double cadence = data.getCadenceOrZero();
            int power = data.getPowerOrZero();

            updateDistanceAlignment(boat, data);

            // Calculate strokes since last tick
            if (lastUpdateTick >= 0 && cadence > 0) {
                long tickDelta = currentTick - lastUpdateTick;
                if (tickDelta > 0) {
                    // cadence = strokes per minute → strokes per tick = cadence / 1200
                    double strokesPerTick = cadence / 1200.0;
                    accumulatedStrokes += strokesPerTick * tickDelta;

                    // Each accumulated stroke triggers a paddle
                    if (accumulatedStrokes >= 1.0) {
                        int newStrokes = (int) accumulatedStrokes;
                        accumulatedStrokes -= newStrokes;

                        // Game-feel propulsion: cadence and power jointly shape impulse.
                        double strokeImpulse = computeStrokeImpulseTicks(cadence, power);
                        double alpha = clamp(config.propulsion.impulseSmoothing, 0.0, 1.0);
                        smoothedStrokeImpulse += alpha * (strokeImpulse - smoothedStrokeImpulse);
                        double effectiveImpulse = smoothedStrokeImpulse > 0 ? smoothedStrokeImpulse : strokeImpulse;
                        paddleImpulseBuffer += effectiveImpulse * newStrokes;
                        int wholeTicks = (int) paddleImpulseBuffer;
                        paddleImpulseBuffer -= wholeTicks;
                        paddleTicksRemaining += wholeTicks;
                    }
                }
            }
            lastUpdateTick = currentTick;

            // Apply collision avoidance: stop paddling if blocked
            if (autoSteer.isBlocked()) {
                paddleTicksRemaining = 0;
            }

            // Set vanilla paddle input, preserving keyboard steering
            boolean paddling = paddleTicksRemaining > 0;
            if (paddling) {
                boolean left = client.options.keyLeft.isDown();
                boolean right = client.options.keyRight.isDown();
                boat.setInput(left, right, true, false);
                paddleTicksRemaining--;
            }
            // When not paddling, don't call setInput — let vanilla keyboard controls work

            // Publish rowing state for the server-side AbstractBoatMixin
            RowingLinkMod.activeRowingBoatId = boat.getId();
            RowingLinkMod.activeDragCompensation = config.physics.dragCompensation;
            RowingLinkMod.activeMaxSpeed = config.physics.maxSpeed;

            wasActive = true;
        } else {
            // No rowing machine data — reset stroke tracking, let vanilla controls work
            if (wasActive) {
                accumulatedStrokes = 0;
                paddleTicksRemaining = 0;
                paddleImpulseBuffer = 0;
                smoothedStrokeImpulse = 0;
                adaptiveDistanceScale = 1.0;
                lastUpdateTick = -1;
                hasLastBoatPos = false;
                lastMachineDistanceMeters = null;
                gameDistanceMeters = 0;
                machineDistanceMeters = 0;
                // Don't clear input — let vanilla handle it
                RowingLinkMod.activeRowingBoatId = -1;
                wasActive = false;
            }
        }

        // Track speed for HUD
        lastForwardSpeed = computeForwardSpeed(boat);
    }

    private double computeForwardSpeed(AbstractBoat boat) {
        float yaw = boat.getYRot();
        double forwardX = -Math.sin(Math.toRadians(yaw));
        double forwardZ = Math.cos(Math.toRadians(yaw));
        Vec3 vel = boat.getDeltaMovement();
        return vel.x * forwardX + vel.z * forwardZ;
    }

    public void calibrate() {
        accumulatedStrokes = 0;
        paddleTicksRemaining = 0;
        paddleImpulseBuffer = 0;
        smoothedStrokeImpulse = 0;
        adaptiveDistanceScale = 1.0;
        lastUpdateTick = -1;
        lastForwardSpeed = 0;
        wasActive = false;
        hasLastBoatPos = false;
        lastMachineDistanceMeters = null;
        gameDistanceMeters = 0;
        machineDistanceMeters = 0;
    }

    public double getCurrentSpeed() {
        return lastForwardSpeed;
    }

    public double getCurrentDistanceMatchScale() {
        return Math.max(0.0, config.propulsion.distanceScale) * adaptiveDistanceScale;
    }

    public double getGameDistanceMetersForMatch() {
        return gameDistanceMeters;
    }

    public double getMachineDistanceMetersForMatch() {
        return machineDistanceMeters;
    }

    private double computeStrokeImpulseTicks(double cadence, int power) {
        RowingConfig.PropulsionConfig p = config.propulsion;
        double safePower = Math.max(0.0, power);
        double safeCadence = Math.max(0.0, cadence);

        double powerNorm = safePower / Math.max(1.0, p.referencePower);
        double powerFactor = Math.pow(powerNorm, p.powerGamma);
        powerFactor = clamp(powerFactor, p.minPowerFactor, p.maxPowerFactor);

        double z = (safeCadence - p.targetSpm) / Math.max(0.1, p.cadenceSigma);
        double gaussian = Math.exp(-0.5 * z * z);
        double cadenceFactor = p.minCadenceFactor + (p.maxCadenceFactor - p.minCadenceFactor) * gaussian;
        if (safeCadence < p.lowSpmThreshold) {
            double lowRatio = safeCadence / Math.max(1.0, p.lowSpmThreshold);
            cadenceFactor *= clamp(lowRatio, 0.0, 1.0);
        }

        double impulse = p.baseTicksPerStroke * powerFactor * cadenceFactor;
        impulse *= Math.max(0.1, p.distanceScale) * adaptiveDistanceScale;
        return clamp(impulse, 0.0, p.maxPaddleTicksPerStroke);
    }

    private void updateDistanceAlignment(AbstractBoat boat, RowingData data) {
        RowingConfig.PropulsionConfig p = config.propulsion;

        if (!hasLastBoatPos) {
            lastBoatX = boat.getX();
            lastBoatZ = boat.getZ();
            hasLastBoatPos = true;
        } else {
            double dx = boat.getX() - lastBoatX;
            double dz = boat.getZ() - lastBoatZ;
            gameDistanceMeters += Math.sqrt(dx * dx + dz * dz);
            lastBoatX = boat.getX();
            lastBoatZ = boat.getZ();
        }

        Integer machineDist = data.totalDistance();
        if (machineDist != null) {
            if (lastMachineDistanceMeters != null) {
                int delta = machineDist - lastMachineDistanceMeters;
                // Ignore resets and obvious spikes from reconnect/noise.
                if (delta > 0 && delta < 25) {
                    machineDistanceMeters += delta;
                }
            }
            lastMachineDistanceMeters = machineDist;
        }

        if (!p.autoDistanceMatch) {
            adaptiveDistanceScale = 1.0;
            return;
        }

        double warmup = Math.max(1.0, p.distanceMatchWarmupMeters);
        if (machineDistanceMeters < warmup || gameDistanceMeters < warmup) {
            return;
        }

        double targetScale = machineDistanceMeters / Math.max(0.1, gameDistanceMeters);
        targetScale = Math.max(p.distanceMatchMinScale, targetScale);
        if (p.distanceMatchMaxScale > 0.0) {
            targetScale = Math.min(p.distanceMatchMaxScale, targetScale);
        }
        adaptiveDistanceScale = targetScale;
    }
}
