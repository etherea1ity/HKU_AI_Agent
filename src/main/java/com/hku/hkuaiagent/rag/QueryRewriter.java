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
     * Entry point that identifies course codes, semester hints (both English and Chinese), and triggers tailored rewrites.
     */
    public String doQueryRewrite(String prompt) {
        if (prompt == null) return "";
        String lower = prompt.toLowerCase();

        /* ====== 1. Fast path for second-semester questions (Chinese + English variants) ====== */
        if (lower.contains("\u7b2c\u4e8c\u5b66\u671f") || // supports Chinese "second semester"
                lower.contains("semester 2") ||
                lower.contains("s2") ||
                lower.contains("spring semester") ||   // extra English keywords
                lower.contains("second semester")) {   // extra English keywords
            return "List all courses offered in Semester 2 (2025-26S2) with course code, instructor, content summary, exam period and key differences.";
        }

        /* ====== 2. Fast path for first-semester questions (Chinese + English variants) ====== */
        if (lower.contains("\u7b2c\u4e00\u5b66\u671f") || // supports Chinese "first semester"
                lower.contains("semester 1") ||
                lower.contains("s1") ||
                lower.contains("fall semester") ||     // extra English keywords
                lower.contains("first semester")) {    // extra English keywords
            return "List all courses offered in Semester 1 (2025-26S1) with course code, instructor, content summary, exam period and key differences.";
        }

        /* ====== 3. Course prefix recognition (e.g. COMP7103) to request comparisons ====== */
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("\\b(comp\\d{4})[a-z]?\\b", java.util.regex.Pattern.CASE_INSENSITIVE);
        java.util.regex.Matcher m = p.matcher(lower);
        if (m.find()) {
            String prefix = m.group(1).toUpperCase(); // e.g. COMP7103
            return "List all versions of " + prefix + " (A/B/C/D) with instructors, semesters, schedules, exam dates, and add/drop deadlines. Compare them and recommend the best one.";
        }

        /* ====== 4. Default rewrite ====== */
        Query query = new Query(prompt);
        return queryTransformer.transform(query).text();
    }
}