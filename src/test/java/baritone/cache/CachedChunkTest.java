/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package baritone.cache;

import baritone.api.utils.BlockUtils;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.Test;

import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class CachedChunkTest {

    @Test
    public void specialBlocksUseRelativeIndexWithNegativeMinY() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        int minY = -64;
        int height = 384;
        BlockState[] overview = new BlockState[256];
        Arrays.fill(overview, Blocks.AIR.defaultBlockState());
        String chest = BlockUtils.blockToString(Blocks.CHEST);
        Map<String, List<BlockPos>> specialBlocks = Collections.singletonMap(
                chest,
                Collections.singletonList(new BlockPos(3, minY + 1, 5))
        );
        CachedChunk chunk = new CachedChunk(
                7, -2, minY, height, new BitSet(CachedChunk.size(height)), overview, specialBlocks, 0L
        );

        assertEquals(Blocks.CHEST, chunk.getBlock(3, 1, 5, null, null).getBlock());
        assertEquals(new BlockPos(7 * 16 + 3, minY + 1, -2 * 16 + 5), chunk.getAbsoluteBlocks(chest).get(0));
    }
}
