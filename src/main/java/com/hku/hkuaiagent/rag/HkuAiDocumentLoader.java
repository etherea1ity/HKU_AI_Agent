package com.hku.hkuaiagent.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * HKU 校园助手文档加载器（适配 HKU Markdown 资料）
 */
@Component
@Slf4j
public class HkuAiDocumentLoader {

    private final ResourcePatternResolver resourcePatternResolver;

    public HkuAiDocumentLoader(ResourcePatternResolver resourcePatternResolver) {
        this.resourcePatternResolver = resourcePatternResolver;
    }

    /**
     * 加载多篇 Markdown 文档
     */
    public List<Document> loadMarkdowns() {
        List<Document> allDocuments = new ArrayList<>();
        try {
            Resource[] resources = resourcePatternResolver.getResources("classpath:document/*.md");
            for (Resource resource : resources) {
                String filename = resource.getFilename();
                String category = extractCategory(filename);
                MarkdownDocumentReaderConfig config = MarkdownDocumentReaderConfig.builder()
                        .withHorizontalRuleCreateDocument(true)
                        .withIncludeCodeBlock(true)
                        .withIncludeBlockquote(true)
                        .withAdditionalMetadata("filename", filename)
                        .withAdditionalMetadata("category", category)
                        .withAdditionalMetadata("semester", extractSemester(filename))
                        .build();
                MarkdownDocumentReader markdownDocumentReader = new MarkdownDocumentReader(resource, config);
                allDocuments.addAll(markdownDocumentReader.get());
            }
            log.info("成功加载 {} 篇Markdown文档", allDocuments.size());
            for (Document d : allDocuments) {
                log.info("加载文档：{} -> category={}  semester={}",
                        d.getMetadata().get("filename"),
                        d.getMetadata().get("category"),
                        d.getMetadata().get("semester"));
            }
        } catch (IOException e) {
            log.error("Markdown 文档加载失败", e);
        }
        return allDocuments;
    }

    private String extractCategory(String filename) {
        if (filename == null) {
            return "general";
        }
        int underscoreIndex = filename.indexOf('_');
        if (underscoreIndex > 0) {
            return filename.substring(0, underscoreIndex);
        }
        return "general";
    }

    private String extractSemester(String filename) {
        if (filename == null) {
            return "unknown";
        }
        var m = java.util.regex.Pattern.compile("S[12]").matcher(filename);
        return m.find() ? m.group() : "unknown";
    }
}
