package com.hku.hkuaiagent.agent;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

/**
 * Abstract agent that implements the ReAct (Reasoning and Acting) pattern.
 * Alternates between reasoning steps and tool actions.
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public abstract class ReActAgent extends BaseAgent {

    /**
     * Analyse the current state and decide whether an action is required.
     *
     * @return true when the agent should execute an action; false otherwise
     */
    public abstract boolean think();

    /**
     * Perform the action that was selected during {@link #think()}.
     *
     * @return action result summary for logging
     */
    public abstract String act();

    /**
     * Execute one ReAct loop consisting of a thinking phase followed by an action phase.
     *
     * @return status string describing the step outcome
     */
    @Override
    public String step() {
        try {
            // Run the reasoning phase first
            boolean shouldAct = think();
            if (!shouldAct) {
                return "Reasoning finished - no action required";
            }
            // Execute the action phase when required
            return act();
        } catch (Exception e) {
            // Record the failure for diagnostics
            e.printStackTrace();
            return "Step execution failed: " + e.getMessage();
        }
    }

}

