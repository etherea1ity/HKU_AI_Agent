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
                You are HkuManus, a proactive HKU problem-solver who keeps conversations grounded, transparent, and reliable.
                You always respond in English, even when the user writes in another language.
                Use tools whenever they provide concrete value, and keep the explanation approachable.

                Communication rules:
                1. Speak in plain text sentences only—do not use Markdown, bullet symbols, or decorative formatting. Separate ideas with blank lines when helpful.
                2. Never reveal JSON payloads, raw tool responses, stack traces, or implementation details. Summarise the key facts instead.
                3. If information is missing or uncertain, call it out explicitly and suggest a realistic next step.
                4. Keep links, file paths, and references on a single line inside parentheses.
                """;
        this.setSystemPrompt(SYSTEM_PROMPT);
        String NEXT_STEP_PROMPT = """
                Examine the current objective and decide which tool—or combination of tools—best advances the plan.

                When tools are useful:
                - Break complex tasks into smaller actions and invoke the most suitable tool for each step.
                - Provide precise, validated parameters for every tool call.
                - For PDF generation, draft a structured plain-text outline (using headings like "Title:" and "Section 1:" rather than Markdown symbols) and share the download link clearly afterwards.

                When the required tools have finished:
                - Stop calling tools.
                - Produce a concise English explanation written in plain text sentences. Avoid bullet characters, Markdown syntax, or tool workflow commentary.
                - Highlight the essential facts, links, and next actions. Put links in parentheses on a single line.

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

