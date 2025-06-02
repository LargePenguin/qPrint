package com.qprint.states;

import baritone.api.BaritoneAPI;
import com.qprint.modules.QuickPrintModule;
import meteordevelopment.meteorclient.events.world.TickEvent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

import static com.qprint.states.StateMachine.PAUSE_BUFFER_TICKS;
import static com.qprint.utils.MessageUtils.*;
import static com.qprint.utils.Utilities.posToString;
import static com.qprint.utils.Utilities.vecToPos;
import static meteordevelopment.meteorclient.MeteorClient.mc;

public class PrintState extends AbstractState {
    private boolean wasPaused, userPause;
    private String schematic;
    private final Random random = new Random();

    private final int STUCK_RESOLUTION_TICKS = 2 * PAUSE_BUFFER_TICKS;
    private int stuckResolutionCounter = 0;
    private final int[] stuckMoveAmounts = { 2, 2, 3, 3, 4 };
    private int stuckCount = 0;

    private final Map<PlayerEntity, Integer> avoidancePlayers = new HashMap<>();

    public PrintState(QuickPrintModule module, String schematic) {
        super(module);
        this.schematic = schematic;
    }

    @Override
    public void onBecameStuck() {
        if (stuckResolutionCounter == 0)
            stuckCount = 1;
        else
            stuckCount++;
    }

    @Override
    public void onPaused() {
        super.onPaused();
        stopBuilding();
    }

    @Override
    public void onUnpaused() {
        runBuildCommand();
    }

    @Override
    public void stateResume() {
        BaritoneAPI.getSettings().allowBreak.value = true;
        BaritoneAPI.getSettings().allowPlace.value = true;

        runBuildCommand();
    }

    @Override
    public void stateEnter() {
        if (module.doStateLogging.get())
            module.info("PrintState::StateEnter (s=" + schematic + ")");

        BaritoneAPI.getSettings().allowBreak.value = true;
        BaritoneAPI.getSettings().allowPlace.value = true;

        runBuildCommand();
    }

    @Override
    public void stateExit() {
        super.stateExit();

        stopBuilding();

        if (module.doStateLogging.get())
            module.info("PrintState::StateExit");
    }

    @Override
    public void statePostTick(TickEvent.Post event) {
        if (isStuck) {
            if (stuckCount < module.maxStuckResolutions.get()) {
                if (hasDoneBuildingMessage()) {
                    parent.pop(true);
                    return;
                }

                var pos = getJiggleTarget(stuckMoveAmounts[stuckCount]);
                module.error("Baritone is stuck! Attempting resolution (" + stuckCount + "/" + module.maxStuckResolutions.get() + ")...");
                if (module.doStateLogging.get())
                    module.info("r=" + stuckMoveAmounts[stuckCount] + ", resolutionPos=" + posToString(pos));
                stopBuilding();
                isStuck = false;
                parent.push(new MoveState(module, pos, 0));

                stuckResolutionCounter = STUCK_RESOLUTION_TICKS;

                return;
            } else {
                module.error("Baritone still stuck after " + module.maxStuckResolutions.get() + " attempted resolutions. Canceling.");
                parent.pop(false);

                return;
            }
        } else if (stuckResolutionCounter > 0) {
            stuckResolutionCounter--;
        }

        var isPaused = BaritoneAPI.getProvider().getPrimaryBaritone().getBuilderProcess().isPaused();

        if (isPaused && !wasPaused) {
            // Are we done building?
            if (hasDoneBuildingMessage()) {
                parent.pop(true);
                return;
            }

            // Check if we need to restock.
            var materials = getLastBaritoneMaterialDump();

            if (materials != null) {
                parent.push(new RestockState(module, materials, mc.player.getBlockPos()));
            }
        }

        var playersInRange = getAvoidancePlayers();

        for (var p : playersInRange) {
            if (!avoidancePlayers.containsKey(p)) {
                avoidancePlayers.put(p, module.avoidanceTime.get() + random.nextInt(-10, 10));
            }
        }

        for (var p : avoidancePlayers.entrySet()) {
            if (!playersInRange.contains(p.getKey()))
                avoidancePlayers.remove(p.getKey());
            else if (p.getValue() > 0)
                p.setValue(p.getValue() - 1);
            else {
                var pos = getAvoidancePos(p.getKey());
                module.info(p.getKey().getName().toString() + " is too close; avoiding them");

                if (module.doStateLogging.get())
                    module.info("(resolutionPos=" + posToString(pos) + ")");

                parent.push(new MoveState(module, pos, 0));
                return;
            }
        }

        wasPaused = isPaused;
    }

    private BlockPos getAvoidancePos(PlayerEntity otherPlayer) {
        var dir = mc.player.getPos().subtract(otherPlayer.getPos()).normalize();
        var dest = otherPlayer.getPos().add(dir.multiply(2 * module.avoidanceDistance.get()));
        return new BlockPos((int)Math.round(dest.x), (int)Math.round(dest.y), (int)Math.round(dest.z));
    }

    private void runBuildCommand() {
        double x = module.platformOrigin.get().x,
               y = module.platformOrigin.get().y,
               z = module.platformOrigin.get().z;

        BaritoneAPI
            .getProvider()
            .getPrimaryBaritone()
            .getCommandManager()
            .execute("build " + schematic + " " + x + " " + y + " " + z);
    }

    private void stopBuilding() {
        BaritoneAPI
            .getProvider()
            .getPrimaryBaritone()
            .getBuilderProcess()
            .pause();
    }

    private BlockPos getJiggleTarget(int distance) {
        Random random = new Random();
        var angle = random.nextDouble() * 2 * Math.PI;
        var dx = (int)Math.ceil(Math.cos(angle) * distance);
        var dz = (int)Math.ceil(Math.sin(angle) * distance);

        return new BlockPos(mc.player.getBlockPos().add(dx, 0, dz));
    }

    private List<PlayerEntity> getAvoidancePlayers() {
        return mc.world.getPlayers().stream()
            .filter(p -> !p.equals(mc.player))  // avoid yourself (generally good advice for life)
            .filter(p -> module.otherBots.get().contains(p.getName()))
            .filter(p -> p.getPos().isInRange(mc.player.getPos(), module.avoidanceDistance.get()))
            .collect(Collectors.toList());
    }
}
