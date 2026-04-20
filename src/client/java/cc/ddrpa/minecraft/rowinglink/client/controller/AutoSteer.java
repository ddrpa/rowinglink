package cc.ddrpa.minecraft.rowinglink.client.controller;

import cc.ddrpa.minecraft.rowinglink.RowingLinkMod;
import cc.ddrpa.minecraft.rowinglink.client.config.RowingConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

/**
 * Auto-steer using fan-shaped water sampling + heading hold.
 * Solves the one-dimensional input problem — the boat automatically follows open water.
 */
public class AutoSteer {
    private static final float YAW_CHANGE_THRESHOLD = 1.5f;

    private boolean enabled = true;
    private float lastAutoSteerYaw = Float.NaN;
    private long lastManualInputTick = -1;
    private boolean blocked;

    public AutoSteerResult steer(AbstractBoat boat, RowingConfig.SteeringConfig config) {
        if (!enabled) {
            lastAutoSteerYaw = boat.getYRot();
            return new AutoSteerResult(boat.getYRot(), false);
        }

        Minecraft client = Minecraft.getInstance();
        long currentTick = client.level != null ? client.level.getGameTime() : 0;

        // Check manual input
        boolean manualSteering = false;

        if (client.player != null) {
            // Keyboard input (A/D keys)
            if (client.player.input != null) {
                net.minecraft.world.phys.Vec2 moveVector = client.player.input.getMoveVector();
                if (moveVector.x != 0) {
                    manualSteering = true;
                }
            }

            // Mouse input: compare boat yaw against what auto-steer set last tick.
            // Only external changes (player mouse drag) trigger detection.
            float currentYaw = boat.getYRot();
            if (!Float.isNaN(lastAutoSteerYaw)) {
                float yawDelta = Math.abs(Mth.wrapDegrees(currentYaw - lastAutoSteerYaw));
                if (yawDelta > YAW_CHANGE_THRESHOLD) {
                    manualSteering = true;
                }
            }
        }

        if (manualSteering) {
            lastManualInputTick = currentTick;
        }

        // Cooldown check
        if (lastManualInputTick >= 0 && currentTick - lastManualInputTick < config.steerCooldownTicks) {
            lastAutoSteerYaw = boat.getYRot();
            return new AutoSteerResult(boat.getYRot(), false);
        }

        // Fan-shaped water sampling
        float boatYaw = boat.getYRot();
        int fanAngle = config.steerFanAngle;
        int numRays = config.steerFanRays;
        int lookAhead = config.steerLookAhead;

        float maxDist = 0;
        float minDist = Float.MAX_VALUE;
        float bestYaw = boatYaw;
        float bestDist = -1;
        float bestAbsAngleDelta = Float.MAX_VALUE;

        for (int i = 0; i < numRays; i++) {
            float angleDelta = -fanAngle + (2f * fanAngle * i / (numRays - 1));
            float rayYaw = boatYaw + angleDelta;
            float dist = castWaterRay(boat, rayYaw, lookAhead);

            float absAngleDelta = Math.abs(angleDelta);
            if (dist > bestDist || (dist == bestDist && absAngleDelta < bestAbsAngleDelta)) {
                bestDist = dist;
                bestYaw = rayYaw;
                bestAbsAngleDelta = absAngleDelta;
            }
            maxDist = Math.max(maxDist, dist);
            minDist = Math.min(minDist, dist);
        }

        // Heading hold: prevent spinning on open water
        float narrowing = (maxDist - minDist) / lookAhead;
        float targetYaw;
        if (narrowing < config.steerOpenWaterThreshold) {
            // Open water — maintain current heading
            targetYaw = boatYaw;
        } else {
            targetYaw = bestYaw;
        }

        // Collision avoidance
        blocked = bestDist < config.steerStopDistance;

        // Apply steering with rate limit
        float diff = Mth.wrapDegrees(targetYaw - boatYaw);
        float maxTurnRate = (float) config.steerMaxTurnRate;
        float turn = Mth.clamp(diff, -maxTurnRate, maxTurnRate);
        float newYaw = boatYaw + turn;

        // Track what auto-steer produced, so next tick can detect external changes
        lastAutoSteerYaw = newYaw;

        return new AutoSteerResult(newYaw, blocked);
    }

    private float castWaterRay(AbstractBoat boat, float yaw, int maxDistance) {
        if (boat.level() == null) return 0;

        Vec3 boatPos = boat.position();
        double angleRad = Math.toRadians(yaw);
        double dx = -Math.sin(angleRad);
        double dz = Math.cos(angleRad);

        for (int dist = 1; dist <= maxDistance; dist++) {
            BlockPos checkPos = BlockPos.containing(
                    boatPos.x + dx * dist,
                    boatPos.y - 0.3,
                    boatPos.z + dz * dist
            );
            BlockPos belowPos = checkPos.below();

            BlockState state = boat.level().getBlockState(checkPos);
            BlockState belowState = boat.level().getBlockState(belowPos);
            if (!isNavigableWater(state, belowState)) {
                return dist - 1;
            }
        }

        return maxDistance;
    }

    private boolean isNavigableWater(BlockState state, BlockState belowState) {
        if (state.getFluidState().is(FluidTags.WATER)) {
            return true;
        }
        if (state.hasProperty(BlockStateProperties.WATERLOGGED)) {
            return state.getValue(BlockStateProperties.WATERLOGGED);
        }
        // Air is only navigable if it is water surface (air above water).
        if (state.isAir()) {
            if (belowState.getFluidState().is(FluidTags.WATER)) {
                return true;
            }
            if (belowState.hasProperty(BlockStateProperties.WATERLOGGED)) {
                return belowState.getValue(BlockStateProperties.WATERLOGGED);
            }
        }
        return false;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isBlocked() {
        return blocked;
    }

    public record AutoSteerResult(float targetYaw, boolean blocked) {
    }
}
