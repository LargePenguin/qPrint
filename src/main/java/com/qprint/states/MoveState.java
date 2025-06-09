package com.qprint.states;

import baritone.api.BaritoneAPI;
import baritone.api.pathing.goals.GoalGetToBlock;
import baritone.api.pathing.goals.GoalNear;
import com.qprint.modules.QuickPrintModule;
import meteordevelopment.meteorclient.events.world.TickEvent;
import net.minecraft.util.math.BlockPos;

import static com.qprint.utils.MessageUtils.hasPausedMessage;
import static com.qprint.utils.Utilities.posToString;
import static meteordevelopment.meteorclient.MeteorClient.mc;

public class MoveState extends AbstractState {
    private final BlockPos target;
    private boolean wasPathing, userPause;
    private int maxRange;
    private boolean shouldFail;

    public MoveState(QuickPrintModule module, BlockPos target, int maxRange, boolean failIfCannotReach) {
        super(module);
        this.target = target;
        this.maxRange = maxRange;
        shouldFail = failIfCannotReach;
    }

    @Override
    public void stateEnter() {
        if (module.doStateLogging.get())
            module.info("MoveState::StateEnter (goal=" + posToString(target) + ",r=" + maxRange + ",f=" + shouldFail +")");

        BaritoneAPI.getSettings().allowBreak.value = false;
        BaritoneAPI.getSettings().allowPlace.value = false;

        runCommand();
    }

    @Override
    public void onPaused() {
        super.onPaused();
        stop();
    }

    @Override
    public void onUnpaused() {
        runCommand();
    }

    @Override
    public void stateExit() {
        super.stateExit();
        if (module.doStateLogging.get())
            module.info("MoveState::StateExit");
    }

    @Override
    public void statePostTick(TickEvent.Post event) {
        var baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
        var pathing = baritone.getPathingBehavior();
        var isPathing = pathing.isPathing();

        if (wasPathing && !isPathing) {
            if (isAtTarget()) {
                parent.pop(true);   // success!
            }

            // Did the user request a pause?
            if (hasPausedMessage()) {
                userPause = true;
            }
        } else if (!wasPathing && !isPathing && !userPause) {
            if (isAtTarget()) {
                parent.pop(true);
                return;
            }
        } else if (isPathing) {
            userPause = false;
        }

        wasPathing = isPathing;
    }

    @Override
    public void onBecameStuck() {
        if (isAtTarget()) {
            parent.pop(true);
            return;
        }

        if (!shouldFail) {
            parent.pop(true);
            return;
        }

        module.error("Baritone pathfinding timed out.");
        parent.pop(false);
        noTick = true;
    }

    private boolean isAtTarget() {
        return mc.player.getBlockPos().isWithinDistance(target, maxRange + 1);
    }

    private void stop() {
        BaritoneAPI
            .getProvider()
            .getPrimaryBaritone()
            .getCustomGoalProcess()
            .setGoal(null);
    }

    private void runCommand() {
        var baritoneProcess = BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess();

        if (maxRange > 0)
            baritoneProcess.setGoalAndPath(new GoalNear(target, maxRange));
        else
            baritoneProcess.setGoalAndPath(new GoalGetToBlock(target));
    }
}
