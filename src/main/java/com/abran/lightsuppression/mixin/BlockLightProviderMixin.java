package com.abran.lightsuppression.mixin;

import com.abran.lightsuppression.LightSuppressionManager;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.lighting.BlockLightEngine;
import net.minecraft.world.level.lighting.LightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockLightEngine.class)
@SuppressWarnings("rawtypes")
public abstract class BlockLightProviderMixin extends LightEngine {

    protected BlockLightProviderMixin() {
        super(null, null);
    }

    @Inject(method = "getEmission", at = @At("RETURN"), cancellable = true)
    private void suppressLight(long blockPos, BlockState blockState, CallbackInfoReturnable<Integer> cir) {
        if (cir.getReturnValueI() <= 0) return;

        BlockGetter world = this.chunkSource.getLevel();
        if (world instanceof ServerLevel serverLevel) {
            if (LightSuppressionManager.get(serverLevel).isSuppressed(BlockPos.of(blockPos))) {
                cir.setReturnValue(0);
            }
        }
    }
}
