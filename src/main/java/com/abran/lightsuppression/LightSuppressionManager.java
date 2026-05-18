package com.abran.lightsuppression;

import com.mojang.serialization.Codec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.util.datafix.DataFixTypes;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class LightSuppressionManager extends SavedData {
    private final Set<BlockPos> suppressedPositions = new HashSet<>();
    private boolean needsRelight = false;

    public LightSuppressionManager() {
    }

    private LightSuppressionManager(List<Long> positions) {
        for (long l : positions) {
            suppressedPositions.add(BlockPos.of(l));
        }
        if (!suppressedPositions.isEmpty()) {
            needsRelight = true;
        }
    }

    public static final Codec<LightSuppressionManager> CODEC = Codec.LONG.listOf()
            .fieldOf("suppressed")
            .codec()
            .xmap(
                    LightSuppressionManager::new,
                    manager -> manager.suppressedPositions.stream()
                            .map(BlockPos::asLong)
                            .collect(Collectors.toList())
            );

    public static final SavedDataType<LightSuppressionManager> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("light_suppression_wand", "data"),
            LightSuppressionManager::new,
            CODEC,
            DataFixTypes.LEVEL
    );

    public static LightSuppressionManager get(ServerLevel world) {
        return world.getDataStorage().computeIfAbsent(TYPE);
    }

    /**
     * @return true if now suppressed, false if now restored
     */
    public boolean toggle(BlockPos pos) {
        pos = pos.immutable();
        if (suppressedPositions.remove(pos)) {
            setDirty();
            return false;
        } else {
            suppressedPositions.add(pos);
            setDirty();
            return true;
        }
    }

    public boolean isSuppressed(BlockPos pos) {
        return suppressedPositions.contains(pos);
    }

    public void remove(BlockPos pos) {
        if (suppressedPositions.remove(pos.immutable())) {
            setDirty();
        }
    }

    public boolean needsRelight() {
        return needsRelight;
    }

    public Set<BlockPos> getSuppressedPositions() {
        return suppressedPositions;
    }

    public void clearNeedsRelight() {
        needsRelight = false;
    }
}
