package cc.ddrpa.minecraft.rowinglink.client.ftms;

import cc.ddrpa.minecraft.rowinglink.RowingLinkMod;
import org.slf4j.Logger;

/**
 * Stateless FTMS (Fitness Machine Service) protocol parser.
 * Parses raw GATT characteristic values per Bluetooth SIG spec.
 */
public class FtmsParser {
    // GATT Characteristic UUIDs (uint16)
    public static final int UUID_INDOOR_BIKE_DATA = 0x2AD1;
    public static final int UUID_TRAINING_STATUS = 0x2AD2;
    public static final int UUID_FITNESS_MACHINE_STATUS = 0x2ADA;
    private static final Logger LOGGER = RowingLinkMod.LOGGER;
    // Fitness Machine Status opcodes
    private static final int STATUS_STARTED = 0x04;
    private static final int STATUS_STOPPED = 0x05;
    private static final int STATUS_STARTED_BY_USER = 0x06;
    private static final int STATUS_STOPPED_BY_USER = 0x07;

    static int readUint16LE(byte[] data, int offset) {
        return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8);
    }

    static short readSint16LE(byte[] data, int offset) {
        return (short) readUint16LE(data, offset);
    }

    static int readUint24LE(byte[] data, int offset) {
        return (data[offset] & 0xFF)
                | ((data[offset + 1] & 0xFF) << 8)
                | ((data[offset + 2] & 0xFF) << 16);
    }

    // --- Byte reading helpers (Little Endian) ---

    /**
     * Parse a UDP packet: 2-byte UUID header (LE) + raw characteristic value.
     * Returns null for unknown UUIDs.
     */
    public RowingData parseUdpPacket(byte[] packet) {
        if (packet == null || packet.length < 3) {
            LOGGER.warn("UDP packet too short: {} bytes", packet != null ? packet.length : 0);
            return null;
        }

        int uuid = readUint16LE(packet, 0);
        byte[] value = new byte[packet.length - 2];
        System.arraycopy(packet, 2, value, 0, value.length);

        return switch (uuid) {
            case UUID_INDOOR_BIKE_DATA -> parseIndoorBikeData(value);
            case UUID_FITNESS_MACHINE_STATUS -> parseMachineStatus(value);
            case UUID_TRAINING_STATUS -> {
                LOGGER.debug("Training status received, ignoring");
                yield null;
            }
            default -> {
                LOGGER.debug("Unknown UUID: 0x{}", Integer.toHexString(uuid));
                yield null;
            }
        };
    }

    /**
     * Parse Indoor Bike Data (0x2AD1) per FTMS spec.
     * Reads 2-byte flags, then fields in bit order.
     */
    public RowingData parseIndoorBikeData(byte[] data) {
        if (data.length < 2) {
            LOGGER.warn("Indoor Bike Data too short");
            return null;
        }

        int flags = readUint16LE(data, 0);
        int offset = 2;

        Double instantaneousSpeed = null;
        Double averageSpeed = null;
        Double instantaneousCadence = null;
        Double averageCadence = null;
        Integer totalDistance = null;
        Double resistanceLevel = null;
        Integer instantaneousPower = null;
        Integer averagePower = null;
        Integer expendedEnergy = null;
        Double metabolicEquivalent = null;
        Double elapsedTime = null;
        Double remainingTime = null;

        // Bit 0: Instantaneous Speed (uint16, 0.01 km/h)
        if ((flags & 0x0001) != 0) {
            if (offset + 2 <= data.length) {
                instantaneousSpeed = readUint16LE(data, offset) * 0.01;
                offset += 2;
            }
        }

        // Bit 1: Average Speed (uint16, 0.01 km/h)
        if ((flags & 0x0002) != 0) {
            if (offset + 2 <= data.length) {
                averageSpeed = readUint16LE(data, offset) * 0.01;
                offset += 2;
            }
        }

        // Bit 2: Instantaneous Cadence (uint16, 0.5 rpm)
        if ((flags & 0x0004) != 0) {
            if (offset + 2 <= data.length) {
                instantaneousCadence = readUint16LE(data, offset) * 0.5;
                offset += 2;
            }
        }

        // Bit 3: Average Cadence (uint16, 0.5 rpm)
        if ((flags & 0x0008) != 0) {
            if (offset + 2 <= data.length) {
                averageCadence = readUint16LE(data, offset) * 0.5;
                offset += 2;
            }
        }

        // Bit 4: Total Distance (uint24, meters)
        if ((flags & 0x0010) != 0) {
            if (offset + 3 <= data.length) {
                totalDistance = readUint24LE(data, offset);
                offset += 3;
            }
        }

        // Bit 5: Resistance Level (sint16, 0.1)
        if ((flags & 0x0020) != 0) {
            if (offset + 2 <= data.length) {
                resistanceLevel = readSint16LE(data, offset) * 0.1;
                offset += 2;
            }
        }

        // Bit 6: Instantaneous Power (sint16, watts)
        if ((flags & 0x0040) != 0) {
            if (offset + 2 <= data.length) {
                instantaneousPower = (int) readSint16LE(data, offset);
                offset += 2;
            }
        }

        // Bit 7: Average Power (sint16, watts)
        if ((flags & 0x0080) != 0) {
            if (offset + 2 <= data.length) {
                averagePower = (int) readSint16LE(data, offset);
                offset += 2;
            }
        }

        // Bit 8: Expended Energy (uint16, kCal)
        if ((flags & 0x0100) != 0) {
            if (offset + 2 <= data.length) {
                expendedEnergy = readUint16LE(data, offset);
                offset += 2;
            }
        }

        // Bit 9: Heart Rate (uint8, bpm) - ignored.
        if ((flags & 0x0200) != 0) {
            if (offset + 1 <= data.length) {
                offset += 1;
            }
        }

        // Bit 10: Metabolic Equivalent (uint8, 0.1 METs)
        if ((flags & 0x0400) != 0) {
            if (offset + 1 <= data.length) {
                metabolicEquivalent = (data[offset] & 0xFF) * 0.1;
                offset += 1;
            }
        }

        // Bit 11: Elapsed Time (uint16, 0.25 sec)
        if ((flags & 0x0800) != 0) {
            if (offset + 2 <= data.length) {
                elapsedTime = readUint16LE(data, offset) * 0.25;
                offset += 2;
            }
        }

        // Bit 12: Remaining Time (uint16, 0.25 sec)
        if ((flags & 0x1000) != 0) {
            if (offset + 2 <= data.length) {
                remainingTime = readUint16LE(data, offset) * 0.25;
                offset += 2;
            }
        }

        return new RowingData(
                System.currentTimeMillis(),
                instantaneousSpeed,
                averageSpeed,
                instantaneousCadence,
                averageCadence,
                totalDistance,
                resistanceLevel,
                instantaneousPower,
                averagePower,
                expendedEnergy,
                metabolicEquivalent,
                elapsedTime,
                remainingTime,
                RowingData.MachineState.UNKNOWN
        );
    }

    /**
     * Parse Fitness Machine Status (0x2ADA).
     * Returns a RowingData with only the machine state set.
     */
    public RowingData parseMachineStatus(byte[] data) {
        if (data.length < 1) {
            return null;
        }

        int opcode = data[0] & 0xFF;
        RowingData.MachineState state = switch (opcode) {
            case STATUS_STARTED -> RowingData.MachineState.STARTED;
            case STATUS_STOPPED -> RowingData.MachineState.STOPPED;
            case STATUS_STARTED_BY_USER -> RowingData.MachineState.STARTED_BY_USER;
            case STATUS_STOPPED_BY_USER -> RowingData.MachineState.STOPPED_BY_USER;
            default -> RowingData.MachineState.UNKNOWN;
        };

        LOGGER.debug("Machine status: {} (opcode 0x{})", state, Integer.toHexString(opcode));

        return new RowingData(
                System.currentTimeMillis(),
                null, null, null, null, null, null, null, null, null, null, null, null,
                state
        );
    }
}
