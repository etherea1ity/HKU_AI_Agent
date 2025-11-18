package com.hku.hkuaiagent.agent;

import com.hku.hkuaiagent.advisor.MyLoggerAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * HKU Campus 专用代理，支持工具调用（PDF、天气、MCP 等）
 */
@Component
public class LoveCampusAgent extends ToolCallAgent {

    public LoveCampusAgent(ToolCallback[] allTools, ToolCallbackProvider mcpTools, ChatModel dashscopeChatModel) {
        super(allTools, mcpTools);
        this.setName("loveCampusAgent");
        String SYSTEM_PROMPT = """
                You are the HKU Campus Assistant. Focus on campus life, courses, logistics, and practical student support.
                Always reply in conversational English even if the user writes in another language.

                Operating guidelines:
                1. When a question needs campus knowledge, first call campusRagSearch and cite the filename of every document you rely on.
                2. For directions or venue insight, use the amap map tools (text search, detail, directions) before answering from memory.
                3. For weather updates, prefer the built-in weather_lookup tool, calling it at most once per user request and reuse the result instead of re-querying. Avoid maps_weather.
                4. When users request summaries, itineraries, or reports, call the PDF generator and return the download link in plain English.
                5. If a tool returns HTML or JSON, extract the key facts and present them cleanly—never echo raw payloads.
                6. Final answers must be written in conversational English without Markdown decoration.
                """;
        this.setSystemPrompt(SYSTEM_PROMPT);

        String NEXT_STEP_PROMPT = """
                Pick the strategy that best serves the current user goal:
                - For document lookups or citations, start with campusRagSearch. Stream the filename and key takeaways to the deep-thinking panel.
                - For venue directions or transport planning, call the amap map tools (maps_text_search, maps_search_detail, maps_direction_*).
                - For weather requests, call weather_lookup once per request, cache the output, and keep a single clear summary of current and upcoming conditions.
                - For structured outputs, call generatePDF and share the download link.

                After every necessary tool call, stop and summarise the outcome in concise English with separated numbered points when helpful. Do not embed raw JSON or HTML in the reply.
                """;
        this.setNextStepPrompt(NEXT_STEP_PROMPT);
        this.setMaxSteps(10);

        ChatClient chatClient = ChatClient.builder(Objects.requireNonNull(dashscopeChatModel))
                .defaultAdvisors(new MyLoggerAdvisor())
                .build();
        this.setChatClient(chatClient);
    }
}
