package com.hku.hkuaiagent.rag;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
import org.springframework.stereotype.Component;

@Component
public class QueryRewriter {

    private final QueryTransformer queryTransformer;

    public QueryRewriter(ChatModel dashscopeChatModel) {
        ChatClient.Builder builder = ChatClient.builder(dashscopeChatModel);
        queryTransformer = RewriteQueryTransformer.builder()
                .chatClientBuilder(builder)
                .build();
    }

    /**
     * 入口：自动识别课程编号 + 学期（中英文）+ 课程前缀对比
     */
    public String doQueryRewrite(String prompt) {
        if (prompt == null) return "";
        String lower = prompt.toLowerCase();

        /* ====== 1. 第二学期快速通道（中英文） ====== */
        if (lower.contains("第二学期") ||
                lower.contains("semester 2") ||
                lower.contains("s2") ||
                lower.contains("spring semester") ||   // ➕ 英文
                lower.contains("second semester")) {   // ➕ 英文
            return "List all courses offered in Semester 2 (2025-26S2) with course code, instructor, content summary, exam period and key differences.";
        }

        /* ====== 2. 第一学期快速通道（中英文） ====== */
        if (lower.contains("第一学期") ||
                lower.contains("semester 1") ||
                lower.contains("s1") ||
                lower.contains("fall semester") ||     // ➕ 英文
                lower.contains("first semester")) {    // ➕ 英文
            return "List all courses offered in Semester 1 (2025-26S1) with course code, instructor, content summary, exam period and key differences.";
        }

        /* ====== 3. 课程前缀识别（如 COMP7103）并触发对比 ====== */
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("\\b(comp\\d{4})[a-z]?\\b", java.util.regex.Pattern.CASE_INSENSITIVE);
        java.util.regex.Matcher m = p.matcher(lower);
        if (m.find()) {
            String prefix = m.group(1).toUpperCase(); // 如 COMP7103
            return "List all versions of " + prefix + " (A/B/C/D) with instructors, semesters, schedules, exam dates, and add/drop deadlines. Compare them and recommend the best one.";
        }

        /* ====== 4. 默认重写 ====== */
        Query query = new Query(prompt);
        return queryTransformer.transform(query).text();
    }
}