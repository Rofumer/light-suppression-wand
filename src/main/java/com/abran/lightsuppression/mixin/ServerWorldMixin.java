package com.abran.lightsuppression.mixin;

import com.abran.lightsuppression.LightSuppressionManager;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
public abstract class ServerWorldMixin {

    @Inject(method = "sendBlockUpdated", at = @At("HEAD"))
    private void cleanupSuppression(BlockPos pos, BlockState oldState, BlockState newState, int flags, CallbackInfo ci) {
        if (oldState.getLightEmission() > 0 && newState.getLightEmission() == 0) {
            ServerLevel self = (ServerLevel) (Object) this;
            LightSuppressionManager manager = LightSuppressionManager.get(self);
            if (manager.isSuppressed(pos)) {
                manager.remove(pos);
            }
        }
    }
}
