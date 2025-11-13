package com.hku.hkuaiagent.rag;

import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;

/**
 * HKU 校园助手 RAG 检索增强顾问工厂（兼容低版本 spring-ai）。
 */
public class HkuAiRagCustomAdvisorFactory {

    /**
     * 支持按 category + semester 过滤（任一可为 null）。
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
     * 支持自定义 topK。
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
