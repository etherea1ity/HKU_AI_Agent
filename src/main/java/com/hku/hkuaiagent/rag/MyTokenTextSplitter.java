package com.hku.hkuaiagent.rag;

import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 自定义基于 Token 的切词器（适配HKU课程结构化文档）
 */
@Component
class MyTokenTextSplitter {
    public List<Document> splitDocuments(List<Document> documents) {
        TokenTextSplitter splitter = new TokenTextSplitter();
        return splitter.apply(documents);
    }

    public List<Document> splitCustomized(List<Document> documents) {
        // 优化：调整切分参数，适配HKU课程文档（结构化、关键信息集中）
        TokenTextSplitter splitter = new TokenTextSplitter(
                300,    // chunkSize：每块300Token（课程文档单模块内容适中，无需过细）
                50,     // chunkOverlap：重叠50Token（减少关键信息拆分，如表格、列表）
                10,     // chunkNum：最大切分块数（默认即可）
                5000,   // maxTokensPerDocument：单文档最大Token数（默认即可）
                true    // keepSeparator：保留分隔符（如标题符号、表格分隔符，保持结构）
        );
        return splitter.apply(documents);
    }
}