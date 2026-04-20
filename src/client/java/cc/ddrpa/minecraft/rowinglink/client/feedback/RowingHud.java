package cc.ddrpa.minecraft.rowinglink.client.feedback;

import cc.ddrpa.minecraft.rowinglink.client.config.RowingConfig;
import cc.ddrpa.minecraft.rowinglink.client.controller.RowingController;
import cc.ddrpa.minecraft.rowinglink.client.ftms.RowingData;
import cc.ddrpa.minecraft.rowinglink.client.udp.RowingUdpReceiver;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;

public class RowingHud implements HudElement {

    @Override
    public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;
        if (client.options.hideGui) return;

        RowingUdpReceiver receiver = RowingUdpReceiver.getInstance();
        RowingController controller = RowingController.getInstance();
        RowingConfig cfg = controller.getConfig();

        // Connection status indicator (top-left)
        renderConnectionStatus(graphics, receiver, cfg);

        // Only show data HUD when in a boat and receiving data
        if (!(client.player.getVehicle() instanceof AbstractBoat boat)) return;
        if (!receiver.isRunning()) return;

        RowingData data = receiver.getLatestData();
        if (data == null) return;
        renderDataOverlay(graphics, data, controller);
    }

    private void renderConnectionStatus(GuiGraphics graphics, RowingUdpReceiver receiver, RowingConfig cfg) {
        int x = 4;
        int y = 4;
        int color;

        if (!receiver.isRunning()) {
            color = 0xFF555555; // Gray - off
        } else if (receiver.isConnected(cfg.network.connectionTimeoutMs)) {
            RowingData data = receiver.getLatestData();
            if (data != null && data.hasValidData()) {
                color = 0xFF55FF55; // Green - connected + data
            } else {
                color = 0xFFFFFF55; // Yellow - connected but no data
            }
        } else {
            color = 0xFF5555FF; // Red - timeout
        }

        graphics.fill(x, y, x + 6, y + 6, color);
    }

    private void renderDataOverlay(GuiGraphics graphics, RowingData data, RowingController controller) {
        int x = 12;
        int y = 4;
        int lineHeight = 11;
        int color = 0xFFFFFFFF;

        int panelWidth = 150;
        int lines = 0;
        if (data.instantaneousPower() != null) lines++;
        if (data.instantaneousCadence() != null) lines++;
        lines++; // game speed always shown
        if (data.totalDistance() != null) lines++;
        lines += 1; // distance match scale

        int panelHeight = lines * lineHeight + 6;
        graphics.fill(x - 2, y - 2, x + panelWidth, y + panelHeight, 0x90505050);

        int currentY = y + 2;

        if (data.instantaneousPower() != null) {
            String text = String.format("Power: %d W", data.instantaneousPower());
            graphics.drawString(Minecraft.getInstance().font, text, x + 2, currentY, color, true);
            currentY += lineHeight;
        }

        if (data.instantaneousCadence() != null) {
            String text = String.format("Cadence: %.0f spm", data.instantaneousCadence());
            graphics.drawString(Minecraft.getInstance().font, text, x + 2, currentY, color, true);
            currentY += lineHeight;
        }

        double speedBlocksPerSec = controller.getCurrentSpeed() * 20;
        String speedText = String.format("Speed: %.1f m/s", speedBlocksPerSec);
        graphics.drawString(Minecraft.getInstance().font, speedText, x + 2, currentY, color, true);
        currentY += lineHeight;

        if (data.totalDistance() != null) {
            String text = String.format("Distance: %d m", data.totalDistance());
            graphics.drawString(Minecraft.getInstance().font, text, x + 2, currentY, color, true);
            currentY += lineHeight;
        }

        double matchScale = controller.getCurrentDistanceMatchScale();
        String scaleText = String.format("Distance Match Scale: %.3f", matchScale);
        graphics.drawString(Minecraft.getInstance().font, scaleText, x + 2, currentY, color, true);
    }
}
