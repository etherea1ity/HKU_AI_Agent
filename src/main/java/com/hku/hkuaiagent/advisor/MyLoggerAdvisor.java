package com.hku.hkuaiagent.advisor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientMessageAggregator;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.lang.NonNull;
import reactor.core.publisher.Flux;

import java.util.Objects;

/**
 * Custom logging advisor that prints info-level logs for each prompt and response.
 */
@Slf4j
public class MyLoggerAdvisor implements CallAdvisor, StreamAdvisor {

    @Override
    public @NonNull String getName() {
        return Objects.requireNonNull(this.getClass().getSimpleName(), "advisorName");
    }

    @Override
    public int getOrder() {
        return 0;
    }

    private @NonNull ChatClientRequest before(@NonNull ChatClientRequest request) {
        log.info("AI Request: {}", request.prompt());
        return request;
    }

    private void observeAfter(@NonNull ChatClientResponse chatClientResponse) {
        var response = chatClientResponse.chatResponse();
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            log.info("AI Response: <empty>");
            return;
        }
        log.info("AI Response: {}", response.getResult().getOutput().getText());
    }

    @Override
    public @NonNull ChatClientResponse adviseCall(@NonNull ChatClientRequest chatClientRequest, @NonNull CallAdvisorChain chain) {
        ChatClientRequest safeRequest = before(chatClientRequest);
        ChatClientResponse chatClientResponse = Objects.requireNonNull(chain.nextCall(safeRequest), "chatClientResponse");
        observeAfter(chatClientResponse);
        return chatClientResponse;
    }

    @Override
    public @NonNull Flux<ChatClientResponse> adviseStream(@NonNull ChatClientRequest chatClientRequest, @NonNull StreamAdvisorChain chain) {
        ChatClientRequest safeRequest = before(chatClientRequest);
        Flux<ChatClientResponse> chatClientResponseFlux = Objects.requireNonNull(chain.nextStream(safeRequest), "chatClientResponseFlux");
        return new ChatClientMessageAggregator().aggregateChatClientResponse(chatClientResponseFlux, this::observeAfter);
    }
}

