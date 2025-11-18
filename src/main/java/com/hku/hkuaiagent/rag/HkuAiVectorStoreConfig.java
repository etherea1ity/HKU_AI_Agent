package com.hku.hkuaiagent.rag;

import jakarta.annotation.Resource;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Vector store configuration for the HKU assistant using an in-memory SimpleVectorStore.
 */
@Configuration
@ConditionalOnProperty(name = "hku.ai.rag.enabled", havingValue = "true", matchIfMissing = false)
public class HkuAiVectorStoreConfig {

    @Resource
    private HkuAiDocumentLoader documentLoader;

    @Resource
    private MyTokenTextSplitter tokenTextSplitter;

    @Bean
    VectorStore hkuAiVectorStore(EmbeddingModel dashscopeEmbeddingModel) {
        SimpleVectorStore simpleVectorStore = SimpleVectorStore.builder(dashscopeEmbeddingModel).build();

        List<Document> documentList = documentLoader.loadMarkdowns();
        List<Document> splitDocuments = tokenTextSplitter.splitCustomized(documentList);
        simpleVectorStore.add(splitDocuments);

        return simpleVectorStore;
    }
}
