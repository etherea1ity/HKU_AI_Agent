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
import java.util.stream.Collectors;

/**
 * 处理工具调用的基础代理类，具体实现了 think 和 act 方法，可以用作创建实例的父类
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public class ToolCallAgent extends ReActAgent {

    // 可用的工具 8个
    private final ToolCallback[] availableTools;

    // MCP工具
    private final ToolCallbackProvider mcpToolProvider;

    // 保存工具调用信息的响应结果（要调用那些工具）
    private ChatResponse toolCallChatResponse;

    // 工具调用管理者
    private final ToolCallingManager toolCallingManager;

    // 禁用 Spring AI 内置的工具调用机制，自己维护选项和消息上下文
    private final ChatOptions chatOptions;

    public ToolCallAgent(ToolCallback[] availableTools, ToolCallbackProvider mcpToolProvider) {
        super();
        this.availableTools = availableTools;
        this.mcpToolProvider = mcpToolProvider;
        this.toolCallingManager = ToolCallingManager.builder().build();
        // 禁用 Spring AI 内置的工具调用机制，自己维护选项和消息上下文
        this.chatOptions = DashScopeChatOptions.builder()
                .withInternalToolExecutionEnabled(false)
                .build();
    }


    /**
     * 处理当前状态并决定下一步行动
     *
     * @return 是否需要执行行动
     */
    @Override
    public boolean think() {
        // 1、校验提示词，拼接用户提示词
        if (StrUtil.isNotBlank(getNextStepPrompt())) {
            UserMessage userMessage = new UserMessage(getNextStepPrompt());
            getMessageList().add(userMessage);
        }
        // 2、调用 AI 大模型，获取工具调用结果
        List<Message> messageList = getMessageList();
        Prompt prompt = new Prompt(messageList, this.chatOptions);
        try {
            ChatResponse chatResponse = getChatClient().prompt(prompt)
                    .system(getSystemPrompt())
                    .toolCallbacks(availableTools)
                    .toolCallbacks(mcpToolProvider)
                    //.tools(availableTools)
                    .call()
                    .chatResponse();
            // 记录响应，用于等下 Act
            this.toolCallChatResponse = chatResponse;
            // 3、解析工具调用结果，获取要调用的工具
            // 助手消息
            AssistantMessage assistantMessage = chatResponse.getResult().getOutput();
            // 获取要调用的工具列表
            List<AssistantMessage.ToolCall> toolCallList = assistantMessage.getToolCalls();
            // 输出提示信息
            String result = assistantMessage.getText();
            if (StrUtil.isNotBlank(result)) {
                log.info(getName() + "的思考：" + result);
            }
            log.info(getName() + "选择了 " + toolCallList.size() + " 个工具来使用");
            
            // 如果不需要调用工具，返回 false
            if (toolCallList.isEmpty()) {
                // 只有不调用工具时，才需要手动记录助手消息
                getMessageList().add(assistantMessage);
                // 标记任务完成
                setState(AgentState.FINISHED);
                return false;
            } else {
                // 发送工具调用信息到前端（简洁版）
                if (!toolCallList.isEmpty()) {
                    String toolNames = toolCallList.stream()
                            .map(toolCall -> getToolDisplayName(toolCall.name()))
                            .collect(Collectors.joining("、"));
                    sendSseMessage("[TOOL_CALL]第" + getCurrentStep() + "步：调用" + toolNames);
                    
                    // 详细信息只输出到日志
                    String toolCallInfo = toolCallList.stream()
                            .map(toolCall -> String.format("工具名称：%s，参数：%s", toolCall.name(), toolCall.arguments()))
                            .collect(Collectors.joining("\n"));
                    log.info(toolCallInfo);
                }
                // 需要调用工具时，无需记录助手消息，因为调用工具时会自动记录
                return true;
            }
        } catch (Exception e) {
            log.error(getName() + "的思考过程遇到了问题：" + e.getMessage());
            getMessageList().add(new AssistantMessage("处理时遇到了错误：" + e.getMessage()));
            setState(AgentState.FINISHED);
            return false;
        }
    }

    /**
     * 获取工具的友好显示名称
     * 
     * @param toolName 工具的内部名称
     * @return 用户友好的显示名称
     */
    private String getToolDisplayName(String toolName) {
        // 将工具名称映射为用户友好的中文名称
        return switch (toolName) {
            case "maps_text_search" -> "地图搜索工具";
            case "maps_around_search" -> "周边搜索工具";
            case "maps_geo" -> "地理编码工具";
            case "maps_regeocode" -> "逆地理编码工具";
            case "maps_weather" -> "天气查询工具";
            case "maps_direction_driving" -> "驾车路线规划工具";
            case "maps_direction_walking" -> "步行路线规划工具";
            case "maps_direction_transit_integrated" -> "公交路线规划工具";
            case "maps_bicycling" -> "骑行路线规划工具";
            case "maps_distance" -> "距离测量工具";
            case "maps_search_detail" -> "POI详情查询工具";
            case "maps_ip_location" -> "IP定位工具";
            case "doWebSearch" -> "网络搜索工具";
            case "doWebScraping" -> "网页抓取工具";
            case "downloadResource" -> "资源下载工具";
            case "generatePDF" -> "PDF生成工具";
            case "executeCommand" -> "命令执行工具";
            case "fileOperation" -> "文件操作工具";
            case "doTerminate" -> "终止工具";
            default -> toolName; // 默认返回原名称
        };
    }
    
    /**
     * 执行工具调用并处理结果
     *
     * @return 执行结果
     */
    @Override
    public String act() {
        if (!toolCallChatResponse.hasToolCalls()) {
            return "没有工具需要调用";
        }
        // 调用工具
        Prompt prompt = new Prompt(getMessageList(), this.chatOptions);
        ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(prompt, toolCallChatResponse);
        // 记录消息上下文，conversationHistory 已经包含了助手消息和工具调用返回的结果
        setMessageList(toolExecutionResult.conversationHistory());
        ToolResponseMessage toolResponseMessage = (ToolResponseMessage) CollUtil.getLast(toolExecutionResult.conversationHistory());
        // 判断是否调用了终止工具
        boolean terminateToolCalled = toolResponseMessage.getResponses().stream()
                .anyMatch(response -> response.name().equals("doTerminate"));
        if (terminateToolCalled) {
            // 任务结束，更改状态
            setState(AgentState.FINISHED);
        }
        String results = toolResponseMessage.getResponses().stream()
                .map(response -> "工具 " + response.name() + " 返回的结果：" + response.responseData())
                .collect(Collectors.joining("\n"));
        log.info(results);
        
        // 在工具调用完成后，添加一个提示，要求AI总结结果并回答用户问题
        // 这样可以确保AI会在下一次think时生成最终的自然语言回复
        // 注意：不要在这里添加提示，因为 NEXT_STEP_PROMPT 会在下一次 think 时自动添加
        
        return results;
    }
}

