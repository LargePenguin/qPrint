package com.qprint.states;

import baritone.api.BaritoneAPI;
import com.qprint.modules.QuickPrintModule;
import meteordevelopment.meteorclient.events.world.TickEvent;
import net.minecraft.command.argument.EntityAnchorArgumentType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.qprint.utils.ItemUtils.*;
import static com.qprint.utils.Utilities.*;
import static meteordevelopment.meteorclient.MeteorClient.mc;

public class RestockState extends AbstractContainerState {
    public final Map<Item, Integer> missingMaterials;
    private final BlockPos playerPosPreState;

    private boolean hasMovedBack;
    private boolean regionRefreshed;

    private Item currentTargetItem;

    private int totalClicksThisTick = 0;
    private int maxClicks = 0;
    private int openTicks;

    private int slotsToFreeUp;

    private boolean initialized;
    private boolean pausePending;
    private boolean hasDumped;

    public RestockState(QuickPrintModule module, Map<Item, Integer> missingMaterials, BlockPos playerPos) {
        super(module);
        this.missingMaterials = missingMaterials;
        this.playerPosPreState = playerPos;

        updateReach();

        if (missingMaterials.entrySet().size() > getFreeSlots())
            slotsToFreeUp = missingMaterials.entrySet().size() - getFreeSlots();
    }

    @Override
    public void stateEnter() {
        if (module.doStateLogging.get())
            module.info("RestockState::StateEnter (nMats=" + missingMaterials.size() + ",returnPos=" + posToString(playerPosPreState) + ")");

        BaritoneAPI.getSettings().allowBreak.value = false;
        BaritoneAPI.getSettings().allowPlace.value = false;

        // First priority-- path to be within render distance of chest region & rebuild region's container mapping.
        if (!module.activeStorage.getP0().isWithinDistance(mc.player.getBlockPos(), 32))
            parent.push(new MoveState(module, module.activeStorage.getP0(), 32));
    }

    @Override
    public void stateExit() {
        super.stateExit();
        if (module.doStateLogging.get())
            module.info("RestockState::StateExit");
    }

    @Override
    public void statePreTick(TickEvent.Pre event) {
        if (!regionRefreshed || currentTargetContainer == null)
            return;

        if (pausePending) {
            pausePending = false;
            module.pause(true);
            return;
        }

        updateReach();

        if (!hasDumped && module.useTrash.get()) {
            hasDumped = true;
            parent.push(new DumpState(module, this, slotsToFreeUp));
            return;
        }

        if (!canReachTarget()) {
            parent.push(new MoveState(module, currentTargetContainer, (int)reach - 1));
            return;
        }

        if (checkStuck()) {
            return;
        }

        var blockState = mc.world.getBlockState(currentTargetContainer);
        var block = blockState.getBlock();

        if (isValidContainerBlock(block) && !isChestOpen) {
            if (openTicks < module.containerOpenDelay.get()) {
                openTicks++;
            } else {
                openContainer(currentTargetContainer);
                openTicks = 0;
            }
        } else if (isChestOpen && isContainerScreen(mc.player.currentScreenHandler)) {
            processChestsAfterDelay();
        }
    }

    @Override
    public void statePostTick(TickEvent.Post event) {
        if (!regionRefreshed) {
            regionRefreshed = true;
            module.activeStorage.rescan();
        }

        if (missingMaterials.isEmpty()) {
            if (!hasMovedBack) {    // return to whence we came
                hasMovedBack = true;
                parent.push(new MoveState(module, playerPosPreState, 0));
                return;
            } else {
                parent.pop(true);   // we did it!
                return;
            }
        }

        if (currentTargetContainer == null)
            updateTarget();
    }

    @Override
    protected boolean processContainerItems() {
        assert mc.player != null;

        // Get the index of the first player inventory slot
        int playerInvStart = mc.player.currentScreenHandler.slots.size() - 36;
        totalClicksThisTick = module.maxClicksPerTick.get();

        if (!initialized) {
            initialized = true;
            scaleMaterialsList(missingMaterials);
        }

        // Build a list of all the slots in the container which contain currentTargetItem
        Map<Integer, Integer> slotIndices = new HashMap<>();
        for (int i = 0; i < playerInvStart; i++) {
            Item item = mc.player.currentScreenHandler.getSlot(i).getStack().getItem();
            if (item.equals(currentTargetItem))
                slotIndices.put(i, mc.player.currentScreenHandler.getSlot(i).getStack().getCount());
        }

        // Take target items up to the defined limit.
        for (int slotIdx : slotIndices.entrySet().stream()
            .sorted(Map.Entry.<Integer, Integer>comparingByValue().reversed())
            .map(Map.Entry::getKey)
            .collect(Collectors.toList())) {
            var item = mc.player.currentScreenHandler.getSlot(slotIdx).getStack().getItem();
            int currentCount = getItemCount(item);
            if (currentCount < missingMaterials.get(currentTargetItem)) {
                if (totalClicksThisTick <= 0)
                    return false;   // still got more to do next tick
                mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, slotIdx, 0, SlotActionType.QUICK_MOVE, mc.player);
                totalClicksThisTick--;
            }
        }

        missingMaterials.remove(currentTargetItem);
        currentTargetItem = null;
        return true;
    }

    private int dumpTrash(int playerInvStart, int slotsToFreeUp) {
        assert mc.player != null && mc.interactionManager != null;

        int slotsDumped = 0;
        var slotsToTrash = module.recentItems.getOldestSlots(playerInvStart, slotsToFreeUp);

        for (var slot : slotsToTrash) {
            for (int i = 0; i < playerInvStart; i++) {
                var containerStack = mc.player.currentScreenHandler.getSlot(i).getStack();
                if (containerStack.isEmpty()) {
                    if (totalClicksThisTick >= maxClicks) {
                        return slotsDumped; // Stop moving items if we've exceeded maxClicks this tick.
                    }

                    mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, slot, 0, SlotActionType.QUICK_MOVE, mc.player);
                    totalClicksThisTick++;
                    slotsDumped++;
                    break;
                }
            }
        }

        return slotsDumped;
    }

    private void swapSmallerStacksForBigger(int playerInvStart) {
        assert mc.player != null;

        // Get a list of all non-empty slots in the player's inventory
        List<Integer> containerSlotIndices = new ArrayList<>();
        for (var i = 0; i < playerInvStart; i++) {
            var containerStack = mc.player.currentScreenHandler.getSlot(i).getStack();
            if (!containerStack.isEmpty())
                containerSlotIndices.add(i);
        }

        for (int i = playerInvStart; i < mc.player.currentScreenHandler.slots.size(); i++) {
            var playerStack = mc.player.currentScreenHandler.getSlot(i).getStack();
            if (!playerStack.isEmpty()) {
                var playerItem = playerStack.getItem();
                var playerStackSize = playerStack.getCount();

                for (int containerSlotIndex : containerSlotIndices) {
                    var containerStack = mc.player.currentScreenHandler.getSlot(containerSlotIndex).getStack();
                    var containerItem = containerStack.getItem();
                    var containerStackSize = containerStack.getCount();

                    if (playerItem == containerItem) {
                        if (containerStackSize > playerStackSize) {
                            swapItems(i, containerSlotIndex, playerInvStart);
                            break;
                        }
                    }
                }
            }
        }
    }

    private void swapItems(int playerSlotIndex, int containerSlotIndex, int playerInvStart) {
        if (mc.player != null && mc.interactionManager != null && isContainerScreen(mc.player.currentScreenHandler)) {
            // Perform the swap if we have enough clicks left
            if (totalClicksThisTick + 3 <= maxClicks) {
                // Move the entire stack to the container
                for (int j = 0; j < playerInvStart; j++) {
                    ItemStack containerStack = mc.player.currentScreenHandler.getSlot(j).getStack();
                    if (containerStack.isEmpty()) {
                        mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, playerSlotIndex, 0, SlotActionType.QUICK_MOVE, mc.player);
                        totalClicksThisTick++;

                        mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, containerSlotIndex, 0, SlotActionType.PICKUP, mc.player);
                        totalClicksThisTick++;

                        mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, playerSlotIndex, 0, SlotActionType.PICKUP, mc.player);
                        totalClicksThisTick++;
                        break; // Exit the inner loop since the stack has been moved
                    }
                }
            }
        }
    }

    private void moveItems(int slotIndex, int amount) {
        assert mc.player != null;
        assert mc.interactionManager != null;

        if (isContainerScreen(mc.player.currentScreenHandler) && slotIndex >= 0 && slotIndex < mc.player.currentScreenHandler.slots.size()) {
            var sourceStack = mc.player.currentScreenHandler.getSlot(slotIndex).getStack();

            // TODO: Uncomment if there's issues with grabbing wee little stacks
            if (!sourceStack.isEmpty() /*&& Math.round(((double)sourceStack.getCount() / sourceStack.getItem().getMaxCount()) * 100) >= minLootableStackSize*/) {
                // Figure out how many items to move from this stack & how many clicks we'll need to take them.
                amount = Math.min(amount, sourceStack.getCount());
                int clicksNeeded = (int)Math.ceil((double)amount / sourceStack.getMaxCount());

                for (int i = 0; i < clicksNeeded; i++) {
                    if (totalClicksThisTick <= 0)
                        return;

                    // Takey McStealems
                    mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, slotIndex, 0, SlotActionType.QUICK_MOVE, mc.player);
                    totalClicksThisTick--;
                }
            }
        }
    }

    private int getItemCount(Item item) {
        assert mc.player != null;
        int count = 0;

        for (var stack : mc.player.getInventory().main) {
            var stackItem = stack.getItem();
            if (stackItem == item) {
                if (stack.isStackable()) {
                    count += stack.getCount();
                } else {
                    count++;
                }
            }
        }

        // remember to account for items in the offhand
        ItemStack offhandStack = mc.player.getOffHandStack();
        Item offhandItem = offhandStack.getItem();
        if (item == offhandItem) {
            if (offhandStack.isStackable()) {
                count += offhandStack.getCount();
            } else {
                count++;
            }
        }

        return count;
    }

    private boolean canReachTarget() {
        assert mc.player != null;

        return mc.player.getBlockPos().isWithinDistance(currentTargetContainer, reach);
    }

    private void updateTarget() {
        currentTargetItem = missingMaterials.keySet().iterator().next();
        currentTargetContainer = module.activeStorage.getContainerFor(currentTargetItem.getName().getString());

        if (currentTargetContainer == null) {
            if (module.stopOnMissingMaterial.get()) {
                module.error("No containers found in the storage region containing " + currentTargetItem.getName().getString());
                parent.pop(false);
            } else {
                module.warning("No containers found in the storage region containing " + currentTargetItem.getName().getString() + ", but stopOnMissingMaterials=false. Ignoring...");
                missingMaterials.remove(currentTargetItem);
            }
        }
    }
}
