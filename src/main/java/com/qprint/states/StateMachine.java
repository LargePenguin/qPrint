package com.qprint.states;

import com.qprint.modules.QuickPrintModule;

import java.util.Stack;

public class StateMachine {
    // after this much time with no activity, assume baritone failed to path.
    public static final int PAUSE_BUFFER_TICKS = 50;

    public boolean isPaused = false;

    private final Stack<AbstractState> stateStack = new Stack<>();
    private AbstractState stateCurrent;

    // actual architecture? in my java? forget about it
    private QuickPrintModule module;

    public StateMachine(QuickPrintModule module) {
        this.module = module;
    }

    public void push(AbstractState stateNext) {
        stateNext.parent = this;
        stateStack.push(stateNext);
        stateCurrent = stateNext;
        stateCurrent.stateEnter();
    }

    public void pop(boolean success) {
        if (!success) {
            module.fail();
            return;
        }

        if (stateCurrent != null)
            stateCurrent.stateExit();

        stateStack.pop();

        if (!stateStack.empty()) {
            stateCurrent = stateStack.peek();
            stateCurrent.stateResume();
        }
        else
            stateCurrent = null;
    }

    public boolean isComplete() {
        return stateStack.empty();
    }

    public boolean isActiveState(AbstractState state) {
        return stateCurrent == state;
    }

    public void reset() {
        if (stateStack.empty())
            return;

        while (!stateStack.empty())
        {
            var state = stateStack.pop();
            state.stateExit();
        }

        stateCurrent = null;
    }
}
