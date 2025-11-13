package com.hku.hkuaiagent.app;

import com.hku.hkuaiagent.advisor.MyLoggerAdvisor;
import com.hku.hkuaiagent.rag.HkuAiDocumentLoader;
import com.hku.hkuaiagent.rag.HkuAiRagCustomAdvisorFactory;
import com.hku.hkuaiagent.rag.QueryRewriter;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.stream.Collectors;

@Component
@Slf4j
public class HkuApp {

    private final ChatClient chatClient;

    @Autowired(required = false)
    @Nullable
    private VectorStore hkuAiVectorStore;

    @Autowired(required = false)
    @Nullable
    private QueryRewriter queryRewriter;

    @Resource
    private ToolCallback[] allTools;

    @Resource
    private ToolCallbackProvider toolCallbackProvider;

    @Resource
    private HkuAiDocumentLoader hkuAiDocumentLoader;

    private static final String SYSTEM_PROMPT = """
            You are 'HKU Campus Companion', an AI concierge for The University of Hong Kong.

            STRICT RULES:
            1. If the user mentions a course code like COMP7103, COMP7106, etc., ALWAYS:
               - First identify the course code prefix (e.g., COMP7103).
               - List ALL available versions (A/B/C/D) with:
                 - Instructor
                 - Semester (S1/S2)
                 - Schedule
                 - Exam date
                 - Add/drop deadline
               - Then recommend the most suitable version based on user preferences (e.g., time, instructor, semester).
            2. If the user asks for "second semester courses" or "S2 courses", ONLY list courses where semester=S2.
            3. If the user asks for "course selection advice" for a code like COMP7103, you MUST compare ALL versions (A/B/C/D) and give a structured comparison.
            4. Never invent courses not found in the RAG knowledge base.
            5. Always cite the document filename in parentheses when referencing facts.
            """;

    public HkuApp(ChatModel dashscopeChatModel) {
        MessageWindowChatMemory chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(new InMemoryChatMemoryRepository())
                .maxMessages(20)
                .build();
        this.chatClient = ChatClient.builder(dashscopeChatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        new MyLoggerAdvisor())
                .build();
    }

    /* ===================== 对外聊天入口（含强制拦截） ===================== */

    public String doChat(String message, String chatId) {
        /* ⬇️ 强制拦截学期 / 课程前缀 ⬇️ */
        String lower = message.toLowerCase();
        if (lower.contains("semester 2") || lower.contains("s2") || lower.contains("second semester") || lower.contains("spring semester")) {
            log.info("[走向] 强制拦截 semester 2 → 走 doChatWithRag");
            return doChatWithRag(message, chatId);
        }
        if (lower.contains("semester 1") || lower.contains("s1") || lower.contains("first semester") || lower.contains("fall semester")) {
            log.info("[走向] 强制拦截 semester 1 → 走 doChatWithRag");
            return doChatWithRag(message, chatId);
        }
        if (lower.contains("comp7103")) return doChatWithCoursePrefix(message, chatId, "COMP7103");
        if (lower.contains("comp7106")) return doChatWithCoursePrefix(message, chatId, "COMP7106");

        /* 原逻辑保持不变 */
        ChatResponse resp = chatClient.prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .advisors(new QuestionAnswerAdvisor(hkuAiVectorStore))
                .advisors(HkuAiRagCustomAdvisorFactory.createHkuAiRagCustomAdvisor(hkuAiVectorStore, "course"))
                .call()
                .chatResponse();
        return resp.getResult().getOutput().getText();
    }

    public Flux<String> doChatByStream(String message, String chatId) {
        return chatClient.prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .advisors(new QuestionAnswerAdvisor(hkuAiVectorStore))
                .advisors(HkuAiRagCustomAdvisorFactory.createHkuAiRagCustomAdvisor(hkuAiVectorStore, "course"))
                .stream()
                .content();
    }

    /* ===================== 手动加载文档 + 前缀过滤（兼容 1.0.0） ===================== */

    public String doChatWithCoursePrefix(String message, String chatId, String coursePrefix) {
        String targetPrefix = "course_" + coursePrefix.toUpperCase(); // 如 course_COMP7103

        // 1️⃣ 手动重新加载所有文档（不走 VectorStore）
        List<Document> allDocs = hkuAiDocumentLoader.loadMarkdowns();

        // 2️⃣ 手动过滤 filename 前缀
        List<Document> filtered = allDocs.stream()
                .filter(d -> {
                    String fn = (String) d.getMetadata().get("filename");
                    return fn != null && fn.startsWith(targetPrefix);
                })
                .collect(Collectors.toList());

        if (filtered.isEmpty()) {
            return "未找到课程前缀 " + coursePrefix + " 的任何版本。";
        }

        // 3️⃣ 构造增强上下文
        StringBuilder sb = new StringBuilder();
        for (Document d : filtered) {
            sb.append(d.getText()).append("\n------\n");
        }
        String augmented = "以下是匹配的课程文档：\n" + sb;

        // 4️⃣ 发给模型
        ChatResponse resp = chatClient.prompt()
                .system(SYSTEM_PROMPT + "\n" + augmented)
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .call()
                .chatResponse();
        return resp.getResult().getOutput().getText();
    }

    /* ===================== 带学期过滤的 RAG 入口（已放大 topK） ===================== */

    public String doChatWithRag(String message, String chatId) {
        if (hkuAiVectorStore == null) {
            return "RAG 知识库未启用，请检查配置后重试。";
        }
        String rewritten = queryRewriter != null ? queryRewriter.doQueryRewrite(message) : message;

        String lower = message.toLowerCase();
        boolean isS2 = lower.contains("semester 2") || lower.contains("s2") || lower.contains("second semester") || lower.contains("spring semester");
        boolean isS1 = lower.contains("semester 1") || lower.contains("s1") || lower.contains("first semester") || lower.contains("fall semester");
        String semester = isS2 ? "S2" : (isS1 ? "S1" : null);

        String manualContext = semester != null ? buildSemesterCourseContext(semester) : null;
        log.info("[RAG] message='{}' semester={} manualContextNull={} ", message, semester, manualContext == null);

        Advisor advisor = HkuAiRagCustomAdvisorFactory.createHkuAiRagCustomAdvisor(
                hkuAiVectorStore, "course", semester, 10);

        var promptBuilder = chatClient.prompt();
        if (manualContext != null && !manualContext.isBlank()) {
            promptBuilder = promptBuilder.system(SYSTEM_PROMPT + "\n\n" + manualContext);
        }

        ChatResponse resp = promptBuilder
                .user(rewritten)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .advisors(new MyLoggerAdvisor())
                .advisors(advisor)
                .call()
                .chatResponse();

        return resp.getResult().getOutput().getText();
    }

    private String buildSemesterCourseContext(String semester) {
        List<Document> allDocs = hkuAiDocumentLoader.loadMarkdowns();
        List<Document> filtered = allDocs.stream()
                .filter(d -> {
                    String filename = asString(d.getMetadata().get("filename"));
                    if (filename == null) {
                        return false;
                    }
                    String normalized = filename.toUpperCase();
                    boolean matchSemester = normalized.contains(semester.toUpperCase() + ".MD");
                    boolean matchCourse = normalized.startsWith("COURSE_");
                    return matchSemester && matchCourse;
                })
                .collect(Collectors.toList());
        if (filtered.isEmpty()) {
            log.info("[RAG] {} semester found no matching documents via filename filter", semester);
            return null;
        }
        log.info("[RAG] {} semester matched {} documents", semester, filtered.size());
        StringBuilder sb = new StringBuilder();
        sb.append("### HKU Semester ").append(semester).append(" Course Digest\n");
        for (Document doc : filtered) {
            String filename = asString(doc.getMetadata().get("filename"));
            String courseCode = extractCourseCode(filename);
            String courseName = extractCourseName(filename);
            String instructor = extractBetween(doc.getText(), "Instructor:", "Semester");
            String examPeriod = extractAfter(doc.getText(), "Examination Period:");
            String intro = extractIntroduction(doc.getText());
            sb.append("- ").append(courseCode != null ? courseCode : "Unknown Code");
            if (courseName != null) {
                sb.append(" – ").append(courseName);
            }
            if (instructor != null) {
                sb.append(" (Instructor: ").append(instructor).append(")");
            }
            sb.append(" [Source: ").append(filename).append("]\n");
            if (intro != null) {
                sb.append("  Summary: ").append(intro).append('\n');
            }
            if (examPeriod != null) {
                sb.append("  Exam Period: ").append(examPeriod).append('\n');
            }
        }
        return sb.toString();
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String extractCourseCode(String filename) {
        if (filename == null) {
            return null;
        }
        String[] parts = filename.split("_");
        if (parts.length < 2) {
            return null;
        }
        return parts[1];
    }

    private String extractCourseName(String filename) {
        if (filename == null) {
            return null;
        }
        String[] parts = filename.replace(".md", "").split("_");
        if (parts.length < 3) {
            return null;
        }
        StringBuilder name = new StringBuilder();
        for (int i = 2; i < parts.length - 1; i++) {
            if (name.length() > 0) {
                name.append(' ');
            }
            name.append(parts[i]);
        }
        return name.toString();
    }

    private String extractBetween(String text, String startLabel, String endLabel) {
        if (text == null) {
            return null;
        }
        String lowerText = text;
        int startIdx = lowerText.indexOf(startLabel);
        if (startIdx < 0) {
            return null;
        }
        startIdx += startLabel.length();
        int endIdx = endLabel == null ? -1 : lowerText.indexOf(endLabel, startIdx);
        String segment = endIdx > startIdx ? lowerText.substring(startIdx, endIdx) : lowerText.substring(startIdx);
        segment = segment.replaceAll("\r", " ").replaceAll("\n", " ").trim();
        return segment.isEmpty() ? null : truncate(segment, 120);
    }

    private String extractAfter(String text, String label) {
        if (text == null) {
            return null;
        }
        int idx = text.indexOf(label);
        if (idx < 0) {
            return null;
        }
        idx += label.length();
        int endIdx = text.indexOf('\n', idx);
        String result = endIdx > idx ? text.substring(idx, endIdx) : text.substring(idx);
        result = result.replaceAll("\r", " ").trim();
        return result.isEmpty() ? null : truncate(result, 120);
    }

    private String extractIntroduction(String text) {
        if (text == null) {
            return null;
        }
        int idx = text.indexOf("2. Course Introduction");
        if (idx < 0) {
            return null;
        }
        String snippet = text.substring(idx);
        snippet = snippet.replaceAll("\r", " ");
        int nextSection = snippet.indexOf("3.");
        if (nextSection > 0) {
            snippet = snippet.substring(0, nextSection);
        }
        snippet = snippet.replaceAll("\s+", " ").trim();
        return snippet.isEmpty() ? null : truncate(snippet, 200);
    }

    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }

    /* ===================== 工具 / MCP 入口 ===================== */

    public String doChatWithTools(String message, String chatId) {
        ChatResponse resp = chatClient.prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .advisors(new MyLoggerAdvisor())
                .toolCallbacks(allTools)
                .call()
                .chatResponse();
        return resp.getResult().getOutput().getText();
    }

    public String doChatWithMcp(String message, String chatId) {
        ChatResponse resp = chatClient.prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .advisors(new MyLoggerAdvisor())
                .toolCallbacks(toolCallbackProvider)
                .call()
                .chatResponse();
        return resp.getResult().getOutput().getText();
    }

    /* ===================== 结构化恋爱报告（保留原签名） ===================== */

    public record LoveReport(String title, List<String> suggestions) {}

    public LoveReport doChatWithReport(String message, String chatId) {
        LoveReport report = chatClient.prompt()
                .system(SYSTEM_PROMPT + "每次对话后都要生成恋爱结果，标题为{用户名}的恋爱报告，内容为建议列表")
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .call()
                .entity(LoveReport.class);
        log.info("loveReport: {}", report);
        return report;
    }
}
