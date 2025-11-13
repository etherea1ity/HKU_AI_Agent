package com.hku.hkuaiagent.rag;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class HkuAiDocumentLoaderTest {

    @Resource
    private HkuAiDocumentLoader hkuAiDocumentLoader;

    @Test
    void loadMarkdowns() {
        hkuAiDocumentLoader.loadMarkdowns();
    }
}
