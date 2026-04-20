package cc.ddrpa.minecraft.rowinglink;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RowingLinkMod implements ModInitializer {
    public static final String MOD_ID = "rowinglink";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    // Cross-thread state: set by client RowingController, read by AbstractBoatMixin on server thread
    public static volatile int activeRowingBoatId = -1;
    public static volatile double activeDragCompensation = 1.0;
    public static volatile double activeMaxSpeed = 0.5;

    @Override
    public void onInitialize() {
        LOGGER.info("RowingLink initialized");
    }
}
