package com.hku.hkuaiagent.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hku.hkuaiagent.rag.QueryRewriter;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.lang.Nullable;
import org.springframework.util.FileCopyUtils;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 访问 HKU 向量知识库的工具，优先用于课程与校园信息检索。
 */
public class CampusKnowledgeSearchTool {

    private final VectorStore vectorStore;
    private final QueryRewriter queryRewriter;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CampusKnowledgeSearchTool(VectorStore vectorStore, @Nullable QueryRewriter queryRewriter) {
        this.vectorStore = vectorStore;
        this.queryRewriter = queryRewriter;
    }

    @Tool(description = "Search HKU campus knowledge base (RAG) and summarise findings")
    public String campusRagSearch(
            @ToolParam(description = "User question about HKU campus, courses, policies, etc.") String question,
            @ToolParam(description = "Number of entries to return (default 3)", required = false) Integer topK
    ) {
        if (question == null || question.isBlank()) {
            return "{\"summary\":\"问题内容为空，无法执行 HKU 知识库检索。\"}";
        }
        String courseCode = extractCourseCode(question);
        String rewritten = queryRewriter != null ? queryRewriter.doQueryRewrite(question) : question;
        String safeQuery = (rewritten != null && !rewritten.isBlank()) ? rewritten : question;
        if (courseCode == null) {
            courseCode = extractCourseCode(safeQuery);
        }
        boolean isCourseQuery = courseCode != null;

        if (vectorStore == null && !isCourseQuery) {
            return "{\"summary\":\"HKU RAG knowledge base is not configured. 请启用 hku.ai.rag.enabled 或提供更具体的课程代码以返回本地资料。\"}";
        }

        int defaultLimit = isCourseQuery ? 5 : 3;
        int limit = (topK != null && topK > 0) ? Math.min(topK, 5) : defaultLimit;
        List<Document> documents = null;

        if (vectorStore != null) {
            documents = vectorStore.similaritySearch(
                SearchRequest.builder()
                    .query(safeQuery)
                    .topK(limit)
                    .build()
            );

            // 如果重写后的查询没有命中，尝试回退到原始问题
            if ((documents == null || documents.isEmpty()) && !safeQuery.equals(question)) {
                documents = vectorStore.similaritySearch(
                    SearchRequest.builder()
                        .query(question)
                        .topK(limit)
                        .build()
                );
            }
        }

        if (courseCode != null) {
            documents = enrichCourseDocuments(documents, courseCode);
        }

        if (documents == null || documents.isEmpty()) {
            if (isCourseQuery) {
                return "{\"summary\":\"未找到与课程" + courseCode + "相关的本地资料，请确认课程代码是否正确。\"}";
            }
            if (vectorStore == null) {
                return "{\"summary\":\"HKU RAG knowledge base is not configured. 请启用 hku.ai.rag.enabled 以检索更多信息。\"}";
            }
            return "{\"summary\":\"未在 HKU 知识库中找到与问题直接匹配的资料。请尝试提供更多上下文。\"}";
        }

        ArrayNode sources = objectMapper.createArrayNode();
        for (int index = 0; index < documents.size(); index++) {
            Document document = documents.get(index);
            ObjectNode source = objectMapper.createObjectNode();
            source.put("title", resolveTitle(document));
            source.put("snippet", buildSnippet(document.getText()));
            if (document.getMetadata() != null) {
                Object filename = document.getMetadata().get("filename");
                if (filename != null) {
                    source.put("source", String.valueOf(filename));
                }
                Object topic = document.getMetadata().get("topic");
                if (topic != null) {
                    source.put("topic", String.valueOf(topic));
                }
            }
            source.put("rank", index + 1);
            sources.add(source);
        }

        String summary = buildSummary(documents);

        ObjectNode response = objectMapper.createObjectNode();
        response.put("summary", summary);
        response.set("sources", sources);
        return response.toString();
    }

    private List<Document> enrichCourseDocuments(List<Document> documents, String courseCode) {
        List<Document> baseList = documents != null ? new ArrayList<>(documents) : new ArrayList<>();
        Map<String, Document> variantMap = new LinkedHashMap<>();
        List<Document> nonCourseDocuments = new ArrayList<>();

        for (Document doc : baseList) {
            String variant = extractVariant(doc, courseCode);
            if (variant != null) {
                variantMap.putIfAbsent(variant, doc);
            } else {
                nonCourseDocuments.add(doc);
            }
        }

        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        try {
            Resource[] resources = resolver.getResources("classpath*:document/course_" + courseCode + "*.md");
            Map<String, Document> loaded = loadCourseDocuments(resources, courseCode);
            for (Map.Entry<String, Document> entry : loaded.entrySet()) {
                variantMap.putIfAbsent(entry.getKey(), entry.getValue());
            }
        } catch (IOException ignored) {
            // 如果本地文件无法读取，也不阻塞正常返回
        }

        List<Document> merged = new ArrayList<>();
        variantMap.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(courseVariantComparator()))
                .forEach(entry -> merged.add(entry.getValue()));
        merged.addAll(nonCourseDocuments);
        return merged;
    }

    private Comparator<String> courseVariantComparator() {
        return (left, right) -> {
            if (left == null && right == null) {
                return 0;
            }
            if (left == null) {
                return 1;
            }
            if (right == null) {
                return -1;
            }
            if (left.equals(right)) {
                return 0;
            }
            if (left.length() == 0) {
                return -1;
            }
            if (right.length() == 0) {
                return 1;
            }
            return left.compareTo(right);
        };
    }

    private Map<String, Document> loadCourseDocuments(Resource[] resources, String courseCode) throws IOException {
        Map<String, Document> courseDocs = new HashMap<>();
        if (resources == null) {
            return courseDocs;
        }
        for (Resource resource : resources) {
            if (resource == null || !resource.exists()) {
                continue;
            }
            String filename = resource.getFilename();
            String variant = extractVariantFromFilename(filename, courseCode);
            if (variant == null) {
                continue;
            }
            try (InputStreamReader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
                String content = FileCopyUtils.copyToString(reader);
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("filename", filename);
                metadata.put("title", resolveTitleFromContent(filename, content));
                metadata.put("topic", "HKU Course");
                Document document = new Document(content, metadata);
                courseDocs.putIfAbsent(variant, document);
            }
        }
        return courseDocs;
    }

    private String resolveTitleFromContent(String filename, String content) {
        if (content != null) {
            String firstLine = content.lines().findFirst().orElse(null);
            if (firstLine != null && !firstLine.isBlank()) {
                return firstLine.trim();
            }
        }
        return filename != null ? filename.replace(".md", "") : "HKU 资料";
    }

    private String extractVariant(Document document, String courseCode) {
        if (document == null || document.getMetadata() == null) {
            return null;
        }
        Object filename = document.getMetadata().get("filename");
        if (filename == null) {
            return null;
        }
        return extractVariantFromFilename(String.valueOf(filename), courseCode);
    }

    private String extractVariantFromFilename(String filename, String courseCode) {
        if (filename == null) {
            return null;
        }
        String pattern = "course_" + courseCode + "([A-Z]?)_";
        Matcher matcher = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(filename);
        if (matcher.find()) {
            return matcher.group(1).toUpperCase();
        }
        return null;
    }

    private String extractCourseCode(String text) {
        if (text == null) {
            return null;
        }
        Matcher matcher = Pattern.compile("(?i)(comp\\d{4})").matcher(text);
        if (matcher.find()) {
            return matcher.group(1).toUpperCase();
        }
        return null;
    }

    private String buildSummary(List<Document> documents) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < documents.size(); i++) {
            Document document = documents.get(i);
            String title = resolveTitle(document);
            String snippet = buildSnippet(document.getText());
            builder.append(i + 1).append(". ").append(title);
            if (!snippet.isBlank()) {
                builder.append("\n    - ").append(snippet);
            }
            if (i < documents.size() - 1) {
                builder.append("\n\n");
            }
        }
        return builder.toString();
    }

    private String resolveTitle(Document document) {
        if (document == null || document.getMetadata() == null) {
            return "HKU 资料";
        }
        Object title = document.getMetadata().get("title");
        if (title != null) {
            return String.valueOf(title);
        }
        Object filename = document.getMetadata().get("filename");
        if (filename != null) {
            return String.valueOf(filename);
        }
        return "HKU 资料";
    }

    private String buildSnippet(String text) {
        if (text == null) {
            return "";
        }
        String normalized = text.replaceAll("\\s+", " ").trim();
        return normalized.length() > 180 ? normalized.substring(0, 180) + "…" : normalized;
    }
}
