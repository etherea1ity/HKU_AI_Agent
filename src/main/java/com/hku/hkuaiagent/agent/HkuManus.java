package com.hku.hkuaiagent.agent;

import com.hku.hkuaiagent.advisor.MyLoggerAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Objects;

/**
 * HKU AI super agent with autonomous planning capabilities.
 */
@Component
public class HkuManus extends ToolCallAgent {

        public HkuManus(ToolCallback[] allTools, ToolCallbackProvider mcpTools, ChatModel dashscopeChatModel) {
                super(filterRagTools(allTools), filterRagToolsFromProvider(mcpTools));
                this.setName("hkuManus");
        String SYSTEM_PROMPT = """
                You are HkuManus, a proactive AI problem-solver for HKU.
                Your goal is to deliver accurate, helpful answers while keeping the experience friendly and approachable.
                You have access to multiple tools—plan ahead, decide whether a tool is needed, and execute without hesitation when it adds value.
                Always respond in English, even if the user asks questions in another language.

                Core rules:
                1. Call tools only when they provide concrete benefits for the current step.
                2. After every tool call sequence, craft a natural English response that is easy to read and free of implementation details.
                3. Do not expose JSON blobs, raw tool payloads, or stack traces to the user.
                4. If you lack enough data to answer, explain the gap and propose a realistic next step.
                """;
        this.setSystemPrompt(SYSTEM_PROMPT);
        String NEXT_STEP_PROMPT = """
                Review the current objective and decide which tool—or combination of tools—best advances the plan.

                When tools are useful:
                - Break complex tasks into smaller actions and call the right tool for each step.
                - Provide every tool with precise, validated parameters.
                - For PDF generation, include a complete Markdown-ready outline with headings, sub-sections, and emphasis where it helps readability. Share the download link clearly afterwards.

                When all essential tools are done:
                - Do not issue extra tool calls.
                - Summarise the findings in clear English, focusing on what matters to the user.
                - Highlight key facts, links, and next actions. Avoid referencing the tool workflow explicitly.

                If you ever need to end the session early, call the `terminate` tool.
                """;
        this.setNextStepPrompt(NEXT_STEP_PROMPT);
        // Allow up to twenty reasoning/action loops
        this.setMaxSteps(20);
        // Initialise chat client with logging advisor
        ChatClient chatClient = ChatClient.builder(Objects.requireNonNull(dashscopeChatModel))
                .defaultAdvisors(new MyLoggerAdvisor())
                .build();
                this.setChatClient(chatClient);
        }

        private static ToolCallback[] filterRagTools(ToolCallback[] allTools) {
                if (allTools == null || allTools.length == 0) {
                        return new ToolCallback[0];
                }
                return Arrays.stream(allTools)
                                .filter(HkuManus::allowTool)
                                .toArray(ToolCallback[]::new);
        }

        private static ToolCallbackProvider filterRagToolsFromProvider(ToolCallbackProvider provider) {
                if (provider == null) {
                        return () -> new ToolCallback[0];
                }
                return () -> {
                        ToolCallback[] callbacks = provider.getToolCallbacks();
                        if (callbacks == null || callbacks.length == 0) {
                                return new ToolCallback[0];
                        }
                        java.util.List<ToolCallback> retained = new java.util.ArrayList<>();
                        for (ToolCallback callback : callbacks) {
                                if (allowTool(callback)) {
                                        retained.add(callback);
                                }
                        }
                        ToolCallback[] result = new ToolCallback[retained.size()];
                        for (int i = 0; i < retained.size(); i++) {
                                result[i] = retained.get(i);
                        }
                        return result;
                };
        }

        private static boolean allowTool(ToolCallback tool) {
                if (tool == null) {
                        return false;
                }
                try {
                        String name = tool.getClass().getSimpleName();
                        if (name == null) {
                                return true;
                        }
                        String lowerName = name.toLowerCase();
                        if (lowerName.startsWith("campusknowledgesearchtool") || lowerName.contains("rag")) {
                                return false;
                        }
                } catch (Exception ignored) {
                        // default allow
                }
                return true;
        }
}

