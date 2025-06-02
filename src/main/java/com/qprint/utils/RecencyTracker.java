package com.qprint.utils;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class RecencyTracker {
    private final LinkedHashSet<Item> recentItems = new LinkedHashSet<>();

    public RecencyTracker() {
        MeteorClient.EVENT_BUS.subscribe(this);
    }

    public Item getOldest() {
        return recentItems.getFirst();
    }

    public List<Item> getOldest(int nItems) {
        int size = recentItems.size();
        if (nItems <= 0 || size == 0) return List.of();

        return recentItems.stream().limit(nItems).toList();
    }

    public List<Integer> getOldestSlots(int playerInvStart, int nSlots) {
        var result = new ArrayList<Integer>();

        for (var item : recentItems) {
            var slots = getSlotsWithItem(playerInvStart, item);
            if (slots.size() > nSlots) {
                result.addAll(slots.stream().limit(nSlots).toList());
                nSlots = 0;
            } else {
                result.addAll(slots);
                nSlots -= slots.size();
            }

            if (nSlots == 0) {
                break;
            }
        }

        return result;
    }

    private List<Integer> getSlotsWithItem(int playerInvStart, Item item) {
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

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        if (recentItems.isEmpty()) {
            for (var stack : mc.player.getInventory().main) {
                if (!stack.isEmpty() && (stack.getItem() instanceof BlockItem))
                    recentItems.add(stack.getItem());
            }
        }

        var mainHandStack = mc.player.getMainHandStack();
        if (!mainHandStack.isEmpty() && (mainHandStack.getItem() instanceof BlockItem)) {
            var currentItem = mainHandStack.getItem();

            recentItems.remove(currentItem);
            recentItems.add(currentItem);
        }

        recentItems.removeIf(item -> !playerHasItem(item));
    }

    private boolean playerHasItem(Item item) {
        return mc.player.getInventory().main.stream()
            .anyMatch(stack -> !stack.isEmpty() && stack.getItem() == item);
    }
}
