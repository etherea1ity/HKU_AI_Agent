package com.hku.hkuaiagent.fallback;

import java.util.List;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * Basic fallback ChatModel used when DashScope chat is disabled on this branch.
 * It keeps the application wiring intact and surfaces a clear message to users
 * instead of bubbling up HTTP 400 errors.
 */
@Component
@Primary
@ConditionalOnProperty(name = "spring.ai.dashscope.chat.enabled", havingValue = "false", matchIfMissing = false)
public class DisabledChatModel implements ChatModel {

    private static final String FALLBACK_MESSAGE = "DashScope chat service is disabled for this branch. Please configure a valid provider before retrying.";

    @Override
    public ChatResponse call(Prompt prompt) {
        AssistantMessage assistantMessage = new AssistantMessage(FALLBACK_MESSAGE);
        Generation generation = new Generation(assistantMessage);
        return new ChatResponse(List.of(generation));
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        return Flux.just(call(prompt));
    }
}
