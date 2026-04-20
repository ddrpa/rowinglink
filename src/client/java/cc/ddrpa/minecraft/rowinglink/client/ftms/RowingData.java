package cc.ddrpa.minecraft.rowinglink.client.ftms;

public record RowingData(
        long timestamp,
        Double instantaneousSpeed,
        Double averageSpeed,
        Double instantaneousCadence,
        Double averageCadence,
        Integer totalDistance,
        Double resistanceLevel,
        Integer instantaneousPower,
        Integer averagePower,
        Integer expendedEnergy,
        Double metabolicEquivalent,
        Double elapsedTime,
        Double remainingTime,
        MachineState machineState) {

    public boolean hasValidData() {
        return instantaneousPower != null || instantaneousCadence != null;
    }

    public int getPowerOrZero() {
        return instantaneousPower != null ? instantaneousPower : 0;
    }

    public double getCadenceOrZero() {
        return instantaneousCadence != null ? instantaneousCadence : 0.0;
    }

    public enum MachineState {
        UNKNOWN, STARTED, STOPPED, STARTED_BY_USER, STOPPED_BY_USER
    }
}
