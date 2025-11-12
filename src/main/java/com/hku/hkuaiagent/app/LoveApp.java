package com.hku.hkuaiagent.app;

import com.hku.hkuaiagent.advisor.MyLoggerAdvisor;
import com.hku.hkuaiagent.advisor.ReReadingAdvisor;
import com.hku.hkuaiagent.chatmemory.FileBasedChatMemory;
import com.hku.hkuaiagent.rag.QueryRewriter;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;

@Component
@Slf4j
public class LoveApp {

    private final ChatClient chatClient;

    private static final String SYSTEM_PROMPT = """
            你是一个HKU校园大使，用来解决校园一些情况
            """;

    private static final String SYSTEM_PROMPT1 = """
    You are "HKU Campus Companion", an AI concierge that supports current and prospective students, staff, and researchers of The University of Hong Kong.
            
            Conversation principles:
            1. Opening: greet the user, restate your role, and offer help with HKU academics, campus life, administrative matters, or research resources.
            2. Clarify intent: ask short follow-up questions when the request is ambiguous or lacks context (for example, programme, faculty, semester, policy year).
            3. Knowledge usage: rely on official HKU documents, calendars, regulations, news, and FAQs supplied in the conversation or retrieved via the RAG knowledge base, and cite the document title and year in parentheses when referencing specific facts.
            4. If information is missing or outdated, state the limitation and suggest the exact office, webpage, or hotline to confirm.
            5. Tone: stay professional, concise, and empathetic; encourage responsible conduct and adherence to HKU policies.
            6. Provide structured responses: use bullet points or numbered steps for procedures, include important deadlines, eligibility criteria, and contact points.
            7. Do not invent data such as locations, contacts, or policy numbers; when unsure, say "I am not certain" and guide the user to official HKU sources.
            8. When relevant, summarize the key takeaways at the end and highlight urgent actions.

            Your goal is to deliver actionable guidance grounded in verified HKU resources while remaining transparent about any uncertainties.
            """;
    /**
     * 初始化 ChatClient
     *
     * @param dashscopeChatModel
     */
    public LoveApp(ChatModel dashscopeChatModel) {

//        // 初始化基于文件的对话记忆
//        String fileDir = System.getProperty("user.dir") + "/tmp/chat-memory";
//        ChatMemory chatMemory = new FileBasedChatMemory(fileDir);
        log.info("[LoveApp] hit doChat666");
        // 初始化基于内存的对话记忆
        MessageWindowChatMemory chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(new InMemoryChatMemoryRepository()) // ChatMemory授课部分，保存到内存中
                .maxMessages(20) // AI一次对话中最多记住多少条最近的消息
                .build();

        chatClient = ChatClient.builder(dashscopeChatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build() // ChatMemoryAdvisor部分，保留完整的对话结构，包括助手、用户、系统
                        // 自定义日志 Advisor，可按需开启
//                        new MyLoggerAdvisor()
//                        // 自定义推理增强 Advisor，可按需开启
//                       ,new ReReadingAdvisor()
                )
                .build();
    }

    /**
     * AI 基础对话（支持多轮对话记忆）
     *
     * @param message
     * @param chatId
     * @return
     */
    public String doChat(String message, String chatId) {
        log.info("[LoveApp] hit doChat1");
        ChatResponse chatResponse = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId)) // advisor部分，主要是存用户id，才能识别不同用户  点开param发现是map，所以存K-V
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }

    /**
     * AI 基础对话（支持多轮对话记忆，SSE 流式传输）
     *
     * @param message
     * @param chatId
     * @return
     */
    public Flux<String> doChatByStream(String message, String chatId) {
        log.info("[LoveApp] hit doChat2");
        return chatClient
                .prompt()
                .system(SYSTEM_PROMPT + """
                        每句话前面加上"jojojo~"
                        """)
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .stream()
                .content();
    }
    record LoveReport(String title, List<String> suggestions) {

    }

    /**
     * AI 恋爱报告功能（实战结构化输出）
     *
     * @param message
     * @param chatId
     * @return
     */
    public LoveReport doChatWithReport(String message, String chatId) {
        log.info("[LoveApp] hit doChat3");
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
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .call()
                .entity(LoveReport.class);
        log.info("loveReport: {}", loveReport);
        return loveReport;
    }

    // 改成HKU RAG 知识库问答

        @Autowired(required = false)
        @Nullable
        private VectorStore loveAppVectorStore;

        @Autowired(required = false)
        @Nullable
        private Advisor loveAppRagCloudAdvisor;

        @Autowired(required = false)
        @Nullable
        private VectorStore pgVectorVectorStore;

        @Autowired(required = false)
        @Nullable
        private QueryRewriter queryRewriter;

    /**
     * 和 RAG 知识库进行对话
     *
     * @param message
     * @param chatId
     * @return
     */
    public String doChatWithRag(String message, String chatId) {
        log.info("[LoveApp] hit doChat4");
                if (loveAppVectorStore == null) {
                        return "RAG 知识库未启用，请检查配置后重试。";
                }
                // 查询重写
                String rewrittenMessage = queryRewriter != null ? queryRewriter.doQueryRewrite(message) : message;
        ChatResponse chatResponse = chatClient
                .prompt()
                // 使用改写后的查询
                .user(rewrittenMessage)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                // 开启日志，便于观察效果
                .advisors(new MyLoggerAdvisor())
                // 应用 RAG 知识库问答
                .advisors(new QuestionAnswerAdvisor(loveAppVectorStore))
                // 应用 RAG 检索增强服务（基于云知识库服务）
//                .advisors(loveAppRagCloudAdvisor)
                // 应用 RAG 检索增强服务（基于 PgVector 向量存储）
//                .advisors(new QuestionAnswerAdvisor(pgVectorVectorStore))
                // 应用自定义的 RAG 检索增强服务（文档查询器 + 上下文增强器）
//                .advisors(
//                        LoveAppRagCustomAdvisorFactory.createLoveAppRagCustomAdvisor(
//                                loveAppVectorStore, "单身"
//                        )
//                )
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }

    // AI 调用工具能力
    @Resource
    private ToolCallback[] allTools;

    /**
     * AI 恋爱报告功能（支持调用工具）
     *
     * @param message
     * @param chatId
     * @return
     */
    public String doChatWithTools(String message, String chatId) {
        log.info("[LoveApp] hit doChat5");
        ChatResponse chatResponse = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                // 开启日志，便于观察效果
                .advisors(new MyLoggerAdvisor())
                .toolCallbacks(allTools)
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }

    // AI 调用 MCP 服务

    @Resource
    private ToolCallbackProvider toolCallbackProvider;

    /**
     * AI 恋爱报告功能（调用 MCP 服务）
     *
     * @param message
     * @param chatId
     * @return
     */
    public String doChatWithMcp(String message, String chatId) {
        log.info("[LoveApp] hit doChat6");
        ChatResponse chatResponse = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                // 开启日志，便于观察效果
                .advisors(new MyLoggerAdvisor())
                .toolCallbacks(toolCallbackProvider)
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }
}

