package com.hku.hkuaiagent.rag;

import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;

/**
 * HKU 校园助手上下文查询增强器工厂。
 */
public class HkuAiContextualQueryAugmenterFactory {

    public static ContextualQueryAugmenter createInstance() {
        PromptTemplate emptyContextPromptTemplate = new PromptTemplate("""
                Sorry, I can't find relevant information about your query. 
                Please try the following:
                1. Check if the course code, semester, or keyword is correct (e.g., COMP7103A, Semester 1 2025-26)
                2. Ask questions related to HKU courses, campus regulations, academic schedules, etc.
                3. Contact HKU IT Support for further assistance if needed.
                """);

        return ContextualQueryAugmenter.builder()
                .allowEmptyContext(false)
                .emptyContextPromptTemplate(emptyContextPromptTemplate)
                .build();
    }
}
