package com.qprint.states;

import com.qprint.modules.QuickPrintModule;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.util.math.BlockPos;

import static com.qprint.states.StateMachine.PAUSE_BUFFER_TICKS;
import static com.qprint.utils.MessageUtils.hasPausedMessage;
import static com.qprint.utils.MessageUtils.hasResumedMessage;
import static meteordevelopment.meteorclient.MeteorClient.mc;

public abstract class AbstractState {
    protected QuickPrintModule module;
    public StateMachine parent;
    protected boolean userPause = false;
    protected boolean isStuck = false;
    protected boolean noTick = false;

    private boolean interactThisTick, resumedThisTick;
    private int stuckTicks = 0;
    private BlockPos lastPos;

    private boolean parentWasPaused;

    public AbstractState(QuickPrintModule module) {
        this.module = module;

        MeteorClient.EVENT_BUS.subscribe(this);
    }

    public void stateResume() {}

    public void stateEnter() {}

    public void statePreTick(TickEvent.Pre event) {}

    public void statePostTick(TickEvent.Post event) {}

    public void stateExit() {
        MeteorClient.EVENT_BUS.unsubscribe(this);
    }

    public void onBecameStuck() {}

    public void onPaused() {
        isStuck = false;
        stuckTicks = 0;
    }

    public void onUnpaused() {}

    @EventHandler
    private void onTickPre(TickEvent.Pre event) {
        if (!parent.isActiveState(this))
            return;

        if (parent.isPaused && !parentWasPaused) {
            userPause = true;
            onPaused();
        }
        else if (!parent.isPaused && parentWasPaused) {
            userPause = false;
            resumedThisTick = true;
            onUnpaused();
        }
        else if (!parent.isPaused && !userPause && !noTick) {
            if (hasPausedMessage() && !resumedThisTick) {
                userPause = true;
                onPaused();
                return;
            }

            updateStuck();

            statePreTick(event);
            resumedThisTick = false;
        }
        else if (!parent.isPaused && userPause){
            if (hasResumedMessage() && !resumedThisTick) {
                userPause = false;
                resumedThisTick = true;
                onUnpaused();
            }
        }

        parentWasPaused = parent.isPaused;
    }

    @EventHandler
    private void onTickPost(TickEvent.Post event) {
        if (parent.isActiveState(this) && !parent.isPaused && !userPause && !noTick)
            statePostTick(event);
    }

    @EventHandler
    private void onSendPacket(PacketEvent.Send event) {
        if (event.packet instanceof PlayerInteractBlockC2SPacket) {
            interactThisTick = true;
        } else if (event.packet instanceof PlayerActionC2SPacket p) {
            if (p.getAction() == PlayerActionC2SPacket.Action.START_DESTROY_BLOCK ||
                p.getAction() == PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK) {
                interactThisTick = true;
            }
        }
    }

    private void updateStuck() {
        if (!module.doStuckResolution.get())
            return;

        if (lastPos == null) {
            lastPos = mc.player.getBlockPos();
            return;
        }

        var curPos = mc.player.getBlockPos();
        if (curPos.isWithinDistance(lastPos, 0.5) && !isStuck && !interactThisTick) {
            if (stuckTicks > PAUSE_BUFFER_TICKS) {
                isStuck = true;
                onBecameStuck();
                return;
            }

            stuckTicks++;
        } else {
            stuckTicks = 0;
            lastPos = curPos;
        }

        interactThisTick = false;
    }
}
