package com.qprint.states;

import baritone.api.BaritoneAPI;
import com.qprint.modules.QuickPrintModule;
import meteordevelopment.meteorclient.events.world.TickEvent;
import net.minecraft.util.math.BlockPos;

import static com.qprint.utils.Utilities.posToString;

public class SurveyState extends AbstractState {
    public enum SurveyType {
        Center, // visit the center of each corner
        Wander, // visit a random point in each corner
        Transit,// visit the closest point in each corner
    }

    private final SurveyType mode;
    private int currentCorner = 0;
    private final boolean shouldFail;

    public SurveyState(QuickPrintModule module, SurveyType mode, boolean failIfCannotReach) {
        super(module);
        this.mode = mode;
        shouldFail = failIfCannotReach;
    }

    @Override
    public void stateEnter() {
        if (module.doStateLogging.get())
            module.info("SurveyState::StateEnter (mode=" + mode + ")");

        BaritoneAPI.getSettings().allowBreak.value = false;
        BaritoneAPI.getSettings().allowPlace.value = false;
    }

    private void addMoveState() {
        BlockPos target;
        int radius = 0;

        switch (mode) {
            case SurveyType.Center:
                target = module.mapPlatform.getCornerCenter(currentCorner);
                break;
            case SurveyType.Transit:
                target = module.mapPlatform.getCornerCenter(currentCorner);
                radius = 30;
                break;
            case SurveyType.Wander:
                target = module.mapPlatform.getRandomCornerPos(currentCorner);
                break;
            default:
                return;
        }

        parent.push(new MoveState(module, target, radius, shouldFail));
    }

    @Override
    public void stateResume() {
        super.stateResume();
    }

    @Override
    public void statePreTick(TickEvent.Pre event) {
        if (currentCorner > 0) {
            module.mapPlatform.tick();
        }
        if (currentCorner == 4) {
            parent.pop(true);
            return;
        }

        addMoveState();
        currentCorner++;
    }

    @Override
    public void stateExit() {
        super.stateExit();
        if (module.doStateLogging.get())
            module.info("SurveyState::StateExit");
    }
}
