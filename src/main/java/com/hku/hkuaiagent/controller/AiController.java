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
import java.util.Objects;

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

    // MCP tool provider injected via Spring
    @Resource
    private ToolCallbackProvider toolCallbackProvider;
    
    // Cache Manus agent instances per chatId to preserve conversation memory
    private final java.util.Map<String, HkuManus> manusCache = new java.util.concurrent.ConcurrentHashMap<>();

    // Cache LoveCampusAgent instances per chatId so each session can call tools independently
    private final java.util.Map<String, com.hku.hkuaiagent.agent.LoveCampusAgent> loveCache = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Handle synchronous chat requests for the Love App agent.
     *
     * @param message user prompt content
     * @param chatId conversation identifier
     * @return agent response content
     */
    @GetMapping("/love_app/chat/sync")
    public String doChatWithLoveAppSync(String message, String chatId) {
        log.info("[AiController] doChatWithLoveAppSync");
        return loveApp.doChat(message, chatId);
    }

    /**
     * Stream responses from the Love App agent using SSE.
     *
     * @param message user prompt content
     * @param chatId conversation identifier
     * @return SSE emitter that streams the agent response
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
     * Stream responses from the Love App agent using reactive Server-Sent Events.
     *
     * @param message user prompt content
     * @param chatId conversation identifier
     * @return server-sent event flux with response chunks
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
     * Stream responses from the Love App agent using a Spring MVC SseEmitter.
     *
     * @param message user prompt content
     * @param chatId conversation identifier
     * @return SSE emitter that streams the agent response
     */
    @GetMapping(value = "/love_app/chat/sse_emitter")
    public SseEmitter doChatWithLoveAppServerSseEmitter(String message, String chatId) {
        log.info("[AiController] Emitter");
        // Use a generous timeout so longer conversations are not interrupted
        SseEmitter sseEmitter = new SseEmitter(180000L); // 3-minute timeout
        // Subscribe to the reactive stream and forward each chunk to the emitter
        loveApp.doChatByStream(message, chatId)
                .subscribe(chunk -> {
                    try {
                        sseEmitter.send(Objects.requireNonNull(chunk, "sseChunk"));
                    } catch (IOException e) {
                        sseEmitter.completeWithError(e);
                    }
                }, sseEmitter::completeWithError, sseEmitter::complete);
        // Return the emitter to the caller
        return sseEmitter;
    }

    /**
     * Stream responses from the Manus super-agent while preserving conversation memory per chatId.
     *
     * @param message user prompt content
     * @param chatId conversation identifier used to retain state across turns
     * @return SSE emitter that streams the agent response
     */
    @GetMapping("/manus/chat")
    public SseEmitter doChatWithManus(String message, String chatId) {
        log.info("[AiController] Manus - chatId: {}", chatId);
        
        // Fall back to a default chatId when none is provided
        if (chatId == null || chatId.isEmpty()) {
            chatId = "default";
        }
        
        // Retrieve or create the Manus agent for this chat session
        HkuManus hkuManus = manusCache.computeIfAbsent(chatId,
            id -> new HkuManus(allTools, toolCallbackProvider, dashscopeChatModel));
        
        return hkuManus.runStream(message);
    }
    
    /**
     * Clear the cached Manus agent for the provided chatId so the next request starts fresh.
     *
     * @param chatId conversation identifier
     * @return acknowledgement message
     */
    @GetMapping("/manus/clear")
    public String clearManusChat(String chatId) {
        if (chatId == null || chatId.isEmpty()) {
            chatId = "default";
        }
        manusCache.remove(chatId);
        log.info("[AiController] Cleared chat history for chatId: {}", chatId);
        return "Chat history cleared.";
    }
}

