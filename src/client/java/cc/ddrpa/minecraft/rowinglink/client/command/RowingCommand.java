package cc.ddrpa.minecraft.rowinglink.client.command;

import cc.ddrpa.minecraft.rowinglink.client.RowingLinkClient;
import cc.ddrpa.minecraft.rowinglink.client.config.RowingConfig;
import cc.ddrpa.minecraft.rowinglink.client.controller.RowingController;
import cc.ddrpa.minecraft.rowinglink.client.training.TrainingSession;
import cc.ddrpa.minecraft.rowinglink.client.udp.RowingUdpReceiver;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public class RowingCommand {

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(
                ClientCommandManager.literal("rowing")
                        .then(ClientCommandManager.literal("connect")
                                .executes(ctx -> connect(ctx, null))
                                .then(ClientCommandManager.argument("port", IntegerArgumentType.integer(1, 65535))
                                        .executes(ctx -> connect(ctx, IntegerArgumentType.getInteger(ctx, "port")))
                                )
                        )
                        .then(ClientCommandManager.literal("disconnect")
                                .executes(RowingCommand::disconnect)
                        )
                        .then(ClientCommandManager.literal("reload")
                                .executes(RowingCommand::reload)
                        )
                        .then(ClientCommandManager.literal("train")
                                .then(ClientCommandManager.argument("minutes", IntegerArgumentType.integer(1))
                                        .executes(ctx -> trainByMinutes(ctx, IntegerArgumentType.getInteger(ctx, "minutes")))
                                )
                        )
        );
    }

    // --- Connection commands ---

    private static int connect(com.mojang.brigadier.context.CommandContext<FabricClientCommandSource> ctx, Integer port) {
        RowingConfig config = RowingController.getInstance().getConfig();
        int usePort = port != null ? port : config.network.udpPort;

        RowingUdpReceiver receiver = RowingUdpReceiver.getInstance();
        if (receiver.isRunning()) {
            sendFeedback(ctx.getSource(), "Already connected on port " + receiver.getCurrentPort() + ". Disconnect first.", false);
            return 0;
        }

        try {
            receiver.start(usePort);
            sendFeedback(ctx.getSource(), "UDP receiver started on port " + usePort, true);
            return 1;
        } catch (Exception e) {
            sendFeedback(ctx.getSource(), "Failed to start: " + e.getMessage(), false);
            return 0;
        }
    }

    private static int disconnect(com.mojang.brigadier.context.CommandContext<FabricClientCommandSource> ctx) {
        RowingUdpReceiver receiver = RowingUdpReceiver.getInstance();
        if (!receiver.isRunning()) {
            sendFeedback(ctx.getSource(), "Not connected", false);
            return 0;
        }

        TrainingSession training = RowingLinkClient.getTraining();
        var client = ctx.getSource().getClient();
        long tick = client.level != null ? client.level.getGameTime() : 0;
        if (training.isActive()) {
            training.stop(tick);
        }

        receiver.stop();
        RowingController.getInstance().calibrate();
        sendFeedback(ctx.getSource(), "Disconnected", true);
        return 1;
    }

    private static int reload(com.mojang.brigadier.context.CommandContext<FabricClientCommandSource> ctx) {
        RowingConfig config = RowingConfig.load();
        RowingController.getInstance().setConfig(config);
        sendFeedback(ctx.getSource(), "Config reloaded", true);
        return 1;
    }

    private static int trainByMinutes(com.mojang.brigadier.context.CommandContext<FabricClientCommandSource> ctx, int minutes) {
        TrainingSession t = RowingLinkClient.getTraining();
        var client = ctx.getSource().getClient();
        long tick = client.level != null ? client.level.getGameTime() : 0;
        int seconds = minutes * 60;

        if (t.isActive()) {
            t.stop(tick);
        }
        t.setTargetDurationSeconds(seconds);
        t.start(TrainingSession.Mode.TIME_TARGET, tick);
        sendFeedback(ctx.getSource(), String.format("Training started: %d min", minutes), true);
        return 1;
    }

    // --- Helper ---

    private static void sendFeedback(FabricClientCommandSource source, String message, boolean success) {
        MutableComponent text = Component.literal("[RowingLink] " + message);
        text.withStyle(success ? ChatFormatting.GREEN : ChatFormatting.RED);
        source.sendFeedback(text);
    }
}
