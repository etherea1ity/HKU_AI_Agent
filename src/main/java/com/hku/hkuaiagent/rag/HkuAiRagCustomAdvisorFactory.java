package com.hku.hkuaiagent.rag;

import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;

/**
 * Factory for HKU retrieval advisors, compatible with older spring-ai releases.
 */
public class HkuAiRagCustomAdvisorFactory {

    /**
     * Build an advisor that filters by category and/or semester (either argument may be null).
     */
    public static Advisor createHkuAiRagCustomAdvisor(VectorStore vectorStore,
                                                      String category,
                                                      String semester) {
        FilterExpressionBuilder b = new FilterExpressionBuilder();
        Filter.Expression expr = null;

        if (category != null && semester != null) {
            expr = b.and(b.eq("category", category), b.eq("semester", semester)).build();
        } else if (category != null) {
            expr = b.eq("category", category).build();
        } else if (semester != null) {
            expr = b.eq("semester", semester).build();
        }

        DocumentRetriever retriever = VectorStoreDocumentRetriever.builder()
                .vectorStore(vectorStore)
                .filterExpression(expr)
                .similarityThreshold(0.0)
                .topK(5)
                .build();

        return RetrievalAugmentationAdvisor.builder()
                .documentRetriever(retriever)
                .queryAugmenter(HkuAiContextualQueryAugmenterFactory.createInstance())
                .build();
    }

    public static Advisor createHkuAiRagCustomAdvisor(VectorStore vectorStore, String status) {
        return createHkuAiRagCustomAdvisor(vectorStore, status, (String) null);
    }

    /**
     * Build an advisor with custom topK retrieval limits.
     */
    public static Advisor createHkuAiRagCustomAdvisor(VectorStore vectorStore,
                                                      String category,
                                                      String semester,
                                                      int topK) {
        FilterExpressionBuilder b = new FilterExpressionBuilder();
        Filter.Expression expr = null;

        if (category != null && semester != null) {
            expr = b.and(b.eq("category", category), b.eq("semester", semester)).build();
        } else if (category != null) {
            expr = b.eq("category", category).build();
        } else if (semester != null) {
            expr = b.eq("semester", semester).build();
        }

        DocumentRetriever retriever = VectorStoreDocumentRetriever.builder()
                .vectorStore(vectorStore)
                .filterExpression(expr)
                .similarityThreshold(0.0)
                .topK(topK)
                .build();

        return RetrievalAugmentationAdvisor.builder()
                .documentRetriever(retriever)
                .queryAugmenter(HkuAiContextualQueryAugmenterFactory.createInstance())
                .build();
    }
}
