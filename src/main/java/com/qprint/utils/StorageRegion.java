package com.qprint.utils;

import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.HashMap;
import java.util.Map;

import static com.qprint.utils.Utilities.isValidContainerBlock;
import static meteordevelopment.meteorclient.MeteorClient.mc;

public class StorageRegion {
    private BlockPos p0;
    private BlockPos p1;

    private final String name;
    private final int minX, minY, minZ, maxX, maxY, maxZ;
    private final Map<String, BlockPos> containerMap = new HashMap<>();
    private BlockPos trashLocation;

    public StorageRegion(String name, BlockPos p0, BlockPos p1) {
        this.p0 = p0;
        this.p1 = p1;
        this.name = name;

        minX = Math.min(p0.getX(), p1.getX());
        maxX = Math.max(p0.getX(), p1.getX()) + 1;

        minY = Math.min(p0.getY(), p1.getY());
        maxY = Math.max(p0.getY(), p1.getY()) + 1;

        minZ = Math.min(p0.getZ(), p1.getZ());
        maxZ = Math.max(p0.getZ(), p1.getZ()) + 1;
    }

    public BlockPos getP0() {
        return p0;
    }

    public BlockPos getP1() {
        return p1;
    }

    public int getMinX() {
        return minX;
    }

    public int getMinY() {
        return minY;
    }

    public int getMinZ() {
        return minZ;
    }

    public int getMaxX() {
        return maxX;
    }

    public int getMaxY() {
        return maxY;
    }

    public int getMaxZ() {
        return maxZ;
    }

    public int getContainerCount() {
        return containerMap.size();
    }

    public void setP0(BlockPos pos) {
        p0 = pos;
        containerMap.clear();
    }

    public void setP1(BlockPos pos) {
        p1 = pos;
        containerMap.clear();
    }

    public BlockPos getTrash() {
        return trashLocation;
    }

    public BlockPos getContainerFor(String itemName) {
        if (containerMap.isEmpty())
            rescan();

        return containerMap.get(itemName);
    }

    public void rescan() {
        containerMap.clear();

        for (Entity entity : mc.world.getEntities()) {
            if (entity instanceof ItemFrameEntity frame && isWithinRegion(frame.getBlockPos())) {
                Direction facing = frame.getHorizontalFacing();
                BlockPos attachedPos = frame.getBlockPos().offset(facing.getOpposite());
                Block attachedBlock = mc.world.getBlockState(attachedPos).getBlock();

                if (isValidContainerBlock(attachedBlock)) {
                    ItemStack stack = frame.getHeldItemStack();

                    if (stack.isEmpty())
                        continue;

                    if (stack.getItem().getName().getString().contains("Cactus"))
                        trashLocation = attachedPos;
                    else
                        containerMap.put(stack.getItem().getName().getString(), attachedPos);
                }
            }
        }
    }

    public boolean isWithinRegion(BlockPos pos) {
        return pos.getX() >= minX && pos.getX() <= maxX &&
            pos.getY() >= minY && pos.getY() <= maxY &&
            pos.getZ() >= minZ && pos.getZ() <= maxZ;
    }

    public void printContents() {
        if (containerMap.isEmpty())
            rescan();

        StringBuilder sb = new StringBuilder(toString() + ":");
        for (Map.Entry<String, BlockPos> kvp : containerMap.entrySet()) {
            sb.append("\n\t" + kvp.getKey() + " @ " + kvp.getValue().toShortString());
        }

        ChatUtils.infoPrefix("StorageRegion", sb.toString());
    }

    @Override
    public String toString() {
        return "Region '" + name + "' (" + p0 + ") (" + p1 + ")";
    }
}
