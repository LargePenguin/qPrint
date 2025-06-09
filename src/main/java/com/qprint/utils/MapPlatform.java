package com.qprint.utils;

import net.minecraft.util.math.BlockPos;

import java.util.Arrays;
import java.util.Random;

import static com.qprint.utils.Utilities.countNonAirBlocks;
import static com.qprint.utils.Utilities.isPointInRegion;
import static meteordevelopment.meteorclient.MeteorClient.mc;

public class MapPlatform {
    private BlockPos[][] platformCorners;
    private BlockPos platformOrigin;
    private final int[] blocksPerCorner = new int[4];
    private final int[] deltaBlocks = new int[4];
    private int lastDelta;
    private long lastTicks;
    private boolean initialized;

    private static final Random random = new Random();

    private static final double DEF_BPT = 0.1;

    public void updateBounds(BlockPos newPlatformOrigin) {
        platformOrigin = newPlatformOrigin;
        platformCorners = new BlockPos[][] {
            new BlockPos[] {newPlatformOrigin, newPlatformOrigin.add(63, 0, 63)},
            new BlockPos[] {newPlatformOrigin.add(64, 0, 0), newPlatformOrigin.add(127, 0, 63)},
            new BlockPos[] {newPlatformOrigin.add(0, 0, 64), newPlatformOrigin.add(63, 0, 127)},
            new BlockPos[] {newPlatformOrigin.add(64, 0, 64), newPlatformOrigin.add(127, 0, 127)}
        };
    }

    public BlockPos getPlatformCenter() { return platformOrigin.add(63, 0, 63); }

    public BlockPos getCornerCenter(int corner) { return platformCorners[corner][0].add(31, 0, 31); }

    public BlockPos getRandomPos() { return platformOrigin.add(random.nextInt(127), 0, random.nextInt(127)); }

    public BlockPos getRandomCornerPos(int corner) { return platformCorners[corner][0].add(random.nextInt(63), 0, random.nextInt(63)); }

    public int getCurrentCorner() {
        assert mc.player != null;

        for (var i = 0; i < platformCorners.length; i++) {
            if (isPointInRegion(platformCorners[i][0], platformCorners[i][1], mc.player.getBlockPos()))
                return i;
        }

        return -1;
    }

    public boolean hasInitialized() { return initialized; }

    public void reset() {
        for (var i = 0; i < 4; i++) {
            blocksPerCorner[i] = 0;
            deltaBlocks[i] = 0;
        }

        initialized = false;
        lastDelta = 0;
        lastTicks = (int)Math.round((128 * 128) / DEF_BPT);
    }

    public void tick() {
        var corner = getCurrentCorner();

        if (corner == -1) return;

        var currentBlocks = countNonAirBlocks(platformCorners[corner][0], platformCorners[corner][1]);
        deltaBlocks[corner] += currentBlocks - blocksPerCorner[corner];
        blocksPerCorner[corner] += currentBlocks - blocksPerCorner[corner];
    }

    public long getTicksRemaining(long ticksPassed) {
        var bpt = (double)lastDelta / ticksPassed;
        if (bpt <= 0) return lastTicks - ticksPassed;

        lastTicks = (long)Math.ceil(((128 * 128) - Arrays.stream(blocksPerCorner).sum()) / bpt);

        return lastTicks;
    }

    public int getLastDelta() { return lastDelta; }

    public double reportProgress() {
        tick();

        var totalBlocks = Arrays.stream(blocksPerCorner).sum();
        lastDelta = Math.max(Arrays.stream(deltaBlocks).sum(), 0);

        for (var i = 0; i < 4; i++) deltaBlocks[i] = 0;

        return 100 * ((double)totalBlocks / (128 * 128));
    }
}
