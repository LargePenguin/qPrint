package com.qprint.states;

import com.qprint.modules.QuickPrintModule;
import meteordevelopment.meteorclient.events.world.TickEvent;
import net.minecraft.command.argument.EntityAnchorArgumentType;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

import static com.qprint.utils.ItemUtils.getSmallBlockStacks;
import static com.qprint.utils.Utilities.*;
import static meteordevelopment.meteorclient.MeteorClient.mc;

public class DumpState extends AbstractContainerState {
    private RestockState parentState;
    private final List<Integer> dumpStacks = new ArrayList<>();
    private int nSlots;
    private int curIdx = 0;

    private boolean initialized = false;

    public DumpState(QuickPrintModule module, RestockState parentState, int nSlots) {
        super(module);
        this.parentState = parentState;
        this.nSlots = nSlots;
        currentTargetContainer = module.activeStorage.getTrash();
        updateReach();
    }

    @Override
    public void stateResume() {
        super.stateResume();
    }

    @Override
    public void stateEnter() {
        if (module.doStateLogging.get())
            module.info("DumpState::StateEnter (nSlots=" + nSlots + ")");

        // Move to the trash chest
        if (!currentTargetContainer.isWithinDistance(mc.player.getBlockPos(), reach)) {
            parent.push(new MoveState(module, currentTargetContainer, (int)reach - 1));
        }
    }

    @Override
    public void statePreTick(TickEvent.Pre event) {
        if (currentTargetContainer == null) {
            parent.pop(true);
            return;
        }
        var blockState = mc.world.getBlockState(currentTargetContainer);
        var block = blockState.getBlock();

        updateReach();

        if (checkStuck()) return;

        if (isValidContainerBlock(block) && !isChestOpen) {
            openContainer(currentTargetContainer);
        } else if (isChestOpen && isContainerScreen(mc.player.currentScreenHandler)) {
            processChestsAfterDelay();
        }
    }

    @Override
    public void statePostTick(TickEvent.Post event) {
        super.statePostTick(event);
    }

    @Override
    public void stateExit() {
        super.stateExit();
        if (module.doStateLogging.get())
            module.info("DumpState::StateExit");
    }

    @Override
    public void onBecameStuck() {
        super.onBecameStuck();
    }

    @Override
    public void onPaused() {
        super.onPaused();
    }

    @Override
    public void onUnpaused() {
        super.onUnpaused();
    }

    @Override
    protected boolean processContainerItems() {
        assert mc.player != null;

        // Get the index of the first player inventory slot
        int playerInvStart = mc.player.currentScreenHandler.slots.size() - 36;
        var clicks = module.maxClicksPerTick.get();

        if (!initialized) {
            calculateDumpStacks(playerInvStart);
        }

        // if we still have clicks available this tick & stuff to dump, dump things until one of these conditions is no longer met
        while (clicks > 0) {
            if (curIdx == dumpStacks.size())
                return true;    // nothing left to dump

            for (int i = 0; i < playerInvStart; i++) {
                var containerStack = mc.player.currentScreenHandler.getSlot(i).getStack();
                if (containerStack.isEmpty()) {
                    mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, dumpStacks.get(curIdx), 0, SlotActionType.QUICK_MOVE, mc.player);
                    clicks--;
                    curIdx++;
                    break;
                }

                if (i == playerInvStart - 1) {
                    module.error("No empty slots in the dump chest! Pausing so you can take out the trash.");
                    module.pause(true);
                    return false;
                }
            }
        }

        return false;   // out of clicks but still have stuff to dump
    }

    private void calculateDumpStacks(int playerInvStart) {
        assert mc.player != null;

        // dump any items with > the defined threshold of stacks
        var excessStacks = module.recentItems.getExcessStackSlots(playerInvStart, module.excessMaterialsThreshold.get());
        dumpStacks.addAll(excessStacks);
        nSlots -= excessStacks.size();

        // get all stacks with size < defined threshold
        // for each item, add the found stacks to dumpStacks and add a full stack of the item to missingMaterials
        if (module.swapStacks.get()) {
            var swapStacks = getSmallBlockStacks(playerInvStart, module.swapStackThreshold.get());

            for (var entry : swapStacks.entrySet()) {
                if (entry.getValue().isEmpty()) continue;

                dumpStacks.addAll(entry.getValue());
                if (parentState.missingMaterials.containsKey(entry.getKey())) {
                    var count = parentState.missingMaterials.get(entry.getKey());
                    count += -entry.getValue().size();
                    parentState.missingMaterials.put(entry.getKey(), count);
                } else {
                    parentState.missingMaterials.put(entry.getKey(), -entry.getValue().size());
                }
            }
        }

        // if we still have to free up slots, pick the items to dump
        if (nSlots > 0) {
            dumpStacks.addAll(module.recentItems.getOldestSlots(playerInvStart, nSlots));
            nSlots = 0;
        }
    }
}
