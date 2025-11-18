package com.hku.hkuaiagent.demo.rag;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.expansion.MultiQueryExpander;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * Demo component showcasing how to expand a query into multiple variants.
 */
@Component
public class MultiQueryExpanderDemo {

    private final ChatClient.Builder chatClientBuilder;

    public MultiQueryExpanderDemo(ChatModel dashscopeChatModel) {
        this.chatClientBuilder = ChatClient.builder(Objects.requireNonNull(dashscopeChatModel));
    }

    public List<Query> expand(String query) {
        MultiQueryExpander queryExpander = MultiQueryExpander.builder()
                .chatClientBuilder(Objects.requireNonNull(chatClientBuilder))
                .numberOfQueries(3)
                .build();
        return queryExpander.expand(new Query(Objects.requireNonNull(query)));
    }
}

