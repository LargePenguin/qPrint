package com.qprint.states;

import com.qprint.modules.QuickPrintModule;
import meteordevelopment.meteorclient.events.world.TickEvent;
import net.minecraft.command.argument.EntityAnchorArgumentType;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;

import static com.qprint.utils.Utilities.isContainerScreen;
import static com.qprint.utils.Utilities.vecToPos;
import static meteordevelopment.meteorclient.MeteorClient.mc;

public abstract class AbstractContainerState extends AbstractState {
    protected static final int OPEN_TICK_STUCK_DELAY = 5;

    protected final Map<BlockPos, Integer> containersToProcess = new HashMap<>();

    protected boolean isChestOpen = false;
    protected int openedTicksPassed;
    protected double reach;
    protected int currentStuckRetry = 0;
    protected float originalYaw;
    protected float originalPitch;

    protected BlockPos currentTargetContainer;

    public AbstractContainerState(QuickPrintModule module) {
        super(module);
    }

    protected abstract boolean processContainerItems();

    protected boolean checkStuck() {
        if (isChestOpen && mc.player.currentScreenHandler != null && !isContainerScreen(mc.player.currentScreenHandler)) {
            if (openedTicksPassed > 0) {
                openedTicksPassed--;
            } else {
                // Likely stuck-- we should have a chest open, but we don't.
                isChestOpen = false;
                currentStuckRetry++;
                containersToProcess.remove(currentTargetContainer);
                openedTicksPassed = OPEN_TICK_STUCK_DELAY;

                module.error("Failed to open container! Attempting resolution (" + currentStuckRetry + "/" + module.maxStuckResolutions.get() + ")...");

                if (Math.floor(reach) - currentStuckRetry > 0) {    // Method 1: try getting closer to the container
                    parent.push(new MoveState(module, currentTargetContainer, (int) Math.floor(reach) - currentStuckRetry));
                    return true;
                } else {    // Method 2: pathfind back to platformOrigin to see if we can approach from a different angle
                    parent.push(new MoveState(module, vecToPos(module.platformOrigin.get()), 0));
                    return true;
                }
            }
        }

        return false;
    }

    protected void updateReach() {
        reach = module.reachMode.get() == QuickPrintModule.ReachModes.Sphere ? module.sphereReachRange.get() : module.boxReachRange.get();
    }

    protected void openContainer(BlockPos blockPos) {
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

    protected void processChestsAfterDelay() {
        assert mc.player != null;

        containersToProcess.entrySet().removeIf(e -> {
            var delay = e.getValue();
            // have we kept the container open long enough?
            if (delay <= 0) {
                if (mc.player.currentScreenHandler != null && isContainerScreen(mc.player.currentScreenHandler)) {
                    if (processContainerItems()) {
                        mc.player.closeHandledScreen(); // close the container

                        // restore orientation
                        if (module.rotateToFaceContainer.get()) {
                            mc.player.setYaw(originalYaw);
                            mc.player.setPitch(originalPitch);
                        }
                        isChestOpen = false;
                        currentTargetContainer = null;

                        currentStuckRetry = 0;

                        // all done
                        return true;
                    }
                }

                // still have shit to do
                return false;
            } else {    // decrease delay
                e.setValue(delay - 1);
                return false;
            }
        });
    }
}
