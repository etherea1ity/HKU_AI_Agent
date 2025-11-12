package com.hku.hkuaiagent.agent;

import com.hku.hkuaiagent.advisor.MyLoggerAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.stereotype.Component;

/**
 * HKU 的 AI 超级智能体（拥有自主规划能力，可以直接使用）
 */
@Component
public class HkuManus extends ToolCallAgent {

        public HkuManus(ToolCallback[] allTools, ToolCallbackProvider mcpTools, ChatModel dashscopeChatModel) {
        super(allTools,mcpTools);
                this.setName("hkuManus");
        String SYSTEM_PROMPT = """
                                You are HkuManus, an all-capable AI assistant, aimed at solving any task presented by the user.
                You have various tools at your disposal that you can call upon to efficiently complete complex requests.
                """;
        this.setSystemPrompt(SYSTEM_PROMPT);
        String NEXT_STEP_PROMPT = """
                Based on user needs, proactively select the most appropriate tool or combination of tools.
                For complex tasks, you can break down the problem and use different tools step by step to solve it.
                After using each tool, clearly explain the execution results and suggest the next steps.
                If you want to stop the interaction at any point, use the `terminate` tool/function call.
                """;
        this.setNextStepPrompt(NEXT_STEP_PROMPT);
        // 限定10次调用
        this.setMaxSteps(10);
        // 初始化 AI 对话客户端
        ChatClient chatClient = ChatClient.builder(dashscopeChatModel)
                .defaultAdvisors(new MyLoggerAdvisor())
                .build();
        this.setChatClient(chatClient);
    }
}

