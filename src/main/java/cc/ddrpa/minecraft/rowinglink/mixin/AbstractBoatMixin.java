package cc.ddrpa.minecraft.rowinglink.mixin;

import cc.ddrpa.minecraft.rowinglink.RowingLinkMod;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractBoat.class)
public abstract class AbstractBoatMixin {

    /**
     * After the boat's tick (including friction), compensate water drag
     * for the boat currently controlled by the rowing machine.
     * Runs on both client and server threads so physics stay in sync.
     */
    @Inject(method = "tick", at = @At("TAIL"))
    private void rowinglink$applyDragCompensation(CallbackInfo ci) {
        AbstractBoat self = (AbstractBoat) (Object) this;
        if (self.getId() != RowingLinkMod.activeRowingBoatId) return;

        double comp = RowingLinkMod.activeDragCompensation;
        if (comp == 1.0) return;

        Vec3 vel = self.getDeltaMovement();
        double newX = vel.x * comp;
        double newZ = vel.z * comp;

        double hSpeed = Math.sqrt(newX * newX + newZ * newZ);
        double maxSpeed = RowingLinkMod.activeMaxSpeed;
        if (hSpeed > maxSpeed) {
            double scale = maxSpeed / hSpeed;
            newX *= scale;
            newZ *= scale;
        }

        self.setDeltaMovement(newX, vel.y, newZ);
    }
}
