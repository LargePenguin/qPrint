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

import static com.qprint.utils.ItemUtils.*;
import static com.qprint.utils.Utilities.*;
import static meteordevelopment.meteorclient.MeteorClient.mc;

public class RestockState extends AbstractState{
    private static final int OPEN_TICK_STUCK_DELAY = 5;
    private final Map<Item, Integer> missingMaterials;
    private final BlockPos playerPosPreState;
    private final Map<BlockPos, Integer> containersToProcess = new HashMap<>();

    private boolean hasMovedBack;
    private boolean regionRefreshed;

    private BlockPos currentTargetContainer;
    private Item currentTargetItem;

    private boolean isChestOpen = false;
    private int totalClicksThisTick = 0;
    private int maxClicks = 0;
    private int openTicks;
    private int autoStealTicks;
    private double reach;
    private float originalYaw;
    private float originalPitch;

    private int slotsToFreeUp = 0;
    private int currentStuckRetry = 0;
    private boolean pausePending;
    private int openedTicksPassed;

    public RestockState(QuickPrintModule module, Map<Item, Integer> missingMaterials, BlockPos playerPos) {
        super(module);
        this.missingMaterials = missingMaterials;
        this.playerPosPreState = playerPos;

        // scale up requested material amounts so that we're never leaving without a full inventory
        // TODO: implement a better heuristic for this. could scan the schematic to figure out relative ratios of remaining blocks to place
        if (missingMaterials.entrySet().size() > getFreeSlots())
            if (module.useTrash.get()) {
                slotsToFreeUp = missingMaterials.entrySet().size() - getFreeSlots();
            } else {
                module.warning("Not enough free slots! Please dump some unneeded items or enable the \"Use Trash\" option in settings.");
                pausePending = true;
            }
        else
            scaleMaterialsList(this.missingMaterials);

        // add partial stacks AFTER scaling material list to avoid picking up any additional stacks of these items
        // set target amount to 0 items so that only stack swapping logic will be performed
        if (module.swapStacks.get()) {
            for (var partialStack : getPartialBlockStacks()) {
                if (!missingMaterials.keySet().contains(partialStack)) {
                    missingMaterials.put(partialStack, 0);
                }
            }
        }
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

        if (!canReachTarget()) {
            parent.push(new MoveState(module, currentTargetContainer, (int)reach - 1));
            return;
        }

        // reset autoStealTicks when we're not in a container
        if (!isContainerScreen(mc.player.currentScreenHandler))
            autoStealTicks = 0;

        if (isChestOpen && mc.player.currentScreenHandler != null && !isContainerScreen(mc.player.currentScreenHandler)) {
            if (openedTicksPassed > 0) {
                openedTicksPassed--;
            } else {
                // Likely stuck-- we should have a chest open, but we don't.
                // Try moving a bit closer and opening the container again.
                isChestOpen = false;
                containersToProcess.remove(currentTargetContainer);
                currentStuckRetry++;

                openedTicksPassed = OPEN_TICK_STUCK_DELAY;

                module.error("Failed to open container! Attempting resolution (" + currentStuckRetry + "/" + module.maxStuckResolutions.get() + ")...");

                if (Math.floor(reach) - currentStuckRetry > 0) {    // Method 1: try getting closer to the container
                    parent.push(new MoveState(module, currentTargetContainer, (int) Math.floor(reach) - currentStuckRetry));
                    return;
                } else {    // Method 2: pathfind back to platformOrigin to see if we can approach from a different angle
                    parent.push(new MoveState(module, vecToPos(module.platformOrigin.get()), 0));
                    return;
                }
            }
        }

        if (currentTargetContainer != null) {
            var blockState = mc.world.getBlockState(currentTargetContainer);
            var block = blockState.getBlock();

            // Check if block is a container & the container screen is open
            if (isValidContainerBlock(block)) {
                if (mc.player.currentScreenHandler != null && isContainerScreen(mc.player.currentScreenHandler)) {
                    // TODO: Won't this result in the container being processed in 2 concurrent ticks?
                    if (autoStealTicks == 0) {
                        processContainerItems();
                    }

                    if (autoStealTicks < module.autoStealDelay.get()) {
                        autoStealTicks++;
                    }
                    else if (autoStealTicks >= module.autoStealDelay.get()) {
                        processContainerItems();
                        autoStealTicks = 0;
                    }
                }
            }

            if (openTicks < module.containerOpenDelay.get())
                openTicks++;

            if (mc.interactionManager == null)
                return;

            // Check if we can open a new container
            if (openTicks >= module.containerOpenDelay.get()) {
                if (isValidContainerBlock(block) && !isChestOpen) {
                    openContainer(currentTargetContainer);
                }

                openTicks = 0;
            }
        }

        processChestsAfterDelay();
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

    private void processContainerItems() {
        if (mc.player == null)
            return;

        // Get the index of the first player inventory slot
        int playerInvStart = mc.player.currentScreenHandler.slots.size() - 36;
        maxClicks = module.maxClicksPerTick.get();

        if (currentTargetItem == null) {
            if (!module.useTrash.get() || currentTargetContainer != module.activeStorage.getTrash())
                return;

            if (slotsToFreeUp <= 0) {
                totalClicksThisTick = 0;
                return;
            }

            if (isContainerScreen(mc.player.currentScreenHandler)) {
                var dumpedThisTick = dumpTrash(playerInvStart, slotsToFreeUp);
                slotsToFreeUp -= dumpedThisTick;
                totalClicksThisTick = 0;
                return;
            }
        }

        // Build a list of all the slots in the container which contain currentTargetItem
        List<Integer> slotIndices = new ArrayList<>();
        for (int i = 0; i < mc.player.currentScreenHandler.slots.size(); i++) {
            Item item = mc.player.currentScreenHandler.getSlot(i).getStack().getItem();
            if (item.equals(currentTargetItem))
                slotIndices.add(i);
        }

        // Take target items up to the defined limit.
        for (int slotIdx : slotIndices) {
            var item = mc.player.currentScreenHandler.getSlot(slotIdx).getStack().getItem();
            int currentCount = getItemCount(item);
            if (currentCount < missingMaterials.get(currentTargetItem)) {
                int amountToMove = missingMaterials.get(currentTargetItem) - currentCount;
                moveItems(slotIdx, amountToMove);
            }
        }

        // Swap out items for larger stacks
        if (module.swapStacks.get() && isContainerScreen(mc.player.currentScreenHandler)) {
            swapSmallerStacksForBigger(playerInvStart);
        }

        totalClicksThisTick = 0;
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
                    if (totalClicksThisTick >= maxClicks)
                        return;

                    // Takey McStealems
                    mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, slotIndex, 0, SlotActionType.QUICK_MOVE, mc.player);
                    totalClicksThisTick++;
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

    private void processChestsAfterDelay() {
        assert mc.player != null;

        containersToProcess.entrySet().removeIf(e -> {
            var delay = e.getValue();
            // have we kept the container open long enough?
            if (delay <= 0) {
                if (mc.player.currentScreenHandler != null && isContainerScreen(mc.player.currentScreenHandler)) {
                    processContainerItems();    // steal some shit
                    mc.player.closeHandledScreen(); // close the container

                    // restore orientation
                    if (module.rotateToFaceContainer.get()) {
                        mc.player.setYaw(originalYaw);
                        mc.player.setPitch(originalPitch);
                    }
                    isChestOpen = false;
                    missingMaterials.remove(currentTargetItem);
                    currentTargetContainer = null;
                    currentTargetItem = null;

                    currentStuckRetry = 0;
                }

                return true;
            } else {    // decrease delay
                e.setValue(delay - 1);
                return false;
            }
        });
    }

    private void openContainer(BlockPos blockPos) {
        assert mc.player != null;

        // Face the container (if configured to do so)
        if (module.rotateToFaceContainer.get()) {
            originalYaw = mc.player.getYaw();
            originalPitch = mc.player.getPitch();
            mc.player.lookAt(EntityAnchorArgumentType.EntityAnchor.EYES, new Vec3d(blockPos.getX(), blockPos.getY(), blockPos.getZ()));
        }

        // Perform the hand swing (if configured to do so)
        if (module.doHandSwing.get()) {
            mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
            mc.player.swingHand(Hand.MAIN_HAND);
        }

        // Open the container
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, new BlockHitResult(blockPos.toCenterPos(), Direction.UP, blockPos, true));
        isChestOpen = true;
        containersToProcess.put(blockPos, module.remainOpenDelay.get());
        openedTicksPassed = OPEN_TICK_STUCK_DELAY;
    }

    private void updateReach() {
        reach = module.reachMode.get() == QuickPrintModule.ReachModes.Sphere ? module.sphereReachRange.get() : module.boxReachRange.get();
    }

    private void updateTarget() {
        if (slotsToFreeUp > 0) {
            if (module.activeStorage.getTrash() == null) {
                module.error("\"Use Trash\" is enabled, but storage region does not contain a trash chest. Use a cactus to denote a trash chest.");
                slotsToFreeUp = 0;
            } else {
                currentTargetItem = null;
                currentTargetContainer = module.activeStorage.getTrash();
                return;
            }
        }

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
