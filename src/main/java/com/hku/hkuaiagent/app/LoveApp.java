package com.hku.hkuaiagent.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hku.hkuaiagent.advisor.MyLoggerAdvisor;
import com.hku.hkuaiagent.rag.QueryRewriter;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
@Slf4j
public class LoveApp {

    private final ChatClient chatClient;

    @Value("${hku.ai.rag.enabled:false}")
    private boolean ragEnabled;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String SYSTEM_PROMPT = """
            You are the HKU Campus Companion, a friendly concierge who helps students and staff with campus life, logistics, and quick fact-finding.
            Always respond in clear English and keep explanations concise, practical, and encouraging.
            """;

    /**
     * Initialize the shared ChatClient with memory and advisors.
     *
     * @param dashscopeChatModel configured DashScope chat model
     */
    public LoveApp(ChatModel dashscopeChatModel) {
        log.info("[LoveApp] hit doChat666");
        // In-memory conversation history keeps recent turns per chatId
        MessageWindowChatMemory chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(new InMemoryChatMemoryRepository())
                .maxMessages(20)
                .build();

        chatClient = ChatClient.builder(Objects.requireNonNull(dashscopeChatModel))
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build()
                )
                .build();
    }

    /**
     * Run a standard multi-turn chat with conversation memory.
     */
    public String doChat(String message, String chatId) {
        log.info("[LoveApp] hit doChat1");
        String promptMessage = Objects.requireNonNull(message, "message must not be null");
        String conversationId = Objects.requireNonNull(chatId, "chatId must not be null");
        ChatResponse chatResponse = chatClient
                .prompt()
                .user(promptMessage)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .chatResponse();
        String content = extractOutputText(chatResponse);
        log.info("content: {}", content);
        return content;
    }

    /**
     * Stream chat responses with conversation memory via SSE.
     */
    public Flux<String> doChatByStream(String message, String chatId) {
        log.info("[LoveApp] hit doChat2");
        String promptMessage = Objects.requireNonNull(message, "message must not be null");
        String conversationId = Objects.requireNonNull(chatId, "chatId must not be null");

        List<Map<String, String>> ragContexts = resolveRagContext(promptMessage);

        Flux<String> contentFlux = chatClient
                .prompt()
                .system(SYSTEM_PROMPT)
                .user(promptMessage)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, conversationId))
                .stream()
                .content();

        return Flux.create(sink -> {
            sink.next("[THINKING_START]HKU Campus Companion is working on it...");

            if (!ragContexts.isEmpty()) {
                sink.next("[TOOL_CALL]Searching the HKU knowledge base");
                String payload = buildRagContextPayload(ragContexts);
                if (payload != null) {
                    sink.next("[RAG_CONTEXT]" + payload);
                }
            }

            sink.next("[TOOL_CALL]Drafting a response");

            Disposable disposable = contentFlux.subscribe(chunk -> {
                if (chunk != null && !chunk.isBlank()) {
                    sink.next(chunk);
                }
            }, error -> {
                log.error("[LoveApp] SSE error", error);
                sink.next("[THINKING_END]Processing ended with an issue");
                sink.next("Sorry, something went wrong while handling the request. Please try again in a moment.");
                sink.next("[DONE]");
                sink.complete();
            }, () -> {
                sink.next("[THINKING_END]Ready to share the answer");
                sink.next("[DONE]");
                sink.complete();
            });

            sink.onDispose(disposable::dispose);
        });
    }

    record LoveReport(String title, List<String> suggestions) { }

        /**
         * Generate a structured HKU support digest while retaining conversation memory.
         */
    public LoveReport doChatWithReport(String message, String chatId) {
        log.info("[LoveApp] hit doChat3");
        String promptMessage = Objects.requireNonNull(message, "message must not be null");
        String conversationId = Objects.requireNonNull(chatId, "chatId must not be null");
        LoveReport loveReport = chatClient
                .prompt()
                .system(SYSTEM_PROMPT + """
                        Every conversation must end with a "HKU Support Digest" that contains:
                        - Title: "HKU Support Digest for {user_name or 'the user'}".
                        - Summary: 2-3 bullet points recapping the user's situation.
                        - Action items: a numbered list of concrete next steps, including offices or links if available.
                        - Resources: a bullet list of HKU units, webpages, or contacts mentioned above.
                        Ensure the digest reflects only validated information and flag any item that needs user confirmation.
                        """)
                .user(promptMessage)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .entity(LoveReport.class);
        log.info("loveReport: {}", loveReport);
        return loveReport;
    }

        // RAG configuration for the HKU knowledge base

        @Autowired(required = false)
        @Nullable
        private VectorStore loveAppVectorStore;

        @Autowired(required = false)
        @Nullable
        private QueryRewriter queryRewriter;

        /**
         * Converse with the HKU RAG knowledge base using vector search.
         */
    public String doChatWithRag(String message, String chatId) {
        log.info("[LoveApp] hit doChat4");
        if (loveAppVectorStore == null) {
                return "The RAG knowledge base is disabled. Please enable it in the configuration and try again.";
        }
        String promptMessage = Objects.requireNonNull(message, "message must not be null");
        String conversationId = Objects.requireNonNull(chatId, "chatId must not be null");
        String rewrittenMessage = queryRewriter != null ? queryRewriter.doQueryRewrite(promptMessage) : promptMessage;
        String effectivePrompt = (rewrittenMessage != null && !rewrittenMessage.isBlank()) ? rewrittenMessage : promptMessage;
        VectorStore activeStore = Objects.requireNonNull(loveAppVectorStore);

        ChatResponse chatResponse = chatClient
                .prompt()
                .user(effectivePrompt)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, conversationId))
                .advisors(new MyLoggerAdvisor())
                .advisors(new QuestionAnswerAdvisor(activeStore))
                .call()
                .chatResponse();
        String content = extractOutputText(chatResponse);
        log.info("content: {}", content);
        return content;
    }

    // Inject tool callbacks so the agent can invoke Spring AI tools
    @Resource
    private ToolCallback[] allTools;

    /**
     * Run the chat while allowing tool invocations.
     */
    public String doChatWithTools(String message, String chatId) {
        log.info("[LoveApp] hit doChat5");
        String promptMessage = Objects.requireNonNull(message, "message must not be null");
        String conversationId = Objects.requireNonNull(chatId, "chatId must not be null");
        ToolCallback[] toolCallbacks = Objects.requireNonNull(allTools, "allTools must not be null");
        ChatResponse chatResponse = chatClient
                .prompt()
                .user(promptMessage)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, conversationId))
                .advisors(new MyLoggerAdvisor())
                .toolCallbacks(toolCallbacks)
                .call()
                .chatResponse();
        String content = extractOutputText(chatResponse);
        log.info("content: {}", content);
        return content;
    }

    // Inject MCP callback provider for Model Context Protocol tools

    @Resource
    private ToolCallbackProvider toolCallbackProvider;

    /**
     * Run the chat while allowing Model Context Protocol tool invocations.
     */
    public String doChatWithMcp(String message, String chatId) {
        log.info("[LoveApp] hit doChat6");
        String promptMessage = Objects.requireNonNull(message, "message must not be null");
        String conversationId = Objects.requireNonNull(chatId, "chatId must not be null");
        ToolCallbackProvider callbackProvider = Objects.requireNonNull(toolCallbackProvider, "toolCallbackProvider must not be null");
        ChatResponse chatResponse = chatClient
                .prompt()
                .user(promptMessage)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, conversationId))
                .advisors(new MyLoggerAdvisor())
                .toolCallbacks(callbackProvider)
                .call()
                .chatResponse();
        String content = extractOutputText(chatResponse);
        log.info("content: {}", content);
        return content;
    }

    private List<Map<String, String>> resolveRagContext(String message) {
        if (!ragEnabled || loveAppVectorStore == null) {
            return Collections.emptyList();
        }
        String baseMessage = Objects.requireNonNull(message, "message must not be null");
        String rewritten = queryRewriter != null ? queryRewriter.doQueryRewrite(baseMessage) : baseMessage;
        String query = (rewritten != null && !rewritten.isBlank()) ? rewritten : baseMessage;
        if (query.isBlank()) {
            return Collections.emptyList();
        }
        try {
            VectorStore store = Objects.requireNonNull(loveAppVectorStore);
            List<Document> documents = store.similaritySearch(
                    SearchRequest.builder().query(query).topK(3).build());
            if (documents == null || documents.isEmpty()) {
                return Collections.emptyList();
            }
            return documents.stream()
                    .filter(Objects::nonNull)
                    .map(this::toContextEntry)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("[LoveApp] Failed to collect RAG context", e);
            return Collections.emptyList();
        }
    }

    private Map<String, String> toContextEntry(Document document) {
        Map<String, String> entry = new LinkedHashMap<>();
        if (document == null) {
            entry.put("title", "HKU reference material");
            entry.put("snippet", "");
            return entry;
        }
        Map<String, Object> metadata = document.getMetadata();
        Object filename = metadata != null ? metadata.getOrDefault("filename", "HKU reference material") : "HKU reference material";
        entry.put("title", String.valueOf(filename));
        entry.put("snippet", buildSnippet(document.getText()));
        return entry;
    }

    private String buildSnippet(String text) {
        if (text == null) {
            return "";
        }
        String normalized = text.replaceAll("\\s+", " ").trim();
        return normalized.length() > 200 ? normalized.substring(0, 200) + "..." : normalized;
    }

    private String buildRagContextPayload(List<Map<String, String>> contexts) {
        if (contexts == null || contexts.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(Collections.singletonMap("sources", contexts));
        } catch (JsonProcessingException e) {
            log.warn("[LoveApp] Failed to serialize RAG context", e);
            return null;
        }
    }

    private String extractOutputText(@Nullable ChatResponse chatResponse) {
        if (chatResponse == null || chatResponse.getResult() == null
                || chatResponse.getResult().getOutput() == null) {
            return "";
        }
        String text = chatResponse.getResult().getOutput().getText();
        return text != null ? text : "";
    }
}

