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

    public static void scaleMaterialsList(Map<Item, Integer> mats) {
        var total = mats.values().stream().mapToInt(Integer::intValue).sum();
        var emptySlots = getFreeSlots();

        if (mats.size() > emptySlots)
            return; // we'd be scaling down the requested amounts; we only ever want to scale up

        if (total == 0)
            return;

        // Compute relative ratios of each requested item, scaled to available slots
        for (var entry : mats.entrySet()) {
            var ratio = entry.getValue() / (double)total;
            entry.setValue(Math.max(64, 64 * (int)Math.floor(ratio * emptySlots)));
        }
    }

    public static List<Item> getPartialBlockStacks() {
        assert mc.player != null;
        var result = new ArrayList<Item>();

        for (var stack : mc.player.getInventory().main) {
            if (stack.isEmpty())
                continue;

            var item = stack.getItem();

            if (!(item instanceof BlockItem))
                continue;

            if (stack.getCount() < stack.getMaxCount() && !result.contains(item)) {
                result.add(item);
            }
        }

        return result;
    }
}
