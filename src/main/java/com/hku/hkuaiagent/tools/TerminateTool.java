package com.hku.hkuaiagent.tools;

import org.springframework.ai.tool.annotation.Tool;

/**
 * Utility tool that allows autonomous agents to end a workflow explicitly.
 */
public class TerminateTool {

    @Tool(description = """
            Terminate the interaction when the request is met OR if the assistant cannot proceed further with the task.
            "When you have finished all the tasks, call this tool to end the work.
            """)
    public String doTerminate() {
        return "Task completed";
    }
}

