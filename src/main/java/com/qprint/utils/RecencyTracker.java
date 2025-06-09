package com.qprint.utils;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.*;

import static com.qprint.utils.ItemUtils.getSlotsWithItem;
import static meteordevelopment.meteorclient.MeteorClient.mc;

public class RecencyTracker {
    // could be a tuple but that's not a thing in java?
    // java bad >:(
    public class SetEntry {
        private transient Item item;
        public int lifetime;

        public String itemName;

        public SetEntry() {}

        public Item getItem() {
            if (this.item == null)
                this.item = Registries.ITEM.get(Identifier.of(itemName));

            return item;
        }

        public SetEntry(Item item) { this.item = item; itemName = item.getName().getString(); }
    }

    private static final int RESTOCKS_BEFORE_EXPIRE = 3;
    private LinkedHashSet<SetEntry> recentItems = new LinkedHashSet<>();

    public RecencyTracker() {
        MeteorClient.EVENT_BUS.subscribe(this);
    }

    public void loadFromData(List<SetEntry> data) { recentItems = new LinkedHashSet<>(data); }
    public List<SetEntry> getRecentItems() { return recentItems.stream().toList(); }

    public Item getOldest() {
        return recentItems.getFirst().getItem();
    }

    public List<Item> getOldest(int nItems) {
        int size = recentItems.size();
        if (nItems <= 0 || size == 0) return List.of();

        return recentItems.stream()
            .map(e -> e.getItem())
            .toList();
    }

    public List<Integer> getOldestSlots(int playerInvStart, int nSlots) {
        var result = new ArrayList<Integer>();

        for (var entry : recentItems) {
            var slots = getSlotsWithItem(playerInvStart, entry.getItem());
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

    public List<Integer> getSlotsToDump(int playerInvStart, int threshold) {
        assert mc.player != null;

        var result = new ArrayList<Integer>();

        for (var entry : recentItems) {
            entry.lifetime++;
            var slots = getSlotsWithItem(playerInvStart, entry.getItem());

            if (entry.lifetime >= RESTOCKS_BEFORE_EXPIRE) {
                result.addAll(slots);
            } else if (slots.size() > threshold) {
                result.addAll(slots.stream().limit(slots.size() - threshold).toList());
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
                    recentItems.add(new SetEntry(stack.getItem()));
            }
        }

        var mainHandStack = mc.player.getMainHandStack();
        if (!mainHandStack.isEmpty() && (mainHandStack.getItem() instanceof BlockItem)) {
            var currentItem = mainHandStack.getItem();

            recentItems.removeIf(e -> e.getItem() == currentItem);
            recentItems.add(new SetEntry(currentItem));
        }

        recentItems.removeIf(e -> !playerHasItem(e.getItem()));
    }

    private boolean playerHasItem(Item item) {
        return mc.player.getInventory().main.stream()
            .anyMatch(stack -> !stack.isEmpty() && stack.getItem() == item);
    }
}
