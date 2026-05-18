package com.abran.lightsuppression;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.Items;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacket;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.core.SectionPos;

import java.util.BitSet;

public class LightSuppressionWand implements ModInitializer {
    @Override
    public void onInitialize() {
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClientSide()) return InteractionResult.PASS;
            if (!player.isShiftKeyDown()) return InteractionResult.PASS;
            if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
            if (player.getItemInHand(hand).getItem() != Items.GOLDEN_HOE) return InteractionResult.PASS;

            ServerLevel serverLevel = (ServerLevel) world;
            BlockPos pos = hitResult.getBlockPos();
            BlockState state = world.getBlockState(pos);

            LightSuppressionManager manager = LightSuppressionManager.get(serverLevel);

            // Allow toggle if block emits light OR is already suppressed
            if (state.getLightEmission() == 0 && !manager.isSuppressed(pos)) {
                return InteractionResult.PASS;
            }

            boolean suppressed = manager.toggle(pos);

            // Force light recalculation and sync to clients
            serverLevel.getChunkSource().getLightEngine().checkBlock(pos);
            sendLightUpdate(serverLevel, pos);

            // Feedback
            if (suppressed) {
                player.sendOverlayMessage(Component.literal("Light Suppressed!").withStyle(ChatFormatting.GOLD));
                serverLevel.playSound(null, pos, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1.0f, 1.0f);
            } else {
                player.sendOverlayMessage(Component.literal("Light Restored!").withStyle(ChatFormatting.GREEN));
                serverLevel.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.0f, 1.0f);
            }
            serverLevel.sendParticles(ParticleTypes.END_ROD,
                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    10, 0.3, 0.3, 0.3, 0.05);

            return InteractionResult.SUCCESS;
        });

        // Re-apply suppressions after world load (handles chunk lighting on restart)
        ServerTickEvents.END_LEVEL_TICK.register(world -> {
            LightSuppressionManager manager = LightSuppressionManager.get(world);
            if (manager.needsRelight()) {
                for (BlockPos pos : manager.getSuppressedPositions()) {
                    world.getChunkSource().getLightEngine().checkBlock(pos);
                    sendLightUpdate(world, pos);
                }
                manager.clearNeedsRelight();
            }
        });
    }

    /**
     * After the light engine finishes recalculating, send a ClientboundLightUpdatePacket
     * to all players tracking the chunk. Uses ThreadedLevelLightEngine.waitForPendingTasks()
     * to wait for the async light engine to finish processing.
     */
    private static void sendLightUpdate(ServerLevel world, BlockPos pos) {
        ThreadedLevelLightEngine lightEngine =
                (ThreadedLevelLightEngine) world.getChunkSource().getLightEngine();
        ChunkPos centerChunk = new ChunkPos(pos.getX() >> 4, pos.getZ() >> 4);

        lightEngine.waitForPendingTasks(centerChunk.x(), centerChunk.z()).thenRun(() -> {
            world.getServer().execute(() -> {
                int bottomSection = world.getMinSectionY();
                int blockSection = SectionPos.blockToSectionCoord(pos.getY());

                BitSet blockLightBits = new BitSet();
                for (int dy = -1; dy <= 1; dy++) {
                    int idx = blockSection + dy - bottomSection;
                    if (idx >= 0) {
                        blockLightBits.set(idx);
                    }
                }

                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        ChunkPos chunkPos = new ChunkPos(centerChunk.x() + dx, centerChunk.z() + dz);
                        ClientboundLightUpdatePacket packet = new ClientboundLightUpdatePacket(
                                chunkPos, lightEngine, null, blockLightBits
                        );
                        BlockPos chunkCenter = new BlockPos(
                                chunkPos.getMinBlockX() + 8, pos.getY(), chunkPos.getMinBlockZ() + 8
                        );
                        for (ServerPlayer tracking : PlayerLookup.tracking(world, chunkCenter)) {
                            tracking.connection.send(packet);
                        }
                    }
                }
            });
        });
    }
}
