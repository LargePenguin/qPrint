package com.qprint.utils;

import com.google.gson.Gson;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.block.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.screen.*;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.joml.Vector3d;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.List;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class Utilities {
    private static final Gson GSON = new Gson();
    private static final File QPRINT_CACHE = new File(MeteorClient.FOLDER, "qprint.json");

    public static void saveData(QPrintData data) {
        try (FileWriter writer = new FileWriter(QPRINT_CACHE)) {
            GSON.toJson(data, writer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static QPrintData loadData() {
        QPrintData data;
        try {
            if (QPRINT_CACHE.exists()) {
                data = GSON.fromJson(new FileReader(QPRINT_CACHE), QPrintData.class);
            } else {
                data = new QPrintData();
                saveData(data);
            }
        } catch (Exception e) {
            e.printStackTrace();
            data = new QPrintData();
        }

        return data;
    }

    public static BlockPos vecToPos(Vector3d vec) {
        return new BlockPos((int)vec.x, (int)vec.y, (int)vec.z);
    }

    public static Vector3d posToVec(BlockPos pos) {
        return new Vector3d(pos.getX(), pos.getY(), pos.getZ());
    }

    public static int getEntityId(PlayerInteractEntityC2SPacket packet) {
        try {
            Field entityIdField = PlayerInteractEntityC2SPacket.class.getDeclaredField("entityId");
            entityIdField.setAccessible(true);
            return entityIdField.getInt(packet);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
        }
        return -1; // Return -1 if the field cannot be accessed
    }

    public static boolean isContainerScreen(ScreenHandler screenHandler) {
        return screenHandler instanceof GenericContainerScreenHandler
            || screenHandler instanceof ShulkerBoxScreenHandler
            || screenHandler instanceof HopperScreenHandler
            || screenHandler instanceof Generic3x3ContainerScreenHandler
            || screenHandler instanceof FurnaceScreenHandler
            || screenHandler instanceof AbstractFurnaceScreenHandler
            || screenHandler instanceof BlastFurnaceScreenHandler
            || screenHandler instanceof SmokerScreenHandler
            || screenHandler instanceof BrewingStandScreenHandler
            || screenHandler instanceof CrafterScreenHandler;
    }

    public static boolean isNonJunkVariant(ItemStack playerItem, List<Item> itemList) {
        String itemName = playerItem.getItem().getTranslationKey().toLowerCase();

        if (itemName.contains("shulker_box")) {
            return itemList.stream().anyMatch(item -> item.getTranslationKey().contains("shulker_box"));
        } else if (itemName.contains("_pickaxe")) {
            return itemList.stream().anyMatch(item -> item == Items.DIAMOND_PICKAXE || item == Items.NETHERITE_PICKAXE);
        } else if (itemName.contains("_sword")) {
            return itemList.stream().anyMatch(item -> item == Items.DIAMOND_SWORD || item == Items.NETHERITE_SWORD);
        } else if (itemName.contains("_shovel")) {
            return itemList.stream().anyMatch(item -> item == Items.DIAMOND_SHOVEL || item == Items.NETHERITE_SHOVEL);
        } else if (itemName.contains("_axe")) {
            return itemList.stream().anyMatch(item -> item == Items.DIAMOND_AXE || item == Items.NETHERITE_AXE);
        } else if (itemName.contains("_hoe")) {
            return itemList.stream().anyMatch(item -> item == Items.DIAMOND_HOE || item == Items.NETHERITE_HOE);
        } else if (itemName.contains("_helmet")) {
            return itemList.stream().anyMatch(item -> item == Items.DIAMOND_HELMET || item == Items.NETHERITE_HELMET);
        } else if (itemName.contains("_chestplate")) {
            return itemList.stream().anyMatch(item -> item == Items.DIAMOND_CHESTPLATE || item == Items.NETHERITE_CHESTPLATE);
        } else if (itemName.contains("_leggings")) {
            return itemList.stream().anyMatch(item -> item == Items.DIAMOND_LEGGINGS || item == Items.NETHERITE_LEGGINGS);
        } else if (itemName.contains("_boots")) {
            return itemList.stream().anyMatch(item -> item == Items.DIAMOND_BOOTS || item == Items.NETHERITE_BOOTS);
        }

        return false;
    }

    public static boolean isValidContainerBlock(Block block) {
        return block instanceof ChestBlock || block instanceof BarrelBlock || block instanceof ShulkerBoxBlock || block instanceof HopperBlock || block instanceof DispenserBlock || block instanceof DropperBlock || block instanceof FurnaceBlock || block instanceof BlastFurnaceBlock || block instanceof SmokerBlock || block instanceof BrewingStandBlock || block instanceof CrafterBlock;
    }

    public static File loadSchematic(String filename) {
        File file = new File("schematics/" + filename);

        if (!file.exists()) {
            ChatUtils.error("Schematic '" + filename + "' does not exist!");
            return null;
        }

        return file;
    }

    public static String posToString(BlockPos pos) {
        return "BlockPos{x=" + pos.getX() + ", y=" + pos.getY() + ", z=" + pos.getZ() + "}";
    }

    public static int countNonAirBlocks(BlockPos pos1, BlockPos pos2) {
        var count = 0;

        int minX = Math.min(pos1.getX(), pos2.getX());
        int minY = Math.min(pos1.getY(), pos2.getY());
        int minZ = Math.min(pos1.getZ(), pos2.getZ());

        int maxX = Math.max(pos1.getX(), pos2.getX());
        int maxY = Math.max(pos1.getY(), pos2.getY());
        int maxZ = Math.max(pos1.getZ(), pos2.getZ());

        BlockPos.Mutable mutable = new BlockPos.Mutable();

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    mutable.set(x, y, z);
                    BlockState state = mc.world.getBlockState(mutable);
                    if (!state.isAir()) count++;
                }
            }
        }

        return count;
    }

    public static String formatTime(long totalSeconds) {
        // could probably use java.time.duration but whatever
        int hours = (int)(totalSeconds / 3600);
        int minutes = (int)((totalSeconds % 3600) / 60);
        int seconds = (int)(totalSeconds % 60);

        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    private static boolean isInLoadedChunk(BlockPos pos) {
        return mc.world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4);
    }

    public static boolean isPointInRegion(BlockPos pos1, BlockPos pos2, BlockPos point) {
        int minX = Math.min(pos1.getX(), pos2.getX());
        int maxX = Math.max(pos1.getX(), pos2.getX());
        int minY = Math.min(pos1.getY(), pos2.getY());
        int maxY = Math.max(pos1.getY(), pos2.getY());
        int minZ = Math.min(pos1.getZ(), pos2.getZ());
        int maxZ = Math.max(pos1.getZ(), pos2.getZ());

        return point.getX() >= minX && point.getX() <= maxX &&
            point.getY() >= minY && point.getY() <= maxY &&
            point.getZ() >= minZ && point.getZ() <= maxZ;
    }
}
