package com.hku.hkuaiagent.agent;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hku.hkuaiagent.agent.model.AgentState;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.CompletableFuture;

/**
 * 抽象基础代理类，用于管理代理状态和执行流程。
 * <p>
 * 提供状态转换、内存管理和基于步骤的执行循环的基础功能。
 * 子类必须实现step方法。
 */
@Data
@Slf4j
public abstract class BaseAgent {

    // 核心属性
    private String name;

    private static final ObjectMapper SUMMARY_OBJECT_MAPPER = new ObjectMapper();

    // 提示词
    private String systemPrompt;
    private String nextStepPrompt;

    // 代理状态
    private AgentState state = AgentState.IDLE;

    // 执行步骤控制
    private int currentStep = 0;
    private int maxSteps = 10;

    // LLM 大模型
    private ChatClient chatClient;

    // Memory 记忆（需要自主维护会话上下文）
    private List<Message> messageList = new ArrayList<>();
    
    // SSE 发射器（用于流式输出思考步骤）
    private SseEmitter currentSseEmitter;

    /**
     * 运行代理
     *
     * @param userPrompt 用户提示词
     * @return 执行结果
     */
    public String run(String userPrompt) {
        // 1、基础校验
        if (this.state != AgentState.IDLE) {
            throw new RuntimeException("Cannot run agent from state: " + this.state);
        }
        if (StrUtil.isBlank(userPrompt)) {
            throw new RuntimeException("Cannot run agent with empty user prompt");
        }
        // 2、执行，更改状态
        this.state = AgentState.RUNNING;
        // 记录消息上下文
        messageList.add(new UserMessage(userPrompt));
        // 保存结果列表
        List<String> results = new ArrayList<>();
        try {
            // 执行循环
            for (int i = 0; i < maxSteps && state != AgentState.FINISHED; i++) {
                int stepNumber = i + 1;
                currentStep = stepNumber;
                log.info("Executing step {}/{}", stepNumber, maxSteps);
                // 单步执行
                String stepResult = step();
                String result = "Step " + stepNumber + ": " + stepResult;
                results.add(result);
            }
            // 检查是否超出步骤限制
            if (currentStep >= maxSteps) {
                state = AgentState.FINISHED;
                results.add("Terminated: Reached max steps (" + maxSteps + ")");
            }
            return String.join("\n", results);
        } catch (Exception e) {
            state = AgentState.ERROR;
            log.error("error executing agent", e);
            return "执行错误" + e.getMessage();
        } finally {
            // 3、清理资源
            this.cleanup();
        }
    }

    /**
     * 运行代理（流式输出）
     *
     * @param userPrompt 用户提示词
     * @return 执行结果
     */
    public SseEmitter runStream(String userPrompt) {
        // 创建一个超时时间较长的 SseEmitter
        SseEmitter sseEmitter = new SseEmitter(300000L); // 5 分钟超时
        // 使用线程异步处理，避免阻塞主线程
        CompletableFuture.runAsync(() -> {
            // 1、基础校验
            try {
                if (this.state != AgentState.IDLE) {
                    sseEmitter.send("错误：无法从状态运行代理：" + this.state);
                    sseEmitter.complete();
                    return;
                }
                if (StrUtil.isBlank(userPrompt)) {
                    sseEmitter.send("错误：不能使用空提示词运行代理");
                    sseEmitter.complete();
                    return;
                }
            } catch (Exception e) {
                sseEmitter.completeWithError(e);
            }
            // 2、执行，更改状态
            this.state = AgentState.RUNNING;
            // 设置当前的 SSE 发射器
            this.currentSseEmitter = sseEmitter;
            // 记录消息上下文
            messageList.add(new UserMessage(userPrompt));
            // 保存结果列表
            List<String> results = new ArrayList<>();
            try {
                // 发送开始思考的信号
                sseEmitter.send("[THINKING_START]正在思考如何处理您的问题...");
                
                // 执行循环
                for (int i = 0; i < maxSteps && state != AgentState.FINISHED; i++) {
                    int stepNumber = i + 1;
                    currentStep = stepNumber;
                    log.info("Executing step {}/{}", stepNumber, maxSteps);
                    
                    // 单步执行
                    String stepResult = step();
                    results.add(stepResult);
                    
                    // 只在调试时输出详细步骤信息到日志
                    log.info("Step {}: {}", stepNumber, stepResult);
                }
                
                // 发送思考完成信号
                sseEmitter.send("[THINKING_END]思考完成，正在整理答案...");
                
                // 检查是否超出步骤限制
                boolean reachedMaxSteps = currentStep >= maxSteps;
                if (reachedMaxSteps) {
                    state = AgentState.FINISHED;
                    log.warn("Terminated: Reached max steps ({})", maxSteps);
                }
                
                // 获取最终响应 - 从消息列表中提取最后的助手回复
                String finalResponse = formatPlainTextResponse(getFinalResponse());
                if (StrUtil.isNotBlank(finalResponse)) {
                    // 分段发送最终响应，避免一次性发送大量文本
                    sendInChunks(sseEmitter, finalResponse);
                } else {
                    // 如果没有最终响应，尝试从工具返回中提取信息
                    String toolResponse = formatPlainTextResponse(getLastToolResponse());
                    if (StrUtil.isNotBlank(toolResponse)) {
                        // 直接发送工具返回的信息（特别是PDF生成的结果）
                        sendInChunks(sseEmitter, toolResponse);
                    } else if (reachedMaxSteps) {
                        // 如果达到最大步骤且没有工具响应，生成一个总结
                        String summary = formatPlainTextResponse(generateMaxStepsSummary());
                        if (StrUtil.isNotBlank(summary)) {
                            sendInChunks(sseEmitter, summary);
                        } else {
                            sseEmitter.send("抱歉，我已经尝试了多个步骤来解决您的问题，但未能得出完整的结论。请尝试重新表述您的问题，或者提供更多具体信息。");
                        }
                    } else {
                        // 最后的兜底
                        sseEmitter.send("任务已完成。");
                    }
                }
                
                // 发送完成标记
                sseEmitter.send("[DONE]");
                // 正常完成
                sseEmitter.complete();
            } catch (Exception e) {
                state = AgentState.ERROR;
                log.error("error executing agent", e);
                try {
                    sseEmitter.send("执行错误：" + e.getMessage());
                    sseEmitter.send("[DONE]");
                    sseEmitter.complete();
                } catch (IOException ex) {
                    sseEmitter.completeWithError(ex);
                }
            } finally {
                // 3、清理资源
                this.currentSseEmitter = null;
                this.cleanup();
            }
        });

        // 设置超时回调
        sseEmitter.onTimeout(() -> {
            this.state = AgentState.ERROR;
            this.cleanup();
            log.warn("SSE connection timeout");
        });
        // 设置完成回调
        sseEmitter.onCompletion(() -> {
            if (this.state == AgentState.RUNNING) {
                this.state = AgentState.FINISHED;
            }
            this.cleanup();
            log.info("SSE connection completed");
        });
        return sseEmitter;
    }
    
    /**
     * 获取最终响应 - 从消息列表中提取最后的助手回复
     * 
     * @return 最终响应文本
     */
    protected String getFinalResponse() {
        // 从消息列表的末尾向前查找，找到最后一条助手消息
        for (int i = messageList.size() - 1; i >= 0; i--) {
            Message message = messageList.get(i);
            if (message instanceof org.springframework.ai.chat.messages.AssistantMessage) {
                org.springframework.ai.chat.messages.AssistantMessage assistantMessage = 
                    (org.springframework.ai.chat.messages.AssistantMessage) message;
                String text = assistantMessage.getText();
                // 确保文本不为空且不是工具调用相关的内部消息
                if (StrUtil.isNotBlank(text) && !text.contains("思考完成")) {
                    return text;
                }
            }
        }
        return null;
    }
    
    /**
     * 获取最后一个工具的响应
     * 
     * @return 工具响应文本
     */
    protected String getLastToolResponse() {
        // 从消息列表的末尾向前查找，找到最后一条工具响应消息
        for (int i = messageList.size() - 1; i >= 0; i--) {
            Message message = messageList.get(i);
            if (message instanceof org.springframework.ai.chat.messages.ToolResponseMessage) {
                org.springframework.ai.chat.messages.ToolResponseMessage toolMsg = 
                    (org.springframework.ai.chat.messages.ToolResponseMessage) message;
                for (var response : toolMsg.getResponses()) {
                    String data = response.responseData();
                    if (StrUtil.isNotBlank(data)) {
                        return data;
                    }
                }
            }
        }
        return null;
    }
    
    /**
     * 分段发送文本，实现流式效果
     * 
     * @param sseEmitter SSE发射器
     * @param text 要发送的文本
     * @throws IOException IO异常
     */
    protected void sendInChunks(SseEmitter sseEmitter, String text) throws IOException {
        // 按字符分段发送，每次发送3-5个字符，模拟打字效果
        int chunkSize = 4;
        for (int i = 0; i < text.length(); i += chunkSize) {
            int end = Math.min(i + chunkSize, text.length());
            String chunk = text.substring(i, end);
            sseEmitter.send(chunk);
            
            // 添加微小延迟，使流式效果更自然
            try {
                Thread.sleep(15);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    /**
     * 定义单个步骤
     *
     * @return
     */
    public abstract String step();

    /**
     * 生成达到最大步骤时的总结
     * 
     * @return 总结文本
     */
    protected String generateMaxStepsSummary() {
        // 尝试从消息列表中提取工具返回的信息
        StringBuilder summary = new StringBuilder();
        summary.append("经过 ").append(currentStep).append(" 步深度思考和工具调用，我为您整理了以下信息：\n\n");
        
        // 从消息历史中提取有用信息
        int infoCount = 0;
        for (int i = messageList.size() - 1; i >= 0 && infoCount < 3; i--) {
            Message message = messageList.get(i);
            if (message instanceof org.springframework.ai.chat.messages.ToolResponseMessage) {
                org.springframework.ai.chat.messages.ToolResponseMessage toolMsg = 
                    (org.springframework.ai.chat.messages.ToolResponseMessage) message;
                for (var response : toolMsg.getResponses()) {
                    String data = response.responseData();
                    String formatted = extractToolSummary(data);
                    if (StrUtil.isNotBlank(formatted)) {
                        infoCount++;
                        summary.append("• ").append(formatted);
                        if (!formatted.endsWith("\n")) {
                            summary.append("\n");
                        }
                    }
                }
            }
        }
        
        if (infoCount == 0) {
            return null; // 没有找到有用信息
        }
        
        summary.append("\n如需更详细的信息，请提供更具体的查询条件。");
        return summary.toString();
    }

    /**
     * 对模型输出做基础的纯文本整理，移除 Markdown 并补充必要换行。
     */
    protected String formatPlainTextResponse(String text) {
        if (StrUtil.isBlank(text)) {
            return text;
        }
        String normalized = text.replace("\r\n", "\n");
            normalized = normalized.replaceAll("\\*\\*([^*]+)\\*\\*", "$1");
            normalized = normalized.replaceAll("`([^`]+)`", "$1");
            normalized = normalized.replaceAll("(?m)^#{1,6}\\s*", "");
            normalized = normalized.replaceAll("(?<!\\n)(\\d+\\.\\s)", "\n$1");
            normalized = normalized.replaceAll("(?<!\\n)(第[一二三四五六七八九十百千万两0-9]+[、,，])", "\n$1");
            normalized = normalized.replaceAll("：\\s*-", "：\n  - ");
            normalized = normalized.replaceAll("(?<!\\n)-(\\s)", "\n  - $1");
            normalized = normalized.replaceAll("(?<!\\n)([•·]\\s)", "\n  $1");
            normalized = normalized.replaceAll("(?<!\\n)([\\u4e00-\\u9fa5A-Za-z0-9]+[:：])", "\n$1");
            normalized = normalized.replaceAll("(?<!\\n)(课程基本信息|授课教师与班级信息|主要学习内容|相关建议|建议与选课指导|总体建议|课程亮点|学习建议|考试安排|推荐行程|出行建议|天气状况)", "\n\n$1");
            normalized = normalized.replaceAll("(课程基本信息|授课教师与班级信息|主要学习内容|相关建议|建议与选课指导|总体建议|课程亮点|学习建议|考试安排|推荐行程|出行建议|天气状况)(?!\n)", "$1\n");
            normalized = normalized.replaceAll("(?<!\\n)([\\u4e00-\\u9fa5]{2,}信息|建议与选课指导|主要学习内容|授课教师与班级信息|课程基本信息|推荐行程|出行建议|天气状况)([:：])", "\n\n$1$2");
            normalized = normalized.replaceAll("(\n\s*)([0-9]+\\.\\s)", "$1$2");
        normalized = isolateUrls(normalized);
        normalized = normalized.replaceAll("\n{3,}", "\n\n");
        normalized = normalized.replaceAll("\s*\n\s*", "\n");
        normalized = normalized.replaceAll(" {2,}", " ");
        normalized = normalized.trim();
        return normalized;
    }
    
    /**
     * 发送 SSE 消息（供子类使用）
     * 
     * @param message 要发送的消息
     */
    protected void sendSseMessage(String message) {
        if (currentSseEmitter != null) {
            try {
                currentSseEmitter.send(message);
            } catch (IOException e) {
                log.error("发送 SSE 消息失败", e);
            }
        }
    }
    
    /**
     * 清理资源
     */
    protected void cleanup() {
        // 重置状态为 IDLE，以支持多轮对话
        this.state = AgentState.IDLE;
        this.currentStep = 0;
        // 注意：不清空 messageList，保留对话历史
        // 子类可以重写此方法来清理资源
    }

    private String extractToolSummary(String raw) {
        if (StrUtil.isBlank(raw)) {
            return null;
        }
        String trimmed = raw.trim();
        String parsed = parseJsonToolSummary(trimmed);
        String candidate = StrUtil.isNotBlank(parsed) ? parsed : trimmed;
        if (candidate.length() > 220) {
            candidate = candidate.substring(0, 220) + "…";
        }
        return formatPlainTextResponse(candidate);
    }

    private String parseJsonToolSummary(String raw) {
        try {
            JsonNode node = SUMMARY_OBJECT_MAPPER.readTree(raw);
            if (node == null) {
                return null;
            }
            if (node.hasNonNull("summary")) {
                String summary = node.get("summary").asText();
                if (StrUtil.isNotBlank(summary)) {
                    return summary;
                }
            }
            if (node.has("sources") && node.get("sources").isArray()) {
                StringBuilder builder = new StringBuilder();
                Iterator<JsonNode> iterator = node.get("sources").elements();
                int count = 0;
                while (iterator.hasNext() && count < 2) {
                    JsonNode source = iterator.next();
                    String title = source.path("title").asText("相关资料");
                    String snippet = source.path("snippet").asText("");
                    builder.append(title);
                    if (StrUtil.isNotBlank(snippet)) {
                        builder.append("：").append(snippet);
                    }
                    count++;
                    if (iterator.hasNext() && count < 2) {
                        builder.append("\n");
                    }
                }
                String combined = builder.toString();
                if (StrUtil.isNotBlank(combined)) {
                    return combined;
                }
            }
        } catch (Exception ignore) {
            // 如果不是 JSON，直接返回原始文本的精简版本
        }
        return null;
    }

    private String isolateUrls(String text) {
        Matcher matcher = URL_PATTERN.matcher(text);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String url = matcher.group(0);
            boolean addLeadingNewline = matcher.start() == 0 || text.charAt(matcher.start() - 1) != '\n';
            boolean addTrailingNewline = matcher.end() == text.length() || text.charAt(matcher.end()) != '\n';
            StringBuilder replacement = new StringBuilder();
            if (addLeadingNewline) {
                replacement.append('\n');
            }
            replacement.append(url);
            if (addTrailingNewline) {
                replacement.append('\n');
            }
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement.toString()));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private static final Pattern URL_PATTERN = Pattern.compile("https?://[A-Za-z0-9\\-._~:/?#\\[\\]@!$&'()*+,;=%]+");
}

