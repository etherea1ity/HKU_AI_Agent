package com.hku.hkuaiagent.rag;

import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Token-based splitter tailored for HKU course documents.
 */
@Component
class MyTokenTextSplitter {
    public List<Document> splitDocuments(List<Document> documents) {
        TokenTextSplitter splitter = new TokenTextSplitter();
        return splitter.apply(documents);
    }

    public List<Document> splitCustomized(List<Document> documents) {
        // Custom configuration tuned for structured HKU course notes
        TokenTextSplitter splitter = new TokenTextSplitter(
            300,    // chunkSize: 300 tokens per chunk keeps modules intact
            50,     // chunkOverlap: 50-token overlap to preserve tabular context
            10,     // chunkNum: cap on chunk count (default)
            5000,   // maxTokensPerDocument: leave at default maximum
            true    // keepSeparator: retain headers and separators for structure
        );
        return splitter.apply(documents);
    }
}