package cc.ddrpa.minecraft.rowinglink.client.udp;

import cc.ddrpa.minecraft.rowinglink.RowingLinkMod;
import cc.ddrpa.minecraft.rowinglink.client.ftms.FtmsParser;
import cc.ddrpa.minecraft.rowinglink.client.ftms.RowingData;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;

/**
 * UDP receiver that runs on a daemon thread.
 * Receives FTMS packets and maintains the latest parsed RowingData.
 */
public class RowingUdpReceiver {
    private static final Logger LOGGER = RowingLinkMod.LOGGER;
    private static final RowingUdpReceiver INSTANCE = new RowingUdpReceiver();

    private final FtmsParser parser = new FtmsParser();
    private final Object lock = new Object();
    private volatile RowingData latestData;
    private volatile boolean running;
    private Thread receiverThread;
    private int currentPort = -1;

    private RowingUdpReceiver() {
    }

    public static RowingUdpReceiver getInstance() {
        return INSTANCE;
    }

    public void start(int port) {
        if (running) {
            throw new IllegalStateException("Already running on port " + currentPort);
        }

        running = true;
        currentPort = port;
        latestData = null;

        receiverThread = new Thread(() -> {
            try (DatagramChannel channel = DatagramChannel.open()) {
                channel.bind(new InetSocketAddress(port));
                channel.configureBlocking(true);
                LOGGER.info("UDP receiver started on port {}", port);

                ByteBuffer buffer = ByteBuffer.allocate(512);

                while (running) {
                    buffer.clear();
                    channel.receive(buffer);
                    buffer.flip();

                    byte[] data = new byte[buffer.remaining()];
                    buffer.get(data);

                    try {
                        RowingData parsed = parser.parseUdpPacket(data);
                        if (parsed != null) {
                            // Merge machine state from status packets into data packets
                            synchronized (lock) {
                                if (parsed.hasValidData()) {
                                    latestData = parsed;
                                } else if (parsed.machineState() != RowingData.MachineState.UNKNOWN) {
                                    // Status-only packet: update state but keep existing data
                                    if (latestData != null) {
                                        latestData = new RowingData(
                                                parsed.timestamp(),
                                                latestData.instantaneousSpeed(),
                                                latestData.averageSpeed(),
                                                latestData.instantaneousCadence(),
                                                latestData.averageCadence(),
                                                latestData.totalDistance(),
                                                latestData.resistanceLevel(),
                                                latestData.instantaneousPower(),
                                                latestData.averagePower(),
                                                latestData.expendedEnergy(),
                                                latestData.metabolicEquivalent(),
                                                latestData.elapsedTime(),
                                                latestData.remainingTime(),
                                                parsed.machineState()
                                        );
                                    } else {
                                        latestData = parsed;
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        LOGGER.warn("Failed to parse UDP packet", e);
                    }
                }
            } catch (IOException e) {
                if (running) {
                    LOGGER.error("UDP receiver error", e);
                }
            } finally {
                running = false;
                LOGGER.info("UDP receiver stopped");
            }
        }, "RowingLink-UDP-Receiver");

        receiverThread.setDaemon(true);
        receiverThread.start();
    }

    public void stop() {
        running = false;
        if (receiverThread != null) {
            receiverThread.interrupt();
            // Close the channel to unblock receive()
            try {
                // Interrupt is enough for daemon threads; the channel will close when thread exits
            } catch (Exception e) {
                LOGGER.warn("Error stopping UDP receiver", e);
            }
            receiverThread = null;
        }
        currentPort = -1;
        latestData = null;
    }

    public RowingData getLatestData() {
        synchronized (lock) {
            return latestData;
        }
    }

    public boolean isRunning() {
        return running;
    }

    public int getCurrentPort() {
        return currentPort;
    }

    public boolean isConnected(long connectionTimeoutMs) {
        synchronized (lock) {
            if (latestData == null) return false;
            long age = System.currentTimeMillis() - latestData.timestamp();
            return age < connectionTimeoutMs;
        }
    }
}
