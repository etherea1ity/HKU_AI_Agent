package com.hku.hkuimagesearchmcpserver.tools;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class ImageSearchToolTest {

    @Resource
    private ImageSearchTool imageSearchTool;

    @Test
    void searchImage() {
        String result = imageSearchTool.searchImage("Hong Kong");
        Assertions.assertNotNull(result);
    }
}

