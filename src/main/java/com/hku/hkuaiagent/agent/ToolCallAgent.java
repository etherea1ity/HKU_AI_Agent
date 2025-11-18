package com.hku.hkuaiagent.agent;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.hku.hkuaiagent.agent.model.AgentState;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Base agent for orchestrating tool calls. Implements dedicated think/act steps and can be extended.
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public class ToolCallAgent extends ReActAgent {

    // Registered tools exposed to the agent (local + MCP)
    private final ToolCallback[] availableTools;

    // Provider that exposes dynamically discovered MCP tools
    private final ToolCallbackProvider mcpToolProvider;

    // Last tool-call planning response returned by the model
    private ChatResponse toolCallChatResponse;

    // Helper responsible for executing tool calls outside the model
    private final ToolCallingManager toolCallingManager;

    // Disable the built-in Spring AI tool execution so we can manage context manually
    private final ChatOptions chatOptions;

    public ToolCallAgent(ToolCallback[] availableTools, ToolCallbackProvider mcpToolProvider) {
        super();
        this.availableTools = availableTools;
        this.mcpToolProvider = mcpToolProvider;
        this.toolCallingManager = ToolCallingManager.builder().build();
        // Disable the built-in Spring AI tool execution so we can manage context manually
        this.chatOptions = DashScopeChatOptions.builder()
                .withInternalToolExecutionEnabled(false)
                .build();
    }


    /**
     * Plan the next step and decide whether any tools must be executed.
     *
     * @return true if the agent should execute tool calls; false if it can produce a final answer
     */
    @Override
    public boolean think() {
        // Append the next-step prompt if one was prepared already
        String nextStepPrompt = getNextStepPrompt();
        if (StrUtil.isNotBlank(nextStepPrompt)) {
            UserMessage userMessage = new UserMessage(Objects.requireNonNull(nextStepPrompt));
            getMessageList().add(userMessage);
        }
        // Ask the LLM to decide which tools to call, if any
        List<Message> messageList = getMessageList();
        Prompt prompt = new Prompt(messageList, this.chatOptions);
        try {
            var promptSpec = getChatClient().prompt(prompt);
            String systemPrompt = getSystemPrompt();
            if (StrUtil.isNotBlank(systemPrompt)) {
                promptSpec = promptSpec.system(Objects.requireNonNull(systemPrompt));
            }
            ChatResponse chatResponse = promptSpec
                    .toolCallbacks(Objects.requireNonNull(availableTools))
                    .toolCallbacks(mcpToolProvider)
                    //.tools(availableTools)
                    .call()
                    .chatResponse();
            if (chatResponse == null || chatResponse.getResult() == null) {
                log.warn(getName() + " returned an empty response while planning; finishing without tool calls");
                setState(AgentState.FINISHED);
                return false;
            }
            // Cache the full response so act() can execute the plan
            this.toolCallChatResponse = chatResponse;
            // Extract the assistant message and parse tool-call directives
            AssistantMessage assistantMessage = chatResponse.getResult().getOutput();
            // Collect the tool-call directives that need to be executed
            List<AssistantMessage.ToolCall> toolCallList = assistantMessage.getToolCalls();
            // Log the reasoning so we can trace behaviour during debugging
            String result = assistantMessage.getText();
            if (StrUtil.isNotBlank(result)) {
                log.info(getName() + " reasoning: " + result);
            }
            log.info(getName() + " selected " + toolCallList.size() + " tool(s) to execute");
            
            // If no tool invocations are required, finish immediately
            if (toolCallList.isEmpty()) {
                // Persist the assistant message when no tools run so the conversation is complete
                getMessageList().add(assistantMessage);
                // Mark the agent as finished because there is nothing else to execute
                setState(AgentState.FINISHED);
                return false;
            } else {
                // Emit the compact tool-call summary for the frontend stream
                if (!toolCallList.isEmpty()) {
                    String toolNames = toolCallList.stream()
                        .map(toolCall -> getToolDisplayName(toolCall.name()))
                        .collect(Collectors.joining(", "));
                    sendSseMessage("[TOOL_CALL]Step " + getCurrentStep() + ": using " + toolNames);
                    
                    // Send the detailed payload only to logs to avoid noisy UI output
                    String toolCallInfo = toolCallList.stream()
                        .map(toolCall -> String.format("Tool: %s, arguments: %s", toolCall.name(), toolCall.arguments()))
                            .collect(Collectors.joining("\n"));
                    log.info(toolCallInfo);
                }
                // Do not duplicate assistant messages since tool execution will append its own context
                return true;
            }
        } catch (Exception e) {
            log.error(getName() + " encountered an issue during planning: " + e.getMessage());
            getMessageList().add(new AssistantMessage("An error occurred while planning: " + e.getMessage()));
            setState(AgentState.FINISHED);
            return false;
        }
    }

    /**
     * Translate a tool's internal identifier into a user-facing label.
     *
     * @param toolName Internal tool identifier returned by the planner
     * @return User-friendly label used in logs and UI messages
     */
    private String getToolDisplayName(String toolName) {
        // Map tool identifiers to user-friendly English labels
        return switch (toolName) {
            case "maps_text_search" -> "Map text search";
            case "maps_around_search" -> "Nearby map search";
            case "maps_geo" -> "Geocoding tool";
            case "maps_regeocode" -> "Reverse geocoding";
            case "maps_weather" -> "Map weather lookup";
            case "maps_direction_driving" -> "Driving directions";
            case "maps_direction_walking" -> "Walking directions";
            case "maps_direction_transit_integrated" -> "Transit directions";
            case "maps_bicycling" -> "Cycling directions";
            case "maps_distance" -> "Distance measurement";
            case "maps_search_detail" -> "POI detail lookup";
            case "maps_ip_location" -> "IP geolocation";
            case "doWebSearch" -> "Web search";
            case "doWebScraping" -> "Web scraping";
            case "downloadResource" -> "Resource download";
            case "generatePDF" -> "PDF generator";
            case "executeCommand" -> "Command executor";
            case "fileOperation" -> "File operator";
            case "doTerminate" -> "Terminate";
            default -> toolName; // Fall back to the raw identifier
        };
    }
    
    /**
     * Execute the selected tools and record their responses in the conversation.
     *
     * @return Aggregated tool output as a human-readable string
     */
    @Override
    public String act() {
        if (toolCallChatResponse == null) {
            log.warn(getName() + " act() invoked without a prior planning response; skipping tool execution");
            return "No tool invocations required";
        }
        if (!toolCallChatResponse.hasToolCalls()) {
            return "No tool invocations required";
        }
        // Execute the planned tool calls
        Prompt prompt = new Prompt(getMessageList(), this.chatOptions);
        ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(prompt, Objects.requireNonNull(toolCallChatResponse));
        // Persist the updated conversation that now contains assistant + tool responses
        setMessageList(toolExecutionResult.conversationHistory());
        ToolResponseMessage toolResponseMessage = (ToolResponseMessage) CollUtil.getLast(toolExecutionResult.conversationHistory());
        // Detect if the terminate tool was triggered to finish the workflow early
        boolean terminateToolCalled = toolResponseMessage.getResponses().stream()
                .anyMatch(response -> response.name().equals("doTerminate"));
        if (terminateToolCalled) {
            // Mark the agent as finished when the terminate tool is used
            setState(AgentState.FINISHED);
        }
        String results = toolResponseMessage.getResponses().stream()
            .map(response -> "Tool " + response.name() + " returned: " + response.responseData())
                .collect(Collectors.joining("\n"));
        log.info(results);
        
        // Note: we rely on NEXT_STEP_PROMPT to ask the model for a natural-language answer on the next think() call
        
        return results;
    }
}

