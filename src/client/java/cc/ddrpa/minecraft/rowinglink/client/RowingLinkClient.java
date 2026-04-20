package cc.ddrpa.minecraft.rowinglink.client;

import cc.ddrpa.minecraft.rowinglink.RowingLinkMod;
import cc.ddrpa.minecraft.rowinglink.client.command.RowingCommand;
import cc.ddrpa.minecraft.rowinglink.client.config.RowingConfig;
import cc.ddrpa.minecraft.rowinglink.client.controller.RowingController;
import cc.ddrpa.minecraft.rowinglink.client.feedback.RowingEffects;
import cc.ddrpa.minecraft.rowinglink.client.feedback.RowingHud;
import cc.ddrpa.minecraft.rowinglink.client.training.TrainingSession;
import cc.ddrpa.minecraft.rowinglink.client.udp.RowingUdpReceiver;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;

public class RowingLinkClient implements ClientModInitializer {
    private static final RowingEffects effects = new RowingEffects();
    private static final TrainingSession training = new TrainingSession();

    private static void sendChat(Minecraft client, String message, ChatFormatting color) {
        if (client.player != null) {
            var text = Component.literal("[RowingLink] " + message).withStyle(color);
            client.player.displayClientMessage(text, false);
        }
    }

    public static TrainingSession getTraining() {
        return training;
    }

    @Override
    public void onInitializeClient() {
        RowingLinkMod.LOGGER.info("RowingLink client initializing...");

        // Load config
        RowingConfig config = RowingConfig.load();
        config.save();

        // Initialize controller
        RowingController controller = RowingController.getInstance();
        controller.setConfig(config);

        // Register HUD overlay
        if (config.feedback.showHud) {
            HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(RowingLinkMod.MOD_ID, "rowing_hud"), new RowingHud());
        }

        // Register tick event — handles physics, effects, training
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            controller.tick(client);
            tickEffects(client, config);
            tickTraining(client);
        });

        // Register commands
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            RowingCommand.register(dispatcher);
        });

        // Auto-connect
        if (config.usability.autoConnect) {
            try {
                RowingUdpReceiver.getInstance().start(config.network.udpPort);
                RowingLinkMod.LOGGER.info("Auto-connected on port {}", config.network.udpPort);
            } catch (Exception e) {
                RowingLinkMod.LOGGER.warn("Auto-connect failed: {}", e.getMessage());
            }
        }

        // First-run welcome
        if (!config.usability.shownWelcome) {
            config.usability.shownWelcome = true;
            config.save();
            // Schedule welcome message for when player joins a world (fire once)
            final boolean[] welcomed = {false};
            ClientTickEvents.END_CLIENT_TICK.register(client -> {
                if (!welcomed[0] && client.player != null) {
                    welcomed[0] = true;
                    sendChat(client, "Welcome to RowingLink!", ChatFormatting.GREEN);
                    sendChat(client, "1. Start your BLE-UDP bridge tool", ChatFormatting.YELLOW);
                    sendChat(client, "2. Get in a boat and run /rowing connect", ChatFormatting.YELLOW);
                    sendChat(client, "3. Start rowing!", ChatFormatting.YELLOW);
                    sendChat(client, "Type /rowing help for all commands", ChatFormatting.GRAY);
                }
            });
        }

        // Shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            RowingUdpReceiver receiver = RowingUdpReceiver.getInstance();
            if (receiver.isRunning()) {
                receiver.stop();
            }
        }));

        RowingLinkMod.LOGGER.info("RowingLink client initialized");
    }

    private void tickEffects(Minecraft client, RowingConfig config) {
        if (client.player == null) return;
        if (!(client.player.getVehicle() instanceof AbstractBoat boat)) return;

        RowingUdpReceiver receiver = RowingUdpReceiver.getInstance();
        if (!receiver.isRunning()) return;

        var data = receiver.getLatestData();
        if (data == null || !data.hasValidData()) return;

        effects.tick(boat, data, config);
    }

    private void tickTraining(Minecraft client) {
        if (!training.isActive()) return;
        if (client.player == null || client.level == null) return;

        RowingUdpReceiver receiver = RowingUdpReceiver.getInstance();
        var data = receiver.getLatestData();
        if (data == null) return;

        long tick = client.level.getGameTime();
        training.update(data, tick);

        if (training.isComplete(tick)) {
            training.stop(tick);
            sendChat(client, "Training complete!", ChatFormatting.GREEN);
            sendChat(client, String.format("Distance: %.0fm  Time: %ds  Avg Power: %.0fW",
                    training.getTotalDistance(),
                    training.getElapsedSeconds(tick),
                    training.getAvgPower()), ChatFormatting.YELLOW);
        }
    }
}
