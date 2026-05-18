package com.abran.lightsuppression.mixin;

import com.abran.lightsuppression.LightSuppressionManager;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.chunk.LightChunkGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(targets = "ca.spottedleaf.starlight.common.light.BlockStarLightEngine")
public class ScalableLuxBlockLightMixin {

    @WrapOperation(
            method = "checkBlock",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;getLightEmission()I"),
            require = 0
    )
    private int wrapCheckBlockEmission(BlockState state, Operation<Integer> original,
            @Local(argsOnly = true, ordinal = 0) LightChunkGetter lightAccess,
            @Local(argsOnly = true, ordinal = 0) int worldX,
            @Local(argsOnly = true, ordinal = 1) int worldY,
            @Local(argsOnly = true, ordinal = 2) int worldZ) {
        int emission = original.call(state);
        if (emission <= 0) return emission;
        BlockGetter world = lightAccess.getLevel();
        if (world instanceof ServerLevel serverLevel) {
            if (LightSuppressionManager.get(serverLevel).isSuppressed(new BlockPos(worldX, worldY, worldZ))) {
                return 0;
            }
        }
        return emission;
    }

    @WrapOperation(
            method = "calculateLightValue",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;getLightEmission()I"),
            require = 0
    )
    private int wrapCalculateLightEmission(BlockState state, Operation<Integer> original,
            @Local(argsOnly = true, ordinal = 0) LightChunkGetter lightAccess,
            @Local(argsOnly = true, ordinal = 0) int worldX,
            @Local(argsOnly = true, ordinal = 1) int worldY,
            @Local(argsOnly = true, ordinal = 2) int worldZ) {
        int emission = original.call(state);
        if (emission <= 0) return emission;
        BlockGetter world = lightAccess.getLevel();
        if (world instanceof ServerLevel serverLevel) {
            if (LightSuppressionManager.get(serverLevel).isSuppressed(new BlockPos(worldX, worldY, worldZ))) {
                return 0;
            }
        }
        return emission;
    }

    @Inject(method = "getSources", at = @At("RETURN"), cancellable = true, require = 0)
    private void filterSuppressedSources(CallbackInfoReturnable<List<BlockPos>> cir,
            @Local(argsOnly = true, ordinal = 0) LightChunkGetter lightAccess) {
        BlockGetter world = lightAccess.getLevel();
        if (world instanceof ServerLevel serverLevel) {
            LightSuppressionManager manager = LightSuppressionManager.get(serverLevel);
            List<BlockPos> sources = cir.getReturnValue();
            if (sources.stream().anyMatch(manager::isSuppressed)) {
                List<BlockPos> filtered = new ArrayList<>(sources);
                filtered.removeIf(manager::isSuppressed);
                cir.setReturnValue(filtered);
            }
        }
    }
}
