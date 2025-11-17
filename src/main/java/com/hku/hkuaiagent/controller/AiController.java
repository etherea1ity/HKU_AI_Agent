package com.hku.hkuaiagent.controller;

import com.hku.hkuaiagent.agent.HkuManus;
import com.hku.hkuaiagent.app.LoveApp;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;

@RestController
@RequestMapping("/ai")
@Slf4j
public class AiController {

    @Resource
    private LoveApp loveApp;

    @Resource
    private ToolCallback[] allTools;

    @Resource
    private ChatModel dashscopeChatModel;

    // MCP服务
    @Resource
    private ToolCallbackProvider toolCallbackProvider;
    
    // 缓存 HkuManus 实例，按 chatId 存储（添加对话记忆支持）
    private final java.util.Map<String, HkuManus> manusCache = new java.util.concurrent.ConcurrentHashMap<>();

    // 缓存 LoveCampusAgent 实例，按 chatId 存储（使 love agent 支持工具调用）
    private final java.util.Map<String, com.hku.hkuaiagent.agent.LoveCampusAgent> loveCache = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 同步调用 AI 恋爱大师应用
     *
     * @param message
     * @param chatId
     * @return
     */
    @GetMapping("/love_app/chat/sync")
    public String doChatWithLoveAppSync(String message, String chatId) {
        log.info("[AiController] doChatWithLoveAppSync");
        return loveApp.doChat(message, chatId);
    }

    /**
     * SSE 流式调用 AI 恋爱大师应用
     *
     * @param message
     * @param chatId
     * @return
     */
    @GetMapping(value = "/love_app/chat/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter doChatWithLoveAppSSE(String message, String chatId) {
        log.info("[AiController] SSE - love agent");

        if (chatId == null || chatId.isEmpty()) {
            chatId = "default";
        }

        com.hku.hkuaiagent.agent.LoveCampusAgent loveAgent = loveCache.computeIfAbsent(chatId,
                id -> new com.hku.hkuaiagent.agent.LoveCampusAgent(allTools, toolCallbackProvider, dashscopeChatModel));

        return loveAgent.runStream(message);
    }

    /**
     * SSE 流式调用 AI 恋爱大师应用
     *
     * @param message
     * @param chatId
     * @return
     */
    @GetMapping(value = "/love_app/chat/server_sent_event")
    public Flux<ServerSentEvent<String>> doChatWithLoveAppServerSentEvent(String message, String chatId) {
        log.info("[AiController] Event");
        return loveApp.doChatByStream(message, chatId)
                .map(chunk -> ServerSentEvent.<String>builder()
                        .data(chunk)
                        .build());
    }

    /**
     * SSE 流式调用 AI 恋爱大师应用
     *
     * @param message
     * @param chatId
     * @return
     */
    @GetMapping(value = "/love_app/chat/sse_emitter")
    public SseEmitter doChatWithLoveAppServerSseEmitter(String message, String chatId) {
        log.info("[AiController] Emitter");
        // 创建一个超时时间较长的 SseEmitter
        SseEmitter sseEmitter = new SseEmitter(180000L); // 3 分钟超时
        // 获取 Flux 响应式数据流并且直接通过订阅推送给 SseEmitter
        loveApp.doChatByStream(message, chatId)
                .subscribe(chunk -> {
                    try {
                        sseEmitter.send(chunk);
                    } catch (IOException e) {
                        sseEmitter.completeWithError(e);
                    }
                }, sseEmitter::completeWithError, sseEmitter::complete);
        // 返回
        return sseEmitter;
    }

    /**
     * 流式调用 Manus 超级智能体（支持对话记忆）
     *
     * @param message 用户消息
     * @param chatId 会话ID（用于区分不同对话，支持多轮对话记忆）
     * @return SSE流式响应
     */
    @GetMapping("/manus/chat")
    public SseEmitter doChatWithManus(String message, String chatId) {
        log.info("[AiController] Manus - chatId: {}", chatId);
        
        // 如果没有提供 chatId，生成一个默认的
        if (chatId == null || chatId.isEmpty()) {
            chatId = "default";
        }
        
        // 从缓存中获取或创建新的 HkuManus 实例
        HkuManus hkuManus = manusCache.computeIfAbsent(chatId, 
            id -> new HkuManus(allTools, toolCallbackProvider, dashscopeChatModel));
        
        return hkuManus.runStream(message);
    }
    
    /**
     * 清除指定会话的对话历史
     *
     * @param chatId 会话ID
     * @return 清除结果
     */
    @GetMapping("/manus/clear")
    public String clearManusChat(String chatId) {
        if (chatId == null || chatId.isEmpty()) {
            chatId = "default";
        }
        manusCache.remove(chatId);
        log.info("[AiController] Cleared chat history for chatId: {}", chatId);
        return "对话历史已清除";
    }
}

