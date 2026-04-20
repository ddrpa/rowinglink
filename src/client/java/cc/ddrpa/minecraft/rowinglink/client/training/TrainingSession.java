package cc.ddrpa.minecraft.rowinglink.client.training;

import cc.ddrpa.minecraft.rowinglink.client.ftms.RowingData;

/**
 * Tracks a single rowing session with cumulative stats.
 * Also serves as the training mode controller.
 */
public class TrainingSession {
    // Session state
    private boolean active = false;
    private long sessionStartTick = 0;
    private long sessionEndTick = 0;
    private Mode mode = Mode.FREE_ROW;
    // Cumulative stats
    private double totalDistanceMeters = 0;
    private double totalPowerSum = 0;
    private int powerSamples = 0;
    // Distance target
    private int targetDistanceMeters = 2000;
    // Time target
    private int targetDurationSeconds = 1800; // 30 min
    // Interval training
    private int intervalSets = 6;
    private int currentSet = 0;
    private IntervalPhase intervalPhase = IntervalPhase.WORK;
    private long lastStrokeTick = 0;

    public void start(Mode mode, long currentTick) {
        this.active = true;
        this.sessionStartTick = currentTick;
        this.sessionEndTick = 0;
        this.mode = mode;
        this.totalDistanceMeters = 0;
        this.totalPowerSum = 0;
        this.powerSamples = 0;
        this.currentSet = 0;
        this.intervalPhase = IntervalPhase.WORK;
        this.lastStrokeTick = 0;
    }

    public void stop(long currentTick) {
        this.active = false;
        this.sessionEndTick = currentTick;
    }

    public void update(RowingData data, long currentTick) {
        if (!active) return;

        // Accumulate distance from machine data
        if (data.totalDistance() != null) {
            totalDistanceMeters = data.totalDistance();
        }

        // Accumulate power average
        if (data.instantaneousPower() != null) {
            totalPowerSum += data.instantaneousPower();
            powerSamples++;
        }

        // Count strokes from cadence
        double cadence = data.getCadenceOrZero();
        if (cadence > 0) {
            double strokesPerTick = cadence / 1200.0;
            if (lastStrokeTick == 0) {
                lastStrokeTick = currentTick;
            }
            long tickDelta = currentTick - lastStrokeTick;
            if (tickDelta > 0 && strokesPerTick > 0) {
                double expectedStrokes = tickDelta * strokesPerTick;
                if (expectedStrokes >= 1.0) {
                    lastStrokeTick = currentTick;
                }
            }
        }
    }

    /**
     * Check if the training goal is complete.
     */
    public boolean isComplete(long currentTick) {
        return switch (mode) {
            case FREE_ROW -> false;
            case DISTANCE_TARGET -> totalDistanceMeters >= targetDistanceMeters;
            case TIME_TARGET -> getElapsedSeconds(currentTick) >= targetDurationSeconds;
            case INTERVAL -> currentSet >= intervalSets && intervalPhase == IntervalPhase.REST;
        };
    }

    public int getElapsedSeconds(long currentTick) {
        long ticks = (active ? currentTick : sessionEndTick) - sessionStartTick;
        return (int) (ticks / 20);
    }

    public double getAvgPower() {
        return powerSamples > 0 ? totalPowerSum / powerSamples : 0;
    }

    public boolean isActive() {
        return active;
    }

    // --- Getters and setters ---

    public double getTotalDistance() {
        return totalDistanceMeters;
    }

    public void setTargetDurationSeconds(int s) {
        this.targetDurationSeconds = s;
    }

    public enum Mode {
        FREE_ROW, DISTANCE_TARGET, TIME_TARGET, INTERVAL
    }

    public enum IntervalPhase {
        WORK, REST
    }
}
