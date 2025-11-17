package com.hku.hkuaiagent.tools;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class WebScrapingToolTest {

    @Test
    void scrapeWebPage() {
        WebScrapingTool webScrapingTool = new WebScrapingTool();
        String url = "https://www.hku.hk";
        String result = webScrapingTool.scrapeWebPage(url);
        Assertions.assertNotNull(result);
        System.out.println(result);
    }
}

