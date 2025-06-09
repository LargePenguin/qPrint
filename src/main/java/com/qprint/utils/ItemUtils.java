package com.qprint.utils;

import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class ItemUtils {
    public static Item getItemFromName(String name) {
        return Registries.ITEM.get(Identifier.of(name));
    }

    public static int getFreeSlots() {
        assert mc.player != null;
        int freeSlots = 0;
        for (var stack : mc.player.getInventory().main) {
            if (stack.isEmpty())
                freeSlots++;
        }

        return freeSlots;
    }

    public static List<Integer> getSlotsWithItem(int playerInvStart, Item item) {
        assert mc.player != null;

        var result = new ArrayList<Integer>();

        for (var i = playerInvStart; i < mc.player.currentScreenHandler.slots.size(); i++) {
            var playerStack = mc.player.currentScreenHandler.getSlot(i).getStack();
            if (!playerStack.isEmpty() && playerStack.getItem().equals(item)) {
                result.add(i);
            }
        }

        return result;
    }

    public static void scaleMaterialsList(Map<Item, Integer> mats, int threshold) {
        var total = mats.values().stream().filter(i -> i >= threshold).mapToInt(Integer::intValue).sum();
        var emptySlots = getFreeSlots() - mats.values().stream()
            .filter(i -> i < threshold)
            .mapToInt(Math::abs)
            .sum();

        if (mats.size() > emptySlots) {
            for (var entry : mats.entrySet()) {
                if (entry.getValue() < 0) {
                    entry.setValue(Math.abs(entry.getKey().getMaxCount() * entry.getValue()));
                } else if (entry.getValue() < threshold) {
                    entry.setValue(entry.getKey().getMaxCount());
                }
            }
            return; // we'd be scaling down the requested amounts; we only ever want to scale up
        }

        if (total == 0)
            return;

        // Compute relative ratios of each requested item, scaled to available slots
        for (var entry : mats.entrySet()) {
            if (entry.getValue() < 0) {
                entry.setValue(Math.abs(entry.getKey().getMaxCount() * entry.getValue()));
            } else if (entry.getValue() < threshold) {
                entry.setValue(entry.getKey().getMaxCount());
            } else {
                var ratio = entry.getValue() / (double) total;
                entry.setValue(Math.max(entry.getKey().getMaxCount(), entry.getKey().getMaxCount() * (int) Math.floor(ratio * emptySlots)));
            }
        }
    }

    public static Map<Item, List<Integer>> getSmallBlockStacks(int playerInvStart, int threshold) {
        assert mc.player != null;
        var result = new HashMap<Item, List<Integer>>();

        for (var i = playerInvStart; i < mc.player.currentScreenHandler.slots.size(); i++) {
            var stack = mc.player.currentScreenHandler.getSlot(i).getStack();
            var item = stack.getItem();

            if (stack.isEmpty()) continue;
            if (!(item instanceof BlockItem)) continue;

            if (stack.getCount() < threshold) {
                if (result.containsKey(item)) {
                    result.get(item).add(i);
                } else {
                    var list = new ArrayList<Integer>();
                    list.add(i);
                    result.put(item, list);
                }
            }
        }

        return result;
    }
}
